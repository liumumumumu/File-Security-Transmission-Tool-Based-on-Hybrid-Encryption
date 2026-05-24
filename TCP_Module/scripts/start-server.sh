#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_NAME="${JAR_NAME:-FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar}"
JAR_PATH="${JAR_PATH:-${PROJECT_DIR}/target/${JAR_NAME}}"
CONFIG_PATH="${CONFIG_PATH:-${PROJECT_DIR}/application-server.yml}"

if [[ ! -f "${JAR_PATH}" && -f "${SCRIPT_DIR}/${JAR_NAME}" ]]; then
  JAR_PATH="${SCRIPT_DIR}/${JAR_NAME}"
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Jar not found: ${JAR_PATH}"
  echo "Run 'mvn clean package' first, or set JAR_PATH to the packaged jar."
  exit 1
fi

ARGS=(
  "--app.role=server"
  "--spring.profiles.active=server"
  "--server.tcp.enabled=${SERVER_TCP_ENABLED:-true}"
  "--server.tcp.bind-host=${SERVER_TCP_BIND_HOST:-0.0.0.0}"
  "--server.tcp.bind-port=${SERVER_TCP_BIND_PORT:-9000}"
  "--server.address=${SERVER_HTTP_ADDRESS:-0.0.0.0}"
  "--server.port=${SERVER_HTTP_PORT:-8080}"
  "--node.device-id=${NODE_DEVICE_ID:-server-node}"
  "--node.auto-connect=false"
)

if [[ -f "${CONFIG_PATH}" ]]; then
  ARGS=("--spring.config.additional-location=file:${CONFIG_PATH}" "${ARGS[@]}")
fi

exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}" "${ARGS[@]}"
