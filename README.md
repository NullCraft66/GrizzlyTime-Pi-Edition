# GrizzlyTime PI Edition

GrizzlyTime PI Edition is a Raspberry Pi deployment of the GrizzlyTime JavaFX time-logging application. It records student and mentor attendance in Google Sheets and supports USB barcode scanners.

This edition targets a Raspberry Pi 4 or 5 running Raspberry Pi OS 64-bit with Desktop. It does not require GPIO hardware: the scanner acts as a USB keyboard and the application communicates with Google Sheets over the network.

## What changed from upstream

- Added the administrative `0000000` code for meeting controls: end a meeting or extend it.
- Added Raspberry Pi installation, display, autostart, troubleshooting, and maintenance guidance.
- Kept the original Google Sheets workflow and JavaFX interface.

See [CHANGELOG.md](CHANGELOG.md) for the full change history.

## Requirements

- Raspberry Pi 4 or 5 recommended
- Raspberry Pi OS 64-bit with Desktop
- Internet connection
- Java 11 or Java 17 runtime/development kit
- USB barcode scanner configured to send Enter after each scan
- Google account and Google Sheets API credentials
- Monitor, keyboard, and mouse for initial setup

## Customize for another team

Use this repository as a GitHub template, then follow [CUSTOMIZATION.md](CUSTOMIZATION.md). The
blank spreadsheet template is available at [templates/GrizzlyTime-v3-template.xlsx](templates/GrizzlyTime-v3-template.xlsx).

This is not a headless service. A graphical desktop session is required because the application uses JavaFX.

## Install on Raspberry Pi

### 1. Update the operating system

```bash
sudo apt update
sudo apt full-upgrade -y
sudo reboot
```

After reboot, confirm the system is 64-bit:

```bash
getconf LONG_BIT
```

The expected result is `64`.

### 2. Install Java and tools

Java 17 is a good default for current Raspberry Pi OS releases:

```bash
sudo apt install -y openjdk-17-jdk git unzip
java -version
```

If Java 11 is required, install `openjdk-11-jdk` and use it consistently. The Gradle build selects JavaFX dependencies based on the Java major version.

### 3. Download the project

```bash
cd ~
git clone https://github.com/YOUR-ACCOUNT/GrizzlyTime-PI-Edition.git
cd GrizzlyTime-PI-Edition
```

Replace `YOUR-ACCOUNT` with the GitHub account that owns this repository.

### 4. Configure Google Sheets

1. Create or select a Google Cloud project.
2. Enable the Google Sheets API.
3. Create OAuth client credentials for a desktop application.
4. Place the downloaded credentials at `src/main/resources/credentials/credentials.json`.
5. Build and run once, then complete Google authorization in the browser.
6. Never commit the credentials file; it is excluded by `.gitignore`.

The destination spreadsheet and application settings are controlled by `config.json`. Review the template before first use and confirm that the authorized account can access the sheet.

### 5. Build and run

```bash
./gradlew clean build
./gradlew run
```

For a distributable fat JAR:

```bash
./gradlew shadowJar
ls -l build/libs
java -jar build/libs/GrizzlyTime-3.0.0-all.jar
```

The exact JAR filename follows the project version.

## USB barcode scanner

Most scanners work without a driver because they emulate a USB keyboard. Test one in a text editor and confirm that the scan is followed by Enter. Configure the scanner to append Enter if needed. Avoid scanners that require Windows-only configuration software unless they can be configured before deployment.

## Optional desktop autostart

After manual startup works, create an autostart entry:

```bash
mkdir -p ~/.config/autostart
nano ~/.config/autostart/grizzlytime.desktop
```

Use this configuration, replacing the path as needed:

```ini
[Desktop Entry]
Type=Application
Name=GrizzlyTime PI Edition
Comment=Attendance time logging
Exec=/bin/bash -lc 'cd /home/pi/GrizzlyTime-PI-Edition && ./gradlew run'
Terminal=false
X-GNOME-Autostart-enabled=true
```

For a production kiosk, use a tested packaged launch command rather than running Gradle at every boot.

## Updating an installed Pi

From the project directory, run:

```bash
chmod +x scripts/update.sh
./scripts/update.sh
./gradlew run
```

The updater fetches `origin/main`, stops if local changes are present, fast-forwards the checkout, rebuilds the application, stops the running app, and starts it again in the background. It does not alter `config.json`, Google credentials, or other untracked files.

### Updating the Pi remotely from Windows

After enabling SSH on the Pi and confirming that the laptop can connect with `ssh pi@<pi-hostname>`, run this from PowerShell or Command Prompt in the project directory:

```bat
scripts\update-pi.bat pi@<pi-hostname>
```

For a non-default project location, provide it as the second argument:

```bat
scripts\update-pi.bat pi@<pi-hostname> /home/pi/GrizzlyTime-PI-Edition
```

The first SSH connection may ask you to confirm the Pi's host key. SSH keys are recommended so the command can run without typing a password each time. The script updates and rebuilds the Pi; it does not automatically stop or restart an already-running application.

## Administrative logout code

Entering `0000000` opens meeting controls. The administrator can end the meeting, which logs out every row whose Logged In value is `TRUE`, or extend the active meeting by entering hours and minutes.

The code is seven digits so it does not collide with the normal six-digit ID format. It is a shared hard-coded administrative code, not a secure password. Anyone who knows it can log out all users, so do not treat it as authentication.

## Troubleshooting

**JavaFX/display errors:** confirm that Raspberry Pi OS Desktop is installed and that the command runs inside the graphical session.

**Google authorization fails:** verify the system date/time, internet access, credentials location, and API enablement.

**Scanner input does nothing:** test in a text editor and configure an Enter suffix.

**Sheets updates are slow:** check Wi-Fi, spreadsheet permissions, and API quotas.

**All-logout does not work:** enter exactly `0000000`, verify that the sheet uses `TRUE` in the Logged In column, and check the application log.

## Development

```bash
./gradlew spotlessApply
./gradlew clean build
```

## Testing status

Meeting scheduling, remote meeting controls, name/email ID lookup, and automatic Pi restart are
currently test-stage features. Test them with a test meeting and test user before using them for
real attendance records. RFID support has not been tested with a physical reader yet; readers
that emulate a USB keyboard and append Enter are expected to use the existing ID input path.

The project is released under the MIT license and is based on [YCSRobotics/GrizzlyTime](https://github.com/YCSRobotics/GrizzlyTime).
