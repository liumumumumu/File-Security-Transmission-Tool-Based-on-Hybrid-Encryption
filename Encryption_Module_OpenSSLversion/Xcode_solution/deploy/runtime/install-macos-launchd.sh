#!/usr/bin/env sh
set -eu

SERVICE_LABEL=${CRYPTO_SERVICE_LABEL:-com.local.crypto-service}
INSTALL_DIR=${CRYPTO_SERVICE_INSTALL_DIR:-/usr/local/crypto-service}
DATA_DIR=${CRYPTO_SERVICE_DATA_DIR:-/usr/local/var/crypto-service}
LOG_DIR=${CRYPTO_SERVICE_LOG_DIR:-/usr/local/var/log}
HOST=${CRYPTO_SERVICE_HOST:-0.0.0.0}
PORT=${CRYPTO_SERVICE_PORT:-9080}

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PLIST_PATH="/Library/LaunchDaemons/$SERVICE_LABEL.plist"
SUDO=
if [ "$(id -u)" -ne 0 ]; then
    SUDO=sudo
fi

$SUDO mkdir -p "$INSTALL_DIR" "$DATA_DIR/crypto_keys" "$LOG_DIR"
$SUDO cp -R "$SCRIPT_DIR"/. "$INSTALL_DIR"/
$SUDO chmod +x "$INSTALL_DIR/crypto-service" "$INSTALL_DIR/start.sh"

$SUDO tee "$PLIST_PATH" >/dev/null <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$SERVICE_LABEL</string>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/crypto-service</string>
        <string>--host</string>
        <string>$HOST</string>
        <string>--port</string>
        <string>$PORT</string>
        <string>--key-dir</string>
        <string>$DATA_DIR/crypto_keys</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>DYLD_LIBRARY_PATH</key>
        <string>$INSTALL_DIR</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOG_DIR/crypto-service.log</string>
    <key>StandardErrorPath</key>
    <string>$LOG_DIR/crypto-service.err.log</string>
</dict>
</plist>
EOF

$SUDO launchctl bootout system "$PLIST_PATH" >/dev/null 2>&1 || true
$SUDO launchctl bootstrap system "$PLIST_PATH"
$SUDO launchctl enable "system/$SERVICE_LABEL"
$SUDO launchctl kickstart -k "system/$SERVICE_LABEL"
$SUDO launchctl print "system/$SERVICE_LABEL"
