#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI="$ROOT/tools/ai-scaffold/dist/cli.js"

if [ ! -f "$CLI" ]; then
  npm --prefix "$ROOT/tools/ai-scaffold" ci
  npm --prefix "$ROOT/tools/ai-scaffold" run build
fi

exec node "$CLI" check-work-item-link "$@"
