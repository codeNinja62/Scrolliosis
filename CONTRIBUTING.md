# Contributing to Scrolliosis

Thank you for your interest in contributing!

## Ground rules

- Be respectful and constructive in all interactions.
- One feature or fix per pull request keeps review simple.
- Discuss large changes in an issue before writing the code.

## Getting started

1. Fork the repository and create a branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. Follow the build instructions in [README.md](README.md).
3. Make your changes, adding or updating tests where applicable.
4. Run the full test suite before opening a PR:
   ```bash
   ./gradlew test
   ```
5. Open a pull request against `main` with a clear description of the change and why it is needed.

## Code style

- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`).
- Compose UI: stateless composables with state hoisted to ViewModels.
- Prefer adding new library versions to `gradle/libs.versions.toml` using `alias(libs.*)` in Gradle scripts. Hardcoded version strings in `app/build.gradle.kts` are acceptable for one-off dependency overrides (e.g. `resolutionStrategy` conflict resolution).
- Never commit `local.properties`, keystores, or any secrets.

## Reporting bugs

Open a GitHub Issue with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behaviour
- Logcat output if relevant

## License

By contributing you agree that your contributions will be licensed under the [MIT License](LICENSE).
