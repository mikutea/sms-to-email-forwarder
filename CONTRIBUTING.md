# Contributing to Yanjian

Thank you for helping improve Yanjian. The project welcomes focused bug fixes,
compatibility evidence, tests, documentation, and maintainability improvements
that preserve its privacy and platform boundaries.

## Before opening an issue

- Use the repository issue forms and search for an existing report first.
- Use synthetic SMS text, example addresses, and redacted diagnostic codes.
- Never post SMTP credentials, authorization codes, real SMS bodies, sender or
  recipient numbers, private email addresses, device identifiers, signing keys,
  or unredacted logs.
- Report suspected vulnerabilities through GitHub private vulnerability
  reporting, not a public issue.

## Development workflow

1. Create a feature or fix branch from `main`.
2. Keep each change scoped and preserve unrelated work.
3. Add or update regression tests and documentation where appropriate.
4. Run the relevant local verification from `clients/android` with JDK 17 and
   Android SDK 35:

   ```bash
   ./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease verifyReleaseMailHandlers connectedDebugAndroidTest
   ```

5. Open a pull request and complete the checklist. The exact proposed commit
   must pass the required GitHub Actions checks before merge.

Release APKs are produced only by the signed GitHub release workflow. Do not
attach locally signed or unknown APKs as official releases.

## Platform and evidence rules

- Android and HarmonyOS devices that retain Android APK compatibility are in
  scope. Do not claim ordinary HarmonyOS NEXT apps can automatically read system
  SMS without a verified public API.
- Emulator results may support layout, unit, and integration checks, but they do
  not prove carrier delivery, vendor background behavior, or long-lock-screen
  reliability.
- Real-device reports should disclose only the platform family and major system
  version needed to reproduce the issue. Do not publish personal device details.
- Do not promise strict exactly-once SMTP delivery, universal mark-read support,
  or permanent background availability.

## Review expectations

Maintainers review scope, privacy impact, platform claims, tests, CI results, and
release implications. Generated changes and automated findings receive the same
human verification requirements as other contributions.
