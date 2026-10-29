#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example and configure the values." >&2
  exit 1
fi
set -a
# Local developer-only configuration. .env should remain private.
source .env
set +a
mvn spring-boot:run
