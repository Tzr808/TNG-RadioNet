package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.RadioStation

/**
 * Optional online artwork for stations without a user favicon.
 * Results are cached separately from [RadioStation.favicon].
 */
interface StationArtworkRepository {
    /** Resolves (and caches) artwork for one station. Returns null when nothing suitable is found. */
    suspend fun resolve(station: RadioStation): String?

    /** Saved stations that still need a lookup, most recently played first. */
    suspend fun nextPending(limit: Int): List<RadioStation>

    /** Drops every cached lookup (used by the settings "reset" action). */
    suspend fun clearAll()
}
