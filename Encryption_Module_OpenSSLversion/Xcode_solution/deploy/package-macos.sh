#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=${BUILD_DIR:-"$ROOT_DIR/cross_platform/build/macos-release"}

case "$(uname -m)" in
    arm64) DEFAULT_ARCH=arm64 ;;
    x86_64) DEFAULT_ARCH=x64 ;;
    *) DEFAULT_ARCH=$(uname -m) ;;
esac

ARCH=${CRYPTO_SERVICE_ARCH:-$DEFAULT_ARCH}
DIST_NAME=${DIST_NAME:-crypto-service-macos-$ARCH}
DIST_DIR="$ROOT_DIR/dist/$DIST_NAME"
ARCHIVE="$ROOT_DIR/dist/$DIST_NAME.zip"

case "$ARCH" in
    arm64) TRIPLET=${VCPKG_TRIPLET:-arm64-osx} ;;
    x64) TRIPLET=${VCPKG_TRIPLET:-x64-osx} ;;
    *) TRIPLET=${VCPKG_TRIPLET:-arm64-osx} ;;
esac

LIB_DIR=
TOOLCHAIN_ARGS=

if [ -n "${VCPKG_ROOT:-}" ]; then
    TOOLCHAIN="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
    if [ ! -f "$TOOLCHAIN" ]; then
        echo "vcpkg toolchain file not found: $TOOLCHAIN" >&2
        exit 1
    fi

    "$VCPKG_ROOT/vcpkg" install "openssl:$TRIPLET" "nlohmann-json:$TRIPLET"
    TOOLCHAIN_ARGS="-DCMAKE_TOOLCHAIN_FILE=$TOOLCHAIN -DVCPKG_TARGET_TRIPLET=$TRIPLET"
    LIB_DIR="$VCPKG_ROOT/installed/$TRIPLET/lib"
else
    if ! command -v brew >/dev/null 2>&1; then
        echo "VCPKG_ROOT is not set and Homebrew is not available." >&2
        echo "Install vcpkg or install OpenSSL with: brew install openssl@3" >&2
        exit 1
    fi

    OPENSSL_PREFIX=$(brew --prefix openssl@3)
    TOOLCHAIN_ARGS="-DOPENSSL_ROOT_DIR=$OPENSSL_PREFIX"
    LIB_DIR="$OPENSSL_PREFIX/lib"
fi

cmake -S "$ROOT_DIR/cross_platform" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    $TOOLCHAIN_ARGS

cmake --build "$BUILD_DIR" --config Release

rm -rf "$DIST_DIR" "$ARCHIVE"
mkdir -p "$DIST_DIR/crypto_keys"

cp "$BUILD_DIR/crypto-service" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/runtime/start.sh" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/runtime/install-macos-launchd.sh" "$DIST_DIR/"
cp "$ROOT_DIR/deploy/README.md" "$DIST_DIR/"

cp "$LIB_DIR/libssl.3.dylib" "$DIST_DIR/"
cp "$LIB_DIR/libcrypto.3.dylib" "$DIST_DIR/"

ssl_ref=$(otool -L "$DIST_DIR/crypto-service" | awk '/libssl\.3\.dylib/ {print $1; exit}')
crypto_ref=$(otool -L "$DIST_DIR/crypto-service" | awk '/libcrypto\.3\.dylib/ {print $1; exit}')
ssl_crypto_ref=$(otool -L "$DIST_DIR/libssl.3.dylib" | awk '/libcrypto\.3\.dylib/ {print $1; exit}')

install_name_tool -id @executable_path/libssl.3.dylib "$DIST_DIR/libssl.3.dylib"
install_name_tool -id @executable_path/libcrypto.3.dylib "$DIST_DIR/libcrypto.3.dylib"
install_name_tool -change "$ssl_ref" @executable_path/libssl.3.dylib "$DIST_DIR/crypto-service"
install_name_tool -change "$crypto_ref" @executable_path/libcrypto.3.dylib "$DIST_DIR/crypto-service"
install_name_tool -change "$ssl_crypto_ref" @executable_path/libcrypto.3.dylib "$DIST_DIR/libssl.3.dylib"

codesign --force --sign - "$DIST_DIR/libcrypto.3.dylib" >/dev/null
codesign --force --sign - "$DIST_DIR/libssl.3.dylib" >/dev/null
codesign --force --sign - "$DIST_DIR/crypto-service" >/dev/null

chmod +x "$DIST_DIR/crypto-service" "$DIST_DIR/start.sh" "$DIST_DIR/install-macos-launchd.sh"

(cd "$ROOT_DIR/dist" && zip -qry -X "$ARCHIVE" "$DIST_NAME")

echo "macOS package created: $ARCHIVE"
