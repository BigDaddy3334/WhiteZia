#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
COMMIT=623d27b3a6b3875bd4b816c96004dcf3c867adec
SOURCE=${TUN2PROXY_SOURCE:-"$ROOT/build/tun2proxy/source"}
NDK=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
ABI=${1:-arm64-v8a}
API=${ANDROID_API:-26}
PATCH="$ROOT/scripts/tun2proxy/embedded-lifetime.patch"
LOCK="$ROOT/scripts/tun2proxy/Cargo.lock"
IPSTACK_VERSION=1.0.1
IPSTACK_SHA=e889a45c1ce3e97268ad2249951528563ec1d02a120fecbd4db690beac34f7fd
IPSTACK_DIR="$ROOT/build/tun2proxy/deps/ipstack-$IPSTACK_VERSION"
IPSTACK_ARCHIVE="$ROOT/build/tun2proxy/deps/ipstack-$IPSTACK_VERSION.crate"
IPSTACK_PATCH="$ROOT/scripts/tun2proxy/ipstack-lifetime.patch"
OUTPUT_DIR=${OUTPUT_DIR:-"$ROOT/build/tun2proxy/$ABI"}

[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$COMMIT" ]] || {
    echo "Expected tun2proxy commit $COMMIT" >&2
    exit 1
}
[[ "$(rustc --version | awk '{print $2}')" == 1.94.0 ]] || {
    echo "Use Rust 1.94.0" >&2
    exit 1
}
[[ "$API" =~ ^[0-9]+$ ]] && (( API >= 26 )) || {
    echo "ANDROID_API must be an integer >= 26" >&2
    exit 1
}
if git -C "$SOURCE" apply --check "$PATCH" 2>/dev/null; then
    [[ -z "$(git -C "$SOURCE" diff --name-only)" ]] || {
        echo "Source contains unrelated edits" >&2
        exit 1
    }
    git -C "$SOURCE" apply "$PATCH"
fi
git -C "$SOURCE" apply --reverse --check "$PATCH"
cmp <(git -C "$SOURCE" diff --binary) "$PATCH"
cp "$LOCK" "$SOURCE/Cargo.lock"

mkdir -p "$(dirname "$IPSTACK_DIR")"
if [[ ! -f "$IPSTACK_ARCHIVE" ]]; then
    curl --fail --location --retry 3 \
        "https://static.crates.io/crates/ipstack/ipstack-$IPSTACK_VERSION.crate" \
        --output "$IPSTACK_ARCHIVE"
fi
printf '%s  %s\n' "$IPSTACK_SHA" "$IPSTACK_ARCHIVE" | sha256sum --check
tar -xzf "$IPSTACK_ARCHIVE" -C "$(dirname "$IPSTACK_DIR")"
git -C "$IPSTACK_DIR" apply "$IPSTACK_PATCH"

case "$(uname -s)" in
    Linux) HOST_TAG=linux-x86_64 ;;
    Darwin) HOST_TAG=darwin-x86_64 ;;
    *) echo "Unsupported NDK host" >&2; exit 1 ;;
esac
BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
case "$ABI" in
    arm64-v8a) TARGET=aarch64-linux-android; TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) TARGET=armv7-linux-androideabi; TRIPLE=armv7a-linux-androideabi ;;
    *) echo "Supported ABIs: arm64-v8a, armeabi-v7a" >&2; exit 1 ;;
esac
CC="$BIN/${TRIPLE}${API}-clang"
[[ -x "$CC" ]]
LINKER_ENV="CARGO_TARGET_${TARGET^^}_LINKER"
LINKER_ENV=${LINKER_ENV//-/_}
export SOURCE_DATE_EPOCH=$(git -C "$SOURCE" show -s --format=%ct "$COMMIT")
export CARGO_TARGET_DIR="$ROOT/build/tun2proxy/target"
mkdir -p "$OUTPUT_DIR"
env "$LINKER_ENV=$CC" CC="$CC" AR="$BIN/llvm-ar" \
    RUSTFLAGS="--remap-path-prefix=$SOURCE=tun2proxy -C link-arg=-Wl,-z,max-page-size=16384" \
    cargo build --manifest-path "$SOURCE/Cargo.toml" --locked --release --lib \
    --config "patch.crates-io.ipstack.path=\"$IPSTACK_DIR\"" \
    --target "$TARGET" --jobs "${BUILD_JOBS:-2}"
cp "$CARGO_TARGET_DIR/$TARGET/release/libtun2proxy.so" "$OUTPUT_DIR/libtun2proxy.so"
"$BIN/llvm-strip" --strip-debug "$OUTPUT_DIR/libtun2proxy.so"
"$BIN/llvm-readelf" --dyn-syms "$OUTPUT_DIR/libtun2proxy.so" | grep -F Java_com_github_shadowsocks_bg_Tun2proxy_run
"$BIN/llvm-readelf" -l "$OUTPUT_DIR/libtun2proxy.so"
sha256sum "$OUTPUT_DIR/libtun2proxy.so"
