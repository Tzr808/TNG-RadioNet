# PATCH_ORIGINALE.md

Registro delle modifiche applicate al **codice dell'autore originale** e degli *hook* inseriti
nei suoi file. Serve a riapplicare tutto rapidamente dopo un `git pull` dal repository upstream.

Convenzioni:

- **[P-nn]** = patch a un bug del codice originale (correzione chirurgica).
- **[H-nn]** = hook: punto in cui il codice originale richiama le funzionalità aggiunte
  (che vivono quasi tutte in `com.armanmaurya.internetradio.custom.*` o in file nuovi).
- Le patch sono tutte additive/sostitutive di pochi blocchi: se `Cerca` non trova nulla dopo un
  pull, molto probabilmente l'autore ha riscritto quella parte e la patch va rivalutata a mano.

Comando di verifica dopo ogni riapplicazione:

```bash
./gradlew assembleDebug
```

---

## 1. Patch ai bug del codice originale

### [P-01] Ampiezza audio limitata a ~10 Hz (CPU/batteria)

**File:** `app/src/main/java/com/armanmaurya/internetradio/service/playback/engine/AudioAmplitudeManager.kt`
**Motivo:** `updateAmplitude()` veniva chiamato per ogni buffer PCM e il valore finiva dentro il
`combine` di `PlayerUiState` → l'intero stato (e la UI) veniva ricreato decine di volte al secondo.

Cerca:

```kotlin
    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    fun reset() {
        _amplitude.value = 0f
    }
```

Sostituisci con:

```kotlin
    private var lastEmitUptimeMs = 0L

    fun updateAmplitude(rms: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitUptimeMs < MIN_EMIT_INTERVAL_MS) return
        if (abs(rms - _amplitude.value) < MIN_DELTA) return
        lastEmitUptimeMs = now
        _amplitude.value = rms
    }

    fun reset() {
        lastEmitUptimeMs = SystemClock.elapsedRealtime()
        _amplitude.value = 0f
    }

    private companion object {
        const val MIN_EMIT_INTERVAL_MS = 100L
        const val MIN_DELTA = 0.01f
    }
```

Import da aggiungere: `android.os.SystemClock`, `kotlin.math.abs`.

### [P-02] RecordingService: `START_STICKY` incondizionato (crash/ANR)

**File:** `app/src/main/java/com/armanmaurya/internetradio/service/RecordingService.kt`
**Motivo:** se il sistema ricrea il servizio con intent nullo, `startForeground()` non viene mai
chiamato → `RemoteServiceException`.

Cerca: `when (intent?.action) {` all'inizio di `onStartCommand`.
Sostituisci con:

```kotlin
        when (intent?.action) {
            // Restart con intent nullo (servizio ricreato dal sistema)
            null -> {
                stopSelf()
                return START_NOT_STICKY
            }
```

E per `ACTION_STOP`:

```kotlin
            ACTION_STOP -> {
                val uuid = intent.getStringExtra("UUID")
                if (uuid == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                recordingController.stopRecording(uuid)
                if (recordingController.activeSessions.value.isEmpty()) stopSelf()
            }
```

### [P-03] AlarmService: watchdog di avvio riproduzione

**File:** `app/src/main/java/com/armanmaurya/internetradio/service/AlarmService.kt`
**Motivo:** se lo stream non parte (URL morto/rete assente) il foreground service con la notifica
"sveglia" restava attivo all'infinito.

1. Campo: aggiungi `private var playbackTimeoutJob: Job? = null` accanto a `playbackObserverJob`.
2. In fondo a `observePlaybackState()` aggiungi:

```kotlin
        playbackTimeoutJob?.cancel()
        playbackTimeoutJob = serviceScope.launch {
            delay(PLAYBACK_START_TIMEOUT_MS)
            if (playerController.playbackSession.value.isPlaying.not()) {
                Log.w("AlarmService", "Playback did not start, dismissing alarm")
                dismissAlarm()
            }
        }
```

3. In `dismissAlarm()` aggiungi:

```kotlin
        playbackTimeoutJob?.cancel()
        playbackTimeoutJob = null
```

4. Nel `companion object`: `private const val PLAYBACK_START_TIMEOUT_MS = 90_000L`.

### [P-04] WidgetUpdater: debounce aggiornamenti widget

**File:** `app/src/main/java/com/armanmaurya/internetradio/ui/widget/WidgetUpdater.kt`
**Motivo:** 6 callback del player chiamavano `updateWidget()` → un update Glance per ogni evento.

1. Aggiungi il campo `private var pendingUpdateJob: Job? = null`.
2. In `attachPlayer()` sostituisci `updateWidget(player)` con `pushWidget(player)`.
3. In `detachPlayer()` aggiungi `pendingUpdateJob?.cancel(); pendingUpdateJob = null`.
4. Rinomina il vecchio corpo di `updateWidget` in `private fun pushWidget(p: Player)` e crea:

```kotlin
    fun updateWidget(player: Player? = attachedPlayer) {
        val p = player ?: return
        pendingUpdateJob?.cancel()
        pendingUpdateJob = scope.launch {
            delay(WIDGET_UPDATE_DEBOUNCE_MS)
            pushWidget(p)
        }
    }
```

5. In `pushStoppedWidgetUpdate()` aggiungi in testa `pendingUpdateJob?.cancel(); pendingUpdateJob = null`.
6. Nel `companion object`: `private const val WIDGET_UPDATE_DEBOUNCE_MS = 300L`.
7. Import: `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`.

### [P-05] PlayerSheet: tick del sleep timer fuori dal corpo della sheet

**File:** `app/src/main/java/com/armanmaurya/internetradio/ui/mobile/screens/player/PlayerSheet.kt`
**Motivo:** `currentTime` aggiornato ogni secondo veniva letto nel corpo di `PlayerSheetContent`
→ ricomposizione completa della sheet (e dei tab) ogni secondo.

1. Rimuovi da `PlayerSheetContent`:

```kotlin
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playbackState.sleepTimerEndTime) { ... }
    val remainingTime = ...
    val sleepTimerProgress = ...
```

2. Nella `Controls Row` passa a `TickingControls(...)` con `sleepTimerTotalDuration` al posto di
   `remainingTime`/`sleepTimerProgress`; per la finestra usa `TickingSleepTimerDialog(...)` con
   `timerTotalDuration` al posto di `timerProgress`.
3. In fondo al file aggiungi i composable privati `rememberSleepTimerRemaining`,
   `sleepTimerProgressOf`, `SharedTransitionScope.TickingControls`,
   `SharedTransitionScope.TickingSleepTimerDialog` (vedi file attuale, sezione finale).
   La cancellazione del timer allo scadere è già gestita da `PlayerControllerImpl.setSleepTimer`.

### [P-06] ScrollingWaveform: frame rate adattivo

**File:** `app/src/main/java/com/armanmaurya/internetradio/ui/mobile/screens/player/ScrollingWaveform.kt`
**Motivo:** loop a 30 fps anche in pausa o a volume zero.

Cerca: `delay(33) // ~30 FPS`
Sostituisci con: `delay(if (animatedAmplitude > 0.002f) 33L else 250L)`

### [P-07] AutoBrowseTreeProvider: niente `runBlocking` sui callback Media3

**File:** `app/src/main/java/com/armanmaurya/internetradio/ui/auto/AutoBrowseTreeProvider.kt`
**Motivo:** `onGetChildren`/`onGetItem`/`findStationByUuid` facevano query Room con `runBlocking`
 dentro i callback di browse (main thread) → rischio ANR su Android Auto.

1. Aggiungi `@Volatile private var appLanguage: String = "System"` e, in un `init`, colleziona
   `settingsRepository.appPreferencesFlow` per aggiornarlo (il valore serve a `getLocalizedString`).
2. `onGetChildren` e `onGetItem` restituiscono un `SettableFuture` completato da `searchScope.launch`.
3. `findStationByUuid`, `browseChildren`, `recentChildren`, `libraryChildren` diventano `suspend`
   (rimuovi i `runBlocking`).
4. `getLocalizedString` usa `appLanguage` invece di `runBlocking { getSavedAppLanguage() }`.

### [P-08] PlaybackSessionCallback: snapshot in memoria al posto di `runBlocking`

**File:** `app/src/main/java/com/armanmaurya/internetradio/service/playback/PlaybackSessionCallback.kt`
**Motivo:** `onConnect` faceva `runBlocking { isStationInLibraryDirect() }` sul main thread;
`onAddMediaItems` faceva query Room in modo bloccante; `getLocalizedString` idem.

1. Campi: `@Volatile private var libraryStationIds: Set<String>` e `@Volatile private var appLanguage: String`.
2. In `init` due collector (`libraryRepository.getAllStations()` e `appPreferencesFlow`).
3. `onConnect`: `val isFav = currentStation?.let { it.stationUuid in libraryStationIds } ?: false`.
4. `onAddMediaItems`: risolvi con `SettableFuture` + `scope.launch`.
5. `getLocalizedString`: usa `appLanguage`.

### [P-09] Cast: discovery mDNS on-demand

**File:** `app/src/main/java/com/armanmaurya/internetradio/data/cast/CastControllerImpl.kt`
**Motivo:** `NsdDeviceDiscoverer` veniva creato nel costruttore del singleton → mDNS attivo per
 tutta la vita del processo anche per chi non usa mai il Cast. La SDK non espone uno stop:
 l'unica mitigazione è crearla solo quando serve.

1. `private val deviceDiscoverer: NsdDeviceDiscoverer by lazy { NsdDeviceDiscoverer(context, discoveryEventHandler) }`.
2. L'`init` che la creava diventa `override fun startDiscovery() { deviceDiscoverer }`.
3. Richiede il metodo `fun startDiscovery() {}` nell'interfaccia `CastController` (hook H-13).

### [P-10] MediaController rilasciato alla chiusura dell'app

**File:** `app/src/main/java/com/armanmaurya/internetradio/data/player/PlayerControllerImpl.kt`
**Motivo:** il binding al `PlaybackService` non veniva mai rilasciato → il servizio (e l'ExoPlayer)
 restavano in memoria anche dopo aver chiuso l'app.

1. `controllerFuture` diventa `var` nullable, creata da `createController()`; la getter `controller`
   la ricrea se è `null`.
2. `initializeController()` cattura la `future` corrente, esce se è stata sostituita e usa
   `runCatching { future.get() }`.
3. Nuovo `override fun releaseController()`: azzera la future e chiama
   `MediaController.releaseFuture(future)`.
4. In `init` chiama `createController()` invece di `initializeController()`.
5. Hook: `releaseController()` nell'interfaccia `PlayerController` (default no-op) e chiamata in
   `MobileActivity.onDestroy()` (H-14/H-15).

### [P-11] ScheduleReceiver: scope legato alla singola ricezione

**File:** `app/src/main/java/com/armanmaurya/internetradio/data/schedule/ScheduleReceiver.kt`
**Motivo:** `CoroutineScope` come campo del receiver, mai cancellato.

Crea lo scope subito dopo `goAsync()` e chiama `scope.cancel()` nel `finally`, prima di
`pendingResult.finish()`.

### [P-12] NowPlayingWidgetReceiver: `goAsync()`

**File:** `app/src/main/java/com/armanmaurya/internetradio/ui/widget/NowPlayingWidgetReceiver.kt`
**Motivo:** lavoro asincrono in `onUpdate` senza `goAsync()` → il sistema poteva ucciderlo a metà.

Nel ramo `else`: `val pendingResult = goAsync()` e `pendingResult.finish()` in un `finally`.

### [P-13] SvgProxyProvider: solo URL http/https

**File:** `app/src/main/java/com/armanmaurya/internetradio/core/provider/SvgProxyProvider.kt`
**Motivo:** provider esportato: accettava qualsiasi stringa codificata in base64.

In `openFile`, dopo il decode, esci con `null` se l'URL non inizia per `http://`/`https://`.

### [P-14] RecordingDurationCache: replace atomico portabile

**File:** `app/src/main/java/com/armanmaurya/internetradio/data/recording/RecordingDurationCache.kt`
**Motivo:** `File.renameTo` non sovrascrive su tutte le piattaforme (su Windows il salvataggio
 falliva silenziosamente e il test `corruptFile_startsEmptyAndCanBeSavedAgain` falliva).

Sostituisci `check(tempFile.renameTo(cacheFile)) { ... }` con:

```kotlin
java.nio.file.Files.move(
    tempFile.toPath(),
    cacheFile.toPath(),
    java.nio.file.StandardCopyOption.REPLACE_EXISTING
)
```

---

## 2. Hook delle funzionalità aggiunte nei file originali

Verifica che questi punti esistano ancora dopo un pull (sono pochi e tutti piccoli).

| ID | File | Cosa deve esserci |
|----|------|-------------------|
| H-01 | `AndroidManifest.xml` | permesso `android.permission.BLUETOOTH_CONNECT` |
| H-02 | `app/build.gradle.kts` | `create("tng") { dimension = "store" }` in `productFlavors` |
| H-03 | `domain/model/AppPreferences.kt` | campi `bufferLevel` e `isPureBlackEnabled` (con default) |
| H-04 | `domain/repository/SettingsRepository.kt` | `setBufferLevel`, `set/getPureBlackEnabled` |
| H-05 | `data/repository/SettingsRepositoryImpl.kt` | chiavi `BUFFER_LEVEL`, `PURE_BLACK_ENABLED` + implementazioni |
| H-06 | `service/PlaybackService.kt` | campo `bufferLevel` + `.setLoadControl(bufferLevel.toLoadControl().build())` |
| H-07 | `ui/shared/theme/Theme.kt` | `if (darkTheme && appPreferences.isPureBlackEnabled) baseScheme.applyPureBlack()` (2 punti) |
| H-08 | `ui/shared/viewmodels/SettingsViewModel.kt` | `setBufferLevel`, `setPureBlackEnabled` |
| H-09 | `ui/mobile/screens/settings/SettingsScreen.kt` | `ToggleItem` Pure Black (solo in tema scuro), `ExpandableItem` buffer, chiamata a `custom.about.TngAboutSection` al posto delle sezioni About/Community/Donate |
| H-10 | `ui/shared/viewmodels/PlayerViewModel.kt` | `stationVolumeManager`, `hasSavedVolumeForCurrentStation`, `saveCurrentVolume`, `clearCurrentSavedVolume`, `onCurrentStationChanged`, `getActiveDeviceType`, `getActiveAudioDeviceName`, `getDeviceVolumeFraction`, `setDeviceVolumeFraction`, `onCleared()` |
| H-11 | `ui/mobile/screens/player/PlayerSheet.kt` | parametri volume su `PlayerSheetContent`, `StationVolumeButton`, `StationVolumeDialog`, `AlertDialog` permesso BT |
| H-12 | `ui/mobile/MobileActivity.kt` | **nessun hook lingua** (voce storica): la forzatura di `Locale.setDefault("it")` è stata rimossa (forzava l'italiano su tutto il processo ignorando la lingua scelta dall'utente). L'app usa `android:localeConfig` + risorse `values-it`. |
| H-13 | `domain/controller/CastController.kt` | `fun startDiscovery() {}` (default no-op) |
| H-14 | `domain/controller/PlayerController.kt` | `fun releaseController() {}` (default no-op) |
| H-15 | `ui/mobile/MobileActivity.kt` | injection di `PlayerController` + `releaseController()` in `onDestroy()` quando `isFinishing && !isChangingConfigurations && !handedOverToTv` |
| H-16 | `ui/mobile/screens/player/PlayerSheet.kt` | parametro `onStartCastDiscovery` chiamato quando si apre il dialog Cast |
| H-17 | `app/build.gradle.kts` | `versionName = "1.0.0.01"` (versione del fork TNG) |
| H-18 | `main/AndroidManifest.xml` | `android:label="TNG RadioNet"` su `<application>`, `MobileActivity`, `TvActivity`, `NowPlayingDreamService` (label letterale: vince sulle traduzioni di `app_name`) |
| H-19 | `ui/mobile/screens/settings/SettingsScreen.kt` | al posto di `CommunitySection()` + `AboutSection(...)` + `DonateSection()` c'è la sola chiamata a `custom.about.TngAboutSection(versionName = ...)`. Le tre funzioni originali restano nel file ma non sono più chiamate |
| H-20 | `ui/tv/screens/about/AboutScreen.kt` | al posto di descrizione + author card + contributors + pulsante GitHub c'è il blocco attribuzione (`about_tng_based_on` / `about_tng_author` / `about_tng_available_on` / `about_tng_github`) |

---

## 3. File nuovi (non richiedono patch, vivono fuori dal codice dell'autore)

- `custom/about/TngAboutSection.kt` — sezione About del flavor TNG.
- `domain/model/BufferLevel.kt` — livelli di buffer ExoPlayer.
- `service/playback/StationVolumeManager.kt` — **unica fonte** dei volumi per stazione+dispositivo.
- `ui/mobile/screens/player/components/stationvolume/StationVolumeButton.kt` (+ `StationVolumeDialog`)
- `res/values/strings_custom.xml`, `res/values-it/strings_custom.xml` — stringhe aggiunte
  (tenute fuori da `strings.xml` per evitare conflitti).
- `res/values/bools.xml` (`is_tng_flavor=false`) + `app/src/tng/res/values/bools.xml` (`true`)
  — **attualmente non usati** (l'About personalizzata è sempre attiva); restano per eventuali
  personalizzazioni future per flavor.
- `app/src/foss/res/values/strings.xml` e `app/src/play/res/values/strings.xml` — `app_name =
  "TNG RadioNet"` per i flavor `foss` e `play` (il flavor `tng` lo aveva già).
  Nota: le traduzioni `values-<lingua>/strings.xml` originali contengono ancora il vecchio
  `app_name`, per questo la label del launcher è impostata in modo letterale nel manifest.
- `app/src/tng/**` — branding TNG (StoreConfig, strings per locale, app_name).

---

## 4. Procedura dopo un `git pull`

1. `git fetch origin && git merge origin/main` (o rebase) sul branch `mia-ia-locale`.
2. Risolvi eventuali conflitti: nei file originali tieni **la versione upstream** e riapplica
   gli hook della sezione 2 (sono piccoli).
3. Riapplica le patch P-01…P-14: per ciascuna, se `Cerca` non trova il testo, controlla il file
   attuale e adatta la patch.
4. Verifica con:

```bash
./gradlew assembleDebug
```

5. Se compila, fai un commit di merge e annota qui eventuali patch non più applicabili.
