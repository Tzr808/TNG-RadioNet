package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.custom.artwork.StationArtworkStore
import com.armanmaurya.internetradio.data.local.dao.StationArtworkCacheDao
import com.armanmaurya.internetradio.data.local.entity.StationArtworkCacheEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.remote.artwork.ArtworkEnricher
import com.armanmaurya.internetradio.data.remote.artwork.HomepageArtworkEnricher
import com.armanmaurya.internetradio.data.remote.artwork.ITunesArtworkEnricher
import com.armanmaurya.internetradio.data.remote.artwork.LastFmArtworkEnricher
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.StationArtworkRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationArtworkRepositoryImpl @Inject constructor(
    private val dao: StationArtworkCacheDao,
    private val store: StationArtworkStore,
    homepageEnricher: HomepageArtworkEnricher,
    iTunesEnricher: ITunesArtworkEnricher,
    lastFmEnricher: LastFmArtworkEnricher,
) : StationArtworkRepository {

    // One lookup at a time: the worker is sequential and this also deduplicates overlapping calls.
    private val mutex = Mutex()

    private val enrichers: List<ArtworkEnricher> =
        listOf(homepageEnricher, iTunesEnricher, lastFmEnricher)

    override suspend fun resolve(station: RadioStation): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = runCatching { dao.get(station.stationUuid) }.getOrNull()
        if (cached != null && cached.expiresAt > now) {
            return@withLock cached.resolvedUrl?.takeIf { it.isNotBlank() }
        }

        var source = "none"
        val found = enrichers.firstNotNullOfOrNull { enricher ->
            runCatching { enricher.enrich(station) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.also { source = enricher.source }
        }

        val entry = StationArtworkCacheEntity(
            stationUuid = station.stationUuid,
            resolvedUrl = found,
            source = source,
            fetchedAt = now,
            expiresAt = now + if (found != null) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
        )
        runCatching { dao.upsert(entry) }

        if (found != null) store.put(station.stationUuid, found) else store.remove(station.stationUuid)
        found
    }

    override suspend fun nextPending(limit: Int): List<RadioStation> =
        runCatching {
            dao.getStationsNeedingArtwork(System.currentTimeMillis(), limit).map { it.toDomain() }
        }.getOrDefault(emptyList())

    override suspend fun clearAll() {
        runCatching { dao.clear() }
        store.clear()
    }

    private companion object {
        const val POSITIVE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        const val NEGATIVE_TTL_MS = 60L * 60L * 1000L
    }
}
