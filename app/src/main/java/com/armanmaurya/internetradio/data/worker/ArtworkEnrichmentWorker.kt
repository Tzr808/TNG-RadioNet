package com.armanmaurya.internetradio.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.armanmaurya.internetradio.domain.repository.StationArtworkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * One-shot enrichment of saved stations without artwork.
 *
 * Small batch + exponential backoff + network constraint: no periodic wakeups, no expedited work.
 * Negative results are cached, so the worker stops calling the network for stations that have none.
 */
@HiltWorker
class ArtworkEnrichmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: StationArtworkRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val pending = repository.nextPending(BATCH_SIZE)
        for (station in pending) {
            if (isStopped) break
            repository.resolve(station)
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    private companion object {
        const val BATCH_SIZE = 25
        const val MAX_RETRIES = 3
    }
}
