# Changelog

All notable changes to GrizzlyTime PI Edition are documented here.

## Unreleased - Raspberry Pi Edition

### Added

- Raspberry Pi deployment documentation for hardware, Java, Google Sheets credentials, building, running, USB scanners, autostart, and troubleshooting.
- Administrative all-user logout command using the seven-digit code `0000000`.
- VS Code launch configuration for the JavaFX application.

### Changed

- README now documents Raspberry Pi OS Desktop as the target environment.
- Clarified that this is a Raspberry Pi-compatible desktop deployment and does not add GPIO-specific functionality.

### Administrative logout behavior

When `0000000` is entered, normal ID validation is bypassed. The application refreshes the spreadsheet, checks each student row, and logs out rows whose Logged In value is `TRUE`. The code works from both the regular confirmation path and the scanner/Enter-key path.

This is a convenience code, not secure authentication. Anyone who knows it can log out all active users.

## 2.4.0 - Upstream baseline

- Original GrizzlyTime 2.4.0 baseline.
- Google Sheets attendance logging, student registration, USB scanner support, and JavaFX desktop interface.
## 3.0.0 - Testing

- Added Google Sheets meeting scheduling with automatic meeting-end attendance processing.
- Added administrator controls to end or extend a meeting using `0000000`.
- Added name/email lookup for forgotten IDs and welcome-name login confirmation.
- Fixed elapsed-time calculations across hour and date boundaries.
- Added update-and-restart behavior to the Pi updater.
- Marked meeting, ID lookup, and RFID behavior as test-stage functionality.
