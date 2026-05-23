#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=${BUILD_DIR:-"$ROOT_DIR/cross_platform/build/linux-release"}
DIST_NAME=${DIST_NAME:-crypto-service-linux-x64}
DIST_DIR="$ROOT_DIR/dist/$DIST_NAME"
ARCHIVE="$ROOT_DIR/dist/$DIST_NAME.tar.gz"
TRIPLET=${VCPKG_TRIPLET:-x64-linux}

if [ -z "${VCPKG_ROOT:-}" ]; then
    echo "VCPKG_ROOT is not set. Example: export VCPKG_ROOT=/path/to/vcpkg" >&2
    exit 1
fi

TOOLCHAIN="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
if [ ! -f "$TOOLCHAIN" ]; then
    echo "vcpkg toolchain file not found: $TOOLCHAIN" >&2
    exit 1
fi

"$VCPKG_ROOT/vcpkg" install "openssl:$TRIPLET" "nlohmann-json:$TRIPLET"

cmake -S "$ROOT_DIR/cross_platform" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DVCPKG_TARGET_TRIPLET="$TRIPLET"

cmake --build "$BUILD_DIR" --config Release

rm -rf "$DIST_DIR" "$ARCHIVE"
mkdir -p "$DIST_DIR/crypto_keys"

cp "$BUILD_DIR/crypto-service" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/runtime/start.sh" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/runtime/install-linux-systemd.sh" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/README.md" "$DIST_DIR/"

for file in "$VCPKG_ROOT/installed/$TRIPLET/lib"/libssl.so* "$VCPKG_ROOT/installed/$TRIPLET/lib"/libcrypto.so*; do
    if [ -e "$file" ]; then
        cp "$file" "$DIST_DIR/"
    fi
done

chmod +x "$DIST_DIR/crypto-service" "$DIST_DIR/start.sh" "$DIST_DIR/install-linux-systemd.sh"

(cd "$ROOT_DIR/dist" && tar -czf "$ARCHIVE" "$DIST_NAME")

echo "Linux package created: $ARCHIVE"
