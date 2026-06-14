# Android Port — Reactivation & Modernization Plan

Goal: reactivate and modernize the Android client so a player can run a
Brain/Out server on an external machine and connect to it from an Android
device.

This document is the working plan and progress tracker for the port. Check off
items as they land.

## Current State (findings)

The Android module exists but is disabled and stale.

- Full module already present under `android/`:
  - `android/src/com/desertkun/brainout/android/AndroidLauncher.java`
  - `android/src/com/desertkun/brainout/BrainOutAndroid.java` (`extends BrainOutClient`)
  - `AndroidEnvironment`, `AndroidSettings`, `AndroidGameController`,
    `AndroidConstants`, `packages/AndroidPackageManager`, `packages/AndroidPackage`
- Touch controls are **partially implemented** in
  `android/src/com/desertkun/brainout/AndroidGameController.java`:
  virtual joystick (`touchpad-move`), fire button (`button-touch-launch`),
  OK/Cancel buttons, and an `aim` mode driven by `AndroidConstants.Touch.*`.
- Server connection flow in the client:
  - `BrainOutClient.ConnectToLocation` (static, set from `--connect` on desktop)
  - decoded by `HashedUrl.unhash()` (base64 of `host;tcp;udp;http`)
  - consumed in `client/.../menu/impl/IntroMenu.java:275` →
    `ClientController.connect(location, tcp, udp, http, ...)`
- Server can run locally with `--offline` (no Anthill backend), listening on
  three ports (default `36555;36556;36557` = tcp/udp/http).

## Main Blockers

| Blocker | Detail | Location |
|---|---|---|
| Module disabled | not in `include` | `settings.gradle` |
| Config commented out | `project(":android")` inside `/* */` | `build.gradle:50-95` |
| Ancient toolchain | AGP `1.0.0`, buildTools `21.1.2`, compileSdk `22` — incompatible with Gradle 8.3 | `build.gradle:16`, `android/build.gradle` |
| libGDX API drift | code targets old gdx (e.g. `scrolled(int)` vs `scrolled(float,float)`) with gdx `1.11.0` | `AndroidGameController.java` |
| No server-address input | Android has no `--connect` CLI args | needs new UI / deep-link |
| Data package signing | client verifies signatures; desktop uses `--unsafe` (no CLI on Android) | `AndroidEnvironment`, `Readme.md:49` |
| Storage model | `WRITE_EXTERNAL_STORAGE` + `getExternalStorageDirectory()` broken on Android 10+ | `AndroidManifest.xml`, `AndroidEnvironment.java:82` |

## Target Versions

- Android Gradle Plugin: **8.1.x** (wrapper is already Gradle 8.3)
- `compileSdk 34`, `targetSdk 34`, `minSdk 24` (Android 7.0)
- Native ABIs: `armeabi-v7a`, **`arm64-v8a`**, `x86`, `x86_64`
  (arm64 is mandatory for modern devices)

## Phased Plan

### Phase 0 — Setup
- [x] Branch created (`claude/peaceful-mccarthy-rigm7y`)
- [x] Plan committed (this document)
- [ ] Confirm target versions above with maintainers

### Phase 1 — Toolchain & module wiring
- [ ] `settings.gradle`: add `'android'` to `include`
- [ ] root `build.gradle`: uncomment `project(":android")` block (50–72)
- [ ] root `build.gradle`: bump AGP classpath `1.0.0` → `8.1.x`
- [ ] `android/build.gradle`: rewrite for AGP 8 (`namespace`, `compileSdk 34`,
      `defaultConfig{applicationId, minSdk 24, targetSdk 34, versionCode/Name}`,
      `buildTypes`, `packagingOptions` for `.so`); drop Ant-era
      `PackageApplication`/`copyAndroidNatives`
- [ ] `AndroidManifest.xml`: drop legacy `package=` (use `namespace`), raise
      `minSdkVersion`, add `android:exported="true"` to launcher activity
- [ ] gdx-backend-android + natives incl. **arm64-v8a**
- [ ] Checkpoint: `./gradlew android:assembleDebug` reaches Java compilation

### Phase 2 — Fix compilation (API drift)
- [ ] `AndroidGameController`: update to gdx 1.11 APIs
      (`scrolled(float,float)`, verify `Touchpad`/`Stage`/`InputProcessor`)
- [ ] Review `AndroidPackageManager`, `AndroidSettings`, `AndroidEnvironment`
      for stale calls
- [ ] `AndroidEnvironment.getUniqueId()`: replace hidden
      `android.os.SystemProperties` reflection with `Settings.Secure.ANDROID_ID`
- [ ] Checkpoint: module compiles and links against `:client`

### Phase 3 — Modern storage
- [ ] `AndroidEnvironment.getExternalPath()` → `context.getExternalFilesDir(null)`
      / `context.getFilesDir()`
- [ ] Remove `WRITE_EXTERNAL_STORAGE` from manifest (or cap `maxSdkVersion`)
- [ ] Verify mainmenu unpack (`AndroidEnvironment.init()`) and package cache
      use writable dirs

### Phase 4 — Server-address entry on Android
- [ ] Deep-link: add `brainout://` `intent-filter`; in
      `AndroidLauncher.onCreate()` read `getIntent().getData()` and set
      `BrainOutClient.ConnectToLocation` before engine start
- [ ] UI "Direct Connect" menu (host + ports) that base64-encodes
      `host;tcp;udp;http` and calls the existing connect path
- [ ] Expose `--offline` / `--unsafe` equivalents as environment flags in
      `AndroidEnvironment`/`AndroidSettings`
- [ ] Decision point: deep-link only, UI only, or both (recommended: both)

### Phase 5 — Data packages & signing
- [ ] Decide: embed public key + signed packages, OR enable an "unsafe" build
      flavor for Android testing
- [ ] `./gradlew make_data`; wire packaged assets into `android/assets/`
- [ ] Validate APK size; if too large, lean on existing `PackageManager`
      download-on-first-run logic

### Phase 6 — Touch controls & UI scale
- [ ] Bring `AndroidGameController` to a working state (move/action modes,
      joystick movement, fire button)
- [ ] Confirm skins `touchpad-move`, `button-touch-launch`,
      `button-touch-okay/cancel` exist in built assets (add if missing)
- [ ] Tune UI scale for phone DPI (UI is desktop-oriented)

### Phase 7 — Networking
- [ ] Verify KryoNet TCP/UDP reaches the external machine's real IP
- [ ] Add `network-security-config` (cleartext) if HTTP port needs it on
      Android 9+

### Phase 8 — Server on external machine
- [ ] Document: `./gradlew server:dist`, run jar with `--offline`, bind
      `0.0.0.0`, open ports `36555-36557`
- [ ] Document connection string format `host;tcp;udp;http` → base64 →
      `brainout://...`
- [ ] Note limitation: online events / Battle Pass unavailable in `--offline`
      (no EventService); dailies + core gameplay work

### Phase 9 — Build, sign, test loop
- [ ] Debug keystore; `android:assembleDebug`; install via `adb` /
      `android:run`
- [ ] Scenario test: start server → open `brainout://` (or UI) → connect →
      load map → play with touch
- [ ] Release keystore; `assembleRelease`; update ProGuard rules
      (`android/proguard-project.txt`) for gdx/kryo/reflection

## Risks

- **Reflection / R8.** The project instantiates content classes by string name
  (`annotation-detector`, custom `Reflection`). Aggressive ProGuard/R8 keep
  rules are required; ART reflection differences are the most likely source of
  runtime crashes.
- **Asset size & data signing** is the second-largest risk.
- **gdx 1.11 API drift** is mechanical but spans several files.

## Server Quick Reference (external machine, offline)

```
# build the server
./gradlew server:dist

# run offline, bound to all interfaces (ports tcp;udp;http = 36555;36556;36557)
java -jar bin/server/brainout-server.jar --offline

# connection string the Android client must decode:
#   <host>;36555;36556;36557   ->  base64  ->  brainout://<base64>
# example for localhost:
#   localhost;36555;36556;36557 -> brainout://bG9jYWxob3N0OzM2NTU1OzM2NTU2OzM2NTU3
```
