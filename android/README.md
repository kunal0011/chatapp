# Android App

Kotlin + Jetpack Compose client for text-only chat.

## Stack
- Jetpack Compose UI
- Hilt dependency injection
- Retrofit for REST
- Socket.IO client for realtime updates
- DataStore for session persistence

## Flow
1. Login/register.
2. Load contacts (all users except current user).
3. Tap contact to create or open direct conversation.
4. View history and send/receive messages in realtime.

## Local configuration
Default backend endpoint is set in `app/build.gradle.kts`:
- `BASE_URL = http://10.0.2.2:4000/api/v1/`
- `SOCKET_URL = http://10.0.2.2:4000`

Use `10.0.2.2` for Android emulator access to host machine.

For a physical device, set LAN URLs in `android/local.properties` (or Gradle command line):
- `CHATAPP_BASE_URL=http://<your-laptop-ip>:4000/api/v1/`
- `CHATAPP_SOCKET_URL=http://<your-laptop-ip>:4000`

## Quality commands
- `./gradlew ktlintCheck`
- `./gradlew detekt`
- `./gradlew testDebugUnitTest`
