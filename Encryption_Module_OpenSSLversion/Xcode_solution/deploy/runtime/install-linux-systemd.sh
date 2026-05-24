#!/usr/bin/env sh
set -eu

SERVICE_NAME=${CRYPTO_SERVICE_NAME:-crypto-service}
INSTALL_DIR=${CRYPTO_SERVICE_INSTALL_DIR:-/opt/crypto-service}
DATA_DIR=${CRYPTO_SERVICE_DATA_DIR:-/var/lib/crypto-service}
HOST=${CRYPTO_SERVICE_HOST:-0.0.0.0}
PORT=${CRYPTO_SERVICE_PORT:-9080}

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SUDO=
if [ "$(id -u)" -ne 0 ]; then
    SUDO=sudo
fi

$SUDO mkdir -p "$INSTALL_DIR" "$DATA_DIR/crypto_keys"
$SUDO cp -R "$SCRIPT_DIR"/. "$INSTALL_DIR"/
$SUDO chmod +x "$INSTALL_DIR/crypto-service" "$INSTALL_DIR/start.sh"

$SUDO tee "/etc/systemd/system/$SERVICE_NAME.service" >/dev/null <<EOF
[Unit]
Description=Crypto Service
After=network.target

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR
Environment=LD_LIBRARY_PATH=$INSTALL_DIR
ExecStart=$INSTALL_DIR/crypto-service --host $HOST --port $PORT --key-dir $DATA_DIR/crypto_keys
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

$SUDO systemctl daemon-reload
$SUDO systemctl enable "$SERVICE_NAME"
$SUDO systemctl restart "$SERVICE_NAME"
$SUDO systemctl status "$SERVICE_NAME" --no-pager
