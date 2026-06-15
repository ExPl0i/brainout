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

## Environment Constraints (verification status)

The toolchain/wiring changes below were authored but **could not be built or
verified** in the session where they were written, because that environment
had:

- **no Android SDK** (`ANDROID_HOME`/`ANDROID_SDK_ROOT` unset, no `sdkmanager`/`adb`)
- **only JDK 21** (AGP 8 targets JDK 17; the rest of the project expects JDK 11)

Consequently the Android module is wired to be **opt-in**: it is only configured
when an Android SDK is present, or when `-PwithAndroid` is passed. Default
desktop/server builds are unaffected. All Android changes must still be built
and validated on a machine with JDK 17 + Android SDK (platform 34) before they
can be trusted. Open known risks to confirm on first real build:

- libGDX version conflict between the custom `com.github.desertkun.libgdx:gdx`
  fork used by `:core` and the stock `gdx-backend-android:1.11.0`.
- exact AGP 8 task wiring for `copyAndroidNatives` (jniLibs packaging).

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
> Implemented (UNVERIFIED — no SDK/JDK17 in authoring env). The wiring is
> guarded so it stays inert without an Android SDK.
- [x] `settings.gradle`: include `'android'` conditionally (SDK present or `-PwithAndroid`)
- [x] root `build.gradle`: gated `google()` repo + AGP classpath `8.1.4`
      (only when SDK present); legacy `project(":android")` block left commented
      — config now lives in `android/build.gradle`
- [x] `android/build.gradle`: rewritten for AGP 8 (`namespace`, `compileSdk 34`,
      `defaultConfig{applicationId, minSdk 24, targetSdk 34, versionCode/Name}`,
      `buildTypes`, `packagingOptions` for `.so`, `copyAndroidNatives` task)
- [x] `AndroidManifest.xml`: dropped legacy `package=`/`uses-sdk`, added
      `android:exported="true"`, removed `WRITE_EXTERNAL_STORAGE`
- [x] gdx-backend-android + natives incl. **arm64-v8a** and x86_64
- [x] Checkpoint: `./gradlew android:assembleDebug` **builds a debug APK**
      (verified on JDK 17 + Android SDK platform 34; `android-debug.apk`, ~5 MB,
      assets not yet bundled — see Phase 5)

> **Verified build environment (2026-06):** Microsoft OpenJDK 17, Android SDK
> platform-34 + build-tools 34.0.0 (AGP 8.1.4 auto-pulled build-tools 33.0.1),
> cmdline-tools installed. `local.properties` points `sdk.dir` at the SDK
> (gitignored). Build command:
> `gradlew.bat :android:assembleDebug -PwithAndroid` with `JAVA_HOME`=JDK 17.
> Two packaging conflicts had to be resolved in `android/build.gradle`:
> 1. **libGDX fork vs stock gdx** — `gdx-backend-android` pulls stock
>    `com.badlogicgames.gdx:gdx:1.11.0`, which duplicates the
>    `com.github.desertkun.libgdx:gdx` fork from `:core`/`:client`. Fixed by
>    excluding the stock `gdx` module from `gdx-backend-android`.
> 2. **Duplicate META-INF resources** from transitive jars — fixed with a
>    `packagingOptions.resources.excludes` block.

### Phase 2 — Fix compilation (API drift)
- [x] `AndroidGameController`: updated to gdx 1.11 APIs
      (`scrolled(float,float)`; replaced stale
      `BrainOutClient.EventMgr.sendEvent(receiver, e)` with the inherited
      `sendEvent(e)` helper). `Touchpad`/`Stage`/`InputProcessor` compile clean.
- [x] Reviewed `AndroidPackageManager`/`AndroidPackage`/`AndroidSettings`/
      `AndroidEnvironment` for stale calls against current `:core`/`:client`:
      - `ClientEnvironment` now requires `getStoreComponent()`; the engine owns
        `Reflection` (`BrainOut.R = new Reflection()`), so the old
        `getReflection()`/`ClientReflection` override was removed.
      - `ClientSettings` now takes a `ClientEnvironment` ctor and requires
        `getDefaultDisplayMode()`/`getDisplayModes()` (was `getDefaultWidth/Height`).
        Added `AndroidDisplayMode` (subclass to reach the protected
        `Graphics.DisplayMode` ctor) built from device `DisplayMetrics`.
      - package helpers moved `PackageManager.*` → `ZipContentPackage.packageFile/
        packageFilename`; `ContentPackage` ctor now throws checked
        `ValidationException` (declared on `AndroidPackage`/`createPackage`);
        `PackageFileHandle.entryName` is gone (now stored locally).
      - `AndroidLauncher` wires env→settings(ctx) in the right order.
- [x] `AndroidEnvironment.getUniqueId()`: replaced hidden
      `android.os.SystemProperties` reflection with `Settings.Secure.ANDROID_ID`
- [x] Checkpoint: module compiles and links against `:client` (full debug APK)

### Phase 3 — Modern storage
- [x] `AndroidEnvironment.getExternalPath()` → `context.getFilesDir()`-based
      absolute path (app-private internal storage, permission-free, scoped-storage
      safe). Chose `getFilesDir()` over `getExternalFilesDir(null)` so it shares
      the base that `Gdx.files.local` uses on Android — otherwise mainmenu unpack
      and `PackageManager.searchPackages()` (which scans `Gdx.files.local
      ("packages")`) would resolve to different locations. The only caller is
      `ZipContentPackage`, always with a relative `packages/<name>.zip`.
- [x] `WRITE_EXTERNAL_STORAGE` already absent from the manifest (Phase 1);
      only `INTERNET` is requested.
- [x] mainmenu unpack (`AndroidEnvironment.init()`) and the package cache use
      writable dirs: packages live under `getFilesDir()` (init() `mkdirs()` the
      parent), cache via `context.getCacheDir()`.

### Phase 4 — Server-address entry on Android
> Decision (confirmed): support **both** deep-link and a UI field.
- [x] Deep-link: `brainout://` `intent-filter` in manifest; `AndroidLauncher`
      reads `getIntent().getData()` and sets `BrainOutClient.ConnectToLocation`
      before engine start
- [x] UI "Direct Connect" menu (host + ports) that base64-encodes
      `host;tcp;udp;http` and calls the existing connect path
      — `client/.../menu/impl/DirectConnectMenu.java` (host + tcp/udp/http
      fields, defaults `36555/36556/36557`); encodes via `Base64.encode` then
      decodes through the same `HashedUrl` → `ClientController.connect` path as
      the deep link. Entry point: a "Direct Connect" button added to `IntroMenu`,
      shown only on `Application.ApplicationType.Android`.
- [ ] Expose `--offline` / `--unsafe` equivalents as environment flags in
      `AndroidEnvironment`/`AndroidSettings`
      — deferred to Phase 5: `--offline` is a *server* flag (irrelevant to the
      client); the only client-side flag is `--unsafe` (`BrainOutClient.unsafe`,
      accept unsigned packages), which is meaningful only once data packages
      land, so it is decided together with signing in Phase 5.

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
