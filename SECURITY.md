# Security Policy

## Sensitive data

Do not commit or paste any of the following into issues, pull requests, logs, or build artifacts:

- SMTP passwords, authorization codes, or app passwords;
- real SMS bodies or sender numbers;
- release signing keys or signing passwords;
- device identifiers.

Use synthetic messages for automated tests. Revoke a credential immediately if it is exposed.

## Supported security properties

- SMTP authentication is allowed only over implicit TLS or required STARTTLS.
- Server certificate hostname verification remains enabled.
- SMTP credentials and pending SMS content are encrypted with an Android Keystore key.
- Android backup and device-transfer extraction are disabled for app data.
- The app does not request historical SMS, call-log, contacts, storage, or accessibility access.
- Notification access is an optional, default-off feature used only after SMTP acceptance to
  request a semantic mark-read action exposed by the current default SMS app. Matching clues are
  bounded, encrypted with the Android Keystore, and removed on opt-out, timeout, scheduling
  failure, history clearing, or queue clearing.

## Reporting

Use GitHub's **Security** tab and the private vulnerability-reporting form to report suspected vulnerabilities. If that form is unavailable, contact the repository owner through a private channel instead of opening a public issue.

Do not include real credentials, SMS content, sender numbers, or device identifiers in the initial report. A maintainer will arrange a private channel if additional reproduction details are required.
