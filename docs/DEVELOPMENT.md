# Development

## Prerequisites

- JDK 17 or newer (the build targets Java 17 bytecode).
- Android SDK with platform 35. Android Studio installs it; on the command
  line, `sdkmanager "platforms;android-35" "build-tools;35.0.0"`.
- A `local.properties` at the repository root pointing at the SDK, if
  `ANDROID_HOME` is not set (Android Studio writes it):

  ```properties
  sdk.dir=/Users/you/Library/Android/sdk
  ```

Gradle itself comes with the wrapper (`./gradlew`).

## Common commands

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build and install on the connected device/emulator
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint (fails on errors)
./gradlew assembleRelease        # minified release build (unsigned without signing config)
```

CI runs `lintDebug testDebugUnitTest assembleDebug` on every push and pull
request.

## Pointing the app at a backend

The API base URL is a Gradle property, never a code change:

| Build | Default | Where |
| --- | --- | --- |
| debug | `http://localhost:19300` | `um.apiBaseUrl.debug` in `gradle.properties` |
| release | `https://api.unlimitedmessaging.app` | `um.apiBaseUrl.release` |

### Against `pnpm dev` on your computer (phone or emulator)

Sign-in goes through the website's page (port 19400) and the app talks to the
API (port 19300), both on your computer. `adb reverse` makes `localhost` on
the phone point at the computer, over USB, with no network setup:

```sh
# Phone plugged in with USB debugging on (or an emulator running)
adb reverse tcp:19300 tcp:19300   # API
adb reverse tcp:19400 tcp:19400   # website: the sign-in page
./gradlew installDebug
```

Run the two `adb reverse` again after unplugging the phone or restarting the
emulator. The API must publish its OAuth endpoints on localhost too:
`MCP_PUBLIC_URL=http://localhost:19300` in `apps/api/.env` (a tunnel URL
there would send the phone to the tunnel instead).

The first sign-in in a fresh browser shows Chrome's welcome screens first;
after that the website's page opens directly in the tab.

### Against another backend

```sh
./gradlew installDebug -Pum.apiBaseUrl=https://staging-api.example.com
```

Debug builds allow plain HTTP (`src/debug/res/xml/network_security_config.xml`);
release builds only HTTPS. The debug build also has its own application id
(`app.unlimitedmessaging.android.debug`), so it installs next to the release
one and uses its own OAuth redirect URI.

## Testing SMS without a SIM

The emulator has a simulated modem.

- **Receive an SMS:** `adb emu sms send +33612345678 "Hello from the network"`,
  or the emulator's *Extended controls > Phone* panel. It shows up in the
  app as *Forwarded* and in the dashboard inbox.
- **Send an SMS:** queue one from the API or the dashboard to any valid
  international number (`+33612345678`): the simulated network accepts it
  and returns a delivery report, so it goes *Sent* then *Delivered*. The
  emulator's own number (`+1 555...`) is a fictional one the API refuses as
  a recipient.
- The emulator SIM's fictional number is not stored on its account either,
  for the same reason. A real SIM's number is.

On a real phone, a second phone or a friend is the simplest recipient.

## Project layout

```text
app/src/main/java/app/unlimitedmessaging/android/
  GatewayApp.kt, AppContainer.kt   application and dependency wiring
  auth/     OAuth 2.1 + PKCE sign-in, encrypted session storage
  api/      HTTP client and the /device/* endpoints
  service/  foreground service loop, start/stop decisions, notifications
  sms/      sending, per-part result aggregation, receiving
  sim/      SIM listing
  work/     durable uploads (WorkManager) and the watchdog
  system/   permissions, battery and manufacturer settings
  data/     preferences, local activity list
  ui/       Compose screens
app/src/test/   JVM unit tests
docs/           this documentation
```

[ARCHITECTURE.md](ARCHITECTURE.md) explains how the pieces fit together.

## Conventions

- Kotlin official code style (`kotlin.code.style=official`), 4-space indent.
- Every user-facing string lives in `res/values/strings.xml` (English,
  default) and `res/values-fr/strings.xml`. Never hard-code one in a screen.
- The look is Unlimited Messaging's, the same as the website and dashboard:
  colors, fonts and shapes come from `ui/theme/Theme.kt` and the shared
  pieces from `ui/components/UmComponents.kt`. Screens never pick a raw
  color or a stock Material component style of their own.
- No em dashes in strings, comments or docs: use a hyphen, a comma or a
  period.
- Keep [API_CONTRACT.md](API_CONTRACT.md) and `api/Models.kt` in step.
- Logic that can be tested without Android (aggregation, decoding, backoff,
  OAuth requests) stays free of Android types so it runs as a JVM test.
- Conventional Commits for commit messages (`feat(sms): ...`,
  `fix(auth): ...`).
