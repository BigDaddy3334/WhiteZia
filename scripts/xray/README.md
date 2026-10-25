# Pinned Xray dependency overlay

Core version remains `v26.7.28`, commit
`5ca6f4b7d4dc20a881d4330e498892697627ec0c`. The overlay changes only `go.mod`
and `go.sum`; it does not change XHTTP configuration, transport flags, routing
rules or the core source. `../build-xray-android.sh` verifies the exact base,
patch checksum and resulting dependency files, and requires Go `1.27.3` with
automatic toolchain switching disabled.

The explicit update roots are `golang.org/x/net v0.60.0`,
`google.golang.org/grpc v1.83.2` and `github.com/klauspost/compress v1.18.7`.
Go module selection also raises crypto to `v0.57.0`, sys to `v0.48.0`, text to
`v0.42.0`, sync to `v0.23.0`, mod to `v0.41.0`, tools to `v0.49.0`, and the
gRPC RPC definitions to `v0.0.0-20260526163538-3dc84a4a5aaa`. These exact
versions are part of the checked overlay.

The current VLESS/XHTTP client calls `golang.org/x/net/http2.Transport`.
[GO-2026-6611](https://pkg.go.dev/vuln/GO-2026-6611) affects both HTTP/2 clients
and servers; Go `1.27.3` updates the standard library, while `x/net v0.60.0`
updates Xray's separately imported HTTP/2 implementation. The other HTTP/2,
gRPC and compression advisory fixes are retained in the dependency overlay so
the full packaged core does not retain their affected module versions.

Binary scans of stripped Go executables may conservatively list advisory
symbols for packages that the target does not import. Such a one-frame trace
does not prove reachability. Exact Android import analysis of the base found
no `x/crypto/ssh`, `x/crypto/openpgp`, `x/text/secure/precis`, `compress/s2`, or
gRPC xDS server/RBAC imports. Deprecated OpenPGP has no fixed release but is
not part of the imported Android executable. The configured XHTTP client also
does not run gRPC or an HTTP/2 server. Recheck both the exact ELF and the source
import graph when changing the core, build flags or transport configuration.

Official release/module references:

- [x/net v0.60.0 dependency requirements](https://github.com/golang/net/blob/v0.60.0/go.mod)
- [gRPC v1.83.2](https://github.com/grpc/grpc-go/releases/tag/v1.83.2)
- [compress v1.18.7](https://github.com/klauspost/compress/releases/tag/v1.18.7)

Reproduction starts from a clean checkout at the commit above. Run the build
script once for each ARM ABI. A repeat build accepts only this exact overlay.
Then run the XHTTP, TLS, VLESS encoding and configuration tests, inspect the
ELF interpreter/page alignment and build metadata, and perform XHTTP traffic
checks on an isolated stand. An Android device is still required for TUN,
FakeDNS, network changes, Doze and OEM background restrictions.
