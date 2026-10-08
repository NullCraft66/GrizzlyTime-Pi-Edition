# Customize GrizzlyTime for another team

This repository is designed to be copied or created from the GitHub template. Team-specific
settings stay outside the Java source wherever possible.

## Quick setup

1. Create a repository from the GitHub template.
2. Copy `config.example.json` to `config.json`.
3. Create a blank Google Sheet from `templates/GrizzlyTime-v3-template.xlsx`.
4. Put the Google Sheet ID in `config.json` under `sheet`.
5. Rename the application with `applicationName`.
6. Add Google OAuth credentials at `src/main/resources/credentials/credentials.json`.
7. Run `./gradlew clean build` on Linux/macOS or `gradlew.bat clean build` on Windows.

## Configuration

`handsFreeMode` disables the confirmation dialog when set to `true`.

`updateNotifier` controls update notifications.

`idLength` is the normal ID length. `idLengthFallback` is the alternate length used for mentor or
other team IDs.

`applicationName` is the name shown in the application window.

## Branding

Replace the runtime `images/error.png` and `images/icon.png` files with the team's PNG assets.
Preserve transparent backgrounds when possible. Colors are in `src/main/resources/styles/root.css`.

## Google Sheet tabs

Keep these tabs and headers because the application reads them by name:

- `Current`
- `Date Log`
- `Meetings`
- `Summary`

Do not commit real IDs, names, emails, attendance, OAuth credentials, `config.json`, or `tokens/`.
Those files are excluded by `.gitignore`.
