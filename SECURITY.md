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
- The app does not request historical SMS, call-log, contacts, storage, accessibility, or notification-listener access.

## Reporting

Because the repository is private, report suspected vulnerabilities directly to the repository owner through a private channel. Do not include real credentials or SMS content in the initial report.

