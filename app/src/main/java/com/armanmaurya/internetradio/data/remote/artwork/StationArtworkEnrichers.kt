package com.armanmaurya.internetradio.data.remote.artwork

import com.armanmaurya.internetradio.BuildConfig
import com.armanmaurya.internetradio.data.remote.ITunesApiService
import com.armanmaurya.internetradio.data.remote.LastFmApiService
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.InetAddress
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/**
 * Optional online enrichment for stations whose user-provided favicon is empty.
 *
 * Result is cached by [com.armanmaurya.internetradio.data.repository.StationArtworkRepositoryImpl];
 * it is never written into `favicon`.
 */
interface ArtworkEnricher {
    val source: String
    suspend fun enrich(station: RadioStation): String?
}

/** Guard against SSRF: only public http(s) URLs are fetched. */
object NetworkUrlGuard {

    fun parse(raw: String?): HttpUrl? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        return normalized.toHttpUrlOrNull()?.takeIf { isAllowed(it) }
    }

    /** Resolves a relative URL and applies the checks that do not require DNS. */
    fun resolve(raw: String?, base: HttpUrl): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val resolved = base.resolve(trimmed) ?: return null
        return resolved.takeIf { isAllowedText(it) }?.toString()
    }

    /** Scheme + literal-host checks only: safe to call when parsing untrusted HTML. */
    fun isAllowedText(url: HttpUrl): Boolean {
        if (url.scheme != "http" && url.scheme != "https") return false
        val host = url.host
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local", ignoreCase = true)) {
            return false
        }
        return !isPrivateIpLiteral(host)
    }

    private fun isPrivateIpLiteral(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val first = parts[0].toInt()
            val second = parts[1].toInt()
            return first == 0 || first == 10 || first == 127 ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168)
        }
        return false
    }

    fun isAllowed(url: HttpUrl): Boolean {
        if (!isAllowedText(url)) return false
        val host = url.host
        return try {
            InetAddress.getAllByName(host).none { address ->
                address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    address.isMulticastAddress ||
                    isUniqueLocalIpv6(address.address)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isUniqueLocalIpv6(bytes: ByteArray): Boolean =
        bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC

    fun isAllowed(url: String?): Boolean = parse(url) != null
}

/** Name normalisation and fuzzy matching used to reject unrelated covers. */
object StationNameMatcher {

    private val DIACRITICS = Regex("\\p{M}+")
    private val STOP_WORDS = Regex("\\b(fm|am|web|official|live|stream|music|tv)\\b")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    fun normalize(name: String): String {
        val withoutDiacritics = Normalizer.normalize(name, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return withoutDiacritics
            .lowercase(Locale.ROOT)
            .replace(STOP_WORDS, " ")
            .replace(NON_ALNUM, " ")
            .replace(SPACES, " ")
            .trim()
    }

    fun searchTerm(name: String): String {
        val normalized = normalize(name)
        if (normalized.isBlank()) return name.trim()
        // Fallback to original name if the normalized term is too short/generic
        if (normalized.split(' ').size < 2 && normalized.length < 5) {
            return name.trim()
        }
        return normalized
    }

    fun similarity(first: String, second: String?): Double {
        val firstTokens = normalize(first).split(' ').filter { it.length > 1 }.toSet()
        val secondTokens = normalize(second.orEmpty()).split(' ').filter { it.length > 1 }.toSet()
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0
        val intersection = firstTokens.intersect(secondTokens).size.toDouble()
        val union = firstTokens.union(secondTokens).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    fun isMatch(stationName: String, candidateName: String?): Boolean =
        similarity(stationName, candidateName) >= MIN_SIMILARITY

    private const val MIN_SIMILARITY = 0.6
}

/** Extracts the best image URL from a station homepage. Pure function, unit tested. */
fun parseArtworkFromHtml(html: String, base: HttpUrl): String? {
    val metaTags = META_TAG.findAll(html).map { it.value }.toList()
    val fromOpenGraph = metaTags
        .firstOrNull { it.contains("og:image", ignoreCase = true) }
        ?.let { attributeValue(it, "content") }
    val fromTwitter = metaTags
        .firstOrNull { it.contains("twitter:image", ignoreCase = true) }
        ?.let { attributeValue(it, "content") }
    val fromAppleIcon = LINK_TAG.findAll(html)
        .map { it.value }
        .firstOrNull { it.contains("apple-touch-icon", ignoreCase = true) }
        ?.let { attributeValue(it, "href") }

    return sequenceOf(fromOpenGraph, fromTwitter, fromAppleIcon)
        .mapNotNull { NetworkUrlGuard.resolve(it, base) }
        .firstOrNull()
}

private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)

private fun attributeValue(tag: String, attribute: String): String? =
    Regex("\\b$attribute\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        .find(tag)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** iTunes serves a 100px thumbnail; the same URL with 600px works. */
fun upscaleItunesArtwork(url: String?): String? =
    url?.replace(Regex("\\d+x\\d+bb"), "600x600bb")?.takeIf { it.isNotBlank() }

class HomepageArtworkEnricher @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : ArtworkEnricher {

    override val source = "homepage"

    override suspend fun enrich(station: RadioStation): String? = withContext(Dispatchers.IO) {
        val homepage = NetworkUrlGuard.parse(station.homepage) ?: return@withContext null
        val html = fetchHtml(homepage) ?: return@withContext null
        parseArtworkFromHtml(html, homepage)
    }

    private fun fetchHtml(startUrl: HttpUrl): String? {
        val client = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var current = startUrl

        repeat(MAX_REDIRECTS) {
            if (!NetworkUrlGuard.isAllowed(current)) return null
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (response.isRedirect && location != null) {
                    current = current.resolve(location) ?: return null
                    return@use
                }
                if (!response.isSuccessful) return null
                val contentType = response.body.contentType()?.toString().orEmpty()
                if (!contentType.contains("html", ignoreCase = true)) return null
                return readAtMost(response.body.source(), MAX_HTML_BYTES)
            }
        }
        return null
    }

    private fun readAtMost(source: okio.Source?, maxBytes: Long): String? {
        if (source == null) return null
        val buffer = Buffer()
        var total = 0L
        while (total < maxBytes) {
            val read = source.read(buffer, minOf(8192L, maxBytes - total))
            if (read == -1L) break
            total += read
        }
        return buffer.readUtf8()
    }

    private companion object {
        const val MAX_REDIRECTS = 3
        const val MAX_HTML_BYTES = 384L * 1024L
        const val USER_AGENT = "TNG-RadioNet/1.0 (station artwork)"
    }
}

class ITunesArtworkEnricher @Inject constructor(
    private val apiService: ITunesApiService,
) : ArtworkEnricher {

    override val source = "itunes"

    override suspend fun enrich(station: RadioStation): String? = withContext(Dispatchers.IO) {
        val term = StationNameMatcher.searchTerm(station.name)
        if (term.isBlank()) return@withContext null
        val country = station.countryCode.takeIf { it.length == 2 }?.lowercase(Locale.ROOT)
        val response = runCatching { apiService.searchAlbum(term = term, country = country) }
            .getOrNull()
            ?: return@withContext null

        response.results.orEmpty()
            .firstOrNull { candidate ->
                StationNameMatcher.isMatch(station.name, candidate.collectionName ?: candidate.trackName)
            }
            ?.artworkUrl100
            ?.let(::upscaleItunesArtwork)
            ?.takeIf { NetworkUrlGuard.isAllowedText(url = it.toHttpUrlOrNull() ?: return@takeIf false) }
    }
}

class LastFmArtworkEnricher @Inject constructor(
    private val apiService: LastFmApiService,
) : ArtworkEnricher {

    override val source = "lastfm"

    override suspend fun enrich(station: RadioStation): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.LASTFM_API_KEY
        if (apiKey.isBlank()) return@withContext null
        val term = StationNameMatcher.searchTerm(station.name)
        if (term.isBlank()) return@withContext null

        val response = runCatching { apiService.searchAlbums(album = term, apiKey = apiKey) }
            .getOrNull()
            ?: return@withContext null

        val matches = response.albumMatches() ?: return@withContext null
        matches.firstOrNull { album ->
            val name = album.stringOrNull("name")
            StationNameMatcher.isMatch(station.name, name)
        }
            ?.bestImageUrl()
            ?.takeIf { NetworkUrlGuard.isAllowedText(it.toHttpUrlOrNull() ?: return@takeIf false) }
    }

    private fun JsonElement.albumMatches(): List<JsonElement>? = runCatching {
        val albums = asJsonObject.get("results")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("albummatches")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("album")
        when {
            albums == null || albums.isJsonNull -> null
            albums.isJsonArray -> albums.asJsonArray.toList()
            albums.isJsonObject -> listOf(albums)
            else -> null
        }
    }.getOrNull()

    private fun JsonElement.bestImageUrl(): String? = runCatching {
        val images = asJsonObject.getAsJsonArray("image") ?: return null
        images.toList()
            .asReversed()
            .mapNotNull { image -> image.asJsonObject.get("#text")?.stringOrNullValue() }
            .firstOrNull { it.isNotBlank() }
    }.getOrNull()

    private fun JsonElement.stringOrNull(key: String): String? =
        runCatching { asJsonObject.get(key)?.stringOrNullValue() }.getOrNull()
}

private fun JsonElement.stringOrNullValue(): String? =
    if (isJsonPrimitive) asString.takeIf { it.isNotBlank() } else null
