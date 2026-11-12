#!/usr/bin/env bash
set -euo pipefail
if [[ ! -d FreeRDP ]]; then
  git clone --depth 1 https://github.com/FreeRDP/FreeRDP.git FreeRDP
fi
chmod +x FreeRDP/client/Android/Studio/gradlew
