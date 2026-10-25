#!/usr/bin/env python3
"""Verify the pinned CottenDNS package using only the Python standard library."""

import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ABIS = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}
BINARY_NAME = "libcottendns_client.so"
NOTICE_ENTRY = "assets/THIRD_PARTY_NOTICES.md"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inspect_elf(path, abi, minimum_alignment=16384):
    data = Path(path).read_bytes()
    require(len(data) >= 52 and data[:4] == b"\x7fELF", f"{abi}: not an ELF executable")
    elf_class, machine = ABIS[abi]
    require(data[4] == elf_class and data[5] == 1, f"{abi}: wrong ELF class or byte order")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH" if elf_class == 2 else "<16sHHIIIIIHHHHHH", data)
    require(header[1] == 3 and header[2] == machine and header[4] != 0, f"{abi}: expected Android PIE executable/machine")
    phoff, phentsize, phnum = header[5], header[9], header[10]
    expected_size = 56 if elf_class == 2 else 32
    require(phentsize == expected_size and 0 < phnum < 4096, f"{abi}: invalid program-header layout")
    require(phoff + phentsize * phnum <= len(data), f"{abi}: truncated program headers")
    load_alignments = []
    stack_flags = []
    executable_entry = False
    for index in range(phnum):
        fields = struct.unpack_from("<IIQQQQQQ" if elf_class == 2 else "<IIIIIIII", data, phoff + index * phentsize)
        if elf_class == 2:
            kind, flags, offset, address, _, file_size, memory_size, alignment = fields
        else:
            kind, offset, address, _, file_size, memory_size, flags, alignment = fields
        if kind == 1:  # PT_LOAD
            require(alignment >= minimum_alignment and alignment & (alignment - 1) == 0,
                    f"{abi}: LOAD alignment is below 16 KiB or invalid")
            require((address - offset) % minimum_alignment == 0, f"{abi}: LOAD offset/address breaks 16 KiB alignment")
            require(file_size <= memory_size and offset + file_size <= len(data), f"{abi}: invalid LOAD bounds")
            load_alignments.append(alignment)
            executable_entry |= bool(flags & 1 and address <= header[4] < address + memory_size)
        if kind == 0x6474E551:  # PT_GNU_STACK
            stack_flags.append(flags)
    require(load_alignments, f"{abi}: no LOAD segments")
    require(executable_entry, f"{abi}: entry is outside executable LOAD segments")
    require(len(stack_flags) == 1 and stack_flags[0] & 1 == 0, f"{abi}: absent or executable GNU_STACK")
    return {"elf_class": elf_class, "elf_machine": machine, "load_alignment_min": min(load_alignments),
            "executable_stack": False, "size_bytes": len(data), "binary_sha256": hashlib.sha256(data).hexdigest()}


def validate_pin(pin):
    require(pin.get("engine") == "CottenDNS" and pin.get("mode") == "normal", "Only CottenDNS Normal is supported")
    require(pin.get("binary_name") == BINARY_NAME, "Unexpected CottenDNS executable name")
    require(pin.get("abis") == list(ABIS), "CottenDNS must contain both supported ARM ABIs")
    require(pin.get("tunnel_mode") == "legacy" and pin.get("legacy_session_id") is False,
            "Normal must use explicit native session framing")
    require(pin.get("minimum_load_alignment") == 16384, "The minimum ELF alignment must remain 16 KiB")
    revision = pin.get("source_revision", "")
    require(len(revision) == 40 and all(char in "0123456789abcdef" for char in revision), "A full immutable source revision is required")
    require(pin.get("source_repository") == "https://github.com/WhiteDNS/CottenDNS.git", "Unexpected engine source repository")
    require(pin.get("go_version") == "1.27.3" and pin.get("ndk_version") == "29.0.14206865" and pin.get("android_api") == 26,
            "Unsupported CottenDNS toolchain pin")
    patches = pin.get("source_patches", [])
    require(isinstance(patches, list) and len(patches) <= 8, "Invalid CottenDNS source patch list")
    names = set()
    for patch in patches:
        require(isinstance(patch, dict) and set(patch) == {"path", "sha256"}, "Invalid CottenDNS source patch entry")
        path, digest = patch["path"], patch["sha256"]
        require(isinstance(path, str) and path.startswith("native-dependencies/patches/") and
                path.endswith(".patch") and ".." not in path and "\\" not in path and path not in names,
                "Invalid or duplicate CottenDNS source patch path")
        require(isinstance(digest, str) and len(digest) == 64 and all(char in "0123456789abcdef" for char in digest),
                "A full CottenDNS source patch SHA-256 is required")
        require(hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == digest, "CottenDNS source patch SHA-256 differs")
        names.add(path)


def validate_package(native_dir, metadata_path=None, pin_path=None, reject_old=True):
    native_dir = Path(native_dir).resolve()
    pin = json.loads(Path(pin_path or ROOT / "native-dependencies/cotten.json").read_text(encoding="utf-8"))
    validate_pin(pin)
    metadata = json.loads(Path(metadata_path or native_dir / "cotten-build-info.json").read_text(encoding="utf-8"))
    for field in ("source_revision", "source_repository", "go_version", "ndk_version", "android_api"):
        require(metadata.get(field) == pin[field], f"CottenDNS provenance mismatch: {field}")
    patches = pin.get("source_patches", [])
    require(metadata.get("source_patches", []) == patches, "CottenDNS source patch provenance mismatch")
    if patches:
        require(metadata.get("vcs_modified") is True, "Patched CottenDNS must declare modified source")
        tree = metadata.get("patched_source_tree", "")
        require(isinstance(tree, str) and len(tree) == 40 and all(char in "0123456789abcdef" for char in tree),
                "Patched CottenDNS requires its exact Git source tree")
        require(tree != metadata.get("source_tree"), "Patched CottenDNS cannot claim the unchanged source tree")
    require(set(metadata.get("runtimes", {})) == set(ABIS), "CottenDNS metadata must contain both ARM ABIs only")
    if reject_old:
        require(not list(native_dir.glob("*/libstormdns_client.so")), "Old StormDNS executable remains in the native package")
    results = {}
    for abi in ABIS:
        result = inspect_elf(native_dir / abi / BINARY_NAME, abi, pin["minimum_load_alignment"])
        require(metadata["runtimes"][abi].get("binary_sha256") == result["binary_sha256"], f"{abi}: executable hash differs from provenance")
        results[abi] = result
    return results


def validate_apk(apk_path, metadata_path=None, pin_path=None):
    with zipfile.ZipFile(apk_path) as archive, tempfile.TemporaryDirectory(prefix="whitezia-cotten-verify-") as temporary:
        entries = archive.namelist()
        require(not any(name.startswith("lib/") and name.endswith("/libstormdns_client.so") for name in entries),
                "APK contains the retired StormDNS executable")
        pin = json.loads(Path(pin_path or ROOT / "native-dependencies/cotten.json").read_text(encoding="utf-8"))
        validate_pin(pin)
        require(entries.count(NOTICE_ENTRY) == 1, f"APK must contain exactly one {NOTICE_ENTRY}")
        notice_info = archive.getinfo(NOTICE_ENTRY)
        require(notice_info.file_size <= 2 * 1024 * 1024, "APK third-party notices are oversized")
        notices = archive.read(notice_info).decode("utf-8")
        # Whitespace may differ between Markdown and upstream license formatting.
        notice_text = " ".join(notices.split())
        for text in ("CottenDNS", "Copyright (c) 2026 tajirax",
                     "Permission is hereby granted, free of charge, to any person obtaining a copy",
                     'THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND', pin["source_revision"]):
            require(text in notice_text, f"APK third-party notices omit CottenDNS license/provenance: {text}")
        for patch in pin.get("source_patches", []):
            require(patch["path"] in notice_text and patch["sha256"] in notice_text,
                    "APK third-party notices omit the audited CottenDNS source patch")
        native_dir = Path(temporary)
        for abi in ABIS:
            entry = f"lib/{abi}/{BINARY_NAME}"
            require(entries.count(entry) == 1, f"APK must contain exactly one {entry}")
            info = archive.getinfo(entry)
            require(info.file_size <= 128 * 1024 * 1024, f"{abi}: oversized CottenDNS executable")
            destination = native_dir / abi / BINARY_NAME
            destination.parent.mkdir()
            destination.write_bytes(archive.read(info))
        return validate_package(native_dir, metadata_path or ROOT / "app/src/main/jniLibs/cotten-build-info.json", pin_path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-dir", type=Path, default=ROOT / "app/src/main/jniLibs")
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--pin", type=Path)
    parser.add_argument("--apk", type=Path, help="Verify actual APK CottenDNS entries against local pinned provenance")
    args = parser.parse_args()
    try:
        results = validate_apk(args.apk, args.metadata, args.pin) if args.apk else validate_package(args.native_dir, args.metadata, args.pin)
    except (ValueError, KeyError, TypeError, OSError, struct.error, zipfile.BadZipFile) as error:
        print(f"CottenDNS package verification failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps({"engine": "CottenDNS Normal", "verified": results}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
