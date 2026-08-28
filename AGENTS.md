# Repository Working Agreement

These instructions apply to the entire repository.

- Never commit SMTP passwords, application-specific passwords, signing keys, real SMS bodies, private email addresses, or unredacted device logs.
- Preserve the documented platform boundary: Android and Android-compatible HarmonyOS can receive SMS with granted permissions; do not claim ordinary HarmonyOS NEXT applications can read system SMS without a verified public API.
- Keep direct SMTP diagnostics actionable but redacted. Persist diagnostic codes, predefined signals, stages, and exception types only; never persist raw server responses or credentials.
- Work on a feature or fix branch. Preserve unrelated changes in shared worktrees and stage files with an explicit allowlist.
- Run Android verification from `clients/android` with JDK 17 and Android SDK 35:

  ```bash
  ./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease connectedDebugAndroidTest
  ```

- Real-device claims require real-device evidence. Emulator checks do not prove carrier, APN, vendor background policy, SMS broadcast, or long-lock-screen behavior.
- Release APKs must be produced by the signed GitHub release workflow, then downloaded again to verify checksum, package/version, signing certificate, installation, launch, and the changed behavior.

## Code Review Rules

- Review the exact proposed HEAD commit; if the branch changes, repeat the review against the new HEAD.
- Merge only after required CI checks pass and all actionable review threads are resolved.
- Confirm that diagnostics and test fixtures contain no credentials, real SMS content, or unredacted personal data.
- Version, changelog, README test-version link, and release notes must agree before creating a release tag.
