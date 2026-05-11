#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] check java"
java -version

echo "[2/3] check maven"
mvn -version

echo "[3/3] run application layer tests"
mvn -q -Dtest='com.recycle.mall.application.*Test' test

echo "done: application layer tests passed"
