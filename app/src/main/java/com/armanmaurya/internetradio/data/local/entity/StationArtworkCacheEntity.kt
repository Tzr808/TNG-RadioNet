package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache of artwork resolved online for a station.
 *
 * Deliberately separate from `StationEntity.favicon`: the author's field stays the user's input
 * (and part of the station identity), while this table only holds optional enrichment.
 */
@Entity(tableName = "station_artwork_cache")
data class StationArtworkCacheEntity(
    @PrimaryKey val stationUuid: String,
    val resolvedUrl: String? = null,
    val source: String = "",
    val fetchedAt: Long = 0L,
    /** Epoch millis; negative results expire much sooner than positive ones. */
    val expiresAt: Long = 0L
)
