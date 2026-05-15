#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

HOST=${CRYPTO_SERVICE_HOST:-0.0.0.0}
PORT=${CRYPTO_SERVICE_PORT:-9080}
KEY_DIR=${CRYPTO_SERVICE_KEY_DIR:-"$SCRIPT_DIR/crypto_keys"}

mkdir -p "$KEY_DIR"

export LD_LIBRARY_PATH="$SCRIPT_DIR:${LD_LIBRARY_PATH:-}"
export DYLD_LIBRARY_PATH="$SCRIPT_DIR:${DYLD_LIBRARY_PATH:-}"

exec "$SCRIPT_DIR/crypto-service" \
    --host "$HOST" \
    --port "$PORT" \
    --key-dir "$KEY_DIR"
