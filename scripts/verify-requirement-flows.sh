#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 || $1 != "--task" || ! $2 =~ ^(9|10|11|12|13)$ ]]; then
  echo "usage: $0 --task {9|10|11|12|13}" >&2
  exit 2
fi

script_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
root=${REQUIREMENT_FLOW_ROOT:-$script_root}
catalog="$root/docs/requirements/use-cases.yaml"

[[ -f $catalog ]] || { echo "catalog not found: $catalog" >&2; exit 1; }

ROOT="$root" TASK="$2" python3 - <<'PY'
import os
import re
import sys
from pathlib import Path
import yaml

root = Path(os.environ["ROOT"])
task = int(os.environ["TASK"])
text = (root / "docs/requirements/use-cases.yaml").read_text(encoding="utf-8")
entries = yaml.safe_load(text)
if not isinstance(entries, list):
    sys.exit("catalog root must be a YAML list")

selected = [entry for entry in entries if entry.get("documentationTask") == task]
if not selected:
    sys.exit(f"no catalog entries selected for documentation task {task}")

def require(condition, message):
    if not condition:
        sys.exit(message)

def has_anchor(section, anchor):
    return (f'id="{anchor}"' in section or f"id='{anchor}'" in section
            or f"{{#{anchor}}}" in section)

def section_for(document, entry_id):
    match = re.search(r"(?m)^(#{2,})\s+" + re.escape(entry_id) + r"(?:\s|$).*?$", document)
    if match is None:
        return None
    level = len(match.group(1))
    remainder = document[match.end():]
    boundary = next((heading.start() for heading in re.finditer(r"(?m)^(#{2,})\s+", remainder)
                     if len(heading.group(1)) <= level), None)
    return document[match.start():match.end() + boundary] if boundary is not None else document[match.start():]

def anchor_index(section, anchor):
    positions = [section.find(f'id="{anchor}"'), section.find(f"id='{anchor}'"), section.find(f"{{#{anchor}}}")]
    return min(position for position in positions if position >= 0)

def mermaid_after(section, anchor):
    position = anchor_index(section, anchor)
    remainder = section[position:]
    opening = remainder.find("```mermaid")
    if opening < 0:
        return False
    body_start = remainder.find("\n", opening)
    closing = remainder.find("```", body_start + 1)
    return body_start >= 0 and closing >= 0 and bool(remainder[body_start + 1:closing].strip())

def symbol_exists(symbol):
    if "#" not in symbol:
        return False
    class_name, method = symbol.split("#", 1)
    for source in root.glob("**/*.java"):
        content = source.read_text(encoding="utf-8")
        declared_method = re.search(r"\b" + re.escape(method) + r"\s*\(", content)
        record_component = re.search(r"\brecord\s+" + re.escape(class_name) + r"\s*\([^)]*\b" + re.escape(method) + r"\b", content, re.DOTALL)
        if re.search(r"\b(class|interface|record)\s+" + re.escape(class_name) + r"\b", content) and (declared_method or record_component):
            return True
    return False

for entry in selected:
    entry_id = entry["id"]
    document_path = root / entry.get("requirementDoc", "")
    require(document_path.is_file(), f"missing selected ID {entry_id}: requirement document {document_path} is absent")
    document = document_path.read_text(encoding="utf-8")
    section = section_for(document, entry_id)
    require(section is not None,
            f"missing selected ID {entry_id} section")
    requirement_anchor = entry.get("requirementAnchor", "")
    development_anchor = entry.get("developmentAnchor", "")
    require(has_anchor(section, requirement_anchor), f"{entry_id}: missing requirement anchor {requirement_anchor}")
    require(has_anchor(section, development_anchor), f"{entry_id}: missing development anchor {development_anchor}")
    require(mermaid_after(section, requirement_anchor), f"{entry_id}: empty Mermaid block for requirement flow")
    require(mermaid_after(section, development_anchor), f"{entry_id}: empty Mermaid block for current development flow")
    for symbol in entry["currentSymbols"]:
        require(symbol_exists(symbol), f"{entry_id}: unresolved current symbol {symbol}")
    if entry.get("implementationStatus") != "implemented":
        tail = section[anchor_index(section, development_anchor):]
        require(re.search(r"(?im)^#{2,6}\s+target architecture flow\s*$", tail) is not None,
                f"{entry_id}: missing target architecture flow")
        require(re.search(r"(?im)^#{2,6}\s+gaps\s*$", tail) is not None,
                f"{entry_id}: missing explicit gaps")

print(f"requirement flow verification passed for task {task}: {len(selected)} catalog entries")
PY
