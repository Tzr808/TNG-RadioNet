package com.armanmaurya.internetradio.domain.model

import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Livelli di buffer per l'ExoPlayer.
 * - STANDARD: usa i default di Media3 (nessuna modifica al comportamento attuale).
 * - HIGH: buffer massimo 120s, minimo 45s, avvio rapido ~3s.
 */
enum class BufferLevel {
    STANDARD,
    HIGH;

    /**
     * Crea un DefaultLoadControl.Builder configurato per questo livello.
     *
     * I parametri di setBufferDurationsMs sono:
     * 1. minBootstrappingMs — tempo minimo prima dell'avvio iniziale (~3s per avvio rapido)
     * 2. minBufferMs        — buffer minimo durante la riproduzione (45s per HIGH)
     * 3. bufferForPlaybackMs — buffer per ripresa dopo pausa/interruzione (2.5s)
     * 4. maxBufferMs         — buffer massimo in memoria (120s per HIGH)
     */
    fun toLoadControl(): DefaultLoadControl.Builder {
        return when (this) {
            STANDARD -> DefaultLoadControl.Builder()
            HIGH -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(3_000, 45_000, 2_500, 120_000)
        }
    }
}
