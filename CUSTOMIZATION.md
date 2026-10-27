# Customize GrizzlyTime for another team

This repository is designed to be copied or used as a GitHub template. Teams can customize the
spreadsheet, name, colors, and logo without changing the Java code.

## 1. Create your repository

Use GitHub's **Use this template** button, or clone this repository into a new folder.

## 2. Create your Google Sheet

Start with `templates/GrizzlyTime-v3-template.xlsx`, upload it to Google Sheets, and keep these
worksheet names:

```text
Current
Date Log
Meetings
Summary
```

Copy the spreadsheet ID from the Google Sheets URL. Do not use the full URL.

## 3. Create your configuration

Copy `config.example.json` to `config.json` and replace the spreadsheet ID and application name.
Keep `config.json` private if it contains a team-specific spreadsheet ID.

## 4. Add Google credentials

Place the authorized Google API credentials at:

```text
src/main/resources/credentials/credentials.json
```

Never commit credentials, tokens, attendance records, or a real spreadsheet export.

## 5. Customize branding

The main colors are in `src/main/resources/styles/root.css`. The main logo and application icon
can be replaced with the image files used by the application. PNG files with transparent
backgrounds work best.

## 6. Build and test

From the project folder:

```powershell
.\gradlew.bat clean build
.\gradlew.bat run
```

Test with a test meeting and test user before recording real attendance.

## 7. Pi deployment

After testing, copy the project to the Pi and run:

```bash
./scripts/update.sh
```

The updater protects local changes, rebuilds the app, and restarts it.
