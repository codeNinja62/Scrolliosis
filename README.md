# BrainGate

BrainGate is an open-source Android app that helps you reclaim focus by blocking distracting apps behind a knowledge gate. Instead of scrolling mindlessly, you must answer a question from your personal Knowledge Vault before any blocked app is allowed to open — for a limited time window only.

---

## Features

- **App blocking** — select any installed app to block via an Accessibility Service
- **Knowledge Vault** — store your own questions & answers; one must be answered correctly to unlock a blocked app for 5 minutes
- **Overlay enforcement** — a full-screen overlay immediately appears when a blocked app is launched
- **GateKeeper mode** — configure blocking schedules and rules
- **System Purge** — quickly clear all entries from the vault
- **Onboarding** — guided step-by-step permission setup (Overlay, Accessibility, battery optimisation)
- **Boot persistence** — the service restores itself after a device reboot
- **Manufacturer workarounds** — auto-detects OEM battery-saver restrictions (Xiaomi, Huawei, Samsung, etc.) and prompts the correct settings screen

---

## Requirements

| | |
|---|---|
| Min Android | 8.0 (API 26) |
| Target Android | 15 (API 35) |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |

---

## Architecture

```
app/src/main/java/com/saltatoryimpulse/braingate/
├── data/                   # Repository pattern (IKnowledgeRepository / KnowledgeRepository)
├── di/                     # Koin dependency injection module
├── overlay/                # Overlay controller & config
├── ui/
│   ├── components/         # Reusable Compose components
│   ├── screens/            # HomeScreen, GateKeeperScreen, KnowledgeVaultScreen,
│   │                       # AppSelectionScreen, OnboardingScreen, SystemPurgeScreen
│   └── theme/              # BrainGateTheme, colour tokens
├── AppViewModel.kt         # Shared ViewModel
├── BrainGateApplication.kt # Koin initialisation
├── BootReceiver.kt         # Restart service on boot
├── Constants.kt            # App-wide constants
├── GateService.kt          # Accessibility service — core blocking logic
├── KnowledgeHistory.kt     # Room entities, DAO, and AppDatabase
├── MainActivity.kt         # Single activity, Compose NavHost
├── ManufacturerUtils.kt    # OEM-specific battery settings helpers
└── PermissionsUtils.kt     # Runtime permission helpers
```

**Key libraries:** Room · Koin · Navigation Compose · Lifecycle · Robolectric (tests)

---

## Building

### Prerequisites

- JDK 11 (e.g. [Eclipse Temurin](https://adoptium.net/))
- Android SDK with API 35 platform installed

### Clone and build

```bash
git clone https://github.com/<your-username>/BrainGate.git
cd BrainGate
./gradlew :app:assembleDebug          # Linux / macOS
gradlew.bat :app:assembleDebug        # Windows
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Tests

```bash
./gradlew test                        # JVM unit tests (Robolectric)
./gradlew connectedAndroidTest        # Instrumented tests (requires device / emulator)
```

---

## Release signing

Signing credentials are **never** stored in the repository. For local release builds, add the following to `local.properties` (gitignored):

```properties
# Path is relative to the project root directory
SIGNING_STORE_FILE=../key/key
SIGNING_STORE_PASSWORD=<your-store-password>
SIGNING_KEY_ALIAS=<your-key-alias>
SIGNING_KEY_PASSWORD=<your-key-password>
```

> The store file path can be absolute or relative to the project root. The file itself must stay **outside** the repository directory.

CI uses GitHub Actions secrets — see [`.github/workflows/release.yml`](.github/workflows/release.yml).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

[MIT](LICENSE)
