#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  WHITEZIA_CORE_SSH=root@host ./scripts/publish-production-android.sh \
    <version-code> <version-name> <release-notes-file>

Required environment:
  WHITEZIA_CORE_SSH                 SSH target of the production Core host

Optional environment:
  WHITEZIA_CORE_SSH_PASSWORD        SSH password, used through sshpass -e
  WHITEZIA_RELEASE_PROPERTIES       Local signing properties file
  WHITEZIA_CORE_ENV_FILE            Remote Core environment file
  WHITEZIA_CORE_APK_DIR             Remote directory for the universal ARM APK
  WHITEZIA_CORE_SERVICE             Remote systemd service name
  WHITEZIA_RELEASE_METADATA_URL     Public Android metadata endpoint
  WHITEZIA_ANDROID_MIN_VERSION_CODE Minimum supported app version code
  WHITEZIA_ANDROID_UPDATE_MANDATORY true or false
  APKSIGNER                         Path to Android apksigner
EOF
}

if [[ $# -ne 3 ]]; then
    usage >&2
    exit 2
fi

version_code="$1"
version_name="$2"
notes_file="$3"
core_ssh="${WHITEZIA_CORE_SSH:?Set WHITEZIA_CORE_SSH to the production Core SSH target}"
release_properties="${WHITEZIA_RELEASE_PROPERTIES:-/home/biba/.whitezia/signing/release.properties}"
remote_env_file="${WHITEZIA_CORE_ENV_FILE:-/root/whitezia-core/.env}"
remote_apk_dir="${WHITEZIA_CORE_APK_DIR:-/root/telegram-ai-agent/data}"
remote_service="${WHITEZIA_CORE_SERVICE:-whitezia-core}"
metadata_url="${WHITEZIA_RELEASE_METADATA_URL:-https://api.whitezia.ru/api/app/releases/android}"
min_version_code="${WHITEZIA_ANDROID_MIN_VERSION_CODE:-0}"
mandatory="${WHITEZIA_ANDROID_UPDATE_MANDATORY:-false}"

ssh_command=(ssh)
scp_command=(scp)
if [[ -n "${WHITEZIA_CORE_SSH_PASSWORD:-}" ]]; then
    command -v sshpass >/dev/null || { echo "sshpass was not found" >&2; exit 2; }
    export SSHPASS="$WHITEZIA_CORE_SSH_PASSWORD"
    ssh_command=(sshpass -e ssh)
    scp_command=(sshpass -e scp)
fi

[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid version code" >&2; exit 2; }
[[ "$version_name" =~ ^[0-9]+(\.[0-9]+)+$ ]] || { echo "Invalid version name" >&2; exit 2; }
[[ -r "$notes_file" ]] || { echo "Release notes file is not readable: $notes_file" >&2; exit 2; }
[[ -r "$release_properties" ]] || { echo "Release signing properties are not readable" >&2; exit 2; }

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
expected_certificate="$(awk -F= '$1 == "certificateSha256" {print tolower($2)}' "$release_properties")"
[[ "$expected_certificate" =~ ^[0-9a-f]{64}$ ]] || {
    echo "certificateSha256 is missing or invalid in release signing properties" >&2
    exit 2
}

if [[ -n "${APKSIGNER:-}" ]]; then
    apksigner="$APKSIGNER"
else
    sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
    apksigner="$(find "$sdk_root/build-tools" -type f -name apksigner -print 2>/dev/null | sort -V | tail -n 1)"
fi
[[ -x "$apksigner" ]] || { echo "apksigner was not found" >&2; exit 2; }

cd "$project_dir"
./gradlew :app:assembleRelease \
    "-PWHITEZIA_VERSION_CODE=$version_code" \
    "-PWHITEZIA_VERSION_NAME=$version_name" \
    "-PWHITEZIA_RELEASE_PROPERTIES=$release_properties"

apk="$project_dir/app/build/outputs/apk/release/app-universal-release.apk"
[[ -f "$apk" ]] || { echo "universal release APK was not produced" >&2; exit 1; }

for abi in arm64-v8a armeabi-v7a; do
    unzip -Z1 "$apk" "lib/${abi}/libxray.so" >/dev/null || {
        echo "Universal APK is missing Xray for ${abi}" >&2
        exit 1
    }
    unzip -Z1 "$apk" "lib/${abi}/libtun2proxy.so" >/dev/null || {
        echo "Universal APK is missing tun2proxy for ${abi}" >&2
        exit 1
    }
    unzip -Z1 "$apk" "lib/${abi}/libstormdns_client.so" >/dev/null || {
        echo "Universal APK is missing StormDNS for ${abi}" >&2
        exit 1
    }
done
for abi in x86 x86_64; do
    unexpected_entries="$(unzip -Z1 "$apk" "lib/${abi}/*.so" 2>/dev/null || true)"
    if [[ -n "$unexpected_entries" ]]; then
        echo "Universal ARM APK unexpectedly contains ${abi} libraries" >&2
        exit 1
    fi
done

"$apksigner" verify --verbose --print-certs "$apk"
actual_certificate="$(
    "$apksigner" verify --print-certs "$apk" |
        awk -F': ' '/certificate SHA-256 digest/ {print tolower($NF); exit}' |
        tr -d ':'
)"
[[ "$actual_certificate" == "$expected_certificate" ]] || {
    echo "APK certificate does not match release signing properties" >&2
    exit 1
}
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
apk_size="$(wc -c < "$apk" | tr -d ' ')"

workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT
release_env="$workspace/android-release.env"
release_notes="$(tr '\n' '|' < "$notes_file" | sed -e 's/[[:space:]]*$//')"
escaped_notes="$(printf '%s' "$release_notes" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
remote_apk="$remote_apk_dir/WhiteZia-${version_name}-universal-arm.apk"

{
    printf 'WHITEZIA_BOT_ANDROID_APK_PATH=%s\n' "$remote_apk"
    # Keep the legacy variable populated while older Core binaries are still deployable.
    printf 'WHITEZIA_BOT_ANDROID_ARM64_APK_PATH=%s\n' "$remote_apk"
    printf 'WHITEZIA_BOT_ANDROID_VERSION=%s\n' "$version_name"
    printf 'WHITEZIA_BOT_ANDROID_VERSION_CODE=%s\n' "$version_code"
    printf 'WHITEZIA_BOT_ANDROID_NOTIFY_VERSION=%s\n' "$version_name"
    printf 'WHITEZIA_BOT_ANDROID_RELEASE_NOTES="%s"\n' "$escaped_notes"
    printf 'WHITEZIA_ANDROID_MIN_VERSION_CODE=%s\n' "$min_version_code"
    printf 'WHITEZIA_ANDROID_UPDATE_MANDATORY=%s\n' "$mandatory"
    printf 'WHITEZIA_ANDROID_RELEASE_APPLICATION_ID=shop.whitezia.client\n'
    printf 'WHITEZIA_ANDROID_RELEASE_CHANNEL=production\n'
    printf 'WHITEZIA_ANDROID_RELEASE_CERTIFICATE_SHA256=%s\n' "$expected_certificate"
} > "$release_env"

remote_token="android-${version_code}-$$"
remote_staged_apk="/tmp/WhiteZia-${remote_token}.apk"
remote_staged_env="/tmp/WhiteZia-${remote_token}.env"
"${scp_command[@]}" "$apk" "${core_ssh}:${remote_staged_apk}"
"${scp_command[@]}" "$release_env" "${core_ssh}:${remote_staged_env}"

"${ssh_command[@]}" "$core_ssh" bash -s -- \
    "$remote_staged_apk" "$remote_staged_env" "$remote_apk" "$remote_env_file" "$remote_service" <<'REMOTE'
set -euo pipefail
staged_apk="$1"
staged_env="$2"
apk_path="$3"
env_path="$4"
service_name="$5"

install -d -m 0755 "$(dirname "$apk_path")"
install -m 0644 "$staged_apk" "$apk_path"
rm -f "$staged_apk"

next_env="$(mktemp "${env_path}.next.XXXXXX")"
keys='^(WHITEZIA_BOT_ANDROID_APK_PATH|WHITEZIA_BOT_ANDROID_ARM64_APK_PATH|WHITEZIA_BOT_ANDROID_VERSION|WHITEZIA_BOT_ANDROID_VERSION_CODE|WHITEZIA_BOT_ANDROID_NOTIFY_VERSION|WHITEZIA_BOT_ANDROID_RELEASE_NOTES|WHITEZIA_ANDROID_MIN_VERSION_CODE|WHITEZIA_ANDROID_UPDATE_MANDATORY|WHITEZIA_ANDROID_RELEASE_APPLICATION_ID|WHITEZIA_ANDROID_RELEASE_CHANNEL|WHITEZIA_ANDROID_RELEASE_CERTIFICATE_SHA256)='
grep -Ev "$keys" "$env_path" > "$next_env" || true
cat "$staged_env" >> "$next_env"
install -m 0600 "$next_env" "$env_path"
rm -f "$next_env" "$staged_env"

systemctl restart "$service_name"
systemctl is-active --quiet "$service_name"
REMOTE

metadata="$(
    curl -fsS \
        -H 'Cache-Control: no-cache' \
        -H 'X-WhiteZia-Application-Id: shop.whitezia.client' \
        -H 'X-WhiteZia-Update-Channel: production' \
        "$metadata_url"
)"
printf '%s\n' "$metadata"
grep -Fq "\"version_code\":${version_code}" <<<"$metadata" || {
    echo "Core returned unexpected OTA version metadata" >&2
    exit 1
}
grep -Fq "\"sha256\":\"${apk_sha256}\"" <<<"$metadata" || {
    echo "Core returned an unexpected APK hash" >&2
    exit 1
}
grep -Fq "\"certificate_sha256\":\"${expected_certificate}\"" <<<"$metadata" || {
    echo "Core returned an unexpected signing certificate" >&2
    exit 1
}

printf 'Published %s (%s), %s bytes, SHA-256 %s\n' \
    "$version_name" "$version_code" "$apk_size" "$apk_sha256"
