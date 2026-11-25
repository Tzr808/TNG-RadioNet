package com.armanmaurya.internetradio.service.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Unica fonte di verità per il volume "per stazione + dispositivo di uscita".
 *
 * - Salva il volume del flusso musica (AudioManager) associato alla stazione corrente e al
 *   dispositivo di uscita attivo, con chiave stabile basata su tipo+indirizzo del dispositivo.
 * - Quando l'utente cambia stazione o collega/scollega un dispositivo, riapplica il volume salvato.
 * - Long-press sul pulsante cancella il volume salvato.
 *
 * Nota: la chiave NON usa il nome commerciale del dispositivo, che può cambiare ed è
 * disponibile solo con il permesso BLUETOOTH_CONNECT su Android 12+.
 */
class StationVolumeManager(
    private val context: Context,
    private val onStateChanged: ((hasSavedVolume: Boolean) -> Unit)? = null
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var currentStationId: String? = null

    // Debounce: evita di riapplicare il volume più volte in rapida successione
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReapply: Runnable? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            scheduleReapply()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            scheduleReapply()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    /**
     * Segnala al manager quale stazione è attualmente selezionata.
     * Questo attiva il ripristino del volume salvato per quella stazione.
     */
    fun onStationSelected(stationId: String, @Suppress("UNUSED_PARAMETER") stationName: String) {
        currentStationId = stationId
        reapplyForCurrentStation()
    }

    /** True se esiste un volume salvato per la stazione e il dispositivo correnti. */
    fun hasSavedVolumeForCurrentStation(): Boolean {
        val id = currentStationId ?: return false
        return prefs.contains(buildKey(id, activeDeviceKey()))
    }

    /** Nome leggibile del dispositivo di uscita attivo (solo per la UI). */
    fun getActiveDeviceName(): String {
        val device = activeOutputDevice() ?: return SPEAKER_LABEL
        return deviceDisplayName(device)
    }

    /** Volume corrente del flusso musica, normalizzato 0..1 (per lo slider del dialog). */
    fun getVolumeFraction(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    }

    /** Applica subito al dispositivo un volume normalizzato 0..1. */
    fun setVolumeFraction(fraction: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) return
        applyStreamVolume((fraction.coerceIn(0f, 1f) * maxVolume).roundToInt())
    }

    /**
     * Salva il volume corrente associato alla stazione e al dispositivo attivo.
     */
    fun saveCurrentVolume() {
        val id = currentStationId ?: return
        val deviceLabel = getActiveDeviceName()
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = if (maxVolume > 0) "${(currentVolume.toFloat() / maxVolume * 100).toInt()}%" else "$currentVolume"

        prefs.edit().putInt(buildKey(id, activeDeviceKey()), currentVolume).apply()
        onStateChanged?.invoke(true)

        Toast.makeText(
            context,
            "Volume salvato ($percent) su $deviceLabel",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Cancella il volume salvato per la stazione e il dispositivo attivo.
     */
    fun clearSavedVolume() {
        val id = currentStationId ?: return
        val deviceLabel = getActiveDeviceName()

        prefs.edit().remove(buildKey(id, activeDeviceKey())).apply()
        onStateChanged?.invoke(false)

        Toast.makeText(
            context,
            "Volume personalizzato rimosso da $deviceLabel",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Riapplica il volume salvato per la stazione corrente.
     * Usa debounce per evitare chiamate multiple rapide.
     */
    private fun reapplyForCurrentStation() {
        val id = currentStationId ?: return
        val key = buildKey(id, activeDeviceKey())
        val savedVolume = prefs.getInt(key, -1)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        if (savedVolume in 0..maxVolume) {
            applyStreamVolume(savedVolume)
            onStateChanged?.invoke(true)
        } else {
            onStateChanged?.invoke(false)
        }
    }

    private fun scheduleReapply() {
        pendingReapply?.let { handler.removeCallbacks(it) }
        val reapplyRunnable = Runnable { reapplyForCurrentStation() }
        pendingReapply = reapplyRunnable
        handler.postDelayed(reapplyRunnable, DEBOUNCE_MS)
    }

    private fun applyStreamVolume(volume: Int) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } catch (_: SecurityException) {
            // Alcune ROM / la modalità Non Disturbare bloccano il cambio volume: non crashare
        } catch (_: Exception) {
            // Difensivo: un errore audio non deve mai far cadere l'app
        }
    }

    /** Dispositivo di uscita realmente attivo, scelto per priorità esplicita. */
    private fun activeOutputDevice(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs
            .filter { isSupportedOutput(it.type) }
            .minByOrNull { typePriority(it.type) }
            ?.takeIf { typePriority(it.type) < PRIORITY_BUILTIN_SPEAKER }
    }

    private fun isSupportedOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
        else -> false
    }

    private fun typePriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> 0
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> 1
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> 2
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 5
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 6
        else -> 9
    }

    /**
     * Chiave stabile per dispositivo: usa l'indirizzo hardware quando disponibile,
     * altrimenti il nome; se non c'è nulla di stabile, solo il tipo.
     */
    private fun activeDeviceKey(): String {
        val device = activeOutputDevice() ?: return SPEAKER_KEY
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.address.orEmpty()
        } else {
            ""
        }
        val stablePart = address.ifBlank { device.productName?.toString()?.trim().orEmpty() }
        return if (stablePart.isBlank()) "type_${device.type}" else "type_${device.type}_$stablePart"
    }

    private fun deviceDisplayName(device: AudioDeviceInfo): String {
        val canReadName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val name = if (canReadName) device.productName?.toString()?.trim() else null
        if (!name.isNullOrEmpty()) return name

        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cuffie"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricolare"
            else -> SPEAKER_LABEL
        }
    }

    private fun buildKey(stationId: String, deviceKey: String): String = "vol_${stationId}_$deviceKey"

    /**
     * Restituisce il TIPO del dispositivo attivo (senza bisogno di permessi).
     * Utile per decidere se chiedere BLUETOOTH_CONNECT.
     */
    fun getActiveDeviceType(): String {
        val device = activeOutputDevice() ?: return "speaker"
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble_bluetooth"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
            else -> "external_${device.type}"
        }
    }

    /**
     * Deve essere chiamato quando il manager non è più necessario (es. onCleared del ViewModel).
     */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        pendingReapply = null
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: IllegalArgumentException) {
            // Callback già disregistrato, ignora
        }
    }

    private companion object {
        const val PREFS_NAME = "station_device_volumes"
        const val DEBOUNCE_MS = 300L
        const val SPEAKER_KEY = "speaker"
        const val SPEAKER_LABEL = "Altoparlante Telefono"
        const val PRIORITY_BUILTIN_SPEAKER = 7
    }
}
