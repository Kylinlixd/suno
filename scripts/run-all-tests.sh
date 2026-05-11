#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] check java"
java -version

echo "[2/3] check maven"
mvn -version

echo "[3/3] run all tests"
mvn -q test

echo "done: all tests passed"
