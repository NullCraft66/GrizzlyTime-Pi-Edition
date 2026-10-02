@echo off
setlocal

if "%~1"=="" (
  echo Usage: %~nx0 pi@hostname [project-directory]
  echo Example: %~nx0 pi@grizzlytime.local /home/pi/GrizzlyTime-PI-Edition
  exit /b 2
)

set "PI_TARGET=%~1"
set "PI_PROJECT=%~2"
if "%PI_PROJECT%"=="" set "PI_PROJECT=/home/pi/GrizzlyTime-PI-Edition"

echo Updating %PI_TARGET%:%PI_PROJECT% ...
ssh "%PI_TARGET%" "cd '%PI_PROJECT%' && ./scripts/update.sh"
if errorlevel 1 (
  echo Remote update failed.
  exit /b 1
)

echo Remote update completed successfully.
