# Third-Party Notices

## CottenDNS Normal engine

- Source: https://github.com/WhiteDNS/CottenDNS
- Reviewed revision: `b3d20fe7fdeba8f157e94223c99cae33116207bf`
- Runtime selection: Normal, with native session framing; CottenMax is not selected.
- License: MIT, including the preserved MasterDnsVPN and StormDNS derivative-work attribution below.
- Only the CottenDNS Go engine is integrated. No WhiteDNS Android application source is included.

The pinned engine retains optional transport code in its source and executable.
Its Soar, QUIC, QPACK and embedded-data notices are preserved here even though
WhiteZia does not select CottenMax. Exact upstream notice/license files are also
retained under `native-dependencies/licenses/` for source audit.

```text
Notices and Attribution

CottenDns is an independent fork / derivative of StormDNS, which is itself an
independent fork / derivative of MasterDnsVPN.

Original project:
- MasterDnsVPN
- Original author: Amin Mahmoudi
- License: MIT License

StormDNS modifications (upstream fork):
- Maintained by: NullRoute1970
- License: MIT License

CottenDns modifications (this repository):
- Maintained by: tajirax
- License: MIT License

This project preserves the MIT License terms of the original project and adds
independent modifications under the same license.

MIT License

Original work:
Copyright (c) 2026 Amin Mahmoudi

Upstream modifications and derivative work:
Copyright (c) 2026 NullRoute1970

Modifications and derivative work:
Copyright (c) 2026 tajirax

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

```

### CottenDNS upstream third-party notices

# Third-party transport

The experimental CottenMax adapter uses a source copy derived from
github.com/getlantern/soar v0.2.3
(commit a8af533d82284f5e6ab13b706552596c108bacf2), developed by the Lantern
team and distributed under the Apache License 2.0. Its license is included in
SOAR_LICENSE.txt. Source: https://github.com/getlantern/soar/tree/v0.2.3

The transport copy is in internal/cottenmax/engine. CottenDNS modifications
are documented in that directory's NOTICE.cottendns.txt and marked in modified
source files. They adjust recovery/scheduling and adapter integration; the
wire format and cryptography retain upstream's implementation. The separately
pinned module remains an independent test and benchmark comparator.

CottenMax is a CottenDNS integration of that transport, not an independent
implementation of Soar's protocol or cryptography. The existing CottenDNS
engine and project lineage retain their existing license and attribution.

Soar's bundled resolver/range data attribution is retained below and in
internal/cottenmax/engine/data/THIRD_PARTY.md.
CottenMax exposes Soar's authenticated resolver discovery as an explicit opt-in.
Bundled discovery and range sampling remain disabled by default.

# Third-party data

`resolvers/ir.txt` and `ranges/ir.txt` are copied unmodified from KevinNet DNS
(https://github.com/kamalalhagh/kevinnet-dns.git, commit a331430, 2026-05-26).
`resolvers/ir.txt` is KevinNet's snapshot of the range-scout WhiteDNS list; `ranges/ir.txt` aggregates RIPE/IRINN allocations.

KevinNet DNS is distributed under the MIT License:

```
MIT License

Copyright (c) 2026 Kevin Haji

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

# Optional QUIC resolver transports

DNS-over-QUIC and DNS-over-HTTPS/HTTP3 use github.com/quic-go/quic-go v0.63.0
under the MIT License, copyright 2016 the quic-go authors & Google, Inc.
Its complete license is included in QUIC_GO_LICENSE.txt.
Source: https://github.com/quic-go/quic-go/tree/v0.63.0

HTTP3 header compression uses github.com/quic-go/qpack v0.6.0 under the MIT
License, copyright 2019 Marten Seemann. Its complete license is included in
QPACK_LICENSE.txt. Source: https://github.com/quic-go/qpack/tree/v0.6.0

These resolver transports are optional and do not replace CottenDNS/Max's
domain, server credential or end-to-end tunnel protocol.


### CottenDNS embedded Soar data attribution

# Third-party data

`resolvers/ir.txt` and `ranges/ir.txt` are copied unmodified from KevinNet DNS
(https://github.com/kamalalhagh/kevinnet-dns.git, commit a331430, 2026-05-26).
`resolvers/ir.txt` is KevinNet's snapshot of the range-scout WhiteDNS list; `ranges/ir.txt` aggregates RIPE/IRINN allocations.

KevinNet DNS is distributed under the MIT License:

```
MIT License

Copyright (c) 2026 Kevin Haji

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```


### Soar transport license

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

```

### Soar transport derivative-work notice

```text
# CottenDNS transport copy

This directory is derived from Lantern's `github.com/getlantern/soar` v0.2.3,
commit `a8af533d82284f5e6ab13b706552596c108bacf2`, under Apache-2.0. The
original license, specification, and embedded data notices are retained.
Wire vectors and tests remain local validation material and are excluded from
the beta source publication. The upstream command, benchmark, and data generator
are excluded.
The package name remains `soar` to keep the changes easy to compare upstream.

CottenDNS modifications (updated 2026-10-21):

- `client.go`: held polls retain their hold-aware deadlines even when later
  polls arrive first; immediate data queries retain fast loss recovery with
  the RTO's variance margin and a minimum 50ms allowance for reordering.
  Automatic transport selection probes UDP and TCP independently from startup;
  both paths carry live streams with independent authentication, sizing,
  congestion and recovery state. Short startup authentication probes do not
  become idle beacons. Statistics distinguish observed wire sizes from limits.
  Failed QNAME-size discovery is retried with exponential backoff (10s to 1m)
  on existing traffic so a replacement forwarder can regain capacity; short
  queries remain the fallback and only an authenticated padded probe promotes it.
  Authenticated late answers/hellos refresh the path RTT after latency changes
  without double-counting delivery or congestion credit; invalid replies cannot.
  The optional QNameLossGuard experiment (off by default) keeps the larger
  QNAME cap only with authenticated near-cap proof fresher than one path RTO;
  smaller/forged replies do not suppress safe-size fallback. Loss and
  congestion accounting remain active.
  Optional size/window/idle limits and encrypted-query padding are configured by
  the surrounding adapter; defaults retain the existing fast path.
- `dnswire.go`: optional label-length/count shaping with exact separator budget
  accounting. Defaults retain ordinary Soar label splitting; parsing remains
  compatible with the independent upstream implementation.
- `discover.go`: an optional address-family predicate applies to every candidate
  source, including cached/system/bundled/sampled endpoints. The adapter adds
  bounded, expiring per-domain/profile cache hints; paths reauthenticate.
  Discovery budgets count distinct resolvers, allowing both UDP/TCP paths per
  candidate without double-counting the requested resolver diversity.
- `session.go`: an in-flight packet owns its ACK ranges. They are removed from
  the pending ACK queue on send and requeued on loss, avoiding redundant ACK
  frames in every query and upload starvation with short names.
- `roundtripper.go`: `DialConfirmed` exposes the existing OPENED wait directly
  for local SOCKS/HTTP proxies.
- `tcp.go`: cancel an in-progress TCP/TLS resolver dial when its client/path closes.
  Back off accept-then-close loops until the connection proves authenticated
  tunnel progress; a previously proven connection can reconnect immediately.
  Bound concurrent resolver TCP/TLS handshakes independently of UDP startup.
  Replace half-open sockets after repeated actual query timeouts and sustained
  lack of authenticated progress. Close the captured socket outside the client
  lock; account old-query losses and discard stale queued messages before redial
  resumes that path. Retain congestion/loss history while clearing old suspension.
- `server.go`: preserve the UDP amplification budget through CottenDNS's
  reply-address wrapper. Stream transports remain exempt, as upstream. Disable
  upstream's environment-controlled plaintext packet trace; app settings use TOML.
- `vectors_test.go`: normalize Windows checkout line endings. The JSON vector
  content and wire encoding are unchanged.
- `cottenmax_*_test.go`: recovery, ACK ownership, wrapped-source budget and
  bidirectional compatibility tests against the independently pinned module,
  mid-session TCP fallback and dial cancellation.

The surrounding client adapter raises the adaptive congestion-window ceiling
from 32 to 256 queries, with a default global limit of 256. Optional bounded
settings expose these ceilings. Initial windows remain small; delivery grows
them and consecutive losses reduce them. Recovery and shaping add no tunnel
handshake or new cryptographic primitive. Optional TLS/QUIC resolver transports
have their own connection setup. The adapter's multi-domain failover uses
independent authenticated sessions and does not migrate existing streams. See
docs/COTTENMAX_OPTIONS.md in the source checkout.

Capacity extension, 2026-10-21: client/server stream ceilings can reach 512
when explicitly enabled by the CottenDNS server. The bounded control queue is
2048 entries, retaining at least one quarter of its capacity as headroom for
the three control frames per admitted stream. Existing server admission
defaults remain 256 and all flow-control, aggregate stream and byte limits
remain in force. The wire encoding and cryptography are unchanged.

Production code imports this copy. The upstream module in go.mod is retained
only for independent interoperability tests and the tagged performance
comparison. Do not replace that comparator with this package.

```

### QUIC resolver transport license

```text
MIT License

Copyright (c) 2016 the quic-go authors & Google, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

```

### QPACK license

```text
Copyright 2019 Marten Seemann

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

```

## MasterDNS Client / MasterDnsVPN

Source: https://github.com/masterking32/MasterDnsVPN
License: MIT

MIT License

Copyright (c) 2026 Amin Mahmoudi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## StormDNS

Source: https://github.com/nullroute1970/StormDNS
License: MIT

MIT License

Copyright (c) 2026 Amin Mahmoudi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## AmneziaWG Android

Source: https://github.com/amnezia-vpn/amneziawg-android
License: Apache-2.0
Included components: Android configuration/parser classes, Java backend bridge, and native userspace backend wrapper.

Copyright (c) 2016 Southern Storm Software, Pty Ltd.
Copyright (c) 2017-2023 WireGuard LLC. All Rights Reserved.
Copyright (c) 2017-2022 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this software except in compliance with the License.
You may obtain a copy of the License at:

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## amneziawg-go

Source: https://github.com/amnezia-vpn/amneziawg-go
Version: v0.2.18
License: MIT

Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## tun2proxy

Source: https://github.com/tun2proxy/tun2proxy
Release: v0.7.21
Asset: tun2proxy-android-libs.zip
SHA-256: 25fe0fb6c853cbb8b1c0c58db8eb9b3f9901336d3511edec5650651c1144c11e
License: MIT

MIT License

Copyright (c) @ssrlive, B. Blechschmidt and contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## WhiteZia audited CottenDNS client patch (2026-10-23)

The immutable upstream base remains
b3d20fe7fdeba8f157e94223c99cae33116207bf. WhiteZia applies
`native-dependencies/patches/cottenmax-client-admission.patch`
(SHA-256: 43c78802d185eb4591aa62a59e39bfccc5eed8b53b7293f87bf4c7d9bf1ded1c).
This local modification bounds CottenMax client stream admission and queue
cancellation. Normal behavior, the Max wire protocol and cryptography are
unchanged. Patched builds report modified source and retain the original MIT
and Apache 2.0 license and attribution notices above.

## WhiteZia audited tun2proxy patches (2026-10-23)

The tun2proxy base remains commit
623d27b3a6b3875bd4b816c96004dcf3c867adec; ipstack remains 1.0.1.
`scripts/tun2proxy/embedded-lifetime.patch` has SHA-256
b48a899986c387a6b51327258dd5f6d6afc0aec9a2dbaf79aaa571835cb8ff5f.
`scripts/tun2proxy/ipstack-lifetime.patch` has SHA-256
e2481ad0abc5dc6ec1883971202df60726b0858a3244354131e062fd2b0a0798.
The reviewed modifications preserve bounded FakeDNS identities for the reader
lifetime, answer unsupported DNS types with NODATA, and optionally reject
application UDP with an ICMP response for the TCP-only CottenMax mode. Existing
upstream licenses and attribution remain unchanged.
