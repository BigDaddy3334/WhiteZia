#!/usr/bin/env python3
"""Read-only GitHub release gate; an existing release is never overwritten."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request


class ReleaseGateError(RuntimeError):
    pass


def fetch(url, token, accept, opener):
    headers = {"Accept": accept, "User-Agent": "WhiteZia-release-gate"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        with opener(urllib.request.Request(url, headers=headers), timeout=30) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        raise ReleaseGateError(f"GitHub request failed with HTTP {error.code}") from error
    except (OSError, ValueError) as error:
        raise ReleaseGateError("GitHub request failed; release state is unknown") from error


def assert_no_unpublished_release(repository, tag, token, opener):
    # The by-tag endpoint describes published releases. With push access, the
    # list endpoint also reveals a draft that must never be treated as absence.
    for page in range(1, 101):
        endpoint = f"https://api.github.com/repos/{repository}/releases?per_page=100&page={page}"
        try:
            releases = json.loads(fetch(endpoint, token, "application/vnd.github+json", opener))
        except (ValueError, TypeError) as error:
            raise ReleaseGateError("Invalid GitHub release listing") from error
        if not isinstance(releases, list) or any(not isinstance(item, dict) for item in releases):
            raise ReleaseGateError("Invalid GitHub release listing")
        if any(item.get("tag_name") == tag for item in releases):
            raise ReleaseGateError("An existing release or draft matches this tag; refusing to replace it")
        if len(releases) < 100:
            return
    raise ReleaseGateError("Release listing exceeded the pagination limit; release state is unknown")


def check_release(repository, tag, token, require_absent=False, opener=urllib.request.urlopen):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ReleaseGateError("Invalid repository")
    if not re.fullmatch(r"v?\d+(?:\.\d+)+(?:[-.][A-Za-z0-9.-]+)?", tag):
        raise ReleaseGateError("Invalid release tag")
    encoded_tag = urllib.parse.quote(tag, safe="")
    endpoint = f"https://api.github.com/repos/{repository}/releases/tags/{encoded_tag}"
    try:
        raw = fetch(endpoint, token, "application/vnd.github+json", opener)
    except ReleaseGateError as error:
        if isinstance(error.__cause__, urllib.error.HTTPError) and error.__cause__.code == 404:
            assert_no_unpublished_release(repository, tag, token, opener)
            return "build"
        raise
    try:
        release = json.loads(raw)
    except (ValueError, TypeError) as error:
        raise ReleaseGateError("Invalid GitHub release response") from error
    if not isinstance(release, dict):
        raise ReleaseGateError("Invalid GitHub release response")
    if require_absent:
        raise ReleaseGateError("A release appeared during the build; refusing to replace its assets")
    if release.get("tag_name") != tag or release.get("draft") is not False or not release.get("published_at"):
        raise ReleaseGateError("Existing release is unpublished or does not match the tag")
    names = (
        f"WhiteZia-{tag}-universal-arm.apk",
        f"WhiteZia-{tag}-THIRD_PARTY_NOTICES.md",
        "SHA256SUMS.txt",
    )
    assets = release.get("assets")
    if not isinstance(assets, list) or any(not isinstance(asset, dict) for asset in assets):
        raise ReleaseGateError("Existing release has an invalid asset list")
    selected = {}
    for name in names:
        matches = [asset for asset in assets if asset.get("name") == name]
        if len(matches) != 1:
            raise ReleaseGateError(f"Existing release is incomplete or ambiguous: {name}")
        asset = matches[0]
        size = asset.get("size")
        if asset.get("state") != "uploaded" or not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            raise ReleaseGateError(f"Existing release asset is not ready: {name}")
        selected[name] = asset
    sums_asset = selected["SHA256SUMS.txt"]
    if sums_asset["size"] > 64 * 1024:
        raise ReleaseGateError("Existing checksum manifest exceeds the size limit")
    sums_url = f"https://github.com/{repository}/releases/download/{encoded_tag}/SHA256SUMS.txt"
    # The manifest is public. Never forward the repository token through download redirects.
    try:
        raw_sums = fetch(sums_url, "", "application/octet-stream", opener)
        if len(raw_sums) > 64 * 1024:
            raise ReleaseGateError("Existing checksum manifest exceeds the size limit")
        text = raw_sums.decode("utf-8")
    except UnicodeError as error:
        raise ReleaseGateError("Existing checksum manifest is not UTF-8") from error
    checksums = {}
    for line in text.splitlines():
        if not line:
            continue
        match = re.fullmatch(r"([0-9a-fA-F]{64}) [ *]([^\r\n]+)", line)
        if not match or match[2] in checksums:
            raise ReleaseGateError("Existing checksum manifest is malformed or ambiguous")
        checksums[match[2]] = match[1].lower()
    for name in names[:2]:
        if name not in checksums:
            raise ReleaseGateError(f"Existing checksum manifest is missing: {name}")
        digest = selected[name].get("digest")
        if digest is not None and digest != f"sha256:{checksums[name]}":
            raise ReleaseGateError(f"Existing asset digest does not match its checksum: {name}")
    return "skip"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME"))
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--require-absent", action="store_true")
    args = parser.parse_args()
    try:
        token = os.environ.get("GH_TOKEN", "")
        if not token:
            raise ReleaseGateError("GH_TOKEN is required to distinguish inaccessible release state")
        if not args.repository or not args.tag:
            raise ReleaseGateError("Repository and tag are required")
        action = check_release(args.repository, args.tag, token, args.require_absent)
    except ReleaseGateError as error:
        print(f"::error::{error}")
        return 1
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"build_required={'true' if action == 'build' else 'false'}\n")
    print(json.dumps({"release_action": action, "tag": args.tag}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
