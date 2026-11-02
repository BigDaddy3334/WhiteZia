#!/usr/bin/env bash
set -euo pipefail
export GOTOOLCHAIN=local

# Build the pristine pinned upstream security release.
# Android launches this executable from jniLibs.
XRAY_REF=v26.10.31
XRAY_COMMIT=a4a3dff5b20c2faae567b742c172c7b95e75c083
SOURCE=${XRAY_SOURCE:?Set XRAY_SOURCE to a clean Xray-core checkout}
MOD_SHA256=ad8701ad59ba44b28c4c8adf924271e0df9c8b0a854662cfda9a61996a42d882
SUM_SHA256=4ead70b9e65f181080638e3e40d850336b85b40bb51cdb2115b814489219c4e3
NDK=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
GO_BINARY=${GO_BINARY:-go}
ABI=${1:-armeabi-v7a}
API=${ANDROID_API:-26}
OUTPUT_DIR=${OUTPUT_DIR:-"$PWD/build/xray/$ABI"}

[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$XRAY_COMMIT" ]] || {
    echo "Expected $XRAY_REF ($XRAY_COMMIT)" >&2
    exit 1
}
[[ -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)" ]] || {
    echo "Xray source must be a pristine checkout of $XRAY_REF" >&2
    exit 1
}
verify_dependencies() {
    [[ "$(sha256sum "$SOURCE/go.mod" | cut -d ' ' -f 1)" == "$MOD_SHA256" &&
       "$(sha256sum "$SOURCE/go.sum" | cut -d ' ' -f 1)" == "$SUM_SHA256" ]] || {
        echo "Expected the exact upstream go.mod/go.sum" >&2
        exit 1
    }
}
verify_dependencies
[[ "$("$GO_BINARY" env GOVERSION)" == go1.27.3 ]] || {
    echo "Use patched Go 1.27.3; Xray core and upstream dependencies remain pinned" >&2
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
env GOTOOLCHAIN=local GOOS=android GOARCH="$ARCH" GOARM=7 CGO_ENABLED=1 \
    CC="$BIN/${TRIPLE}${API}-clang" CXX="$BIN/${TRIPLE}${API}-clang++" \
    "$GO_BINARY" build -mod=readonly -trimpath -buildvcs=false -gcflags='all=-l=4' \
    -ldflags='-X github.com/xtls/xray-core/core.build=a4a3dff -s -w -buildid= -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$OUTPUT_DIR/libxray.so" ./main
verify_dependencies
[[ -z "$(git status --porcelain --untracked-files=all)" ]]
"$BIN/llvm-readelf" -l "$OUTPUT_DIR/libxray.so" | grep -F "Requesting program interpreter: $INTERPRETER"
"$GO_BINARY" version -m "$OUTPUT_DIR/libxray.so"
sha256sum "$OUTPUT_DIR/libxray.so"
