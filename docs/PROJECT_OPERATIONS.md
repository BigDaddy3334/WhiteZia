# WhiteZia project operations

Updated 3 November 2026 (Asia/Yekaterinburg). This is the public operations guide. The private
October 3 handoff and October 4 migration describe historical state; read the
current repository, artifact metadata and live health before making changes.
`C:\WhiteZia\AGENTS.md` is the workstation instruction entry point.

WhiteZia is a subscription VPN service with an Android client, account/billing
backend, Telegram backup channel, management panel and public website. Android
owns the VPN lifecycle and native processes; Core owns accounts, device slots,
subscription state and provisioning; Panel administers and observes the managed
service; Site provides account/payment screens and the current APK download.
Payment-provider callbacks in Core confirm payments. Neither a frontend redirect
nor a panel-side technical client replaces Core's account/subscription identity.

## Current accepted state and next work

This is a documentation/source synchronization dated 3 November 2026, not a fresh
live audit or a production deployment. Release 44 below and the 2 November debug
are distinct immutable/accepted artifacts. Current Xray debug source is `5b4ff02`;
publishing that source does not update OTA, the bot's APK or the production tag.

- Core: migrations 032/033, paid 3–6 device slots, shared wallet, full wallet
  payment, opt-in renewal, hybrid referrals and fixed-scale callback hotfix.
  Runtime hotfix source `a4ebaa46ee80ce20080780151b13c46ae5617b8a`; executable
  SHA-256 `096efe830eda40971bb8ab7995eb1ab05ef35e46cff4b7dc4173b585d8a8a1e6`.
  Later docs/formatting commits do not imply another deployment.
- Site: categorized wallet/device/referral account assets accepted from `4a5a51d`
  on 1 November; cache `20261101-3`. APK links remain driven by Core metadata.
- Panel: paid-slot labels in source/dist are published as source in this sync;
  they are not a verified production frontend update. Wallet/referral admin
  screens and qWDTT controls remain future work.
- Infrastructure: the [four-node Xray rollout](https://github.com/BigDaddy3334/WhiteZia-Core/blob/main/docs/xray-26.10.31-rollout.md)
  accepted 26.10.31 on 1 November and repaired the first chain's flow mismatch.
  The [six-host MPTCP mitigation](https://github.com/BigDaddy3334/WhiteZia-Core/blob/main/docs/mptcp-mitigation-20261101.md)
  persisted MPTCP-off and restarted CottenDNS with MPTCP disabled. Kernel
  remediation remains unverified; check current vendor evidence before work.
  Recovery remains read-only; dormant writable failover is not activated.
- [qWDTT integration plan](QWDTT_INTEGRATION_PLAN.md): automatic managed
  connection, in-app manual captcha when required, background notification,
  session cancellation and fallback. No implementation or field acceptance yet.

For source publication: review each working tree, scan current source and dirty
diffs for secrets, run checks appropriate to code changes, commit explicit paths,
push existing `main` branches without force or tag changes, and verify remote
heads/CI. Android main pushes run checks; the release workflow is tag-triggered.
Never publish private configs, deployment backups or raw diagnostics as source.

## Xray 26.10.31 debug acceptance: 2 November 2026

Built `1.5.8.17-xray26.10.31` / code `44`, package
`shop.whitezia.client.debug`, universal ARM64/ARMv7. This debug build is separate
from immutable production release 44 below and was not published to OTA/GitHub.
APK `WhiteZia-1.5.8.17-xray26.10.31-debug-universal-arm.apk`: 69,531,426 bytes,
SHA-256 `dc6dce3ec4d438b0d94b89b582ea682773619b0a6868e4c7e50f278ad042b31a`. Debug signer SHA-256:
`203af617c921dea249d401cbd25beff19d46154a09a95278b6403079b311a1bc` (same as the preceding account debug).

Both Xray executables use pristine upstream `v26.10.31`, commit
`a4a3dff5b20c2faae567b742c172c7b95e75c083`, Go 1.27.3, NDK 26.3.11579264,
API 26 and 16 KiB ELF alignment. Exact module/input/packaged hashes are in
`app/src/main/jniLibs/xray-build-info.json`. The old dependency overlay remains
historical only. MPL 2.0 text and source provenance are bundled in the APK.

All 502 JVM tests passed without failures/errors/skips. Full debug Lint had
0 Error/Fatal, 210 warnings and 0 hints. APK v2 signature, debug package/version,
16 KiB ZIP/ELF alignment, Go build metadata and exact packaged hashes passed.
Only the two Xray entries differ among fourteen native APK entries; pinned
CottenDNS checks passed, as did a source/dirty-diff secret scan (zero findings).

Selected upstream common/net, TLS, XHTTP and VLESS tests passed
(51 test cases, 4 packages), excluding TestECHDial.
The first attempt failed that external test because encryptedsni.com returned
no valid ECH DNS config, followed by malformed ECHConfigList. No upstream code
was patched to hide this failure; the full upstream suite is not claimed passed.

A fresh Linux companion from the same pinned source passed all four public
routes: 32/32 HTTPS, 8/8 MTProto, 4/4 UDP DNS, 4/4 UDP443 negotiation and four
128 KiB downloads. Local SOCKS payload/checksum passed. The pinned source disables
MPTCP in DefaultListener by default, but this WSL kernel lacks MPTCP sysctl, so
the listener smoke cannot prove behavior with MPTCP enabled in the kernel.
No Android device/SIM, TUN/FakeDNS or long-duration acceptance was obtained.
This operation changed no server/kernel settings or production publication.
Private logs: `C:/WhiteZia/private/releases/xray-android-debug-20261102`.

## Release 1.5.8.17 / versionCode 44: 1 November 2026

Production release 44 adds the categorized personal account, shared RUB balance,
topups and full wallet purchases, opt-in wallet renewal, invitations and paid
capacity up to six devices. Three devices are included; each additional slot
costs 50 RUB for the tariff period, with active-subscription upgrades preserving
expiration. Referral rewards are 40 RUB on the first qualifying tariff purchase
of at least 150 RUB, then 10% on later qualifying purchases, with seven-day holds.
See [account wallet](ACCOUNT_WALLET.md) for operation recovery and consent details.

- Package: `shop.whitezia.client`; channel: `production`; universal ARMv7/ARM64.
- Immutable Android source/tag: `5ba3f853bf828d32c87daecd754404fb4db861ca`,
  `v1.5.8.17`.
- APK: **45,674,648 bytes**, SHA-256
  `b0ab73b6394c9b464b74c158d2336e88945cbd76a5811d16000421e7a858db20`.
- Original signer SHA-256:
  `b7c42dd500f0cb183ac22242018a4f8be8d908f4258d5d89cc95514e95f1dde7`.
- [GitHub release](https://github.com/BigDaddy3334/WhiteZia/releases/tag/v1.5.8.17),
  ID `430438843`, contains the APK, third-party notices, source manifest and checksums.

All 502 JVM tests passed without failures, errors or skips. Full release Lint had
zero errors/fatal issues, 206 warnings and four hints. APK signature, package,
version, non-debuggable flag, 16 KiB ZIP alignment, pinned CottenDNS packaging and
wallet API DEX strings were verified. All fourteen packaged native libraries are
byte-identical to release 43. Synthetic TLS test keystores are absent from the APK.
The build input inventory remained unchanged throughout the build. No connected
phone/emulator was available; this acceptance does not include a device or SIM run.

Actual HTTPS downloads from primary OTA, reserve/site OTA and public GitHub all
matched the exact APK size and SHA-256 above. Both OTA manifests advertise code 44,
the original signer, the same notes, minimum version 0 and `mandatory=false`.
The primary bot's configured path/version identifies that same immutable APK.
Chrome desktop/mobile checks confirmed the website displays 1.5.8.17 and every
download link points to `/api/app/releases/android/44/apk`, without horizontal
overflow or runtime exceptions. Release broadcasts were disabled intentionally.
The tag-triggered Official Release preservation gate passed; its build/publish
job was skipped, retaining the accepted APK unchanged.

Source snapshot in the public release manifest:

| Component | Source revision |
| --- | --- |
| Android | `5ba3f853bf828d32c87daecd754404fb4db861ca` |
| Core | `eafcccc7eba1cb1d733b1ed24cd815830a6d6e5d` |
| Panel | `78eaa2be08da7fd5c1a3056478da65be78fee75f` |
| Site | `a896aab01b7c45facccbfede8f099a8401ef8dd5` |

Android, Core and Site source checks passed on GitHub. All four repositories
passed local source/diff secret scans. Unfinished local Panel changes were
preserved and excluded. Site scanner-only commits do not change the published
account assets from `4a5a51d24600243b896a5a640161ed05b4fd0880`.
Core's deployed hotfix executable remains SHA-256
`096efe830eda40971bb8ab7995eb1ab05ef35e46cff4b7dc4173b585d8a8a1e6`,
from source `a4ebaa46ee80ce20080780151b13c46ae5617b8a`; later source formatting
does not require another backend deployment. Publication preserved unrelated
environment values and immutable release 43 on both origins. Recovery remains
read-only; writable failover is inactive, disabled and unguarded. Later docs-only
commits do not change the immutable release 44 tag or permit replacing its bytes.

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
Both direct exits are in Poland. Route identifiers distinguish nodes, not
countries. Per-network route-quality history was excluded from the requested
implementation and is not part of the released feature set.

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

## CDN HEADER experiments and profile delivery

The 26 October functional experiment used actual CDN endpoints on port 443 with
verified TLS and separate test paths/backends, not direct-origin connections.
Both CDN routes passed eight SHA-256 upload/download checks (8 KiB, 32 KiB,
256 KiB and 1 MiB in both directions), verified HTTPS and an oversized decoded
32769-byte request rejected with an origin **upstream 413**.

| Layer | Tested configuration |
| --- | --- |
| Client XHTTP | `mode: packet-up`, `uplinkHTTPMethod: GET`, `uplinkDataPlacement: header` |
| Client advanced fields | `uplinkChunkSize: "16384"`, `scMaxEachPostBytes: 32768`, `scMinPostsIntervalMs: 10` |
| Isolated receiver | `mode: auto`, `uplinkDataPlacement: auto`, `serverMaxHeaderBytes: 65536`, `scMaxEachPostBytes: 32768` |
| Origin Nginx | `client_header_buffer_size 16k; large_client_header_buffers 8 64k;` |

The two-route experiment was rolled back after testing. A later separate
72-hour manual pilot retained the normal service relay egress and had scheduled
expiry **28 October 20:36:08 UTC / 29 October 01:36:08 Asia/Yekaterinburg**.
Its activation receipt is historical after that time; check the dated expiry
audit before treating its service, test link or Nginx child path as current.
The owner-specific expiry helper checks configuration ownership before rollback
and preserves unrelated changes. Keep its journal, UUID and relay configuration
private. Do not blindly re-run the old dated launch scripts or redistribute the
expired link.

A read-only expiry audit on **31 October 2026** confirmed that the pilot service
and timer were inactive and disabled, the pilot process was absent, its UUID
configuration and owned Nginx include were deleted, port 18443 was closed, the
original Nginx file hash was restored and `nginx -t` passed. Production Xray and
Nginx were active. The stored expiry receipt had reported
`production-pid-or-state-changed` at its strict historical identity check: the
Nginx master PID had changed. The fresh audit confirms the cleanup state; it does
not assert that the original master PID survived until 31 October. The expired
pilot link must not be described as active or silently reissued.

These experiments did not change production customer profiles or the release
APK. The synthetic GET/body control timed out, but saved CDN profiles already
used GET/header with a 4096-byte packet cap. It is not a regression comparison
against current production clients. VPS success does not establish mobile-SIM
usability, long-duration stability or a throughput improvement. The negative
boundary request was HTTP/1.1; origin log protocol describes CDN-to-origin, not
the entire client-to-CDN-to-origin connection.

In the reviewed Core source, `internal/provisioner/xray_delivery.go` still sets
managed CDN packets to 4096 bytes; `scripts/whitezia-xray-client.py` also defaults
to 4096. A production migration therefore requires profile-delivery changes,
not only server buffer changes. Keep direct-route settings outside that change.
Prepare compatible server header limits before issuing larger client headers,
and audit older clients before lowering any shared receiver upload cap.

Android 43 preserves arbitrary XHTTP `extra` fields, so these client parameters
do not by themselves require a new APK. Put `uplinkChunkSize` inside `extra`;
query-only input is not consumed by WhiteZia's parser. Keep duplicate query and
`extra` values consistent: the parser and native core have different precedence
boundaries, and a stale `extra` value can defeat a query-only edit.

For a signed-in installation, a user-initiated connection refreshes the managed
device bundle and prefers it over a manually imported pilot. If the refresh
fails, the last validated local profile remains available. Running tunnels keep
their existing launch snapshot until reconnect. Standalone `vless://` is not a
main-screen import format; use an Xray-only `stormbundle://` in a separate
unauthenticated debug installation or a separate compatible VLESS client for
manual testing. Preserve the main installation's account and profile.

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
was verified as the latest stable release on 26 October, ID `423579918`. Its four
assets matched the release manifest. Actual public APK downloads from GitHub, primary OTA and reserve/site
OTA all produced the exact size and hash above. Both OTA manifests exposed version
43, the same certificate and notes, minimum version 0 and `mandatory=false`.
Primary and site public health checks passed in that acceptance; reserve remained
read-only recovery.
The site's dynamic metadata/download contract passed; a separate browser-rendered
visual check was not part of this acceptance.

At publication, the bot's configured version/path pointed to this same APK. A
read-only delivery audit observed 45 sent notifications and 18 unavailable chats
(12 blocked, 6 not found);
the audit itself sent no messages. The Android build passed 493 JVM tests with no
failures, errors or skips. Release lint reported no errors/fatal issues, 203
warnings and 2 hints. The tag-triggered Official Release workflow passed its
preservation gate and skipped rebuilding/replacing the published artifact.

The CottenDNS server executable verified in that acceptance had SHA-256
`60157ed5d007577a6c4be5e46f1848aeea7634bb7691781d9f24e6f625c6bb0b`.
Normal remains the default; Max remains explicitly experimental. UDP/53 and
TCP/53 listeners and the integration/accounting services were present. The
read-only comparison matched all 33 eligible Core DNS assignments to configured
clients. Fresh Normal probes from a separate server passed through both public
UDP/53 and TCP/53, returning an HTTPS 200 response with verified TLS. Existing
keys, configuration, processes and Max limits remained unchanged. Local TCP
probes on the DNS relay are not equivalent: its existing outbound firewall
restricts TCP/53 by service identity. This acceptance is a server-network test;
it is not a fresh phone TUN or mobile-SIM test.

A later documentation-only commit does not change the APK, its immutable Android
source/tag revision, or the four source revisions recorded in its release manifest.

### Read-only status check: 31 October 2026

Primary and reserve OTA metadata both returned HTTP 200 over verified HTTPS,
advertising `1.5.8.16` / `43`, the same 45,643,800-byte APK, SHA-256 and original
signing certificate recorded above, with minimum version 0 and `mandatory=false`.
This check fetched metadata only. Full APK byte/signature and GitHub/bot delivery
verification remain the dated 26 October publication acceptance. The local
Android source defaults also remain `1.5.8.16` / `43`; documentation changes do
not create or publish a new application version.

## Procedure for the next production Android release

Use the verification/publication sequence below as a checklist, with a newly
chosen version and versionCode greater than 44 and new release notes. Both 43
and 44 already identify immutable published APK bytes and tags. The old numeric
commands in their dated records are reproduction history, not the next command.
Freeze and verify the current native manifest (including Xray 26.10.31), preserve
the original signing key, run release tests/lint/package checks, then publish the
same accepted APK across primary, reserve/site, bot and GitHub as authorized.
Keep recovery read-only and source-only pushes separate from publication.

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
