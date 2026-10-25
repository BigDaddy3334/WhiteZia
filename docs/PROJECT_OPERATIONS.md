# WhiteZia project operations

Updated 26 October 2026 (Asia/Yekaterinburg). This is the current public operations guide. The private
October 3 handoff and October 4 migration describe historical state; read the
current repository, artifact metadata and live health before making changes.
`C:\WhiteZia\AGENTS.md` is the workstation instruction entry point.

## Canonical sources and private material

| Component | Windows source | GitHub repository |
| --- | --- | --- |
| Android | `C:\WhiteZia\WhiteZia` | `BigDaddy3334/WhiteZia` |
| Core, bot, watchdog and DNS server adapter | `C:\WhiteZia\whitezia-core-work` | `BigDaddy3334/WhiteZia-Core` |
| Management panel | `C:\WhiteZia\WhiteZia-Panel-repo` | `BigDaddy3334/WhiteZia-Panel` |
| Public website and account frontend | `C:\WhiteZia\WhiteZia-Site` | `BigDaddy3334/WhiteZia-Site` |

Verify each checkout's configured Git remote before pushing; the table is a map,
not permission to change a remote. Preserve existing uncommitted changes and the
retained older checkout `C:\WhiteZia\archive\WhiteZia-refactor`.

`C:\WhiteZia\private` contains signing and deployment credentials and is outside
Git. Keep original signing keys and an encrypted off-host backup. Never commit
keys, signing properties, `.env` files, device bundles or tunnel credentials,
production database/configuration snapshots, private SSH inventory, payment data,
Codex transcripts, or raw phone diagnostics. Public evidence must be redacted.

## Runtime contracts

Automatic transport order is reachable direct Xray, CDN Xray fallback, then
CottenDNS. Existing direct routes use XHTTP. Changing a DNS option must not
silently modify Xray/CDN settings. AmneziaWG issuance remains disabled for Android
and iOS. Accounts use email/Core user ID; Telegram ID is not the primary identity.

CottenDNS Normal is the default. CottenMax is an explicit beta test mode with a
separate server account allowlist and resource budget. A failure in Max must not
silently substitute Normal or different account credentials. Normal uses
`TUNNEL_MODE = "legacy"` with `LEGACY_SESSION_ID = false`; the upstream mode name
does not select the old one-byte session framing.

Auto / UDP / TCP chooses the client-to-resolver hop, normally port 53. Normal Auto
tries UDP and can fall back to TCP; explicit choices restrict that hop. This does
not add application UDP support to CottenMax or prove that a recursive resolver
uses TCP for its authoritative hop. Server dual UDP/TCP listeners and the passive
per-account traffic accounting are separate acceptance requirements.

Historical `stormdns://`, `stormbundle://`, `stormdns_candidates`, database and
preferences names remain compatibility contracts. The DNS service identity stays
`stormdns.service`, running the CottenDNS adapter; `cottendns.service` is its alias.
Do not run a second listener or rename persistent fields merely to update branding.

## Reviewed native sources

Android's `native-dependencies/cotten.json` pins upstream CottenDNS commit
`b3d20fe7fdeba8f157e94223c99cae33116207bf`, Go 1.27.3, NDK 29.0.14206865,
Android API 26 and both ARM ABIs. Its `source_patches` contains the reviewed Max
client admission patch and exact digest. Build using
`scripts/build-cotten-android.sh` from a separate clean upstream Git root at the
pin. Do not replace it with an unreviewed upstream HEAD or a dirty source copy.
`cotten-build-info.json`, the installed ELFs and both third-party notices must
agree. See [CottenDNS Android integration](COTTENDNS_ANDROID_INTEGRATION.md).

Core's `tools/cotten-server` owns the server adapter and boundary sources,
`UPSTREAM.json`, input/source manifests, `upstream.patch`, notices and the build
wrapper. Use its `README.md` and `build.py` to reconstruct the exact source and
verify tests and provenance. It includes Normal/Max isolation, the reviewed Max
TCP half-close handling and bounded DNS TCP ingress. Android and server patches
are distinct; publishing one does not publish the other.

## Windows and WSL builds

Load `C:\WhiteZia\Environment.ps1` in PowerShell. Linux builds and tests use WSL
`Ubuntu-24.04`, user `bab`, and `source /mnt/c/WhiteZia/Environment.sh`. Java and
Gradle caches use `/opt/whitezia`. Use Windows npm for the panel's Windows
`node_modules`; do not mix host and WSL installations in that directory.

The workstation wrapper runs the Android checkout under the Linux SDK and
restores its previous `local.properties` afterwards:

```powershell
& C:\WhiteZia\Build-Android.ps1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For a signed release, set the private `WHITEZIA_RELEASE_PROPERTIES` and required
bootstrap properties in the build environment without copying their contents
into source or logs. Build the `release` variant with shrinking enabled. A debug
APK (`shop.whitezia.client.debug`) cannot be renamed into a production release.

Release 43 used the existing reviewed Linux Go 1.27.3 toolchain for the bundled
`libwg-go.so` build. Preserve the explicit override when reproducing its native
inputs; `app/src/main/cpp/amneziawg/libwg-go/Makefile` pins the Go version and
archive checksum. For example, with private signing/bootstrap properties already
configured by the workstation environment:

```powershell
wsl.exe -d Ubuntu-24.04 -u bab --exec env `
  WHITEZIA_WG_GO_TOOLCHAIN=/opt/whitezia/tools/wg-go-1.27.3 `
  bash /mnt/c/WhiteZia/Run-Android-Wsl.sh `
  :app:testDebugUnitTest :app:assembleRelease :app:lintRelease `
  --no-daemon --offline --max-workers=1
```

This reuses the reviewed toolchain and cached dependencies. It does not replace
the separate CottenDNS Go/NDK pins or authorize overwriting a published APK.

## Verified publication record: 26 October 2026

Production `1.5.8.16` / `versionCode 43` is published as universal ARM for
`shop.whitezia.client`. The immutable APK is **45,643,800 bytes**, SHA-256
`64b62bd38f90b8c1dd5481a827f968a252abfa7ac96a13d9e7e521e3362bce77`.
Its original signing certificate SHA-256 is
`b7c42dd500f0cb183ac22242018a4f8be8d908f4258d5d89cc95514e95f1dde7`.

| Published component source | Immutable revision |
| --- | --- |
| Android APK and tag `v1.5.8.16` | `3a64265937664bfc6453efbb8f535516ca248c09` |
| Core, bot and server integration | `9c0ceda252cb6328f9a611d00a47e87e24e50e63` |
| Management panel | `78eaa2be08da7fd5c1a3056478da65be78fee75f` |
| Website source | `3b931c2e9873616bb490585a2f7a39cf6272db41` |

[GitHub release v1.5.8.16](https://github.com/BigDaddy3334/WhiteZia/releases/tag/v1.5.8.16)
is the latest stable release, ID `423579918`. Its four assets match the release
manifest. Actual public APK downloads from GitHub, primary OTA and reserve/site
OTA all produced the exact size and hash above. Both OTA manifests expose version
43, the same certificate and notes, minimum version 0 and `mandatory=false`.
Primary and site public health checks pass; reserve remains read-only recovery.
The site's dynamic metadata/download contract passes; a separate browser-rendered
visual check was not part of this acceptance.

The bot's running version/path points to this same APK. A read-only delivery audit
observed 45 sent notifications and 18 unavailable chats (12 blocked, 6 not found);
the audit itself sent no messages. The Android build passed 493 JVM tests with no
failures, errors or skips. Release lint reported no errors/fatal issues, 203
warnings and 2 hints. The tag-triggered Official Release workflow passed its
preservation gate and skipped rebuilding/replacing the published artifact.

The existing CottenDNS server executable is SHA-256
`60157ed5d007577a6c4be5e46f1848aeea7634bb7691781d9f24e6f625c6bb0b`.
Normal remains the default; Max remains explicitly experimental. UDP/53 and
TCP/53 listeners and the integration/accounting services are present. The
read-only comparison matched all 33 eligible Core DNS assignments to configured
clients. Fresh Normal probes from a separate server passed through both public
UDP/53 and TCP/53, returning an HTTPS 200 response with verified TLS. Existing
keys, configuration, processes and Max limits remained unchanged. Local TCP
probes on the DNS relay are not equivalent: its existing outbound firewall
restricts TCP/53 by service identity. This acceptance is a server-network test;
it is not a fresh phone TUN or mobile-SIM test.

A later documentation-only commit does not change the APK, its immutable Android
source/tag revision, or the four source revisions recorded in its release manifest.

## Release 1.5.8.16 / versionCode 43

This records the procedure used for release 43; its publication facts are above.
Confirm the current production manifests and repository version before any later
build. Code 43 identifies one immutable signed APK; never replace it with different
bytes. A later application release must use a greater code.

1. Freeze the latest reviewed debug source in all four repositories. Record the
   source revisions, dirty diff hashes, native metadata and relevant test results.
   Increment Android to `1.5.8.16` / `43` and write its release notes.
2. Build and verify a universal ARM release containing `arm64-v8a` and
   `armeabi-v7a`. Run the applicable JVM/native checks and release lint. Run
   `scripts/check-cotten-package.py --apk PATH` and the publisher's package checks.
   Verify package `shop.whitezia.client`, version, size, APK SHA-256 and the
   original production signing certificate. The expected certificate SHA-256 is
   `b7c42dd500f0cb183ac22242018a4f8be8d908f4258d5d89cc95514e95f1dde7`.
3. Scan current source and staged/unstaged diffs for secrets in every repository.
   Review `git status` and explicit staged paths; publish source, tests, reviewed
   patches and notices, never private workstation or server material.
4. Publish the verified APK to primary Core OTA and the bot with the Android
   repository's `scripts/publish-production-android.sh 43 1.5.8.16
   release-notes/1.5.8.16.txt`. Supply the configured Core SSH target and private
   signing/bootstrap properties. `WHITEZIA_SKIP_BUILD=true` may reuse the already
   verified release. The script locks publication, preserves previous immutable
   artifacts, updates pointers and verifies or rolls back the publication.
   `WHITEZIA_ANDROID_NOTIFY_VERSION` controls release notifications; an empty
   value disables them. Review its value intentionally for each publication.
5. Publish the identical artifact and metadata to the reserve OTA behind
   `whitezia.su`. The primary publisher does not perform this step and does not
   create a GitHub Release. Preserve reserve read-only recovery mode. Back up the
   owned release environment, stage the APK, verify its remote digest and publish
   pointers atomically; rollback must not overwrite concurrent unrelated changes.
6. Push all reviewed source changes to their existing GitHub remotes. If publishing
   a GitHub Release, use the same verified APK, notes and checksum. Save a release
   record containing repository revisions and artifact/channel verification.

Do not blindly rerun historical dated publication scripts: they may contain old
versions, hashes and paths. Private access data belongs outside this guide.

## Public channel verification

Primary metadata is `https://api.whitezia.ru/api/app/releases/android`; reserve
and site metadata is `https://whitezia.su/api/app/releases/android`. Both require:

```text
X-WhiteZia-Application-Id: shop.whitezia.client
X-WhiteZia-Update-Channel: production
```

For both origins verify application/channel, version name/code, ABI
`universal-arm`, size, SHA-256, original certificate, notes and update policy.
Download each advertised immutable APK over HTTPS and compare the actual bytes,
package metadata and signature with the local artifact. Check the bot's configured
APK path/version points to the same bytes; do not expose its token while checking.
Verify reserve health remains `read-only-recovery`.

The website dynamically loads the reserve metadata with `cache:no-store` and
sets every download link to its `apk_url`. Check the displayed version and real
download after reserve publication. Its frontend contains no hardcoded APK
version. If deploying website source changes, verify production account API,
Nginx syntax, the actual DNS stub required by the resolver configuration and
`/.git/config` returning 404. Keep subscription-token routes out of access logs.

## Resolver catalog evidence

A DNS port result is only a discovery candidate. It does not establish recursion,
Normal/Max tunnel function, operator attribution, or accessibility through a SIM.
The 25 October user result contains TCP/53 observations only; the lack of UDP
results is unresolved and must not become a claim that the operators block UDP.
VPS HTTPS payload success is a VPS observation, not a mobile-network acceptance.

Catalog entries require explicit per-mode, per-transport payload verification,
source/provenance and expiry. Operator attribution must use the actual physical
network and registered MCC/MNC, not assume a Wi-Fi network belongs to the SIM.
Preserve local DNS precedence and explicit manual resolver choices. Do not embed
the complete unverified scan result or advertise a candidate as SIM-tested.
