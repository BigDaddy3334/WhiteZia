# CottenDNS Android engine migration

The Android DNS channel defaults to CottenDNS Normal at immutable commit
`b3d20fe7fdeba8f157e94223c99cae33116207bf`. It is an external executable named
`libcottendns_client.so`, packaged for ARM64 and ARMv7. `CottenDnsEngineContract`
owns the executable name, reviewed revision and shared runtime/scanner CLI.
Android never falls back to an old StormDNS executable when CottenDNS is absent.
Native build provenance and per-ABI checksums belong in the CottenDNS build
metadata beside `jniLibs`; packaging verification must compare these with the APK.

The reviewed source/toolchain pin is `native-dependencies/cotten.json`. Build from
a separate clean Git root at that revision, using Bash 4+ and `flock` under
Linux/WSL (or an equivalently equipped macOS environment):

```bash
COTTENDNS_SOURCE_DIR=/path/to/clean/CottenDNS \
GO_BIN=/path/to/go1.27.3/bin/go \
NDK_ROOT=/path/to/android-sdk/ndk/29.0.14206865 \
  bash scripts/build-cotten-android.sh
```

The wrapper rejects a parent Android Git checkout masquerading as an engine
checkout, dirty/staged/untracked source changes, a different HEAD, or a different
Go/NDK/API pin. It runs the upstream Android build for ARM64 and ARMv7 in a fresh
ignored staging directory, verifies both ELFs and embedded Go/VCS metadata, then
copies and hashes all prospective install files before replacing either ABI.
Each replacement is atomic and ordinary installation errors restore the previous
CottenDNS files. `cotten-build-info.json` is installed with the same operation.
Interrupted installations must pass the package check before app building or
publication. This wrapper never removes old StormDNS files; their retirement is
a separate reviewed migration step after preserving the previous artifacts.

Metadata uses these fields:

```json
{
  "source_revision": "b3d20fe7fdeba8f157e94223c99cae33116207bf",
  "source_repository": "https://github.com/WhiteDNS/CottenDNS.git",
  "go_version": "1.27.3",
  "android_api": 26,
  "ndk_version": "29.0.14206865",
  "runtimes": {
    "arm64-v8a": {"binary_sha256": "<SHA-256>"},
    "armeabi-v7a": {"binary_sha256": "<SHA-256>"}
  }
}
```

The wrapper also records the source tree, build flags, architecture, sizes,
minimum LOAD alignment and stack flags. `scripts/check-cotten-package.py` checks
these package properties without fetching/building the engine. Pass `--apk PATH`
to compare actual ZIP entries with the pinned metadata; duplicate entries, an old
StormDNS executable, a wrong ABI, altered hashes, executable stack, or alignment
below 16 KiB fail verification. CI, Make and publication guards use this checker.
The root `THIRD_PARTY_NOTICES.md` and packaged
`app/src/main/assets/THIRD_PARTY_NOTICES.md` must both retain the CottenDNS MIT
credit and full license text, the reviewed source revision, and existing
third-party credits. Updating the root notice alone does not update the APK.
The `--apk` guard checks the actual packaged notice asset for the CottenDNS
copyright, permission and warranty clauses and the current source pin; absent,
duplicate, stale or oversized notice assets fail verification.
Engine source-pin/executable changes are reviewed through the
`allow-cottendns-upstream` PR label. No source-stage/index mutation is performed by
the build script.

## Normal configuration

Both runtime and scanner TOML explicitly select:

```toml
TUNNEL_MODE = "legacy"
LEGACY_SESSION_ID = false
CONFIG_PRESET = "default"
RESOLVER_IP_MODE = "auto"
RESOLVER_TRANSPORT = "auto"
QUERY_TYPES = ["TXT"]
TERMINAL_UI = "plain"
STARTUP_MODE = "resolvers"
RESOLVER_DISCOVERY_ENABLED = false
```

Upstream calls the Normal engine `legacy`. This name does **not** enable the
one-byte legacy session framing: `LEGACY_SESSION_ID = false` selects native
framing. The source pin's `mode: normal` and `tunnel_mode: legacy` describe this
default, rather than removing Max code from the reviewed executable. The separate
explicit test mode below uses the same binary, revision and ABI hashes.
Resolver transport `auto` starts with UDP and can fall back to DNS over TCP when
the Normal engine finds no usable UDP resolver. This changes the resolver hop,
not Xray, XHTTP, TLS, CDN or the application's transport ordering.

Settings → CottenDNS → DNS transport now exposes **Auto / UDP / TCP**. Auto
remains the default for older preferences, runtime snapshots and advanced
profiles. The validated choice is persisted as `cotten_dns_resolver_transport`,
included in runtime snapshots as `cottenDnsResolverTransport`, and exported and
imported with advanced TOML as `RESOLVER_TRANSPORT`. Unsupported imported values
are rejected; unknown saved/runtime values safely resolve to Auto. Both Normal
and Max runtime and scan configurations honor the choice. Changing it during an
active DNS VPN requests the existing serialized reconfiguration; it does not
alter Xray or CDN settings. Operator defaults and subscription refresh preserve
the choice independently of the custom performance-settings switch.

Explicit UDP disables TCP resolver paths. Explicit TCP disables UDP resolver
paths. Max Auto probes both transports, while Normal Auto tries UDP first and
retries over TCP if the UDP fleet is unusable. The resolver entry supplies the
port (normally 53). These options control the client-to-resolver hop; a recursive
resolver can use a different transport to the authoritative server. Proving
TCP/53 server ingress therefore requires a direct authoritative endpoint test,
or server-side evidence for the recursive hop, in addition to client readiness.
This does not add application UDP support to CottenMax.

Normal startup completes MTU checks before opening SOCKS. The shared
`CottenDnsEngineContract.SocksStartupTimeoutMillis` gives VPN, local proxy and
server-trial startup a bounded 75 seconds, including a full UDP-to-TCP fallback
for the current small resolver fleet. Resolver benchmark restarts use the same
VPN startup path. This is an application cap, not a promise that arbitrary fleet
sizes/retry settings finish within it. Xray keeps its separate 15 second budget.
The shared awaiter uses monotonic elapsed time and checks cancellation, user
intent, native failure/exit and the overall deadline on every poll. Scan-only
workers expose no SOCKS listener and retain their separate scan lifecycle.

Automatic VPN preparation captures current physical-network DNS only when its
DNS candidate is reached, after the previous native runner has fully stopped and
the DNS network policy permits startup. It tries at most two isolated pools:
first up to four current physical DNS endpoints, then a public pool only if
startup or actual HTTP readiness fails. A native run never mixes physical and
public fallback endpoints; its adaptive MTU selection therefore cannot promote
a public resolver above the working physical pool. With no physical endpoints,
only the public pool is attempted. The public pool contains up to four eligible
cached/settings endpoints plus two existing Yandex fallbacks, or the existing six
Yandex addresses when it has no primary endpoints. Exact physical endpoints are
excluded from that public pool, including public IPs supplied by the network.

Network-scoped cached/settings endpoints (RFC1918, CGNAT, IPv6 ULA and link-local)
cannot replace current physical DNS. Automatic pools use individual IP endpoints
rather than CIDR expansion. Cached public winners cannot skip the physical-first
attempt, and optimization preserves the verified isolated pool without a live
cross-pool benchmark when physical DNS exists. Public-only benchmark/restore
behavior remains available when there is no physical DNS snapshot. Each native
pool has the shared bounded startup budget; transitions use the orchestrator's
awaited shutdown, cancellation and session ownership checks.

Automatic DNS readiness and ongoing liveness require at least two of the four
fixed HTTP probes, including a non-204 endpoint (validated Cloudflare trace or
Yandex HTTPS). A single connectivity 204 cannot publish a working AUTO DNS VPN.
Xray keeps its existing relay-egress criterion. Explicit custom DNS retains its
supplied resolver list, private/CIDR support and existing single-endpoint health
criterion in Normal; Max always requires the stronger content check. Custom DNS
skips automatic physical/cache selection. Proxy-only mode and
manual server trials keep their existing explicit resolver semantics and are not
covered by this VPN AUTO policy change. The policy uses runtime settings copies
and the existing resolver cache, without rewriting saved user settings/profiles
or adding network-quality history.

Runtime enables `FAST_CONNECT`; scan-only disables it to classify every input.
Scan TOML disables SOCKS/local DNS listeners and uses one MTU probe worker per
Android scan worker. Commands use the pinned engine's `-config` and `-resolvers`
flags; scanners append `-scan-only`. No interactive terminal is required.

The existing bounded MTU, duplication, compression, queue, timeout and worker
preferences still render into their compatible CottenDNS settings. App-owned
HTTP bridge, traffic warmup and routing helpers remain app behavior rather than
being passed as unsupported client TOML options. Imported legacy startup-mode
preferences and advanced TOML exports remain readable and stored, but Android
runtime always starts with `resolvers`; old-engine cached sessions are not used.
TOML credentials escape control characters so profile input cannot add engine
configuration assignments.

## Explicit CottenMax test mode

Android 1.5.8.13 adds **Normal / CottenMax (beta)** in Settings → CottenDNS.
The global client setting `cotten_dns_mode` stores `normal` or `cottenmax` and
defaults to Normal for missing or unrecognized old values. It is independent of
profile identity, credentials, route order and the custom-parameter switch.
Runtime snapshots carry `cottenDnsMode`, so native restart/network recovery use
the requested mode. Subscription refresh, Connect, advanced presets, resolver
pool retries and scan preparation preserve an explicit selection. Saving a mode
change while a DNS VPN is connected explicitly reconfigures that session; Xray
is unaffected. Settings drafts still require Save.

Max runtime and scanner both render `TUNNEL_MODE = "cottenmax"`, with the same
base domain/key and no client-side domain suffix rewrite. The upstream engine
owns the reserved Max wire suffix. Max requires an explicitly enabled compatible
server. Authentication/transfer failure fails that Max attempt; neither the app
nor the engine automatically selects Normal or substitutes credentials. The user
can explicitly select Normal to roll back.

The Max listener opens before authentication. VPN, proxy-only and server trials
therefore wait for `WD_PROGRESS phase=connected percent=100 mode=cottenmax` **and**
the local port, within the existing cancellable 75-second startup budget. At this
revision the marker follows authenticated warmup `Probe(32)` success or a
successful authenticated stream dial. The two
explicit native authentication/transfer failure messages fail startup promptly;
their endpoint/error details are not copied into the exception. Normal keeps its
existing port startup semantics. VPN then requires actual HTTP content health
(at least two successes, including a non-204 endpoint), even with custom resolvers,
and retains that criterion in liveness/restarts. Proxy-only readiness uses the
authenticated native path gate, including configurations with local SOCKS
password authentication; it does not claim a separate app HTTP probe. Server
trials still measure real traffic after the gate. Scan events and shared byte
statistics retain the existing parser format.

JVM tests exercise early-listener gating, authenticated progress, sanitized auth
and transfer failures, bounded waits, cancellation/Stop/process exit, mode copies,
runtime serialization, imports/presets and resolver pools. Device fixtures use
isolated settings namespaces to test Save, settings-UI state recreation and
Normal→Max→Normal persistence without editing real user profiles. They do not
establish mobile Max connectivity; that needs separate app/server acceptance.

## Compatibility boundary

Historical Kotlin `StormDns*` names are retained where they describe shared
models, adapters or parser APIs. They do not select the old engine. Persisted
preferences, `stormdns_candidates`, `active_stormdns_node_id`, profile JSON,
`stormdns://` / `stormbundle://` schemes, launch-request keys, and app-owned
`stormdns/runtime` / `stormdns/scan` paths keep their existing names. The engine
replacement does not require clearing saved profiles, credentials or accounts.
An old link name does not authorize silently selecting legacy session framing.
The managed CottenDNS server must accept native framing for these credentials.
External old servers which only implement one-byte framing are outside this
Native-only integration; there is no guessed protocol downgrade.

`WD_PROGRESS`, `WD_RESOLVERS`, `WD_SCAN` and Normal traffic statistics retain the
existing parser contract. Additional scanner events (`started`, `testing`,
`path`) are ignored; `valid`, `rejected` and final authoritative totals are used.
Tests cover the extended statistics suffix and IPv6 resolver endpoints.

## Network and rollout scope

The priority remains direct Xray, then CDN Xray, then CottenDNS. This migration
inherits the current mobile DNS fallback policy: the DNS VPN path waits for Wi-Fi
to be inactive. CottenDNS support for a network transport does not imply that the
Android product now offers DNS fallback over Wi-Fi. Changing that policy requires
separate network/lifecycle acceptance. There is no route-quality history by
network in this change.

The APK and managed server must be accepted together: verify native ABI hashes,
Normal/native framing, existing account credentials, resolver scan, SOCKS traffic,
VPN TUN traffic, stop/reconnect and background recovery. Unit contract tests do
not establish device connectivity. The isolated device preference test uses a UUID-owned preference namespace under
the target UID, guarded by a ContextWrapper so applicationContext and legacy file
migration cannot access the user's real preferences. Cleanup removes only that
fixture namespace.

The reviewed dependency scan reported conditional `golang.org/x/net` HTTP/2
advisories in the upstream dependency graph. This pin is not a claim that the
whole dependency graph has no known advisories. The Android configuration fixes
resolver transport to `auto` (UDP/TCP), and the reviewed resolver parser accepts
IP/CIDR endpoints with numeric ports, rejecting HTTPS URLs; this path does not
enable DoH/DoT. Reassess reachability and update the affected dependencies before
offering encrypted DNS transports. No dependency overlay is applied in this
migration, so the clean upstream source revision remains reproducible.

### Audited Max admission patch (2026-10-23)

The immutable b3d20fe7 source pin remains the upstream base. The separate
`source_patches` entries in `native-dependencies/cotten.json` record the exact
reviewed patch path and SHA-256. The native builder applies these patches to an
isolated Git clone, verifies the resulting index tree, and requires the binaries
to report `vcs.modified=true`. The base checkout remains clean. Packaged build
metadata records both source trees, the patch list and the patched build version;
the package guard also requires the patch attribution in APK notices.

The current client patch changes only local CottenMax TCP stream admission.
It admits at most 24 active streams (or a smaller configured `MAX_ACTIVE_STREAMS`)
and 64 FIFO waiters. Four separate active slots and eight waiters are reserved
for explicitly marked application health checks; ordinary Google/Cloudflare
connections remain regular traffic. The total is at most 28 streams, leaving
headroom under the unchanged server limit of 32. Queue waiting and the authenticated SDK dial share one
30-second deadline. Cancellation removes a waiter; Android/Linux/macOS also
detect queued TCP peer EOF without consuming pipelined application bytes.
Release is idempotent and does not change server quotas, Normal behavior, Max's
wire format, credentials or cryptography. The existing managed server's separate
32-stream experimental limit remains unchanged, with headroom for probes,
control and delayed stream close acknowledgements.

Admitted idle application flows are retained. If all 24 regular slots remain idle and
open, a new connection can wait at most 30 seconds before receiving a local SOCKS
failure. There is no silent idle-flow eviction. Closed flows release their
slots; transient browser bursts queue instead of oversubscribing the server.

The reviewed local laboratory uses the actual patched native SOCKS process and
an unchanged server cap of 32, verifies complete byte-exact 32 KiB HTTP bodies
for 64 concurrent requests, and checks bounded waiting with 24 live idle flows.
This laboratory does not establish performance on every mobile network or
signed-in application behavior.

Health checks use the local SOCKS destination suffix `.whitezia-health.invalid`
only for HTTPS port 443 and exactly `connectivitycheck.gstatic.com`,
`www.google.com`, `www.cloudflare.com`, and `ya.ru`. The native client validates
and strips this suffix before Max dialing; TLS/SNI/HTTP retain the original URL
host. An unrecognized marker or non-443 port is refused locally. Normal/Xray
and speed requests never use this marker. Configured stream caps below 28 are
respected: health gets at most four and half the total, regular traffic gets
the remainder. A custom cap of one shares its single slot and cannot guarantee
independent health progress while that slot remains occupied.

The reviewed managed-server half-close candidate is ELF SHA-256
`52a64bd43344d062ee745ed44a07dd34bf0b5002418d81be4d9e5e55f706f972`,
reconstructed from 412 reviewed source files (source manifest SHA-256
`e22e6ebb567b32531edb8c77e5f7a41d5fd5fbad3611d6e9640a740c6c086196`).
Its Max-only target wrapper forwards TCP CloseWrite through the existing
tracked/accounted connection. A client FIN reaches the destination while its
response remains readable; closed idle SOCKS streams release their slots.
Normal wrappers, account keys, quotas and the public-only egress policy are
unchanged. Full Go tests, target race tests, all-package vet and a second
independent reconstruction/build passed; both server builds are byte identical.
Actual patched-client SOCKS tests verify 64 complete response bodies, retained
24 idle application streams with bounded regular waiting, four simultaneous
marked health responses, local rejection of invalid markers, and recovery
after closing the owned idle connections. Production rollout evidence is
recorded separately from this build contract.
