# Android architecture

This describes the local refactoring of the 1.5.8.9 source tree and the later
1.5.8.11 release candidate. The initial refactor preserved stored preference
names, JSON formats, package IDs, native binaries and signing keys. The security
work described below deliberately rebuilds Xray and AmneziaWG; the public data
formats, package IDs, signing identity and existing tun2proxy fixes remain.

## Connection flow

`MainActivity` renders the current state and forwards actions to
`ConnectionUiCoordinator`. The coordinator refreshes a managed profile, prepares
the configuration, requests VPN consent and starts the runtime. Profile replacement
uses the same confirmed shutdown boundary as mode changes. Imported external
links require confirmation; the exported launcher cannot authorize a tile connect
command. `TileConnectActivity` is non-exported and issues a process-local one-shot
request to the existing main Activity.

`SubscriptionConnectionPreparer` is a pure settings transformation. It combines
the complete imported transport snapshot with user preferences, cached resolvers
and operator defaults. An omitted transport does not inherit credentials from a
previous subscription. Saved manual profiles remain available for explicit
selection, but do not become candidates of the new bundle.

`WhiteZiaVpnService` owns the TUN and native runtime. `VpnCandidatePlanner` creates
the direct Xray → CDN Xray → CottenDNS candidate chain. `XrayRoutePreflight` checks
reachability without treating it as tunnel authentication.
`VpnConnectionOrchestrator` serializes startup and confirmed cleanup through
`VpnRuntimeOwnership`. The service monitors physical network changes and tunnel
liveness independently of the Activity. AWG import compatibility remains, but the
current candidate planner never starts AWG.

## Responsibilities and files

| Responsibility | Implementation |
|---|---|
| User settings and persisted models | `model/WhiteZiaSettings.kt`, `ConnectionModels.kt`, `ScanModels.kt` |
| Profile selection and normalization | `ProfileSelection.kt`, `SettingsNormalization.kt`, `SettingsResolution.kt` |
| Profile operations | `ConnectionProfileOperations.kt`, `ResolverProfileOperations.kt`, `AdvancedProfileOperations.kt` |
| Transport import and preparation | `WhiteZiaProfileLinks.kt`, `SubscriptionProfileManager.kt`, `SubscriptionConnectionPreparer.kt` |
| DNS discovery, registry and address policy | `resolver/DnsResolverDiscovery.kt` |
| Resolver benchmark/cache | `ServiceResolverCoordinator`, `ResolverCacheStore` |
| Scan preparation/import/resume/result synchronization | `ui/ScanUiController.kt` |
| Parallel server diagnostics | `ui/ServerTestController.kt` |
| Connection state presentation and runtime observation | `ui/WhiteZiaViewModel.kt` |
| Legacy tab shell and feature UI | `ui/WhiteZiaScreen.kt`, `WhiteZia*Content.kt`, feature-specific dialogs and fields |
| External native processes | `runtime/ExternalProcessRunner.kt`, Xray/CottenDNS manager adapters |
| HTTP bridge sockets | `proxy/ProxyBridgeSession.kt`, `HttpProxyBridge.kt` |
| Cross-process launch/state files | `RuntimeLaunchRequestStore`, `WhiteZiaRuntimeStateStore`, `RuntimeFileLock` |
| Requested VPN session across process death | `VpnDesiredSessionStore` |
| Account session and device identity | `AccountSessionManager`, `AccountRepository`, `SecureAccountStore` |
| APK verification and installation | `update/AppUpdateManager.kt` |

Scan and server-test controllers use the ViewModel's lifecycle scope and explicit
state read/write callbacks. They own their jobs and test processes, rather than
sharing mutable runtime ownership. Scan results are applied to the latest settings
after IO finishes, so background refresh does not restore a stale settings snapshot.
Controller delegates are declared before the ViewModel init block: constructor-time
scan refresh must be able to access them. The device startup regression test creates
and clears two ViewModels using persisted settings.

While presenting a connected runtime, the ViewModel checks live runtime health
every five statistics ticks and on Activity resume. A missing healthy runtime clears
the connected indication without sending a user Stop. A later live sticky restart
can still restore the UI; process death must not leave a stale green status.

The shared external-process runner owns startup, termination, output draining and
config cleanup. JNI tun2proxy retains its separate thread/owner-token mechanism;
it is not an external process and must not be managed by that runner.

The VPN service uses a sticky restart, recovering a null system Intent only from
the explicit desired-session marker. Client Stop clears the marker before sending
the service command; stale commands and late readiness are fenced by session ID.
Revoke and terminal startup failure clear the marker, while ordinary process
destruction leaves it available for system recreation. The active VPN launch
snapshot is exempt from normal age/count pruning. This preserves the last session's
settings instead of reconstructing a different connection from current preferences.

The DNS engine mode is a global client preference, independent of profile keys:
Normal is the backward-compatible default and CottenMax is an explicit beta
choice. Runtime snapshots carry the choice across service/native recovery.
Neither profile refresh nor resolver fallback changes Max into Normal. Max's
early SOCKS listener cannot publish readiness: all startup callers first require
the native authenticated-transfer progress marker, then the VPN requires actual
HTTP content health. The shared cancellable 75-second DNS budget and session
guards remain in force. Xray configuration, ordering and 15-second startup budget
do not depend on this mode.

`VpnRuntimeBinding` holds an application-context `BIND_AUTO_CREATE` connection in
the main process while a desired VPN session exists. It uses the same service
interface/filter as Android's VPN client. On the tested Samsung Android 13 device,
this prevented loss of the service registration when the system unbound a dead
TUN, and allowed a fresh VPN process to restore tunneled HTTPS. The non-exported
`VpnBindingReleaseReceiver` releases the binding after notification Stop, revoke
or terminal failure. It rechecks the desired marker so delayed cancellation cannot
unbind a newer session. The binding does not request a permanent wake lock.
Its additional protection depends on the main process surviving; simultaneous
loss of both processes and package force-stop are not covered by that result.

On OnePlus, process death can leave only that binding: Android creates a new
service but no longer delivers a sticky start. `onBind` therefore requests an
explicit foreground restore for a previously Ready desired session and its
persisted launch snapshot. It does not create new connection intent or rewrite
settings. Repeated restoration commands do not restart an already-starting reader;
Stop and newer session IDs reject delayed restoration. Publishing the recovery
status holds the desired-session lock so an old binding cannot overwrite a newer
session's state. A rejected background foreground-service dispatch preserves user
intent but records a failed status instead of the old Ready state.

During route changes and network recovery, native transport cleanup retains
the TUN descriptor and its compatible tun2proxy reader. The reader owns the
virtual DNS map, so retaining only the descriptor would leave cached synthetic
addresses without their domain mappings. Connections fail closed while the
upstream SOCKS proxy is being replaced; virtual DNS identities remain available.
Compatibility includes MTU, split-tunnel applications, SOCKS host/port and auth.
If the reader has exited or any of these settings change, stop it before calling
`Builder.establish()` for a new Android VPN/DNS session, then close the old
descriptor. A fresh reader must never reuse the old Android network's DNS cache.
Reader callbacks are bound to the current TUN and startup attempt, including
when a retained reader serves a newer attempt. Superseded callbacks cannot
recover a replaced reader. Cancellation of a superseded startup retains the
reader/TUN when a connection is still requested. Explicit Stop, permission
revocation and service destruction release it. A native runner that cannot stop
remains a terminal boundary for starting another runner; recovery must not close
the retained TUN just to bypass that boundary. This does not cover process death,
initial connection before any TUN exists, or applications excluded by split tunneling.

October 17 regression on OnePlus PKX110/Android 16 verified unchanged synthetic
DNS addresses and successful HTTPS to those cached addresses across three
upstream reconfigurations. An eight-second cellular outage retained the VPN
network and DNS map, failed closed while offline, and recovered cached-address
HTTPS after connectivity returned (6.6 seconds to readiness). Reconfiguration
with the screen off also restored HTTPS and anonymous Telegram MTProto replies.
These are short recovery checks, not an extended Doze or authenticated Reels test.
The instrumented retained-reader test seeds DNS in a single shell process because
the app UID is excluded from its own VPN and its intentionally unreachable local
upstream fixture has a short health-check budget. A separate test changes split
tunneling twice and requires a fresh VPN network for each new reader. All nine
device tests and 337 JVM tests passed with the repair.

Network loss and capabilities callbacks can acquire a bounded recovery wake lock
before handing work to the polling loop. Battery optimization/Doze restrictions and
vendor process management still require testing on a physical device.
When a physical network returns during an unfinished startup, recovery cancels that
startup and runs the candidate chain again. Otherwise an offline attempt waiting
for CottenDNS/mobile data can prevent retrying Xray after Wi-Fi returns.

Xray readiness and liveness require two successful HTTP endpoints, including
Google's exact 204 response or a Cloudflare trace body containing an IP address.
A regional site alone must not mark a partly broken relay as connected. CottenDNS
keeps its single-endpoint acceptance policy. Native exit callbacks also check
the current routing attempt identity, since consecutive readers may share a TUN.

Readiness, liveness and speed probes use `OwnedHttpTransport` rather than platform
`HttpURLConnection` SOCKS handling. Each call owns its raw socket before the SOCKS
greeting, performs a domain-name SOCKS5 CONNECT without local DNS, and verifies
HTTPS hostnames with SNI. One monotonic deadline covers connect, SOCKS, TLS,
redirects and body reads; cancellation closes the raw socket first, avoiding a
TLS wrapper read-lock. Coroutine cancellation is propagated through health and
speed callers. HTTP workers and the direct-mode DNS resolver pool are bounded
with no task queue. Redirects, headers, chunks, trailers, body transfer and body
capture are capped. Malformed/truncated bodies cannot pass health policy; speed
measurements may report bytes received before a read failure as partial results.
The loopback tests cover stalled handshakes, slow responses, deadline sharing,
cancel-before-dispatch, and cancellation of all four health requests together.

`ProxyStartupAwaiter` shares cancellable monotonic startup waiting across VPN,
local CottenDNS proxy and server trials. CottenDNS has a 75 second application
cap so a full UDP MTU fleet can fall back to TCP before the app tears it down;
Xray retains 15 seconds. A Stop, superseded session, native exit or reported
startup failure interrupts the wait rather than consuming the remaining cap.

Each HTTP listener generation owns accepted and upstream sockets, including
unfinished SOCKS handshakes. Stop closes all owned sockets. The client count is
bounded. Duration/deadline calculations in the runtime Stop awaiter use a monotonic
clock; persisted timestamps still use epoch time.

## Validation

JVM tests cover import/selection, coordinator cancellation/shutdown, actual
localhost HTTP/SOCKS connections, external child-process lifecycle, session
rotation and file serialization. Tests of the retired fallback planner were removed;
the actual VPN planner/orchestrator are the source of connection-policy coverage.

On this Windows machine, use `C:\WhiteZia\Build-Android.ps1` for Linux-dependent
tests/builds. For an explicit check:

```powershell
& C:\WhiteZia\Build-Android.ps1 ':app:testDebugUnitTest' ':app:lintDebug' ':app:assembleDebug'
```

Do not infer device behavior from JVM tests alone. Release validation still needs
Wi-Fi/cellular, background recovery, rapid Stop/Start, mode switching, tile and
external import, account logout/removal, split tunneling, updater cancellation and
ARM32/ARM64 checks on physical Android devices.

On 2026-10-12, Samsung SM-G780G / Android 13 / ARM64 passed seven device tests:
five native startup/shutdown tests, one ViewModel recreation test and one binding
cancellation/release test. Physical scenarios confirmed network recovery after
70 seconds offline, forced Doze with/without a battery exemption, explicit Stop
without resurrection, and VPN-process death recovery after the binding change.
The ten-minute screen-off observation began during recovery and included ADB
diagnostics; it is not proof of ten uninterrupted healthy minutes. Doze and
network-return observations preceded the final binding addition. Repeating the
whole physical matrix on the final build requires a reachable uplink: later direct
HTTPS probes failed with connection resets even without an active VPN.

## Remaining boundaries

On 2026-10-16, the final debug build (1.5.8.9 / 36) passed 337 JVM tests and eight
individual device tests on OnePlus PKX110 / Android 16 / ARM64. The added device
test confirms that transport reconfiguration preserves the registered VPN network
and final Stop releases it. Both direct and both original CDN profiles passed
28 HTTPS GETs, four 256 KiB uploads and four UDP/STUN checks on Yota LTE after the
second origin's relay TLS repair. The APK SHA-256 was
`9cb42ba2a996199190dc2d9098883e1b8d30b95fa6dddffcd2f82ab2fbf0ad9c`.

With the app on Home and exempt from battery optimization, cellular return
restored verified relay egress in 8.6 seconds. All 160 connectivity samples
retained the same VPN network, including route switching and an unreachable
route negative control; that control blocked an explicit-IP Internet request.
A reachable-TLS/invalid-XHTTP-path control was rejected and fell back to CDN in
9.6 seconds. During forced deep Doze, a 45-second quiet interval preserved
traffic, and cellular return restored relay egress in 10.8 seconds. Forced idle
and the temporary keep-screen-on setting were restored afterwards. USB remained
connected and the display was not locked; long screen-off/OEM sleep still needs
separate validation. This final debug instance contains the phone's four Xray
routes as a standalone profile; its account login and DNS key were not copied.

Default K2 lint failed inside AndroidX Compose's
`ConfigurationScreenWidthHeightDetector` / Kotlin UAST. A separate run with
`-Dlint.use.fir.uast=false` completed with zero errors, 219 warnings and two hints.
Retain the failed K2 log when investigating the toolchain; the fallback run is
not evidence that the K2 analyzer issue was repaired.

The runtime-observation ViewModel and Android VPN service are still substantial.
Any further split should follow a separately tested lifecycle boundary; moving JNI
or cross-process ownership without device tests is unsafe. Registry HTTPS/signing,
dynamic signed runtime manifests, upstream native security auditing and server HA
are separate projects requiring their own contracts and infrastructure validation.


## October 21 operational diagnostics and connection presentation

Operational diagnostics are not credential exports. `DiagnosticPrivacy` removes
VLESS/CottenDNS bundle links, UUIDs, labelled tokens/passwords/keys, bearer tokens,
JWTs and known values from saved connection profiles. UI log ingestion, runtime
log/error sinks, runtime-state messages and final text exports apply the same
redactor. The diagnostics report no longer includes the CottenDNS encryption key.
Explicit user-requested profile export/import remains unchanged and retains the
credential needed for importing that profile. The redactor deliberately retains
route IDs, endpoint addresses, HTTP status and timings for useful troubleshooting.

Connection phases now distinguish preparation, native startup, checking proxy
internet access, waiting for a physical network, a required user action, and
recovery. Initial startup is not labelled as recovery. Terminal runtime failures
retain one sanitized reason in the UI instead of immediately erasing it. Session
identity checks and the runtime ownership mutex remain the authority for lifecycle
changes; presentation phases do not start or stop native readers.

A successful service HTTP readiness check records `SocksProxy` coverage. It does
not claim an independent test of Android TUN or virtual DNS. The application's
UID is excluded from its own VPN, so only a separate UID/device test can establish
that coverage. Restoring a ready runtime from its state file does not fabricate a
new health measurement. Standard diagnostics state this limitation explicitly.

Platform TLS preflight remains a fast, bounded reachability signal. It uses a
different TLS implementation from native Xray, so a single failure no longer
removes a route from native fallback. Reachable direct routes precede reachable
CDN; unconfirmed Xray candidates remain before CottenDNS. Manual transport choice,
CDN limits and single-reader ownership are preserved. No network quality history
or persistent network identifiers were added.

Device acceptance still required for these UI/runtime event changes:

- From a separate routed UID, seed Instagram/Telegram virtual DNS addresses and
  verify cached-address TLS after route change, short outage and Wi-Fi/mobile
  handover. Verify explicit Stop and permission revoke release the VPN.
- Observe prolonged screen-off/Doze and OEM power management on multiple Android
  versions. Check both successful recovery and partial connectivity (front-door
  TLS works while relay traffic fails), without permanent wake locks.
- Exercise Android always-on and lockdown with the actual app exclusions. Native
  transport relies on the excluded application UID; compatibility must be proved
  on devices before promising lockdown support. Excluded user apps can lose
  connectivity under lockdown. User force-stop requires opening the app again.
- With the account unavailable, use the saved valid profile; with a device
  explicitly unbound, require re-enrolment rather than using stale credentials.
- Verify preparation/check/recovery text and the sanitized terminal reason on the
  actual screen. SOCKS-only HTTP tests and JVM tests do not replace this checklist.

## Native security and engine provenance

Xray remains version 26.7.28 at commit
`5ca6f4b7d4dc20a881d4330e498892697627ec0c`. Both ARM executables are rebuilt with
Go 1.27.3, NDK 26.3.11579264/API 26 and the dependency-only security overlay in
`scripts/xray`. The exact overlay raises x/net to 0.60.0, gRPC to 1.83.2 and
compress to 1.18.7, plus their required transitive dependencies. Core transport
source, XHTTP flags and CDN settings remain unchanged. Provenance is recorded in
`app/src/main/jniLibs/xray-build-info.json`.

AmneziaWG keeps its pinned 3.1.20260814 module and is rebuilt with Go 1.27.3.
Its existing CLOCK_BOOTTIME runtime patch remains applied, so suspended time is
still counted by recovery timers. The shared-library link now requests 16KB page
alignment. Make dependencies include source files, module checksums and compiler
flags so an existing output cannot silently skip a changed native build recipe.

The DNS fallback now integrates the pinned CottenDNS Normal executable at
`b3d20fe7fdeba8f157e94223c99cae33116207bf`. Its external CLI, explicit native
framing, generated TOML, scanner telemetry and compatibility boundaries are
documented in [CottenDNS Android integration](COTTENDNS_ANDROID_INTEGRATION.md).
Old StormDNS profile/storage names remain readable; they do not select the old
engine. The mobile DNS fallback network policy is retained. In debug 40
(1.5.8.13, 2026-10-23), the existing tun2proxy binaries and their earlier
embedded-lifetime/FakeDNS-related fixes were preserved. Debug 41 changes that
native boundary as described below.

The final candidate APK verification records every packaged native ELF's LOAD
alignment, non-executable stack, hashes and Go build metadata. It also verifies
the production signing certificate and matches packaged CottenDNS executables
with the reviewed source/build provenance. XHTTP/TLS/VLESS/configuration source tests pass with
existing geodata fixtures and only upstream `TestECHDial` excluded: its live
encryptedsni.com DNS fixture fails identically on the clean pinned baseline.
This exclusion does not cover an active client feature; current profiles do not
enable ECH. The exact Linux Xray build also passes isolated direct-route traffic
and complete server-policy validation. These checks still do not cover Android
TUN, device background restrictions or mobile-network behavior.

## Debug 41: transport bootstrap and browser connectivity

The 2026-10-23 debug 41 candidate rebuilds `libtun2proxy.so` and
`libcottendns_client.so` for ARM64 and ARMv7 with the reviewed patches recorded
in their build metadata. Its APK acceptance must verify both ABIs and show that
all other packaged native ELF files match accepted debug 40. Xray's executable,
its source pin, XHTTP options and the saved direct/CDN links are unchanged.

The tun2proxy reader now retains issued virtual DNS mappings for its complete
lifetime. The map admits at most 32,768 names; once full, it returns SERVFAIL for
new names without evicting or reusing addresses already returned to applications.
An existing cached destination therefore cannot turn into a different hostname
when a browser reconnects after the old 60-second expiry. A replacement reader
still requires a replacement Android TUN/DNS network session. In this IPv4 VPN,
AAAA, HTTPS/SVCB and other unsupported query types receive NOERROR/NODATA rather
than an unrelated synthetic A record. This does not add IPv6 VPN support.

CottenMax has no SOCKS UDP ASSOCIATE capability. Only a DNS VPN using Max asks
the reader to reject non-DNS application UDP locally with IPv4 ICMP
port-unreachable, allowing applications to try TCP promptly. UDP DNS on port 53
continues to use the local virtual DNS responder. The reader's UDP capability
is part of its compatibility identity, so a Normal/Max mode change cannot reuse
an incompatible reader. Normal and Xray retain their existing application UDP
behavior; the DNS carrier's UDP/TCP transport settings are unchanged.

Every Xray process launch resolves hostname-based outer transport endpoints using the
selected physical Android `Network.getAllByName`, never process-wide system DNS.
Synthetic, private and other local DNS answers are rejected. Explicit literal IPs
in a saved URI bypass DNS and the reachability pre-probe, preserving intentional
LAN/loopback profiles and isolated device fixtures. The synthetic 198.18/15 pool
is always rejected, including explicitly supplied and IPv4-mapped literals. Bounded physical TCP
attempts select a reachable address, trying IPv4 first while retaining IPv6
fallback. The generated private runtime JSON pins the selected literal address
for the primary transport and any separate XHTTP download transport. A new
launch or recovery performs physical resolution again. It does not rewrite
profile credentials, saved links, TLS SNI, HTTP Host, XHTTP paths or CDN options,
and does not bind the whole Android process to a network.

For the pinned Xray source, XHTTP's HTTP authority is separate from its socket
destination: `splithttp.createHTTPClient` captures the destination passed to
`internet.DialSystem`. Its HTTP/1 and HTTP/2 dial callbacks ignore the URL's
address argument. Therefore newly opened XHTTP connections retain the runtime
literal socket address even after system DNS cache expiry; TLS continues to
verify the configured real server name.

The audited CottenDNS client patch bounds default Max admission to 24 regular
streams with 64 FIFO waiters and four separate health streams with eight waiters.
Queue waiting and authenticated dial share one 30-second deadline; cancellation
and closed queued peers release their owned work. Idle admitted application
flows are retained. With a lower configured stream cap, regular and health
budgets shrink to keep their total within that cap; a cap of one necessarily
shares its only slot. The default combined 28 streams remain below the existing
managed server limit of 32. Normal admission, the Max wire protocol, credentials
and cryptography are unchanged.

Only explicitly requested Max health probes use the private local SOCKS suffix
`.whitezia-health.invalid`, restricted to the four existing health hosts on port
443. The Max client validates and strips this marker before its authenticated
remote dial. Unknown markers or ports fail locally. Ordinary Google/Cloudflare
traffic and throughput tests use the regular admission budget. TLS hostname
verification, SNI and HTTP Host continue to use the original HTTPS URL. The
dedicated health budget prevents 24 idle application flows from blocking all
HTTP liveness probes and causing an unnecessary VPN restart. Normal and Xray
health requests do not carry this marker.

The complementary Max server adapter fix forwards TCP `CloseWrite` through its
accounting/revocation wrapper. A client upload FIN can then reach the upstream
while the remaining server response is still read and delivered completely.
This is a Max-only optional capability; Normal connections, account ownership,
public-only egress and server session/stream caps are preserved. Server rollout
and its evidence are tracked independently of Android APK acceptance.

Validation separates JVM protocol/TLS regressions, native admission/packet
fixtures and actual device traffic. The native admission fixture holds 24 regular
connections while all four marked health requests receive complete bodies; an
unmarked Cloudflare request still competes for a regular slot. Device acceptance
uses a separate routed shell UID, cached-destination requests after more than
60 seconds, A/AAAA/HTTPS queries, UDP rejection timing and complete byte-counted
TLS bodies. SOCKS-only health, compilation or native laboratory results alone
do not establish successful device TUN traffic, background recovery or signed-in
Instagram/Telegram behavior.
