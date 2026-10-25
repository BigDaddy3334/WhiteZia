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
  WHITEZIA_SSH_CONFIG               Optional OpenSSH config, including jump hosts
  WHITEZIA_RELEASE_PROPERTIES       Local signing properties file
  WHITEZIA_CORE_ENV_FILE            Remote Core environment file
  WHITEZIA_CORE_APK_DIR             Remote root for immutable android-releases/<versionCode>
  WHITEZIA_CORE_SERVICE             Remote systemd service name
  WHITEZIA_RELEASE_METADATA_URL     Public Android metadata endpoint
  WHITEZIA_ANDROID_MIN_VERSION_CODE Minimum supported app version code
  WHITEZIA_ANDROID_UPDATE_MANDATORY true or false
  WHITEZIA_ANDROID_NOTIFY_VERSION   Notification version (empty disables release messages)
  WHITEZIA_SKIP_BUILD               true to publish an already built and verified APK
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
notify_version="${WHITEZIA_ANDROID_NOTIFY_VERSION-$version_name}"
skip_build="${WHITEZIA_SKIP_BUILD:-false}"

ssh_options=()
if [[ -n "${WHITEZIA_SSH_CONFIG:-}" ]]; then
    [[ -r "$WHITEZIA_SSH_CONFIG" ]] || { echo "SSH config is not readable" >&2; exit 2; }
    ssh_options=(-F "$WHITEZIA_SSH_CONFIG")
fi
ssh_command=(ssh "${ssh_options[@]}")
scp_command=(scp "${ssh_options[@]}")
if [[ -n "${WHITEZIA_CORE_SSH_PASSWORD:-}" ]]; then
    command -v sshpass >/dev/null || { echo "sshpass was not found" >&2; exit 2; }
    export SSHPASS="$WHITEZIA_CORE_SSH_PASSWORD"
    ssh_command=(sshpass -e ssh "${ssh_options[@]}")
    scp_command=(sshpass -e scp "${ssh_options[@]}")
fi

[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid version code" >&2; exit 2; }
[[ "$version_name" =~ ^[0-9]+(\.[0-9]+)+$ ]] || { echo "Invalid version name" >&2; exit 2; }
[[ -z "$notify_version" || "$notify_version" =~ ^[0-9]+(\.[0-9]+)+$ ]] || { echo "Invalid notification version" >&2; exit 2; }
[[ "$skip_build" == true || "$skip_build" == false ]] || { echo "Invalid WHITEZIA_SKIP_BUILD value" >&2; exit 2; }
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
if [[ "$skip_build" != true ]]; then
    ./gradlew :app:assembleRelease \
        "-PWHITEZIA_VERSION_CODE=$version_code" \
        "-PWHITEZIA_VERSION_NAME=$version_name" \
        "-PWHITEZIA_RELEASE_PROPERTIES=$release_properties"
fi

apk="$project_dir/app/build/outputs/apk/release/app-universal-release.apk"
[[ -f "$apk" ]] || { echo "universal release APK was not produced" >&2; exit 1; }
aapt2="$(dirname "$apksigner")/aapt2"
[[ -x "$aapt2" ]] || { echo "aapt2 was not found next to apksigner" >&2; exit 2; }
badging="$("$aapt2" dump badging "$apk")"
package_line="${badging%%$'\n'*}"
for expected in "name='shop.whitezia.client'" "versionCode='$version_code'" "versionName='$version_name'"; do
    grep -Fq "$expected" <<<"$package_line" || { echo "APK package/version does not match publication arguments" >&2; exit 1; }
done
if grep -q '^application-debuggable' <<<"$badging"; then
    echo "Refusing to publish a debuggable APK" >&2
    exit 1
fi

for abi in arm64-v8a armeabi-v7a; do
    unzip -Z1 "$apk" "lib/${abi}/libxray.so" >/dev/null || {
        echo "Universal APK is missing Xray for ${abi}" >&2
        exit 1
    }
    unzip -Z1 "$apk" "lib/${abi}/libtun2proxy.so" >/dev/null || {
        echo "Universal APK is missing tun2proxy for ${abi}" >&2
        exit 1
    }
    unzip -Z1 "$apk" "lib/${abi}/libcottendns_client.so" >/dev/null || {
        echo "Universal APK is missing CottenDNS for ${abi}" >&2
        exit 1
    }
done
python3 "$project_dir/scripts/check-cotten-package.py" --apk "$apk"
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
release_notes="$(<"$notes_file")"
escaped_notes="$(printf '%s' "$release_notes" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
remote_apk="$remote_apk_dir/android-releases/$version_code/WhiteZia-universal-arm.apk"

{
    printf 'WHITEZIA_BOT_ANDROID_APK_PATH=%s\n' "$remote_apk"
    # Keep the legacy variable populated while older Core binaries are still deployable.
    printf 'WHITEZIA_BOT_ANDROID_ARM64_APK_PATH=%s\n' "$remote_apk"
    printf 'WHITEZIA_BOT_ANDROID_VERSION=%s\n' "$version_name"
    printf 'WHITEZIA_BOT_ANDROID_VERSION_CODE=%s\n' "$version_code"
    printf 'WHITEZIA_BOT_ANDROID_NOTIFY_VERSION=%s\n' "$notify_version"
    printf 'WHITEZIA_BOT_ANDROID_RELEASE_NOTES="%s"\n' "$escaped_notes"
    printf 'WHITEZIA_ANDROID_MIN_VERSION_CODE=%s\n' "$min_version_code"
    printf 'WHITEZIA_ANDROID_UPDATE_MANDATORY=%s\n' "$mandatory"
    printf 'WHITEZIA_ANDROID_RELEASE_APPLICATION_ID=shop.whitezia.client\n'
    printf 'WHITEZIA_ANDROID_RELEASE_CHANNEL=production\n'
    printf 'WHITEZIA_ANDROID_RELEASE_CERTIFICATE_SHA256=%s\n' "$expected_certificate"
} > "$release_env"

release_manifest="$workspace/release.json"
python3 - "$release_manifest" "$version_code" "$version_name" "$apk_sha256" "$apk_size" "$expected_certificate" "$min_version_code" "$mandatory" "$notes_file" <<'MANIFEST'
import datetime, json, pathlib, sys
path, code, name, digest, size, certificate, minimum, mandatory, notes = sys.argv[1:]
if not minimum.isdigit() or not 0 <= int(minimum) <= int(code) or mandatory not in ('true', 'false'):
    raise SystemExit('Invalid minimum supported version or mandatory flag')
manifest = dict(schema_version=1, apk_file='WhiteZia-universal-arm.apk',
    application_id='shop.whitezia.client', channel='production', platform='android', abi='universal-arm',
    version_code=int(code), version_name=name, sha256=digest, size_bytes=int(size),
    certificate_sha256=certificate, min_supported_version_code=int(minimum), mandatory=mandatory == 'true',
    release_notes=[line.strip().lstrip('-* ').strip() for line in pathlib.Path(notes).read_text().replace('\\n', '\n').replace('|', '\n').splitlines() if line.strip().lstrip('-* ').strip()],
    published_at=datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))
pathlib.Path(path).write_text(json.dumps(manifest, ensure_ascii=False) + '\n')
MANIFEST

# A random staging directory avoids collisions and leaves credentials private.
printf -v stage_command 'mktemp -d /tmp/whitezia-android-release.XXXXXXXXXX'
remote_stage="$("${ssh_command[@]}" "$core_ssh" "$stage_command")"
[[ "$remote_stage" =~ ^/tmp/whitezia-android-release\.[a-zA-Z0-9]+$ ]] || { echo 'Invalid remote staging directory' >&2; exit 1; }
cleanup_publication() {
    rm -rf -- "$workspace"
    local cleanup_command
    printf -v cleanup_command 'rm -rf -- %q' "$remote_stage"
    "${ssh_command[@]}" -o ConnectTimeout=5 "$core_ssh" "$cleanup_command" >/dev/null 2>&1 || true
}
trap cleanup_publication EXIT
"${scp_command[@]}" "$apk" "${core_ssh}:${remote_stage}/release.apk"
"${scp_command[@]}" "$release_env" "${core_ssh}:${remote_stage}/release.env"
"${scp_command[@]}" "$release_manifest" "${core_ssh}:${remote_stage}/release.json"

printf -v publish_command 'bash -s -- %q %q %q %q %q' "$remote_stage" "$remote_apk_dir" "$remote_env_file" "$remote_service" "$metadata_url"
"${ssh_command[@]}" "$core_ssh" "$publish_command" <<'REMOTE'
set -euo pipefail
python3 - "$@" <<'PUBLISH_PY'
import datetime, fcntl, hashlib, json, os, pathlib, re, shutil, stat, subprocess, sys, tempfile, time, urllib.request
stage, apk_root, env_name, service, metadata_url = sys.argv[1:]
stage, root, env_path = pathlib.Path(stage), pathlib.Path(apk_root) / 'android-releases', pathlib.Path(env_name)

def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''): result.update(chunk)
    return result.hexdigest()

def sync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try: os.fsync(fd)
    finally: os.close(fd)

def atomic_file(path, data, mode, owner=None):
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            os.fchmod(stream.fileno(), mode)
            if owner: os.fchown(stream.fileno(), *owner)
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path); sync_dir(path.parent)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def env_values(raw):
    values = {}
    for match in re.finditer(r'^(WHITEZIA_[A-Z0-9_]+)=(?:"((?:[^"\\]|\\.)*)"|([^\r\n]*))$', raw.decode(), re.M):
        value = match.group(2) if match.group(2) is not None else match.group(3)
        values[match.group(1)] = value.replace('\\"', '"').replace('\\\\', '\\')
    return values

def install_release(manifest, source):
    code = manifest['version_code']
    destination = root / str(code)
    if destination.exists() or destination.is_symlink():
        if destination.is_symlink() or not destination.is_dir(): raise RuntimeError('Unsafe existing release path')
        old = json.loads((destination / 'release.json').read_text())
        comparable = lambda data: {key: value for key, value in data.items() if key != 'published_at'}
        apk = destination / manifest['apk_file']
        if comparable(old) != comparable(manifest) or apk.is_symlink() or digest(apk) != manifest['sha256']:
            raise RuntimeError('VersionCode already exists with different artifact or metadata')
        return
    temporary = pathlib.Path(tempfile.mkdtemp(prefix='.candidate-', dir=root))
    try:
        apk = temporary / manifest['apk_file']
        with source.open('rb') as incoming, apk.open('xb') as outgoing:
            shutil.copyfileobj(incoming, outgoing); outgoing.flush(); os.fsync(outgoing.fileno())
        if digest(apk) != manifest['sha256']: raise RuntimeError('Staged artifact hash mismatch')
        apk.chmod(0o444)
        atomic_file(temporary / 'release.json', (json.dumps(manifest, ensure_ascii=False) + '\n').encode(), 0o444)
        temporary.chmod(0o555); sync_dir(temporary)
        os.rename(temporary, destination); sync_dir(root)
    finally:
        if temporary.exists():
            temporary.chmod(0o700); shutil.rmtree(temporary)

def checked(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45)

def verify_metadata(manifest):
    for attempt in range(8):
        try:
            request = urllib.request.Request(metadata_url, headers={'Cache-Control':'no-cache',
                'X-WhiteZia-Application-Id':manifest['application_id'], 'X-WhiteZia-Update-Channel':manifest['channel']})
            with urllib.request.urlopen(request, timeout=5) as response:
                actual = json.load(response)
            if all(actual.get(key) == manifest[key] for key in ('version_code', 'sha256', 'certificate_sha256', 'size_bytes')):
                return
        except Exception: pass
        time.sleep(1)
    raise RuntimeError('Promoted release did not pass public metadata verification')

try:
    manifest = json.loads((stage / 'release.json').read_text())
    if digest(stage / 'release.apk') != manifest['sha256'] or (stage / 'release.apk').stat().st_size != manifest['size_bytes']:
        raise RuntimeError('Uploaded APK failed integrity check')
    if manifest['schema_version'] != 1 or manifest['apk_file'] != 'WhiteZia-universal-arm.apk' or not isinstance(manifest['version_code'], int) or manifest['version_code'] <= 0:
        raise RuntimeError('Invalid release manifest')
    root.mkdir(parents=True, exist_ok=True)
    if root.is_symlink(): raise RuntimeError('Release root must not be a symlink')
    with (root / '.publish.lock').open('a') as lock:
        os.fchmod(lock.fileno(), 0o600); fcntl.flock(lock, fcntl.LOCK_EX)
        before = env_path.read_bytes(); metadata = env_path.stat(); old = env_values(before)
        old_code = int(old.get('WHITEZIA_BOT_ANDROID_VERSION_CODE', '0') or '0')
        old_apk = pathlib.Path(old.get('WHITEZIA_BOT_ANDROID_APK_PATH') or old.get('WHITEZIA_BOT_ANDROID_ARM64_APK_PATH') or '/nonexistent')
        if old_code == manifest['version_code'] and old_apk.is_file() and digest(old_apk) != manifest['sha256']:
            raise RuntimeError('Current versionCode already has a different SHA256')
        # Preserve the previous legacy version before changing its current-release pointer.
        if old_code > 0 and old_apk.is_file() and not (root / str(old_code)).exists():
            legacy = dict(schema_version=1, apk_file='WhiteZia-universal-arm.apk',
                application_id=old.get('WHITEZIA_ANDROID_RELEASE_APPLICATION_ID', 'shop.whitezia.client'),
                channel=old.get('WHITEZIA_ANDROID_RELEASE_CHANNEL', 'production'), platform='android', abi='universal-arm',
                version_code=old_code, version_name=old.get('WHITEZIA_BOT_ANDROID_VERSION', ''),
                sha256=digest(old_apk), size_bytes=old_apk.stat().st_size,
                certificate_sha256=old.get('WHITEZIA_ANDROID_RELEASE_CERTIFICATE_SHA256', ''),
                min_supported_version_code=int(old.get('WHITEZIA_ANDROID_MIN_VERSION_CODE', '0') or '0'),
                mandatory=old.get('WHITEZIA_ANDROID_UPDATE_MANDATORY', 'false') == 'true',
                release_notes=[line.strip().lstrip('-* ').strip() for line in old.get('WHITEZIA_BOT_ANDROID_RELEASE_NOTES', '').replace('\\n', '\n').replace('|', '\n').splitlines() if line.strip()],
                published_at=datetime.datetime.fromtimestamp(old_apk.stat().st_mtime, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))
            if not legacy['version_name'] or not re.fullmatch('[0-9a-fA-F]{64}', legacy['certificate_sha256']):
                raise RuntimeError('Existing release metadata is incomplete; refusing to lose historical APK access')
            legacy['certificate_sha256'] = legacy['certificate_sha256'].lower()
            # Same-version first adoption uses the freshly verified manifest, keeping all metadata coherent.
            install_release(manifest if old_code == manifest['version_code'] else legacy, old_apk)
        install_release(manifest, stage / 'release.apk')
        staged = (stage / 'release.env').read_bytes()
        fields = env_values(staged)
        expected_apk = str(root / str(manifest['version_code']) / manifest['apk_file'])
        if fields.get('WHITEZIA_BOT_ANDROID_APK_PATH') != expected_apk or int(fields.get('WHITEZIA_BOT_ANDROID_VERSION_CODE', '0')) != manifest['version_code']:
            raise RuntimeError('Release pointer does not match artifact')
        keys = set(fields)
        # Match complete quoted multiline values, retaining all unrelated environment settings.
        pattern = re.compile(rb'^(?:' + b'|'.join(re.escape(key.encode()) for key in keys) + rb')=(?:"(?:[^"\\]|\\.)*"|[^\r\n]*)(?:\r?\n|$)', re.M)
        promoted = pattern.sub(b'', before).rstrip(b'\r\n') + b'\n' + staged
        backup = root / ('env-before-' + str(manifest['version_code']) + '-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
        atomic_file(backup, before, 0o600)
        if env_path.read_bytes() != before: raise RuntimeError('Core environment changed concurrently')
        atomic_file(env_path, promoted, stat.S_IMODE(metadata.st_mode), (metadata.st_uid, metadata.st_gid))
        try:
            checked('systemctl', 'restart', service)
            checked('systemctl', 'is-active', '--quiet', service)
            verify_metadata(manifest)
            checked('systemctl', 'is-active', '--quiet', service)
        except Exception:
            if env_path.read_bytes() != promoted:
                raise RuntimeError('Verification failed; concurrent environment change prevents automatic rollback') from None
            atomic_file(env_path, before, stat.S_IMODE(metadata.st_mode), (metadata.st_uid, metadata.st_gid))
            checked('systemctl', 'restart', service)
            checked('systemctl', 'is-active', '--quiet', service)
            raise RuntimeError('Publication failed; previous release pointer restored') from None
        print('Immutable release verified; current environment pointer promoted atomically')
except Exception as error:
    # Avoid printing environment content, private signing paths, or full configs.
    print(str(error) if isinstance(error, RuntimeError) else 'Release publication failed; inspect private host diagnostics', file=sys.stderr)
    sys.exit(1)
finally:
    if stage.parent == pathlib.Path('/tmp') and stage.name.startswith('whitezia-android-release.'):
        shutil.rmtree(stage, ignore_errors=True)
PUBLISH_PY
REMOTE

metadata="$(
    curl -fsS \
        --retry 8 --retry-delay 1 --retry-max-time 30 --retry-connrefused \
        --connect-timeout 5 --max-time 10 \
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
