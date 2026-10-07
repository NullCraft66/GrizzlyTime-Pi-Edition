#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_DIR"

echo "Checking for updates in $PROJECT_DIR"
git fetch origin main

LOCAL_COMMIT="$(git rev-parse HEAD)"
REMOTE_COMMIT="$(git rev-parse origin/main)"

if [[ "$LOCAL_COMMIT" == "$REMOTE_COMMIT" ]]; then
  echo "Already up to date at ${LOCAL_COMMIT:0:7}."
  exit 0
fi

if ! git diff --quiet || [[ -n "$(git status --porcelain)" ]]; then
  echo "Local changes detected. Update cancelled to protect them."
  exit 1
fi

git merge --ff-only origin/main
./gradlew clean build

echo "Update complete at $(git rev-parse --short HEAD)."
echo "Stopping any running GrizzlyTime process..."
pkill -f 'GrizzlyTime|org.gradle.wrapper.GradleWrapperMain run' || true

echo "Starting GrizzlyTime..."
nohup ./gradlew run > logs/grizzlytime.log 2>&1 &
echo "GrizzlyTime restarted. Log: ${PROJECT_DIR}/logs/grizzlytime.log"
