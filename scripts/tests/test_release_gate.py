import copy
import importlib.util
import io
import json
from pathlib import Path
import unittest
import urllib.error


SPEC = importlib.util.spec_from_file_location(
    "release_gate", Path(__file__).resolve().parents[1] / "check-release-gate.py")
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
TAG = "v1.5.8.16"
APK = f"WhiteZia-{TAG}-universal-arm.apk"
NOTICES = f"WhiteZia-{TAG}-THIRD_PARTY_NOTICES.md"
SUMS = f"{'a' * 64}  {APK}\n{'b' * 64}  {NOTICES}\n".encode()
RELEASE = {
    "tag_name": TAG, "draft": False, "published_at": "2026-10-25T00:00:00Z",
    "assets": [{"name": name, "size": 100, "state": "uploaded",
                **({"digest": "sha256:" + digest * 64} if digest else {})}
               for name, digest in ((APK, "a"), (NOTICES, "b"), ("SHA256SUMS.txt", ""))],
}


class ReleaseGateTests(unittest.TestCase):
    def run_gate(self, release=None, status=200, sums=SUMS, require_absent=False,
                 listing=None, listing_status=200):
        self.requests = []

        def opener(request, timeout):
            self.requests.append(request)
            if len(self.requests) == 1:
                if status != 200:
                    raise urllib.error.HTTPError(request.full_url, status, "fixture", {}, None)
                return io.BytesIO(json.dumps(RELEASE if release is None else release).encode())
            if status == 404:
                if listing_status != 200:
                    raise urllib.error.HTTPError(request.full_url, listing_status, "fixture", {}, None)
                return io.BytesIO(json.dumps([] if listing is None else listing).encode())
            return io.BytesIO(sums)

        return GATE.check_release("owner/repo", TAG, "fixture-token", require_absent, opener)

    def test_404_allows_build(self):
        self.assertEqual("build", self.run_gate(status=404))

    def test_complete_published_release_skips_build(self):
        self.assertEqual("skip", self.run_gate())
        self.assertEqual("Bearer fixture-token", self.requests[0].get_header("Authorization"))
        self.assertIsNone(self.requests[1].get_header("Authorization"))

    def test_403_fails_instead_of_building(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "HTTP 403"):
            self.run_gate(status=403)

    def test_other_http_failure_is_not_absence(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "HTTP 500"):
            self.run_gate(status=500)

    def test_draft_fails(self):
        with self.assertRaises(GATE.ReleaseGateError):
            self.run_gate({**RELEASE, "draft": True})

    def test_unpublished_fails(self):
        with self.assertRaises(GATE.ReleaseGateError):
            self.run_gate({**RELEASE, "published_at": None})

    def test_wrong_tag_fails(self):
        with self.assertRaises(GATE.ReleaseGateError):
            self.run_gate({**RELEASE, "tag_name": "v0.0"})

    def test_each_missing_asset_fails(self):
        for index in range(3):
            with self.subTest(index=index), self.assertRaises(GATE.ReleaseGateError):
                self.run_gate({**RELEASE, "assets": [row for i, row in enumerate(RELEASE["assets"]) if i != index]})

    def test_duplicate_asset_fails(self):
        with self.assertRaises(GATE.ReleaseGateError):
            self.run_gate({**RELEASE, "assets": RELEASE["assets"] + [RELEASE["assets"][0]]})

    def test_pending_or_empty_asset_fails(self):
        for change in ({"state": "starter"}, {"size": 0}, {"size": True}):
            release = copy.deepcopy(RELEASE)
            release["assets"][0].update(change)
            with self.subTest(change=change), self.assertRaises(GATE.ReleaseGateError):
                self.run_gate(release)

    def test_checksum_missing_or_malformed_fails(self):
        for sums in (b"", f"{'a' * 64}  {APK}\n".encode(), b"malformed\n", SUMS + SUMS):
            with self.subTest(sums=sums), self.assertRaises(GATE.ReleaseGateError):
                self.run_gate(sums=sums)

    def test_digest_mismatch_fails(self):
        release = copy.deepcopy(RELEASE)
        release["assets"][0]["digest"] = "sha256:" + "c" * 64
        with self.assertRaisesRegex(GATE.ReleaseGateError, "digest"):
            self.run_gate(release)

    def test_older_api_without_digest_can_use_manifest(self):
        release = copy.deepcopy(RELEASE)
        for asset in release["assets"]:
            asset.pop("digest", None)
        self.assertEqual("skip", self.run_gate(release))

    def test_late_existing_release_fails_before_create(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "appeared during the build"):
            self.run_gate(require_absent=True)
        self.assertEqual(1, len(self.requests))

    def test_late_404_allows_create(self):
        self.assertEqual("build", self.run_gate(status=404, require_absent=True))

    def test_404_with_draft_in_listing_fails(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "draft"):
            self.run_gate(status=404, listing=[{**RELEASE, "draft": True}])

    def test_listing_403_does_not_allow_build(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "HTTP 403"):
            self.run_gate(status=404, listing_status=403)

    def test_listing_non_array_fails(self):
        with self.assertRaisesRegex(GATE.ReleaseGateError, "listing"):
            self.run_gate(status=404, listing={})

    def test_draft_listing_paginates_before_allowing_absence(self):
        calls = []

        def opener(request, timeout):
            calls.append(request.full_url)
            if len(calls) == 1:
                raise urllib.error.HTTPError(request.full_url, 404, "fixture", {}, None)
            rows = [{"tag_name": "v0.1"}] * 100 if len(calls) == 2 else [{"tag_name": TAG, "draft": True}]
            return io.BytesIO(json.dumps(rows).encode())

        with self.assertRaisesRegex(GATE.ReleaseGateError, "draft"):
            GATE.check_release("owner/repo", TAG, "fixture-token", opener=opener)
        self.assertTrue(calls[-1].endswith("page=2"))

    def test_non_object_response_fails(self):
        with self.assertRaises(GATE.ReleaseGateError):
            self.run_gate([])


if __name__ == "__main__":
    unittest.main()
