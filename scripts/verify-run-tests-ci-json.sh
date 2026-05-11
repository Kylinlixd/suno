#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

assert_json_contract() {
  local payload="$1"
  local expected_ok="$2"
  local expected_scope="$3"
  local expected_dry_run="$4"
  local expected_error="${5:-}"

  python3 - <<'PY' "$payload" "$expected_ok" "$expected_scope" "$expected_dry_run" "$expected_error"
import json
import sys

data = json.loads(sys.argv[1])
expected_ok = sys.argv[2].lower() == "true"
expected_scope = sys.argv[3]
expected_dry_run = sys.argv[4].lower() == "true"
expected_error = sys.argv[5]

assert data["ok"] == expected_ok
assert data["command"] == "run-tests-ci"
assert data["scope"] == expected_scope
assert data["dryRun"] == expected_dry_run
assert isinstance(data["durationMs"], int)
assert isinstance(data.get("steps"), list) and len(data["steps"]) >= 1
if expected_error:
  assert data["error"] == expected_error
PY
}

echo "[check] dry-run json contract"
DRY_JSON="$(./scripts/run-tests-ci.sh query --dry-run --json)"
assert_json_contract "$DRY_JSON" true query true

echo "[check] invalid argument json contract"
set +e
INVALID_JSON="$(./scripts/run-tests-ci.sh --json --bad-arg 2>/dev/null)"
INVALID_EXIT=$?
set -e
if [[ "$INVALID_EXIT" -eq 0 ]]; then
  echo "[fail] expected non-zero exit for invalid argument"
  exit 1
fi
assert_json_contract "$INVALID_JSON" false query false "unsupported argument: --bad-arg"

echo "verify-run-tests-ci-json passed"
