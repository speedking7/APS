#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI="$ROOT/tools/ai-scaffold/dist/cli.js"

if [ ! -f "$CLI" ]; then
  echo "ERROR: Missing scaffold CLI: $CLI"
  echo "Build it first with: npm --prefix tools/ai-scaffold install && npm --prefix tools/ai-scaffold run build"
  exit 1
fi

FEATURE_DIR="${1:-${FEATURE_DIR:-}}"
if [ -n "${FEATURE_DIR:-}" ]; then
  exec node "$CLI" check-task-traceability "$FEATURE_DIR"
fi

exec node "$CLI" check-task-traceability
