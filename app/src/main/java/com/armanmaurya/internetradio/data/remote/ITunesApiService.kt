package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.ITunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApiService {
    @GET("search")
    suspend fun searchTrack(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse

    /** Album search used by the station artwork enricher (no API key required). */
    @GET("search")
    suspend fun searchAlbum(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("country") country: String? = null,
        @Query("limit") limit: Int = 5
    ): ITunesSearchResponse
}
