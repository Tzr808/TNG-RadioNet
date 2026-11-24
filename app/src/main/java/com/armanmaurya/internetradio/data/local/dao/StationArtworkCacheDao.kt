package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.internetradio.data.local.entity.StationArtworkCacheEntity
import com.armanmaurya.internetradio.data.local.entity.StationEntity

@Dao
interface StationArtworkCacheDao {

    @Query("SELECT * FROM station_artwork_cache WHERE stationUuid = :stationUuid LIMIT 1")
    suspend fun get(stationUuid: String): StationArtworkCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StationArtworkCacheEntity)

    @Query("DELETE FROM station_artwork_cache")
    suspend fun clear()

    @Query(
        "SELECT stationUuid, resolvedUrl FROM station_artwork_cache " +
            "WHERE resolvedUrl IS NOT NULL AND resolvedUrl != ''"
    )
    suspend fun getResolvedUrls(): List<StationArtworkUrlRow>

    /**
     * Saved stations that still need artwork: no user favicon and no fresh cache entry
     * (positive or negative). Manual stations are intentionally included.
     */
    @Query(
        """
        SELECT s.* FROM stations s
        LEFT JOIN station_artwork_cache c ON c.stationUuid = s.stationUuid
        WHERE s.favicon = '' AND (c.stationUuid IS NULL OR c.expiresAt < :now)
        ORDER BY COALESCE(s.lastPlayedAt, 0) DESC
        LIMIT :limit
        """
    )
    suspend fun getStationsNeedingArtwork(now: Long, limit: Int): List<StationEntity>
}

data class StationArtworkUrlRow(
    val stationUuid: String,
    val resolvedUrl: String
)
