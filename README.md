# DeskPilot rebuilt — direct FreeRDP session integration

This is a custom Compose application project that hosts the FreeRDP session **inside DeskPilot**. It does not launch `SessionActivity` or the default aFreeRDP UI.

## Source layout
The official FreeRDP repository is fetched by GitHub Actions into `FreeRDP/`. The app depends on its `freeRDPCore` Gradle module. The FreeRDP native libraries/JNI are built by the upstream module's own CMake superbuild; DeskPilot does not duplicate or fake those native bindings.

## Build
Push this project to a GitHub repository's `main` branch. GitHub Actions runs `scripts/prepare-and-build.sh`, which clones FreeRDP and builds DeskPilot. Download `DeskPilot-debug` from the workflow artifacts.

## Current implementation
- Custom Compose home/connect form.
- Custom in-app RDP view backed by `GlobalApp.createSession(Uri, Context)`, `SessionState`, and `LibFreeRDP`.
- Graphics callbacks update a Bitmap rendered by DeskPilot's own `View`.
- Basic pointer and keyboard forwarding through FreeRDP's public Java methods.
- In-app Compose floating toolbar and controller buttons.
- EncryptedSharedPreferences wrapper for saved credentials.

## Important engineering notes
- This is a best-effort integration against the upstream `master` branch and has not been built or tested on a real RDP server in this environment. Pin the FreeRDP checkout to a commit before production releases.
- FreeRDP's `UIEventListener` is a large interface. This implementation supplies all callbacks from the current upstream interface. Authentication is supplied in the URI; certificate verification currently returns the library's default/accept result and should be replaced with a user-confirmation flow before production.
- Mouse flag values used here follow FreeRDP pointer flag definitions. Validate touchpad behavior on the target server/device; Android keycodes are forwarded through the API and may need a dedicated scancode mapper for full keyboard compatibility.
- Android overlay bubble is intentionally drawn inside the app window, not a SYSTEM_ALERT_WINDOW overlay. This keeps the session controls above the RDP surface without special permission and avoids overlay restrictions.
- `EncryptedSharedPreferences` is deprecated by AndroidX Security Crypto. It is used here because it was explicitly requested; review migration options before a production release.
