#!/bin/sh
set -eu

fixture='scripts/test-fixtures/readme-local-script-references.md'

if output=$(README_FILE="$fixture" ./scripts/verify-repository.sh 2>&1); then
  echo 'Expected repository verification to fail for fixture references' >&2
  exit 1
fi

expect_present() {
  if ! printf '%s\n' "$output" | grep -Fqx "README references missing local script: $1"; then
    echo "Expected missing local script to be reported: $1" >&2
    exit 1
  fi
}

expect_absent() {
  if printf '%s\n' "$output" | grep -Fqx "README references missing local script: $1"; then
    echo "Unexpected local script report: $1" >&2
    exit 1
  fi
}

expect_absent './mvnw'
expect_present 'bin/reconcile'
expect_present 'tools/reindex'
expect_present './tools/release.custom'
expect_absent './tools/'
expect_absent './docs/guide.md'
expect_absent 'https://example.com/scripts/release.sh'

printf '%s\n' 'README local-script fixture check passed'
