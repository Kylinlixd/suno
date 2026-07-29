#!/bin/sh
set -eu

failed=0
readme_file=${README_FILE:-README.md}

if git ls-files | grep -E '(^|/)(target|build)/|\.class$'; then
  echo 'Tracked build artifact detected' >&2
  failed=1
fi

if git grep -nE 'change-me|your-secret|demo-payment-secret|root123' -- \
  '*application*.yml' '*application*.yaml' '*.properties'; then
  echo 'Unsafe configuration default detected' >&2
  failed=1
fi

for script in $(awk '
  {
    for (position = 1; position <= NF; position++) {
      token = $position
      sub(/^[`]/, "", token)
      sub(/[`;,)]$/, "", token)

      if (token ~ /^[A-Za-z_][A-Za-z0-9_]*=.*/) {
        continue
      }
      if (token == "env" || token == "command" || token == "sudo" || token ~ /^-/) {
        continue
      }
      if (token ~ /^(\.\/|[A-Za-z0-9_.-]+\/)/ && token !~ /^[A-Za-z][A-Za-z0-9+.-]*:\/\// && token !~ /\/$/) {
        print token
      }
      break
    }
  }
' "$readme_file" | sort -u); do
  if [ ! -f "$script" ]; then
    echo "README references missing local script: $script" >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  exit 1
fi
