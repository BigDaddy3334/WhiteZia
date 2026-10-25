import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "check-cotten-package.py"
SPEC = importlib.util.spec_from_file_location("cotten_package", SCRIPT)
PACKAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGE)


def elf_fixture(abi, alignment=16384, stack_flags=6, machine_override=None, entry_override=None):
    elf_class, machine = PACKAGE.ABIS[abi]
    header_size, ph_size = (64, 56) if elf_class == 2 else (52, 32)
    data = bytearray(512)
    ident = b"\x7fELF" + bytes([elf_class, 1, 1]) + bytes(9)
    header = (ident, 3, machine_override or machine, 1, entry_override if entry_override is not None else 0x4008,
              header_size, 0, 0, header_size, ph_size, 2, 0, 0, 0)
    struct.pack_into("<16sHHIQQQIHHHHHH" if elf_class == 2 else "<16sHHIIIIIHHHHHH", data, 0, *header)
    if elf_class == 2:
        struct.pack_into("<IIQQQQQQ", data, header_size, 1, 5, 0, 0x4000, 0x4000, 512, 512, alignment)
        struct.pack_into("<IIQQQQQQ", data, header_size + ph_size, 0x6474E551, stack_flags, 0, 0, 0, 0, 0, 16)
    else:
        struct.pack_into("<IIIIIIII", data, header_size, 1, 0, 0x4000, 0x4000, 512, 512, 5, alignment)
        struct.pack_into("<IIIIIIII", data, header_size + ph_size, 0x6474E551, 0, 0, 0, 0, 0, stack_flags, 16)
    return bytes(data)


class CottenPackageTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="whitezia-cotten-fixtures-")
        self.root = Path(self.temporary.name)
        self.pin_path = PACKAGE.ROOT / "native-dependencies/cotten.json"
        self.pin = json.loads(self.pin_path.read_text())
        self.metadata_path = self.root / "cotten-build-info.json"
        self.metadata = {key: self.pin[key] for key in ("source_revision", "source_repository", "go_version", "ndk_version", "android_api")}
        self.metadata.update(source_patches=self.pin.get("source_patches", []),
                             vcs_modified=bool(self.pin.get("source_patches")),
                             source_tree="1" * 40, patched_source_tree="2" * 40)
        self.metadata["runtimes"] = {}
        for abi in PACKAGE.ABIS:
            path = self.root / abi / PACKAGE.BINARY_NAME
            path.parent.mkdir()
            path.write_bytes(elf_fixture(abi))
            self.metadata["runtimes"][abi] = {"binary_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        self.write_metadata()

    def tearDown(self):
        self.temporary.cleanup()

    def write_metadata(self):
        self.metadata_path.write_text(json.dumps(self.metadata))

    def check(self):
        return PACKAGE.validate_package(self.root, self.metadata_path, self.pin_path)

    def replace_binary(self, abi, **options):
        path = self.root / abi / PACKAGE.BINARY_NAME
        path.write_bytes(elf_fixture(abi, **options))
        self.metadata["runtimes"][abi]["binary_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_metadata()

    def notice(self):
        return ("CottenDNS\nCopyright (c) 2026 tajirax\n"
                "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
                'THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND\n'
                + self.pin["source_revision"] + "\n" + "\n".join(
                    patch["path"] + " " + patch["sha256"] for patch in self.pin.get("source_patches", [])))

    def apk(self, extra=None, duplicate=False, notice=True, duplicate_notice=False):
        apk = self.root / "fixture.apk"
        with warnings.catch_warnings(), zipfile.ZipFile(apk, "w") as archive:
            warnings.simplefilter("ignore", UserWarning)
            for abi in PACKAGE.ABIS:
                name = f"lib/{abi}/{PACKAGE.BINARY_NAME}"
                archive.write(self.root / abi / PACKAGE.BINARY_NAME, name)
                if duplicate and abi == "arm64-v8a":
                    archive.write(self.root / abi / PACKAGE.BINARY_NAME, name)
            if extra:
                archive.writestr(extra, b"retired-engine")
            if notice is not False:
                text = self.notice() if notice is True else notice
                archive.writestr(PACKAGE.NOTICE_ENTRY, text)
                if duplicate_notice:
                    archive.writestr(PACKAGE.NOTICE_ENTRY, text)
        return apk

    def test_accepts_both_verified_arm_executables(self):
        self.assertEqual(set(PACKAGE.ABIS), set(self.check()))

    def test_rejects_4k_load_alignment_even_with_matching_hash(self):
        self.replace_binary("arm64-v8a", alignment=4096)
        with self.assertRaisesRegex(ValueError, "alignment"):
            self.check()

    def test_rejects_executable_stack(self):
        self.replace_binary("armeabi-v7a", stack_flags=7)
        with self.assertRaisesRegex(ValueError, "GNU_STACK"):
            self.check()

    def test_rejects_wrong_cpu(self):
        self.replace_binary("arm64-v8a", machine_override=62)
        with self.assertRaisesRegex(ValueError, "machine"):
            self.check()

    def test_rejects_shared_library_without_executable_entry(self):
        self.replace_binary("arm64-v8a", entry_override=0)
        with self.assertRaisesRegex(ValueError, "executable"):
            self.check()

    def test_rejects_entry_outside_executable_load(self):
        self.replace_binary("arm64-v8a", entry_override=0x9000)
        with self.assertRaisesRegex(ValueError, "entry"):
            self.check()

    def test_rejects_changed_binary(self):
        path = self.root / "arm64-v8a" / PACKAGE.BINARY_NAME
        path.write_bytes(path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "hash"):
            self.check()

    def test_rejects_missing_second_abi(self):
        (self.root / "armeabi-v7a" / PACKAGE.BINARY_NAME).unlink()
        with self.assertRaises(FileNotFoundError):
            self.check()

    def test_rejects_different_source_provenance(self):
        self.metadata["source_revision"] = "1" * 40
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "source_revision"):
            self.check()

    def test_rejects_unexpected_metadata_abi(self):
        self.metadata["runtimes"]["x86"] = {}
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "both ARM"):
            self.check()

    def test_rejects_retired_engine_in_native_directory(self):
        (self.root / "arm64-v8a/libstormdns_client.so").write_bytes(b"old")
        with self.assertRaisesRegex(ValueError, "Old StormDNS"):
            self.check()

    def test_validates_actual_apk_entries(self):
        self.assertEqual(set(PACKAGE.ABIS), set(PACKAGE.validate_apk(self.apk(), self.metadata_path, self.pin_path)))

    def test_rejects_retired_apk_engine_even_in_other_abi(self):
        with self.assertRaisesRegex(ValueError, "retired StormDNS"):
            PACKAGE.validate_apk(self.apk("lib/x86/libstormdns_client.so"), self.metadata_path, self.pin_path)

    def test_rejects_ambiguous_duplicate_apk_entries(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            PACKAGE.validate_apk(self.apk(duplicate=True), self.metadata_path, self.pin_path)

    def test_rejects_missing_or_duplicate_packaged_notice(self):
        for options in ({"notice": False}, {"duplicate_notice": True}):
            with self.subTest(options=options), self.assertRaisesRegex(ValueError, "exactly one.*NOTICES"):
                PACKAGE.validate_apk(self.apk(**options), self.metadata_path, self.pin_path)

    def test_rejects_stale_packaged_notice_source_revision(self):
        text = self.notice().replace(self.pin["source_revision"], "1" * 40)
        with self.assertRaisesRegex(ValueError, "license/provenance"):
            PACKAGE.validate_apk(self.apk(notice=text), self.metadata_path, self.pin_path)

    def test_rejects_notice_missing_cotten_copyright_permission_or_warranty(self):
        for text in ("Copyright (c) 2026 tajirax", "Permission is hereby granted", 'THE SOFTWARE IS PROVIDED "AS IS"'):
            with self.subTest(text=text), self.assertRaisesRegex(ValueError, "license/provenance"):
                PACKAGE.validate_apk(self.apk(notice=self.notice().replace(text, "omitted")), self.metadata_path, self.pin_path)

    def test_accepts_wrapped_packaged_license_text(self):
        notice = self.notice().replace("to any person obtaining a copy", "to any person\nobtaining a copy")
        self.assertEqual(set(PACKAGE.ABIS), set(PACKAGE.validate_apk(self.apk(notice=notice), self.metadata_path, self.pin_path)))

    def test_rejects_oversized_packaged_notice(self):
        with self.assertRaisesRegex(ValueError, "oversized"):
            PACKAGE.validate_apk(self.apk(notice=self.notice() + "x" * (2 * 1024 * 1024)), self.metadata_path, self.pin_path)

    def test_rejects_cottenmax_or_legacy_framing_pin(self):
        for key, value in (("mode", "cottenmax"), ("legacy_session_id", True)):
            pin = dict(self.pin)
            pin[key] = value
            with self.assertRaises(ValueError):
                PACKAGE.validate_pin(pin)

    def test_rejects_missing_or_mismatched_source_patch_provenance(self):
        if not self.pin.get("source_patches"):
            self.skipTest("Unmodified historical upstream pin")
        self.metadata["source_patches"] = []
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "patch provenance"):
            self.check()

    def test_rejects_patched_binary_claiming_clean_or_unchanged_source(self):
        if not self.pin.get("source_patches"):
            self.skipTest("Unmodified historical upstream pin")
        self.metadata["vcs_modified"] = False
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "modified source"):
            self.check()
        self.metadata["vcs_modified"] = True
        self.metadata["patched_source_tree"] = self.metadata["source_tree"]
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "unchanged source tree"):
            self.check()

    def test_rejects_changed_patch_or_unsafe_patch_path(self):
        if not self.pin.get("source_patches"):
            self.skipTest("Unmodified historical upstream pin")
        pin = json.loads(json.dumps(self.pin))
        pin["source_patches"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "patch SHA-256"):
            PACKAGE.validate_pin(pin)
        pin["source_patches"][0]["path"] = "native-dependencies/patches/../../outside.patch"
        with self.assertRaisesRegex(ValueError, "patch path"):
            PACKAGE.validate_pin(pin)

    def test_rejects_packaged_notice_without_modified_source_provenance(self):
        if not self.pin.get("source_patches"):
            self.skipTest("Unmodified historical upstream pin")
        text = self.notice().replace(self.pin["source_patches"][0]["sha256"], "omitted")
        with self.assertRaisesRegex(ValueError, "audited CottenDNS source patch"):
            PACKAGE.validate_apk(self.apk(notice=text), self.metadata_path, self.pin_path)


if __name__ == "__main__":
    unittest.main()
