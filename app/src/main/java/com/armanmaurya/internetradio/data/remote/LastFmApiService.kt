package com.armanmaurya.internetradio.data.remote

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Last.fm API (ws.audioscrobbler.com). The key is optional: callers must skip the enricher when
 * [com.armanmaurya.internetradio.BuildConfig.LASTFM_API_KEY] is blank.
 *
 * The response is kept as [JsonElement] because Last.fm mixes objects, arrays and empty strings
 * for `album`/`image` depending on the query.
 */
interface LastFmApiService {

    @GET("2.0/")
    suspend fun searchAlbums(
        @Query("method") method: String = "album.search",
        @Query("album") album: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5
    ): JsonElement
}
