#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_NAME="${JAR_NAME:-FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar}"
JAR_PATH="${JAR_PATH:-${PROJECT_DIR}/target/${JAR_NAME}}"
CONFIG_PATH="${CONFIG_PATH:-${PROJECT_DIR}/application-client.yml}"

if [[ ! -f "${JAR_PATH}" && -f "${SCRIPT_DIR}/${JAR_NAME}" ]]; then
  JAR_PATH="${SCRIPT_DIR}/${JAR_NAME}"
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Jar not found: ${JAR_PATH}"
  echo "Run 'mvn clean package' first, or set JAR_PATH to the packaged jar."
  exit 1
fi

ARGS=(
  "--app.role=client"
  "--spring.profiles.active=client"
  "--server.tcp.enabled=false"
  "--server.address=${CLIENT_HTTP_ADDRESS:-127.0.0.1}"
  "--server.port=${CLIENT_HTTP_PORT:-20201}"
  "--node.auto-connect=${NODE_AUTO_CONNECT:-true}"
  "--client.serverHost=${CLIENT_SERVER_HOST:-82.156.228.71}"
  "--client.serverPort=${CLIENT_SERVER_PORT:-9000}"
  "--transfer.receive-dir=${TRANSFER_RECEIVE_DIR:-downloads-client-1}"
)

if [[ -n "${NODE_DEVICE_ID:-}" ]]; then
  ARGS+=("--node.device-id=${NODE_DEVICE_ID}")
fi

if [[ -f "${CONFIG_PATH}" ]]; then
  ARGS=("--spring.config.additional-location=file:${CONFIG_PATH}" "${ARGS[@]}")
fi

exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}" "${ARGS[@]}"
