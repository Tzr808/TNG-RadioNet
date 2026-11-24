package com.armanmaurya.internetradio.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules artwork enrichment only while the user opted in.
 *
 * Uses a unique one-shot request with [ExistingWorkPolicy.KEEP]: repeated app starts do not stack
 * work, and nothing runs when the setting is off.
 */
@Singleton
class ArtworkEnrichmentScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun scheduleIfEnabled() {
        val preferences = settingsRepository.appPreferencesFlow.first()
        if (!preferences.autoArtworkEnabled) {
            cancel()
            return
        }

        val networkType = if (preferences.autoArtworkWifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<ArtworkEnrichmentWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "station_artwork_enrichment"
        const val TAG = "station_artwork"
    }
}
