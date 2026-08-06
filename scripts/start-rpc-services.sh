#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_DIR="$ROOT_DIR/agent-core"
JAVA_DIR="$ROOT_DIR/agent-service"
PYTHON_BIN="${PYTHON_BIN:-$PYTHON_DIR/.venv/bin/python}"
RUNTIME_DIR="$ROOT_DIR/.runtime"
PYTHON_LOG="$RUNTIME_DIR/python-grpc.log"

if [[ ! -x "$PYTHON_BIN" ]]; then
  echo "Python virtual environment not found: $PYTHON_BIN" >&2
  echo "Create it with: cd $PYTHON_DIR && python3 -m venv .venv" >&2
  exit 1
fi

if [[ -n "${MAVEN_CMD:-}" ]]; then
  MAVEN_BIN="$MAVEN_CMD"
elif command -v mvn >/dev/null 2>&1; then
  MAVEN_BIN="$(command -v mvn)"
elif [[ -x "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" ]]; then
  MAVEN_BIN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
else
  echo "Maven was not found. Set MAVEN_CMD to the absolute path of mvn." >&2
  exit 1
fi

mkdir -p "$RUNTIME_DIR"

cleanup() {
  if [[ -n "${PYTHON_PID:-}" ]] && kill -0 "$PYTHON_PID" 2>/dev/null; then
    kill "$PYTHON_PID" 2>/dev/null || true
    wait "$PYTHON_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "Starting Python gRPC server on 127.0.0.1:50051"
(
  cd "$PYTHON_DIR"
  exec "$PYTHON_BIN" -m agent_service.server
) >"$PYTHON_LOG" 2>&1 &
PYTHON_PID=$!

sleep 1
if ! kill -0 "$PYTHON_PID" 2>/dev/null; then
  echo "Python gRPC server exited during startup:" >&2
  cat "$PYTHON_LOG" >&2
  exit 1
fi

echo "Generating Java gRPC sources"
"$MAVEN_BIN" -f "$JAVA_DIR/pom.xml" generate-sources

echo "Starting Java agent service on 127.0.0.1:8080"
"$MAVEN_BIN" -f "$JAVA_DIR/pom.xml" spring-boot:run
