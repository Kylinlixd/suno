#!/usr/bin/env bash
set -euo pipefail

SCOPE="query"
DRY_RUN=false
OUTPUT_JSON=false
GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
JSON_SCHEMA_VERSION="1.0.0"

now_ms() {
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
    return
  fi
  if date +%s%3N >/dev/null 2>&1; then
    date +%s%3N
    return
  fi
  echo "$(( $(date +%s) * 1000 ))"
}

START_MS="$(now_ms)"
REQUESTED_ARGS_JSON='[]'

json_escape() {
  local raw="$1"
  raw="${raw//\\/\\\\}"
  raw="${raw//\"/\\\"}"
  raw="${raw//$'\n'/\\n}"
  raw="${raw//$'\r'/\\r}"
  raw="${raw//$'\t'/\\t}"
  printf '%s' "$raw"
}

if [[ "$#" -gt 0 ]]; then
  REQUESTED_ARGS_JSON='['
  first_arg=true
  for arg in "$@"; do
    if [[ "$first_arg" == true ]]; then
      first_arg=false
    else
      REQUESTED_ARGS_JSON+=','
    fi
    REQUESTED_ARGS_JSON+="\"$(json_escape "$arg")\""
  done
  REQUESTED_ARGS_JSON+=']'
fi

print_help() {
  cat <<'EOF'
Usage: ./scripts/run-tests.sh [query|app|all]
       ./scripts/run-tests.sh [query|app|all] --dry-run
       ./scripts/run-tests.sh list
       ./scripts/run-tests.sh list --json
       ./scripts/run-tests.sh self-check
       ./scripts/run-tests.sh self-check --json

Scopes:
  query   Run query-audit-actions contract tests (default)
  app     Run application-layer tests
  all     Run full test suite
  list    Print available scopes only
  self-check  Validate required scripts exist and executable
EOF
}

run_cmd() {
  local cmd="$1"
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] $cmd"
  else
    eval "$cmd"
  fi
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
    query|app|all|list|self-check)
      POSITIONAL_ARGS+=("$arg")
      ;;
    *)
      echo "unsupported argument: $arg"
      print_help
      exit 1
      ;;
  esac
done

if [[ ${#POSITIONAL_ARGS[@]} -gt 1 ]]; then
  echo "too many scope arguments: ${POSITIONAL_ARGS[*]}"
  print_help
  exit 1
fi

if [[ ${#POSITIONAL_ARGS[@]} -eq 1 ]]; then
  SCOPE="${POSITIONAL_ARGS[0]}"
fi

if [[ "$OUTPUT_JSON" == true && "$SCOPE" != "list" && "$SCOPE" != "self-check" ]]; then
  echo "--json is only supported with 'list' or 'self-check' scope"
  print_help
  exit 1
fi

case "$SCOPE" in
  self-check)
    REQUIRED_SCRIPTS=(
      "./scripts/run-query-audit-actions-tests.sh"
      "./scripts/run-application-tests.sh"
      "./scripts/run-all-tests.sh"
    )
    RESULTS=()
    for s in "${REQUIRED_SCRIPTS[@]}"; do
      if [[ ! -e "$s" ]]; then
        if [[ "$OUTPUT_JSON" == true ]]; then
          RESULTS+=("{\"script\":\"$s\",\"ok\":false,\"reason\":\"MISSING\"}")
          continue
        fi
        echo "[fail] missing script: $s"
        exit 1
      fi
      if [[ ! -x "$s" ]]; then
        if [[ "$OUTPUT_JSON" == true ]]; then
          RESULTS+=("{\"script\":\"$s\",\"ok\":false,\"reason\":\"NOT_EXECUTABLE\"}")
          continue
        fi
        echo "[fail] script not executable: $s"
        exit 1
      fi
      if [[ "$OUTPUT_JSON" == true ]]; then
        RESULTS+=("{\"script\":\"$s\",\"ok\":true}")
      else
        echo "[ok] $s"
      fi
    done
    if [[ "$OUTPUT_JSON" == true ]]; then
      HAS_FAIL=false
      for item in "${RESULTS[@]}"; do
        if [[ "$item" == *"\"ok\":false"* ]]; then
          HAS_FAIL=true
          break
        fi
      done
      END_MS="$(now_ms)"
      DURATION_MS=$((END_MS - START_MS))
      printf '{"schemaVersion":"%s","command":"self-check","requestedArgs":%s,"ok":' "$JSON_SCHEMA_VERSION" "$REQUESTED_ARGS_JSON"
      if [[ "$HAS_FAIL" == true ]]; then
        printf 'false'
      else
        printf 'true'
      fi
      if [[ "$HAS_FAIL" == true ]]; then
        printf ',"exitCode":1'
      else
        printf ',"exitCode":0'
      fi
      printf ',"generatedAt":"%s","durationMs":%s,"results":[' "$GENERATED_AT" "$DURATION_MS"
      local_first=true
      for item in "${RESULTS[@]}"; do
        if [[ "$local_first" == true ]]; then
          local_first=false
        else
          printf ','
        fi
        printf '%s' "$item"
      done
      printf ']}\n'
      if [[ "$HAS_FAIL" == true ]]; then
        exit 1
      fi
    else
      echo "self-check passed"
    fi
    ;;
  list)
    if [[ "$OUTPUT_JSON" == true ]]; then
      END_MS="$(now_ms)"
      DURATION_MS=$((END_MS - START_MS))
      printf '{"schemaVersion":"%s","command":"list","requestedArgs":%s,"ok":true,"exitCode":0,"generatedAt":"%s","durationMs":%s,"scopes":["query","app","all"]}\n' "$JSON_SCHEMA_VERSION" "$REQUESTED_ARGS_JSON" "$GENERATED_AT" "$DURATION_MS"
    else
      echo "query"
      echo "app"
      echo "all"
    fi
    ;;
  query)
    [[ -x ./scripts/run-query-audit-actions-tests.sh ]] || { echo "missing script: ./scripts/run-query-audit-actions-tests.sh"; exit 1; }
    run_cmd "./scripts/run-query-audit-actions-tests.sh"
    ;;
  app)
    [[ -x ./scripts/run-application-tests.sh ]] || { echo "missing script: ./scripts/run-application-tests.sh"; exit 1; }
    run_cmd "./scripts/run-application-tests.sh"
    ;;
  all)
    [[ -x ./scripts/run-all-tests.sh ]] || { echo "missing script: ./scripts/run-all-tests.sh"; exit 1; }
    run_cmd "./scripts/run-all-tests.sh"
    ;;
  *)
    echo "unsupported scope: $SCOPE"
    print_help
    exit 1
    ;;
esac
