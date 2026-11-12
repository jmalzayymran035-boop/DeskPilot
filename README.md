# DeskPilot Android — UI prototype 0.1

This is the first Android Studio source prototype for the requested RDP client.

## Included in this stage
- Dark Home screen with `Add PC` and saved PC cards
- Add PC form: Display Name, Host, Username, Password, Port
- Floating computer bubble and scrollable menu
- Mouse mode selector: Touchpad / Direct Mouse
- Edit Control mode and movable custom buttons
- Add Button dialog with name, key picker, ordered action sequence, OK/Cancel
- Example double-click macro: add Left Click twice
- Local persistence for saved PC profiles and controller macros

## Important: what is not connected yet
The remote desktop screen is deliberately marked as a preview. It does **not** connect to a VPS and does not send mouse/keyboard events yet. The next engineering stage is integrating FreeRDP's Android native core and wiring its bitmap, mouse and key callbacks to this UI. Do not enter real VPS passwords into this prototype: credentials are currently stored in ordinary app preferences and must be moved to Android Keystore-backed encrypted storage before real use.

## Build
1. Install Android Studio (recent stable) with Android SDK 35 and JDK 17.
2. Open this folder (`DeskPilotAndroid`) in Android Studio.
3. Let Gradle sync.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

The first Gradle sync needs internet access to download Android dependencies.

## RDP engine reference
FreeRDP's Android implementation consists of its Android UI, native FreeRDP library, and JNI bindings. Its current Android build requires Android SDK and NDK/CMake. See:
https://github.com/FreeRDP/FreeRDP/blob/master/docs/README.android
