# Contributing to Antigravity Mobile Companion

We welcome contributions, bug reports, and enhancements from the community. Follow these guidelines to ensure a smooth, professional collaboration.

---

## Code of Conduct & Core Invariants

1. **Absolute Scientific & Technical Honesty**:
   - Provide accurate architectural justifications for all proposed changes.
   - Do not claim unsupported capabilities or hide performance bottlenecks.

2. **Zero Project Pollution & Privacy Preservation**:
   - Never commit hardcoded personal credentials, private IP addresses, personal email accounts, or machine identifiers.
   - All tests and configurations must be hermetic and portable across any developer environment.

3. **Style and Formatting Standards**:
   - **Strictly ZERO emojis** anywhere in code, markdown documentation, commit messages, or pull request discussions.
   - Maintain clean, publication-grade Kotlin code conforming to official Android Architecture Guidelines.

---

## Development Workflow

### Prerequisites

- Android Studio Koala / Ladybug or newer (or command-line tools).
- JDK 17 (recommended: Eclipse Temurin or OpenJDK 17).
- Android SDK Platform 34 and Build-Tools 34.0.0.
- `adb` connected device or emulator running Android 8.0 (API 26) or higher.

### Building the Project

```bash
git clone https://github.com/ijstt/antigravity-mobile-companion.git
cd antigravity-mobile-companion

# Verify Gradle wrapper and build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Code Conventions

- **Architecture**: Jetpack Compose declarative UI with unidirectional data flow (UDF), StateFlow, and Coroutines.
- **Networking**: OkHttp 4 for WebSockets, Retrofit/Gson for JSON parsing, and AndroidX Browser (`CustomTabsIntent`) for Google OAuth flows.
- **Camera / QR**: AndroidX CameraX with Google ML Kit Barcode Scanning.
- **Commit Messages**: Follow Conventional Commits:
  - `feat(...)`: new functionality
  - `fix(...)`: bug fix
  - `refactor(...)`: code refactoring without behavior change
  - `docs(...)`: documentation changes
  - `ci(...)`: GitHub Actions workflow changes

---

## Submitting a Pull Request

1. Fork the repository and create your branch from `main`:
   ```bash
   git checkout -b feat/my-enhancement
   ```
2. Make your changes adhering to all formatting rules (no emojis, clean diffs).
3. Ensure the project builds cleanly without warnings:
   ```bash
   ./gradlew check assembleDebug
   ```
4. Push your branch to your fork and open a Pull Request against `main`.
5. Clearly describe the problem solved and test results in the PR description.
