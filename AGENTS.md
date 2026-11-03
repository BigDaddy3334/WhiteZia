# WhiteZia Android instructions

Updated 3 November 2026 (Asia/Yekaterinburg).

Preserve existing work; inspect Git status before editing. Read the README and
the linked feature/operations contract. Core is the source of truth for email
accounts, billing, device slots and provisioning. Keep credentials, private
profiles, raw diagnostics and signing material outside Git. A source push is
not a production deployment, APK release or permission to alter user data.

Read docs/PROJECT_OPERATIONS.md, docs/ANDROID_ARCHITECTURE.md and the relevant
ACCOUNT_WALLET/COTTENDNS integration contract. Release 1.5.8.17 / 44 and tag
v1.5.8.17 are immutable. Current ARM source has Xray 26.10.31 (accepted debug
5b4ff02); a new production artifact requires a code greater than 44 and the
original signer. Preserve the debug package shop.whitezia.client.debug.

Current order: reachable direct Xray → CDN Xray → CottenDNS Normal. Max is explicit
experimental, not a silent fallback. DNS Auto/UDP/TCP selects the resolver hop.
Preserve XHTTP/CDN settings, stormbundle/stormdns contracts and existing identity.
AWG import remains legacy; its issuance/planner activation stays suspended.
One service owns TUN/native lifecycle; keep session fencing and confirmed cleanup.
Preserve retained tun2proxy/FakeDNS identities only across compatible readers.
Actual authenticated content health, not a listening port, determines Connected.

qWDTT is planned in docs/QWDTT_INTEGRATION_PLAN.md, not implemented. Target:
automatic managed connection with a foreground captcha WebView when requested,
background notification, bounded waiting and stale-answer/Stop protection.
Resolve the documented license/auth/dependency boundaries before distribution.
Do not claim TCP-only upstream SOCKS provides application UDP or SIM acceptance.

Use Windows Environment.ps1 and Linux/WSL Environment.sh when present. Preserve
WHITEZIA_WG_GO_TOOLCHAIN=/opt/whitezia/tools/wg-go-1.27.3 for native reproduction.
Run applicable JVM/lint/package checks for code/native changes; docs-only work
does not require rebuilding an accepted APK. Check both ARM ABIs and 16 KiB
alignment on native changes. See scripts/check-source-secrets.py before commits.
