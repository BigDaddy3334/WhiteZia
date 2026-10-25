#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:-origin/main}"
head_ref="${2:-HEAD}"
git rev-parse --verify "${base_ref}^{commit}" >/dev/null 2>&1 || { echo "Base commit is unavailable." >&2; exit 2; }
git rev-parse --verify "${head_ref}^{commit}" >/dev/null 2>&1 || { echo "Head commit is unavailable." >&2; exit 2; }
changed_files="$(git diff --name-only "${base_ref}...${head_ref}" -- \
  'native-dependencies/cotten.json' 'native-dependencies/patches/*.patch' 'third_party/CottenDNS' \
  'app/src/main/jniLibs/cotten-build-info.json' 'app/src/main/jniLibs/*/libcottendns_client.so')" || exit 2
if [[ -z "${changed_files}" ]]; then
  echo "No CottenDNS source pin or executable changes detected."
  exit 0
fi
echo "CottenDNS source pin/executable changes detected:" >&2
echo "${changed_files}" >&2
echo "Review the immutable base source pin, audited patch SHA-256, both ABI hashes, Normal/native framing and build provenance." >&2
echo "The allow-cottendns-upstream PR label records intentional engine maintenance." >&2
exit 1
