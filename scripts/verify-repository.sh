#!/bin/sh
set -eu

failed=0

if git ls-files | grep -E '(^|/)(target|build)/|\.class$'; then
  echo 'Tracked build artifact detected' >&2
  failed=1
fi

if git grep -nE 'change-me|your-secret|demo-payment-secret|root123' -- \
  '*application*.yml' '*application*.yaml' '*.properties'; then
  echo 'Unsafe configuration default detected' >&2
  failed=1
fi

for script in $(git grep -hEo '\./scripts/[A-Za-z0-9._/-]+\.sh' -- README.md | sort -u); do
  if [ ! -f "$script" ]; then
    echo "README references missing local script: $script" >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  exit 1
fi
