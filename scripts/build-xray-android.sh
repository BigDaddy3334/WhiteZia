#!/usr/bin/env bash
set -euo pipefail
export GOTOOLCHAIN=local

# Build the pinned core plus the reviewed dependency-only security overlay.
# Android launches this executable from jniLibs.
XRAY_REF=v26.7.28
XRAY_COMMIT=5ca6f4b7d4dc20a881d4330e498892697627ec0c
SOURCE=${XRAY_SOURCE:?Set XRAY_SOURCE to a clean Xray-core checkout}
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SECURITY_PATCH="$SCRIPT_DIR/xray/security-dependencies-20261021.patch"
PATCH_SHA256=131d97a6e70528d8b5f8ff715707e2045a1c8c08d76892cddb1e6c757d65c310
MOD_SHA256=fb9af2baa8b72e1e29c39956f88eda25f5bb15f451df85b7e5808276aefb099d
SUM_SHA256=9918f4653081f88e063f4c21718243b55e7eee6709a4087b5896f181c4ee5c51
NDK=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
GO_BINARY=${GO_BINARY:-go}
ABI=${1:-armeabi-v7a}
API=${ANDROID_API:-26}
OUTPUT_DIR=${OUTPUT_DIR:-"$PWD/build/xray/$ABI"}

[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$XRAY_COMMIT" ]] || {
    echo "Expected $XRAY_REF ($XRAY_COMMIT)" >&2
    exit 1
}
git -C "$SOURCE" diff --cached --quiet || {
    echo "Xray source must not contain staged changes" >&2
    exit 1
}
[[ -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all -- . ':!go.mod' ':!go.sum')" ]] || {
    echo "Only the exact dependency overlay may modify Xray source" >&2
    exit 1
}
[[ "$(sha256sum "$SECURITY_PATCH" | cut -d ' ' -f 1)" == "$PATCH_SHA256" ]] || {
    echo "Dependency overlay checksum mismatch" >&2
    exit 1
}
if [[ -z "$(git -C "$SOURCE" status --porcelain -- go.mod go.sum)" ]]; then
    git -C "$SOURCE" apply --check "$SECURITY_PATCH"
    git -C "$SOURCE" apply "$SECURITY_PATCH"
fi
verify_dependencies() {
    [[ "$(sha256sum "$SOURCE/go.mod" | cut -d ' ' -f 1)" == "$MOD_SHA256" &&
       "$(sha256sum "$SOURCE/go.sum" | cut -d ' ' -f 1)" == "$SUM_SHA256" ]] || {
        echo "Expected the exact reviewed go.mod/go.sum security overlay" >&2
        exit 1
    }
}
verify_dependencies
[[ "$("$GO_BINARY" env GOVERSION)" == go1.27.3 ]] || {
    echo "Use patched Go 1.27.3; Xray core and dependency overlay remain pinned" >&2
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
    "$GO_BINARY" build -trimpath -buildvcs=false -gcflags='all=-l=4' \
    -ldflags='-X github.com/xtls/xray-core/core.build=5ca6f4b -s -w -buildid= -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$OUTPUT_DIR/libxray.so" ./main
verify_dependencies
"$BIN/llvm-readelf" -l "$OUTPUT_DIR/libxray.so" | grep -F "Requesting program interpreter: $INTERPRETER"
"$GO_BINARY" version -m "$OUTPUT_DIR/libxray.so"
sha256sum "$OUTPUT_DIR/libxray.so"
