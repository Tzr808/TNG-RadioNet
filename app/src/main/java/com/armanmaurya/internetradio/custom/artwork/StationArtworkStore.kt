package com.armanmaurya.internetradio.custom.artwork

import com.armanmaurya.internetradio.data.local.dao.StationArtworkCacheDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory snapshot of [StationArtworkCacheDao] so Compose can show enriched artwork without
 * a database read per card. The Room table remains the source of truth.
 */
@Singleton
class StationArtworkStore @Inject constructor(
    private val dao: StationArtworkCacheDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _artwork = MutableStateFlow<Map<String, String>>(emptyMap())
    val artwork: StateFlow<Map<String, String>> = _artwork.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun reload() {
        val rows = runCatching { dao.getResolvedUrls() }.getOrNull() ?: return
        _artwork.value = rows.associate { it.stationUuid to it.resolvedUrl }
    }

    fun put(stationUuid: String, url: String) {
        _artwork.update { it + (stationUuid to url) }
    }

    fun remove(stationUuid: String) {
        _artwork.update { it - stationUuid }
    }

    fun clear() {
        _artwork.value = emptyMap()
    }

    fun current(): Map<String, String> = _artwork.value
}
