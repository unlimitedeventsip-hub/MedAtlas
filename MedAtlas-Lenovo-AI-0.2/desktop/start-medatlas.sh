#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
command -v python3 >/dev/null 2>&1 || {
  printf '%s\n' "Python 3 is required to start the local MedAtlas Companion server." >&2
  exit 1
}
printf '%s\n' "MedAtlas Companion: http://127.0.0.1:8765"
python3 -m http.server 8765 --bind 127.0.0.1
