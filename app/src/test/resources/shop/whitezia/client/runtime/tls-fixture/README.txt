Dedicated generated test-only TLS credentials.

localhost-server.p12 contains only a synthetic localhost leaf private key
and its certificate chain (SAN localhost, EKU serverAuth). Its public
test-only password is whitezia-http-test-fixture. test-ca.crt is the public
fixture CA certificate. The CA private key is NOT included. Neither file is
a production/signing credential, and neither belongs in main/ or an APK.

OwnedHttpTransportTlsTest uses the normal TrustManagerFactory with only
this CA; it does not use trust-all. All traffic is an owned loopback SOCKS
socket. Trusted localhost must pass TLS/SNI/HTTP, while wrong-host.invalid
must fail hostname verification with the identical trusted chain.

These fixtures were generated separately under work/cotten-migration-20261022/
http-tls-fixture. Normal transport: 2/2 PASS. Isolated identity-off control:
wrong-host test fails (nothing thrown), while localhost remains PASS.
