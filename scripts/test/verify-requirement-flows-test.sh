#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
fixture_root="$repo_root/scripts/test/fixtures/requirement-flows"
checker="$repo_root/scripts/verify-requirement-flows.sh"

run_case() {
  local fixture=$1
  local expected=$2
  local output
  if output=$(REQUIREMENT_FLOW_ROOT="$fixture_root/$fixture" "$checker" --task 9 2>&1); then
    echo "expected $fixture to fail" >&2
    exit 1
  fi
  grep -F "$expected" <<<"$output" >/dev/null || {
    echo "expected diagnostic '$expected' for $fixture, got: $output" >&2
    exit 1
  }
}

REQUIREMENT_FLOW_ROOT="$fixture_root/valid" "$checker" --task 9
run_case missing-section "missing selected ID IDN-001"
run_case missing-anchor "missing requirement anchor idn-001-login"
run_case empty-mermaid "empty Mermaid block"
run_case unresolved-symbol "unresolved current symbol AuthController#login"
run_case missing-target-flow "missing target architecture flow"
run_case missing-gaps "missing explicit gaps"
run_case section-borrow "empty Mermaid block"

echo "requirement flow checker fixtures passed"
