package com.armanmaurya.internetradio.custom.artwork

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.provider.StationAvatarProvider
import com.armanmaurya.internetradio.domain.model.RadioStation
import dagger.hilt.android.EntryPointAccessors

/** `content://` URI of the generated avatar (usable by Compose, Coil, Glance, Media3). */
fun stationAvatarUri(context: Context, name: String, key: String): String =
    StationAvatarProvider.createUri(context, name, key)

private fun artworkStore(context: Context): StationArtworkStore? = runCatching {
    EntryPointAccessors
        .fromApplication(context.applicationContext, StationArtworkEntryPoint::class.java)
        .store()
}.getOrNull()

/** Enriched artwork already persisted for this station, if any. */
fun cachedStationArtwork(context: Context, stationUuid: String): String? =
    artworkStore(context)?.current()?.get(stationUuid)?.takeIf { it.isNotBlank() }

/**
 * Best available station image URL: enriched cache first, then the user-provided favicon.
 * Returns `null` when neither exists (callers can then fall back to the generated avatar).
 */
fun stationArtworkSource(context: Context, station: RadioStation): String? =
    cachedStationArtwork(context, station.stationUuid) ?: station.favicon.takeIf { it.isNotBlank() }

/**
 * Single source of truth for station artwork outside Compose:
 * the enriched/user-provided image when present, the generated avatar otherwise.
 */
fun stationArtworkUrl(context: Context, station: RadioStation): String =
    stationArtworkSource(context, station)
        ?: stationAvatarUri(context, station.name, station.stationUuid)

/**
 * Station thumbnail used by every list/grid.
 *
 * - favicon present -> remote image, avatar shown while loading and on error
 * - favicon missing -> generated avatar immediately (never the app logo)
 * - thumbnails disabled in settings -> plain launcher icon, as before
 */
@Composable
fun StationArtworkImage(
    station: RadioStation,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    showThumbnails: Boolean = true,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) {
        runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, StationArtworkEntryPoint::class.java)
                .store()
        }.getOrNull()
    }
    val artworkMap: Map<String, String> = if (store != null) {
        val state by store.artwork.collectAsState()
        state
    } else {
        emptyMap()
    }
    val resolved = artworkMap[station.stationUuid]

    StationArtworkImage(
        name = station.name,
        favicon = resolved.orEmpty().ifBlank { station.favicon },
        artworkKey = station.stationUuid,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        showThumbnails = showThumbnails
    )
}

/** Same as above for callers that only have a station name and favicon (e.g. schedules). */
@Composable
fun StationArtworkImage(
    name: String,
    favicon: String,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    showThumbnails: Boolean = true,
    artworkKey: String = name,
) {
    val context = LocalContext.current
    val avatar = remember(artworkKey, name) { StationAvatarPainter(name, artworkKey) }
    val background = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    val store = remember(context.applicationContext) { artworkStore(context) }
    val resolvedArtwork: String? = if (store != null) {
        val artworkMap by store.artwork.collectAsState()
        artworkMap[artworkKey]
    } else {
        null
    }
    val effectiveFavicon = resolvedArtwork?.takeIf { it.isNotBlank() } ?: favicon

    if (!showThumbnails) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            contentScale = contentScale,
            modifier = modifier.then(background)
        )
        return
    }

    if (effectiveFavicon.isBlank()) {
        Image(
            painter = avatar,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.then(background)
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context).data(effectiveFavicon).build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = avatar,
        error = avatar,
        fallback = avatar,
        modifier = modifier.then(background)
    )
}
