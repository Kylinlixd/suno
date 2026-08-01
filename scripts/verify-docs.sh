#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
./mvnw -pl suno-bootstrap -am -Dtest=DocumentationCatalogCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test
