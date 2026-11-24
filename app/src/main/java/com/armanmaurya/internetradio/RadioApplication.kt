package com.armanmaurya.internetradio

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.armanmaurya.internetradio.core.crash.GlobalExceptionHandler
import com.armanmaurya.internetradio.data.worker.ArtworkEnrichmentScheduler
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class RadioApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var shortcutController: com.armanmaurya.internetradio.domain.controller.ShortcutController

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var artworkEnrichmentScheduler: ArtworkEnrichmentScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(this, defaultHandler)
        )
        shortcutController.initialize()
        // One-shot: non fa nulla se l'utente non ha attivato le copertine automatiche.
        appScope.launch { artworkEnrichmentScheduler.scheduleIfEnabled() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient }
                    )
                )
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
