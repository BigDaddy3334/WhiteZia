# Pinned Xray Android runtime

Current ARM builds use pristine upstream `v26.10.31`, commit
`a4a3dff5b20c2faae567b742c172c7b95e75c083`. The build wrapper
`../build-xray-android.sh` verifies the complete clean checkout, exact upstream
`go.mod` and `go.sum`, and Go `1.27.3` with automatic toolchain switching disabled.
It builds both supported ARM ABIs with NDK `26.3.11579264`, Android API 26 and
16 KiB ELF page alignment. Module selection is read-only during the build.

Upstream dependencies include `golang.org/x/net v0.61.0`,
`google.golang.org/grpc v1.84.0` and `github.com/klauspost/compress v1.20.0`.
These supersede the former WhiteZia dependency-only security overlay. That patch
is retained as a historical reproduction input for earlier immutable releases;
do not apply it to the new core or downgrade the new upstream dependencies.

The upstream DefaultListener disables MPTCP unless a caller explicitly opts in.
This reduces CVE-2026-98432 exposure for Android SOCKS/HTTP listeners, including
manual LAN binding. WhiteZia's managed subscription and bootstrap listeners
remain loopback-only. This does not fix the phone's kernel. No WhiteZia routing,
XHTTP/CDN settings or DNS tunnel selection was changed.

Start from a clean checkout at the pinned commit. Run
`scripts/build-xray-android.sh` separately for `arm64-v8a` and `armeabi-v7a` with
XRAY_SOURCE, ANDROID_NDK_HOME and GO_BINARY pointing to the pinned tools/source.
Verify the ELF interpreter, architecture, 16 KiB load segments and Go build
metadata before copying both executables into jniLibs. Record original and
NDK-stripped hashes in `app/src/main/jniLibs/xray-build-info.json`, then verify
the actual APK entries against the stripped hashes. CottenDNS, tun2proxy and
AmneziaWG remain separately pinned.

Run JVM configuration/bootstrap/route tests, relevant upstream native tests and
an isolated listener/proxy smoke test. Device TUN, FakeDNS, network changes,
Doze and OEM background restrictions still require an Android device.

References:

- [Upstream release/security notice](https://github.com/XTLS/Xray-core/releases/tag/v26.10.31)
- [Exact dependencies](https://github.com/XTLS/Xray-core/blob/v26.10.31/go.mod)
- [Historical overlay](security-dependencies-20261021.patch)
