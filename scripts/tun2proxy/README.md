# Embedded tun2proxy

The ARM JNI libraries are built from tun2proxy 0.7.21, commit
`623d27b3a6b3875bd4b816c96004dcf3c867adec`, with `embedded-lifetime.patch`.
The patch removes the API shutdown watchdog that calls `process::exit(-1)`
after stopping a tunnel. Android owns the host process lifetime. CLI behavior
is unchanged. Invalid C API CLI arguments return an error instead of exiting.
The pinned ipstack 1.0.1 dependency also receives `ipstack-lifetime.patch`.
TCP stream destruction cancels its task without synchronously waiting for a
runtime that may already be shutting down. Tokio still joins its worker threads
before the JNI runner returns; no unfinished runtime is leaked.

The embedded DNS map keeps issued fake addresses for the lifetime of its TUN
reader. It refuses new names with DNS SERVFAIL at 32,768 mappings rather than
evicting an address cached by a browser or reusing it for another hostname.
AAAA, HTTPS and SVCB queries receive NOERROR/NODATA because the Android VPN
currently carries IPv4. A records retain the upstream five-second DNS TTL.

The original JNI `run` entry keeps normal UDP forwarding. `runWithOptions` adds
an explicit UDP capability: a TCP-only CottenMax reader rejects non-DNS IPv4
UDP locally with ICMP port-unreachable so QUIC clients can fall back to TCP.
Virtual DNS remains local; Normal and Xray keep UDP forwarding enabled. Reader
reuse must include the UDP capability in its configuration identity.

Requirements: Rust 1.94.0 with `aarch64-linux-android` and
`armv7-linux-androideabi`, Android NDK 26.3.11579264, a host C linker, and
`patch` supporting `--fuzz=0` and `--dry-run`.
Check out the pinned commit into a separate source directory. The script
accepts only the exact patch, uses the committed Cargo.lock, and builds the
library with 16 KiB ELF alignment and a deterministic build timestamp. The
ipstack archive is SHA-256 verified before the dependency patch is applied.
The dependency patch is applied without parent Git repository discovery and
verified in reverse; a positive API check rejects silently skipped patches.

```bash
TUN2PROXY_SOURCE=/path/to/tun2proxy \
ANDROID_NDK_HOME=/path/to/android-ndk \
  bash scripts/build-tun2proxy-android.sh arm64-v8a
TUN2PROXY_SOURCE=/path/to/tun2proxy \
ANDROID_NDK_HOME=/path/to/android-ndk \
  bash scripts/build-tun2proxy-android.sh armeabi-v7a
```

Verify both JNI exports and copy the outputs into their `app/src/main/jniLibs`
directories. Update `tun2proxy-build-info.json` with the actual SHA-256 hashes.
Run `VpnShutdownTest` on a phone before distributing an APK. It checks both
terminal service state and historical process exits, including delayed native
crashes after the final STOP. A service restarting successfully after a crash
is not a passing shutdown test.
