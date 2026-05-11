#!/usr/bin/env bash
set -euo pipefail

SCOPE="query"
DRY_RUN=false
OUTPUT_JSON=false
START_MS="$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)"

now_ms() {
  python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

emit_json() {
  local ok="$1"
  local error="$2"
  local steps="$3"
  local self_check_json="${4:-null}"
  local end_ms
  end_ms="$(now_ms)"
  local duration_ms=$((end_ms - START_MS))
  printf '{"ok":%s,"command":"run-tests-ci","scope":"%s","dryRun":%s,"durationMs":%s' "$ok" "$SCOPE" "$DRY_RUN" "$duration_ms"
  if [[ -n "$error" ]]; then
    printf ',"error":"%s"' "$error"
  fi
  if [[ -n "$steps" ]]; then
    printf ',"steps":%s' "$steps"
  fi
  printf ',"selfCheck":%s}\n' "$self_check_json"
}

print_help() {
  cat <<'EOF'
Usage: ./scripts/run-tests-ci.sh [query|app|all]
       ./scripts/run-tests-ci.sh [query|app|all] --dry-run
       ./scripts/run-tests-ci.sh [query|app|all] --json

Behavior:
  1) run self-check JSON preflight
  2) verify self-check ok/exitCode
  3) run requested test scope
EOF
}

POSITIONAL_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --help|-h)
      print_help
      exit 0
      ;;
    --dry-run)
      DRY_RUN=true
      ;;
    --json)
      OUTPUT_JSON=true
      ;;
    query|app|all)
      POSITIONAL_ARGS+=("$arg")
      ;;
    *)
      if [[ "$OUTPUT_JSON" == true ]]; then
        emit_json false "unsupported argument: $arg" '[{"name":"parse-args","status":"FAILED"}]'
      else
        echo "unsupported argument: $arg"
        print_help
      fi
      exit 1
      ;;
  esac
done

if [[ ${#POSITIONAL_ARGS[@]} -gt 1 ]]; then
  if [[ "$OUTPUT_JSON" == true ]]; then
    emit_json false "too many scope arguments: ${POSITIONAL_ARGS[*]}" '[{"name":"parse-args","status":"FAILED"}]'
  else
    echo "too many scope arguments: ${POSITIONAL_ARGS[*]}"
    print_help
  fi
  exit 1
fi
if [[ ${#POSITIONAL_ARGS[@]} -eq 1 ]]; then
  SCOPE="${POSITIONAL_ARGS[0]}"
fi

case "$SCOPE" in
  query|app|all) ;;
  *)
    if [[ "$OUTPUT_JSON" == true ]]; then
      emit_json false "unsupported scope: $SCOPE" '[{"name":"validate-scope","status":"FAILED"}]'
    else
      echo "unsupported scope: $SCOPE"
      print_help
    fi
    exit 1
    ;;
esac

if [[ "$DRY_RUN" == true ]]; then
  if [[ "$OUTPUT_JSON" == true ]]; then
    emit_json true "" '[{"name":"self-check","status":"SKIPPED_DRY_RUN"},{"name":"run-scope","status":"SKIPPED_DRY_RUN"}]'
  else
    echo "[ci] self-check"
    echo "[dry-run] ./scripts/run-tests.sh self-check --json"
    echo "[ci] run scope: $SCOPE"
    echo "[dry-run] ./scripts/run-tests.sh $SCOPE"
    echo "[ci] done"
  fi
  exit 0
fi

echo "[ci] self-check"
SELF_CHECK_JSON="$(./scripts/run-tests.sh self-check --json)" || {
  if [[ "$OUTPUT_JSON" == true ]]; then
    emit_json false "self-check failed" '[{"name":"self-check","status":"FAILED"}]' "$SELF_CHECK_JSON"
  else
    echo "$SELF_CHECK_JSON"
  fi
  exit 1
}

if [[ "$OUTPUT_JSON" == true ]]; then
  :
else
  echo "$SELF_CHECK_JSON"
fi

python3 - <<'PY' "$SELF_CHECK_JSON"
import json, sys
data = json.loads(sys.argv[1])
if not data.get("ok", False) or data.get("exitCode", 1) != 0:
    raise SystemExit("self-check json indicates failure")
PY
if [[ "$OUTPUT_JSON" != true ]]; then
  echo "[ci] self-check json verified"
fi

echo "[ci] run scope: $SCOPE"
if ./scripts/run-tests.sh "$SCOPE"; then
  if [[ "$OUTPUT_JSON" == true ]]; then
    emit_json true "" '[{"name":"self-check","status":"OK"},{"name":"run-scope","status":"OK"}]' "$SELF_CHECK_JSON"
  else
    echo "[ci] done"
  fi
else
  if [[ "$OUTPUT_JSON" == true ]]; then
    emit_json false "scope test failed" '[{"name":"self-check","status":"OK"},{"name":"run-scope","status":"FAILED"}]' "$SELF_CHECK_JSON"
  else
    echo "[ci] scope test failed: $SCOPE"
  fi
  exit 1
fi
