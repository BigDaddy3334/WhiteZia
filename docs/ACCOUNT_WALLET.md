# Account wallet and invitations in Android

The app uses the existing Core wallet API and the same email account as the
website. Categories are Subscription, Devices, Balance, Invitations, Payments;
the tab row scrolls horizontally on narrow screens. Subscription and device
cards offer both direct checkout and full wallet payment for the selected limit.
A separate confirmation displays the amount before a wallet purchase.

The balance screen contains deposits, available funds, recent ledger entries,
pending topup continuation and explicit automatic renewal consent. Amounts are
integer kopecks, input accepts a comma or dot and at most two decimal places;
topup limits come from Core. Pending topup continuation reads the existing ID
instead of creating another topup. On returning to the app the dashboard refreshes;
while the balance screen has a pending topup it also checks for confirmation up to
12 times at five-second intervals. An explicit refresh remains available.
Only provider confirmation can add funds. Debt and payment review are displayed,
and wallet spending requires the entire price to be available.

If provider checkout preparation failed, continuation retries the saved operation
with its original request key and verifies the same topup ID. A checkout started
in a different client with no locally saved key can be continued on the website;
the app never invents a fresh operation key to recover an unknown checkout.

Money request keys are saved synchronously in private preferences before sending,
scoped to the Core account and operation/selection/quote. The request store uses a
process-wide lock across repository instances. Failed/uncertain requests keep
their keys across logout and process recreation; successful purchases clear them
only after updated account data reaches the UI. Repeating an uncertain request
returns the existing Core operation. Authentication refresh preserves the request
payload, and session generations reject responses after logout.

Automatic renewal is off by default. Confirmation binds the subscription ID,
actual catalog period and full price including paid device places. Core remains
responsible for eligibility, price validation, retries and expiration. The app
shows paused/review/insufficient-balance states and allows disabling consent.
It never authorizes automatic bank-card charges or partial wallet payments.

Invitations show the shared link, share/copy controls, invited count and aggregate
pending/credited rewards. Published rules come from Core: first qualifying tariff
purchase of at least 150 RUB gives 40 RUB, subsequent qualifying tariff purchases
and renewals give 10%, held for seven days. Trials, topups and device-only orders
are excluded; refunds reverse rewards. New app registrations can paste a code or
the website invitation link; an existing account cannot change its referrer.

Core contract: `whitezia-core-work/docs/wallet-and-referrals.md`. Missing wallet
or referral data displays an independent retry state while the existing account
remains usable; authentication failures are propagated to session handling.

Validation commands (WSL, pinned native toolchain as in PROJECT_OPERATIONS):

```text
:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

`AccountWalletTest` covers exact amounts, debt/review, API parsing, renewal quotes,
referral rules/link parsing and persistent/concurrent request keys. The account
instrumentation fixture includes Balance and Invitations in both themes, without
real accounts or payments. Compiling that APK does not constitute a device run.
The debug package is `shop.whitezia.client.debug`. This feature was later
published in immutable production release 44, as recorded below. Current source
also includes the separate Xray 26.10.31 debug inputs; a later production APK
requires a new code above 44. See [project operations](PROJECT_OPERATIONS.md).


The following debug record predates production release 44 and remains historical.
qWDTT is a [planned transport](QWDTT_INTEGRATION_PLAN.md); it does not change
wallet/referral rules or consume an extra device slot on the same installation.

## Debug acceptance: 1 November 2026

Source: `354ea6d80de982147b6ec0f397e2d8ef86c85285`. All 502 JVM tests passed.
Full Android Lint passed with zero errors, 222 warnings and four hints; the
instrumentation APK compiled. No connected phone/emulator was available, so the
screen tests were not run on a device. No real payment or customer mutation was
used for acceptance. The website passed 19 tests and Chrome desktop/mobile checks.

The final universal ARM debug APK is 70,808,309 bytes, SHA-256
`62136fffb40ca910d79a4b3fcd267207c11473acd1e438cc9c77b8aa2623cdef`.
Package: `shop.whitezia.client.debug`, version 1.5.8.16 / 43. Signature Scheme v2,
the previous debug signer, and `zipalign -c -P 16 4` were verified. CottenDNS,
Xray, tun2proxy and wg-go libraries for both ABIs match the previous device-limit
debug APK byte for byte; the pinned wg-go toolchain was preserved. APK DEX markers
include the wallet API and saved-key topup recovery. Production release 43 was
not replaced. A separate production release remains outside this debug delivery.

## Production release: 1 November 2026

This functionality is now published in production **1.5.8.17 / versionCode 44**,
from immutable Android source `5ba3f853bf828d32c87daecd754404fb4db861ca`, signed
with the original production key. Primary OTA, the website, bot configuration and
GitHub distribute the same 45,674,648-byte APK, SHA-256
`b0ab73b6394c9b464b74c158d2336e88945cbd76a5811d16000421e7a858db20`.
All 502 JVM tests and full release Lint passed (zero errors). Device/SIM testing
was not performed. See [project operations](PROJECT_OPERATIONS.md) for provenance,
channel download verification and the distinction from the earlier debug build.
