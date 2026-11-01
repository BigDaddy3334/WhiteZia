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
The debug package is `shop.whitezia.client.debug`; production release 43 stays
immutable and is not replaced by this work.
