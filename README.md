<p align="center">
  <img src="docs/assets/whitezia-logo.jpg" width="128" alt="WhiteZia logo">
</p>

# WhiteZia Android

Android client for the WhiteZia subscription service.

Current app version: `1.5.8.2` (`versionCode` 29).

Official production builds are distributed through the WhiteZia Telegram bot,
the in-app updater, and GitHub Releases. Public APKs are universal ARM builds
for `arm64-v8a` and `armeabi-v7a`.
Source code and release notes are published on GitHub:

https://github.com/BigDaddy3334/WhiteZia

The app is not published on Google Play. APKs from other stores or third-party
mirrors are not official.

Telegram bot backup channel for subscriptions and app downloads: [@whitezia](https://t.me/whitezia)

## What The App Does

The app includes an email-based personal account. It stores the rotating refresh session with Android Keystore, shows the subscription, devices, tariffs, and payments, and opens Platega checkout in a Custom Tab. The current installation enrolls into one of the account's device slots; when provisioning finishes, the app downloads its owner-only bundle and applies it without exposing the raw profile in the UI.

Telegram remains a backup account, payment, iOS configuration, and app download
channel. Android profiles are enrolled and applied inside the app and are not
shown by the bot. Existing Telegram-only users require an explicit account-link
migration before the same subscription can be managed through email login.


Automatic mode uses the following ordered chain when the subscription contains
the corresponding profiles: AmneziaWG -> Xray -> StormDNS.

Current connection behavior:

- Automatic mode tries AmneziaWG first. Manual mode can instead start only
  Xray or force the StormDNS channel.
- If AmneziaWG is absent, cannot start, or fails its post-connection check,
  the app stops the old tunnel before considering Xray.
- Xray is attempted only when the bundle has a VLESS/Xray profile, Wi-Fi has
  no active internet connection, and a cellular network is available. It must
  pass the Xray health check before the app reports a successful connection.
- StormDNS is the final automatic fallback, and forced-DNS mode starts it
  directly. StormDNS also waits until Wi-Fi is inactive; the app never routes
  an automatic fallback through an active Wi-Fi connection.
- With no Xray profile, an AmneziaWG failure proceeds directly to StormDNS
  once Wi-Fi is off.
- For built-in and local resolver sets, the app benchmarks candidates on the
  first DNS fallback and again at the next DNS fallback after every 10 app
  launches. It reuses the cached winner between benchmarks. When custom
  resolvers are enabled, their entries are used as entered and never cached.
- Subscription import: supports `stormbundle://` links with AmneziaWG, VLESS,
  and StormDNS data, plus QR-code scanning.
- Logs: connection logs are preserved in order and shown in a scrollable log window.

## Main Features

- AmneziaWG tunnel support through Android `VpnService`.
- VLESS/Xray xHTTP support for automatic fallback and manual Xray-only mode.
- StormDNS tunnel for the final fallback and forced DNS mode.
- Resolver scan, cached winners, and periodic local-versus-Yandex benchmarks.
- Built-in fallback resolvers.
- QR scanner for subscription/profile import.
- Subscription link import.
- Email registration, verification, password recovery, account dashboard, device enrollment, and in-app payment flow.
- Visible connection optimization progress.
- Runtime logs, connection state, progress, and traffic statistics.
- Foreground VPN service notifications.
- Quick Settings tile.
- Jetpack Compose UI.

## Project Structure

```text
.
|-- app/
|   |-- build.gradle.kts
|   `-- src/main/
|       |-- AndroidManifest.xml
|       |-- java/shop/whitezia/client/
|       |   |-- MainActivity.kt
|       |   |-- QrScannerActivity.kt
|       |   |-- model/      # settings, subscription links, profile parsing
|       |   |-- account/    # email auth, Keystore session, billing and device enrollment
|       |   |-- fallback/   # automatic transport selection and health checks
|       |   |-- proxy/      # local proxy and HTTP bridge
|       |   |-- resolver/   # resolver benchmark policy and scheduling
|       |   |-- runtime/    # runtime state, logs, traffic, progress
|       |   |-- scan/       # resolver scan and optimization
|       |   |-- storm/      # StormDNS config and process management
|       |   |-- ui/         # Compose UI, view model, connect and settings screens
|       |   |-- vpn/        # Android VPN, AmneziaWG and tun2proxy
|       |   |-- xray/       # VLESS/Xray runtime and configuration
|       |   |-- controlplane/ # Restricted bootstrap transport for API access
|       |   `-- update/     # signed production update client
|       |-- jniLibs/        # packaged native binaries
|       `-- res/            # app resources
|-- third_party/
|   `-- StormDNS/
|-- docs/
|-- Makefile
`-- THIRD_PARTY_NOTICES.md
```

## Build

Requirements:

- JDK 17.
- Android SDK with `compileSdk = 36`.
- Android NDK `26.3.11579264` for the Gradle build.
- The supplied `Makefile` rebuilds StormDNS with its own default NDK
  `29.0.14206865` and macOS SDK layout. On Linux or another SDK layout,
  set `SDK_ROOT`, `NDK_ROOT`, and `NDK_HOST` when invoking `make`.
- Go matching `third_party/StormDNS/go.mod` if native StormDNS is rebuilt.

Run tests:

```bash
./gradlew testDebugUnitTest
```

Build a signed release APK:

```bash
WHITEZIA_RELEASE_PROPERTIES=/secure/path/release.properties \
WHITEZIA_BOOTSTRAP_PROPERTIES=/secure/path/bootstrap.properties \
  ./gradlew :app:assembleRelease
```

`bootstrap.properties` contains a restricted VLESS URI used only when the
account and update API cannot be reached directly. The corresponding server
credential must be limited to WhiteZia control-plane domains and must not
provide general internet access. See `bootstrap.properties.example`.

When the primary Core remains unavailable, a signed-in installation can make
one read-only request to `https://whitezia.su/api/recovery/device-bundle` using
its encrypted refresh token and installation identity. The endpoint can only
restore that device's current `stormbundle`; it cannot log in, take payments,
enroll devices, or change subscription state.

Build debug APK:

```bash
make debug
```

The debug build uses package `shop.whitezia.client.debug` and app label `WhiteZia Debug`, so it can be installed next to the release app.

## Releases And Signing

The current production build is `v1.5.8.2` (`versionCode` 29).

Release APKs are built from the Android `release` build type with minify and resource shrink enabled.

Production APKs are signed by a dedicated release key held outside this
repository. The release build fails when signing is not configured. Keep an
encrypted off-host backup of the keystore and its properties file: losing the
key prevents future in-place Android updates.

The production updater accepts only an HTTPS APK whose package ID,
version, size, SHA-256 digest, and signing certificate match the release
metadata. Debug builds use `shop.whitezia.client.debug` and do not query the
production update channel.

Publish a production release with:

```bash
WHITEZIA_CORE_SSH=root@core-host \
WHITEZIA_RELEASE_PROPERTIES=/secure/path/release.properties \
WHITEZIA_BOOTSTRAP_PROPERTIES=/secure/path/bootstrap.properties \
  scripts/publish-production-android.sh 29 1.5.8.2 release-notes/1.5.8.2.txt
```

The Telegram bot, OTA endpoint, and GitHub Releases publish the same universal
ARM APK containing both `arm64-v8a` and `armeabi-v7a` native runtimes.

## Third-Party Components

WhiteZia uses:

- StormDNS, based on the MasterDNS client lineage.
- AmneziaWG userspace/native components.
- Xray core for VLESS/xHTTP fallback.
- `tun2proxy` for VPN traffic handling.
- ZXing and CameraX for QR scanning.

See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for third-party license details.
