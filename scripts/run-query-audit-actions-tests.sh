#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] check java"
java -version

echo "[2/3] check maven"
mvn -version

echo "[3/3] run query-audit-actions contract tests"
mvn -q -Dtest=RecycleApplicationServiceQueryAuditActionsTest test

echo "done: query-audit-actions contract tests passed"
