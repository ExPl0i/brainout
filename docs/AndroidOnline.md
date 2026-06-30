# Android online build — plan (Track B / AndroidParity)

The Android port currently runs **offline only**: `AndroidLauncher` forces
`app.offline = true` because the online path crashes on Android. This documents
why and the plan to ship an **online** Android build.

## Root cause

The online stack is: `anthill-runtime-java 0.2.10` → **Unirest 1.4.9**
(`com.mashape.unirest`) → **Apache HttpClient** (`org.apache.http`, +httpcore,
+httpmime). Android ships a *stripped* legacy `org.apache.http` on the
**bootclasspath**, which always shadows any bundled copy. The stripped version is
missing API the full client uses — e.g. `AllowAllHostnameVerifier.INSTANCE` —
so the first online call throws `NoSuchFieldError` (seen via
`Online.release()` → `Unirest.shutdown()`). See `core/build.gradle` (unirest
swapped to 1.4.9) and the note in `BrainOutClient` dispose.

You cannot fix this by bundling a newer httpclient: the bootclasspath copy wins.
The package must be **relocated** so Android's `org.apache.http` is never hit.

## Approach — relocate the HTTP stack for the Android build

1. **Shaded artifact.** Add a small Gradle subproject (e.g. `:anthill-android`)
   that uses the **Shadow** plugin to bundle `anthill-runtime-java` + `unirest`
   + `httpclient`/`httpcore`/`httpmime` and **relocate** `org.apache.http` →
   `shaded.org.apache.http` (and `org.apache.commons.codec`/`logging` if pulled).
   Because Unirest's references are rewritten in the same pass, the relocated
   classes resolve to the bundled full client, never the Android boot one.
2. **Wire the android module** to depend on the shaded artifact and **exclude**
   the original `anthill-runtime-java` / `unirest` / `org.apache.httpcomponents`
   from the `:android` configuration so only the relocated copy is present.
3. **Flip offline off** on Android: make `AndroidLauncher` not force
   `app.offline = true` (gate behind a build flag so an offline APK is still
   possible).
4. **`ENV_SERVICE` via `BuildConfig`.** Add `buildConfigField` for the env URL
   and have `AndroidEnvironment` pass it through (the desktop already reads
   `brainout.env_service`; Android wires it via BuildConfig). Point it at the
   self-hosted backend.

## Risks

- Unirest 1.4.9 may reference `org.apache.http` reflectively or via service
  files (`META-INF/services`) — the relocation must also rewrite those, or the
  shaded client won't load. Verify the merged service descriptors.
- TLS: Android online needs HTTPS to the backend (the local stack is plain
  HTTP). This dovetails with the "deploy on Ubuntu + TLS" track — an online
  Android APK realistically needs the TLS staging backend first.
- Other online-only natives/paths not exercised offline may surface once
  `offline=false` (e.g. the same JZMQ/native concerns are server-side only, so
  not expected here, but the online init flow is newly exercised on device).

## Verification

- Build the online APK, run on the emulator pointed at the backend, watch
  logcat for `onlineInited → Services discovered → Authenticating → Auth done`
  (the same chain the desktop client now completes), then reaching the menu.
- Then matchmake into a match (depends on the in-game join working).

## Status

- [x] Root-caused (Unirest → Apache HttpClient vs Android stripped boot copy).
- [ ] `:anthill-android` shaded/relocated artifact (Shadow plugin).
- [ ] Android module consumes it; exclude the un-relocated originals.
- [ ] `AndroidLauncher` offline flag behind a build flag; `ENV_SERVICE` via
      `BuildConfig` → `AndroidEnvironment`.
- [ ] TLS staging backend (shared with the Ubuntu deploy track).
- [ ] Build + run online APK; verify the env→discovery→login chain on device.
