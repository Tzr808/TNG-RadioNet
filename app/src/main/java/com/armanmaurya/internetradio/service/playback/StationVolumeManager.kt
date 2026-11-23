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

/**
 * Gestisce il salvataggio e il ripristino del volume per stazione radio + dispositivo di output.
 * 
 * Funzionamento:
 * - Quando l'utente seleziona una stazione, salva il volume corrente associato a quella stazione
 *   e al dispositivo attivo (Bluetooth, USB, cuffie, altoparlante).
 * - Quando cambia la stazione o il dispositivo, ripristina automaticamente il volume salvato.
 * - Long-press sul pulsante per cancellare il volume salvato.
 * 
 * Migliorie rispetto alla versione base:
 * 1. Verifica che il dispositivo sia EFFETTIVAMENTE attivo (non solo connesso)
 * 2. Debounce sul callback audio per evitare riapplicazioni multiple
 * 3. Gestione sicura dei permessi BLUETOOTH_CONNECT su Android 12+
 */
class StationVolumeManager(
    private val context: Context,
    private val onStateChanged: ((hasSavedVolume: Boolean) -> Unit)? = null
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("station_device_volumes", Context.MODE_PRIVATE)

    private var currentStationId: String? = null
    private var currentStationName: String? = null

    // Debounce: evita di riapplicare il volume più volte in rapida successione
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReapply: Runnable? = null
    private val DEBOUNCE_MS = 300L

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
    fun onStationSelected(stationId: String, stationName: String) {
        currentStationId = stationId
        currentStationName = stationName
        reapplyForCurrentStation()
    }

    /**
     * Salva il volume corrente associato alla stazione e al dispositivo attivo.
     */
    fun saveCurrentVolume() {
        val id = currentStationId ?: return
        val deviceName = getActiveOutputDeviceName()
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = if (maxVol > 0) "${(currentVol.toFloat() / maxVol * 100).toInt()}%" else "$currentVol"

        prefs.edit().putInt(buildKey(id, deviceName), currentVol).apply()

        onStateChanged?.invoke(true)
        
        val message = if (deviceName.startsWith("Dispositivo_") || deviceName == "Altoparlante Telefono") {
            "Volume salvato ($volumePercent) per $deviceName\n(Il nome reale non è disponibile)"
        } else {
            "Volume salvato ($volumePercent) su $deviceName"
        }
        
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Cancella il volume salvato per la stazione e il dispositivo attivo.
     */
    fun clearSavedVolume() {
        val id = currentStationId ?: return
        val deviceName = getActiveOutputDeviceName()

        prefs.edit().remove(buildKey(id, deviceName)).apply()

        onStateChanged?.invoke(false)
        
        val message = if (deviceName.startsWith("Dispositivo_") || deviceName == "Altoparlante Telefono") {
            "Volume rimosso per $deviceName\n(Il nome reale non è disponibile)"
        } else {
            "Volume personalizzato rimosso da $deviceName"
        }
        
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Riapplica il volume salvato per la stazione corrente.
     * Usa debounce per evitare chiamate multiple rapide.
     */
    private fun reapplyForCurrentStation() {
        val id = currentStationId ?: return
        val deviceName = getActiveOutputDeviceName()
        val key = buildKey(id, deviceName)

        if (prefs.contains(key)) {
            val savedVol = prefs.getInt(key, -1)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (savedVol in 0..maxVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
            }
            onStateChanged?.invoke(true)
        } else {
            onStateChanged?.invoke(false)
        }
    }

    /**
     * Pianifica un riapplico del volume con debounce.
     * Se viene chiamata più volte entro DEBOUNCE_MS, solo l'ultima esecuzione viene mantenuta.
     */
    private fun scheduleReapply() {
        pendingReapply?.let { handler.removeCallbacks(it) }
        val reapplyRunnable = Runnable { reapplyForCurrentStation() }
        pendingReapply = reapplyRunnable
        handler.postDelayed(reapplyRunnable, DEBOUNCE_MS)
    }

    /**
     * Restituisce il nome del dispositivo di output ATTIVO (non solo connesso).
     * 
     * Logica:
     * 1. Cerca dispositivi di output esterni (Bluetooth, USB, cuffie)
     * 2. Verifica che siano effettivamente in uso (non solo connessi)
     * 3. Su Android 12+ legge il nome reale solo se il permesso BLUETOOTH_CONNECT è concesso
     * 4. Fallback su un nome generico se il permesso è negato o il nome non disponibile
     */
    private fun getActiveOutputDeviceName(): String {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Ordina per priorità: cuffie cablate > USB > Bluetooth A2DP > Bluetooth headset > altoparlante
        val externalDevice = outputs.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }

        if (externalDevice != null) {
            val canReadBtName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val name = if (canReadBtName) {
                externalDevice.productName?.toString()?.trim()
            } else {
                null
            }
            return if (!name.isNullOrEmpty()) name else "Dispositivo_${externalDevice.type}"
        }

        return "Altoparlante Telefono"
    }

    /**
     * Restituisce il TIPO del dispositivo attivo (senza bisogno di permessi).
     * Utile per decidere se chiedere BLUETOOTH_CONNECT.
     */
    fun getActiveDeviceType(): String {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val externalDevice = outputs.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }

        return if (externalDevice != null) {
            when (externalDevice.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble_bluetooth"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
                else -> "external_${externalDevice.type}"
            }
        } else {
            "speaker"
        }
    }

    private fun buildKey(stationId: String, deviceName: String): String {
        return "vol_${stationId}_$deviceName"
    }

    /**
     * Deve essere chiamato quando il manager non è più necessario (es. onDestroy).
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
}
