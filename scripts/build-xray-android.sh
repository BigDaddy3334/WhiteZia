#!/usr/bin/env bash
set -euo pipefail

# Build the pinned upstream executable; Android launches it from jniLibs.
XRAY_REF=v26.7.28
XRAY_COMMIT=5ca6f4b7d4dc20a881d4330e498892697627ec0c
SOURCE=${XRAY_SOURCE:?Set XRAY_SOURCE to a clean Xray-core checkout}
NDK=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
GO_BINARY=${GO_BINARY:-go}
ABI=${1:-armeabi-v7a}
API=${ANDROID_API:-26}
OUTPUT_DIR=${OUTPUT_DIR:-"$PWD/build/xray/$ABI"}

[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$XRAY_COMMIT" ]] || {
    echo "Expected $XRAY_REF ($XRAY_COMMIT)" >&2
    exit 1
}
[[ -z "$(git -C "$SOURCE" status --porcelain)" ]] || {
    echo "Xray source checkout must be clean" >&2
    exit 1
}
[[ "$("$GO_BINARY" env GOVERSION)" == go1.26.5 ]] || {
    echo "Use Go 1.26.5, matching the upstream release" >&2
    exit 1
}
if [[ ! "$API" =~ ^[0-9]+$ ]] || (( API < 26 )); then
    echo "ANDROID_API must be an integer >= 26" >&2
    exit 1
fi
case "$(uname -s)" in
    Linux) HOST_TAG=linux-x86_64 ;;
    Darwin) HOST_TAG=darwin-x86_64 ;;
    *) echo "Unsupported NDK host" >&2; exit 1 ;;
esac
BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
case "$ABI" in
    armeabi-v7a) ARCH=arm; TRIPLE=armv7a-linux-androideabi; INTERPRETER=/system/bin/linker ;;
    arm64-v8a) ARCH=arm64; TRIPLE=aarch64-linux-android; INTERPRETER=/system/bin/linker64 ;;
    *) echo "Supported ABIs: armeabi-v7a, arm64-v8a" >&2; exit 1 ;;
esac
[[ -x "$BIN/${TRIPLE}${API}-clang" ]]
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR=$(cd "$OUTPUT_DIR" && pwd)
cd "$SOURCE"
env GOOS=android GOARCH="$ARCH" GOARM=7 CGO_ENABLED=1 \
    CC="$BIN/${TRIPLE}${API}-clang" CXX="$BIN/${TRIPLE}${API}-clang++" \
    "$GO_BINARY" build -trimpath -buildvcs=false -gcflags='all=-l=4' \
    -ldflags='-X github.com/xtls/xray-core/core.build=5ca6f4b -s -w -buildid= -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$OUTPUT_DIR/libxray.so" ./main
"$BIN/llvm-readelf" -l "$OUTPUT_DIR/libxray.so" | grep -F "Requesting program interpreter: $INTERPRETER"
"$GO_BINARY" version -m "$OUTPUT_DIR/libxray.so"
sha256sum "$OUTPUT_DIR/libxray.so"
