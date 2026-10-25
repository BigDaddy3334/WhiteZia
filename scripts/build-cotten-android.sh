#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIN="${ROOT}/native-dependencies/cotten.json"

usage() {
  cat <<'HELP'
Usage: bash scripts/build-cotten-android.sh [--verify-packaged|--help]

Default: build and verify ARM64 + ARMv7 together, then install both executables
and cotten-build-info.json. Existing StormDNS files are never deleted here.
Use --verify-packaged after retiring old StormDNS files to check the complete
packaged native directory without building or needing a source checkout.

Required build environment:
  COTTENDNS_SOURCE_DIR  Clean base CottenDNS Git root (default third_party/CottenDNS)
  GO_BIN               Go 1.27.3 executable (default go)
  NDK_ROOT             Android NDK 29.0.14206865 root
  NDK_HOST             linux-x86_64 or darwin-x86_64 (auto-detected by default)

Prepare the default source checkout explicitly:
  git clone https://github.com/WhiteDNS/CottenDNS.git third_party/CottenDNS
  git -C third_party/CottenDNS checkout --detach b3d20fe7fdeba8f157e94223c99cae33116207bf

Android API 26, source revision, toolchain versions, binary name and both ARM
ABIs are pinned in native-dependencies/cotten.json. GOCACHE and GOMODCACHE may
point to shared caches outside the engine source tree. Use Linux/WSL or macOS.
Explicit SHA-256-pinned source patches are applied only to an isolated clone.
The base checkout remains clean; patched binaries report vcs.modified=true.
HELP
}

case "${1:-}" in
  --help|-h) usage; exit 0 ;;
  --verify-packaged)
    [[ $# == 1 ]] || { usage >&2; exit 2; }
    exec python3 "${ROOT}/scripts/check-cotten-package.py"
    ;;
  '') [[ $# == 0 ]] || { usage >&2; exit 2; } ;;
  *) usage >&2; exit 2 ;;
esac

pin_values="$(python3 - "${ROOT}" "${PIN}" <<'PY'
import importlib.util, json, pathlib, re, sys
root, pin_path = map(pathlib.Path, sys.argv[1:])
spec = importlib.util.spec_from_file_location("cotten_package", root / "scripts/check-cotten-package.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
pin = json.loads(pin_path.read_text())
module.validate_pin(pin)
contract = (root / "app/src/main/java/shop/whitezia/client/storm/CottenDnsEngineContract.kt").read_text()
match = re.search(r'const val SourceRevision = "([0-9a-f]{40})"', contract)
module.require(match and match[1] == pin["source_revision"], "Kotlin engine contract differs from native source pin")
for field in ("source_revision", "source_repository", "go_version", "ndk_version", "android_api"):
    print(pin[field])
PY
)"
mapfile -t values <<<"${pin_values}"
REVISION="${values[0]}"
REPOSITORY="${values[1]}"
GO_VERSION="${values[2]}"
NDK_VERSION="${values[3]}"
ANDROID_API="${values[4]}"

SOURCE="${COTTENDNS_SOURCE_DIR:-${ROOT}/third_party/CottenDNS}"
if [[ ! -d "${SOURCE}" ]]; then
  echo "CottenDNS source checkout is absent: ${SOURCE}" >&2
  usage >&2
  exit 1
fi
SOURCE="$(cd "${SOURCE}" && pwd -P)"
if [[ "$(git -C "${SOURCE}" rev-parse --show-toplevel 2>/dev/null || true)" != "${SOURCE}" ]]; then
  echo "COTTENDNS_SOURCE_DIR must be its own Git root; inherited Android Git metadata is rejected." >&2
  exit 1
fi
check_source() {
  [[ "$(git -C "${SOURCE}" rev-parse HEAD)" == "${REVISION}" ]] || {
    echo "CottenDNS HEAD must be ${REVISION}" >&2; exit 1;
  }
  [[ -z "$(git -C "${SOURCE}" status --porcelain=v1 --untracked-files=all)" ]] || {
    echo "CottenDNS source must be clean, including staged and untracked files." >&2; exit 1;
  }
}
check_source
[[ -f "${SOURCE}/go.mod" && -f "${SOURCE}/scripts/build-android-client.sh" ]] || {
  echo "Pinned CottenDNS module or Android build script is missing." >&2; exit 1;
}
GO_BIN="${GO_BIN:-go}"
GO_BIN="$(command -v "${GO_BIN}")"
[[ -x "${GO_BIN}" ]] || { echo "Go executable was not found." >&2; exit 1; }
GO_BIN="$(cd "$(dirname "${GO_BIN}")" && pwd -P)/$(basename "${GO_BIN}")"
[[ "$(GOTOOLCHAIN=local GOENV=off "${GO_BIN}" version)" == "go version go${GO_VERSION} "* ]] || {
  echo "Use the pinned Go ${GO_VERSION}; automatic toolchain download is disabled." >&2; exit 1;
}
: "${NDK_ROOT:?Set NDK_ROOT to Android NDK ${NDK_VERSION}}"
NDK_ROOT="$(cd "${NDK_ROOT}" && pwd -P)"
python3 - "${NDK_ROOT}/source.properties" "${NDK_VERSION}" <<'PY'
import pathlib, re, sys
text = pathlib.Path(sys.argv[1]).read_text()
match = re.search(r'^Pkg\.Revision\s*=\s*(\S+)\s*$', text, re.M)
if not match or match[1] != sys.argv[2]:
    raise SystemExit("NDK source.properties does not match the pinned version")
PY
if [[ -z "${NDK_HOST:-}" ]]; then
  case "$(uname -s)" in
    Linux) NDK_HOST=linux-x86_64 ;;
    Darwin) NDK_HOST=darwin-x86_64 ;;
    *) echo "Build CottenDNS under Linux/WSL or macOS." >&2; exit 1 ;;
  esac
fi
TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST}/bin"
for compiler in aarch64-linux-android26-clang armv7a-linux-androideabi26-clang; do
  [[ -x "${TOOLCHAIN}/${compiler}" ]] || { echo "Missing NDK compiler: ${compiler}" >&2; exit 1; }
done

mkdir -p "${ROOT}/build/cotten"
# The lock covers building, verification and the all-ABI installation transaction.
exec 9>"${ROOT}/build/cotten/build.lock"
command -v flock >/dev/null 2>&1 || { echo "Install flock for the native build lock (included in Linux/WSL util-linux)." >&2; exit 1; }
flock -n 9 || { echo "Another CottenDNS build is active." >&2; exit 1; }
STAGE="$(mktemp -d "${ROOT}/build/cotten/stage.XXXXXXXX")"
BUILD_SOURCE="${STAGE}/source"
git -c core.autocrlf=false clone --quiet --shared --no-hardlinks --no-checkout "${SOURCE}" "${BUILD_SOURCE}"
git -C "${BUILD_SOURCE}" -c core.autocrlf=false checkout --quiet --detach "${REVISION}"
python3 - "${ROOT}" "${BUILD_SOURCE}" "${STAGE}/source-state.json" <<'PY'
import hashlib, json, pathlib, subprocess, sys
root, source, state_path = map(pathlib.Path, sys.argv[1:])
pin = json.loads((root / "native-dependencies/cotten.json").read_text())
for patch in pin.get("source_patches", []):
    path = root / patch["path"]
    if hashlib.sha256(path.read_bytes()).hexdigest() != patch["sha256"]:
        raise SystemExit("CottenDNS patch changed before application")
    subprocess.run(["git", "-C", str(source), "apply", "--index", str(path)], check=True)
tree = lambda revision: subprocess.check_output(["git", "-C", str(source), "rev-parse", revision], text=True).strip()
patched_tree = subprocess.check_output(["git", "-C", str(source), "write-tree"], text=True).strip()
state = {"source_tree": tree("HEAD^{tree}"), "patched_source_tree": patched_tree,
         "source_patches": pin.get("source_patches", []), "vcs_modified": bool(pin.get("source_patches"))}
state_path.write_text(json.dumps(state, sort_keys=True) + "\n")
PY
check_build_source() {
  python3 - "${BUILD_SOURCE}" "${STAGE}/source-state.json" <<'PY'
import json, pathlib, subprocess, sys
source, state_path = map(pathlib.Path, sys.argv[1:])
state = json.loads(state_path.read_text())
subprocess.run(["git", "-C", str(source), "diff", "--exit-code"], check=True)
tree = subprocess.check_output(["git", "-C", str(source), "write-tree"], text=True).strip()
if tree != state["patched_source_tree"]:
    raise SystemExit("CottenDNS staged source tree changed during build")
unknown = subprocess.check_output(["git", "-C", str(source), "ls-files", "--others", "--exclude-standard"])
if unknown:
    raise SystemExit("Unexpected untracked files in CottenDNS build source")
PY
}
check_build_source
export GOENV=off GOTOOLCHAIN=local GOWORK=off GOFLAGS=-mod=readonly GOEXPERIMENT=
export CGO_CFLAGS= CGO_CPPFLAGS= CGO_CXXFLAGS= CGO_LDFLAGS=
export GOCACHE="${GOCACHE:-${ROOT}/build/cotten/go-cache}"
export GOMODCACHE="${GOMODCACHE:-${ROOT}/build/cotten/module-cache}"
export SOURCE_DATE_EPOCH="$(git -C "${SOURCE}" log -1 --format=%ct)"
BUILD_VERSION="$(python3 - "${REVISION}" "${STAGE}/source-state.json" <<'PY'
import json, pathlib, sys
state=json.loads(pathlib.Path(sys.argv[2]).read_text())
suffix="+whitezia." + state["source_patches"][0]["sha256"][:12] if state["source_patches"] else ""
print(sys.argv[1]+suffix)
PY
)"
for abi in arm64-v8a armeabi-v7a; do
  NDK_ROOT="${NDK_ROOT}" NDK_HOST="${NDK_HOST}" ANDROID_API="${ANDROID_API}" \
    OUTPUT_DIR="${STAGE}" GO_BIN="${GO_BIN}" BUILD_VERSION="${BUILD_VERSION}" \
    bash "${BUILD_SOURCE}/scripts/build-android-client.sh" "${abi}"
done
(cd "${BUILD_SOURCE}" && "${GO_BIN}" mod verify)
check_build_source
check_source

python3 - "${ROOT}" "${SOURCE}" "${STAGE}" "${GO_BIN}" "${BUILD_VERSION}" <<'PY'
import importlib.util, json, pathlib, subprocess, sys
root, source, stage = map(pathlib.Path, sys.argv[1:4])
go = sys.argv[4]
build_version = sys.argv[5]
spec = importlib.util.spec_from_file_location("cotten_package", root / "scripts/check-cotten-package.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
pin = json.loads((root / "native-dependencies/cotten.json").read_text())
metadata = {field: pin[field] for field in ("source_revision", "source_repository", "go_version", "ndk_version", "android_api")}
metadata.update(engine="CottenDNS", mode="normal", tunnel_mode="legacy", legacy_session_id=False,
                source_tree=subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD^{tree}"], text=True).strip(),
                build_flags=["-trimpath", "-mod=readonly", "-linkmode=external", "max-page-size=16384", "common-page-size=16384"], runtimes={})
metadata.update(json.loads((stage / "source-state.json").read_text()), build_version=build_version)
for abi, arch in (("arm64-v8a", "arm64"), ("armeabi-v7a", "arm")):
    binary = stage / abi / module.BINARY_NAME
    info = subprocess.check_output([go, "version", "-m", str(binary)], text=True)
    module.require(info.splitlines()[0].endswith("go" + pin["go_version"]), f"{abi}: wrong embedded Go version")
    required = {"GOOS=android", f"GOARCH={arch}", "CGO_ENABLED=1", f"vcs.revision={pin['source_revision']}",
                "vcs.modified=" + str(metadata["vcs_modified"]).lower()}
    settings = {line.strip().split(None, 1)[1] for line in info.splitlines() if line.strip().startswith("build\t")}
    module.require(required <= settings, f"{abi}: embedded Go source/build metadata does not match the pin")
    # Go omits linker command strings from -m when building with -trimpath.
    # The injected runtime version must still exist in the actual executable.
    module.require(build_version.encode() + b"\0" in binary.read_bytes(), f"{abi}: patched build version is absent")
    if abi == "armeabi-v7a":
        module.require("GOARM=7" in settings or "GOARM=7,hardfloat" in settings, "ARMv7 Go build does not target ARMv7")
    metadata["runtimes"][abi] = module.inspect_elf(binary, abi)
    metadata["runtimes"][abi]["goarch"] = arch
(stage / "cotten-build-info.json").write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n")
module.validate_package(stage)
print("Both staged CottenDNS ARM executables and provenance verified.")
PY
check_source

# Every prospective file is copied/hashed before replacing either installed ABI.
# On replacement failure restore the previous Cotten files; StormDNS is untouched.
python3 - "${ROOT}" "${STAGE}" <<'PY'
import hashlib, os, pathlib, shutil, sys, uuid
root, stage = map(pathlib.Path, sys.argv[1:])
destination = root / "app/src/main/jniLibs"
relative_paths = [pathlib.Path(abi) / "libcottendns_client.so" for abi in ("arm64-v8a", "armeabi-v7a")]
relative_paths.append(pathlib.Path("cotten-build-info.json"))
pending, backups, replaced = {}, {}, []
try:
    for relative in relative_paths:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(target.name + ".tmp-" + uuid.uuid4().hex)
        pending[target] = temporary
        shutil.copyfile(stage / relative, temporary)
        os.chmod(temporary, 0o644 if relative.suffix == ".json" else 0o755)
        if hashlib.sha256(temporary.read_bytes()).digest() != hashlib.sha256((stage / relative).read_bytes()).digest():
            raise RuntimeError("Staged CottenDNS install copy differs")
        backups[target] = target.read_bytes() if target.exists() else None
    for target, temporary in pending.items():
        replaced.append(target)
        os.replace(temporary, target)
except BaseException:
    for target in reversed(replaced):
        previous = backups[target]
        if previous is None:
            target.unlink(missing_ok=True)
        else:
            restore = target.with_name(target.name + ".restore-" + uuid.uuid4().hex)
            restore.write_bytes(previous)
            os.chmod(restore, 0o644 if target.suffix == ".json" else 0o755)
            os.replace(restore, target)
    raise
finally:
    for temporary in pending.values():
        temporary.unlink(missing_ok=True)
print("Installed CottenDNS ARM64 + ARMv7 and cotten-build-info.json. Old StormDNS files were not changed.")
print("Verified staging retained at " + str(stage))
PY
