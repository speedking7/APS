#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLI="$ROOT/tools/ai-scaffold/dist/cli.js"

if [ ! -f "$CLI" ]; then
  npm --prefix "$ROOT/tools/ai-scaffold" ci
  npm --prefix "$ROOT/tools/ai-scaffold" run build
fi

FEATURE_DIR="${1:-${FEATURE_DIR:-}}"
if [ -z "${FEATURE_DIR:-}" ]; then
  echo "ERROR: Provide a feature directory path or set FEATURE_DIR."
  exit 1
fi

ARGS=("$CLI" "check-feature-artifacts" "$FEATURE_DIR")
if [ "${SKIP_CODE_REVIEW_VERDICT:-0}" = "1" ]; then
  ARGS+=("--skip-code-review-verdict")
fi

exec node "${ARGS[@]}"
