#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
./mvnw -pl suno-bootstrap -am \
  -Dtest=DocumentationCatalogCoverageTest,DocumentationFlowCoverageTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

python3 - <<'PY'
from pathlib import Path
import re
import sys

root = Path(".")
for directory in (root / "docs" / "requirements", root / "docs" / "development", root / "docs" / "architecture"):
    for path in directory.rglob("*.md"):
        text = path.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), 1):
            if re.search(r"\b(?:TODO|TBD|FIXME|XXX)\b|待补充|占位", line, re.IGNORECASE):
                sys.exit(f"unfinished documentation content: {path}:{line_number}: {line.strip()}")
print("unfinished documentation scan passed")
PY
