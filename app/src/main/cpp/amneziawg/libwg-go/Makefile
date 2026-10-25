# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.

BUILDDIR ?= $(CURDIR)/build
DESTDIR ?= $(CURDIR)/out

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

comma := ,
CLANG_FLAGS := --target=$(TARGET) --sysroot=$(SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(subst -mthumb,-marm,$(CFLAGS))
export CGO_LDFLAGS := $(CLANG_FLAGS) $(patsubst -Wl$(comma)--build-id=%,-Wl$(comma)--build-id=none,$(LDFLAGS)) -Wl,-soname=libwg-go.so -Wl,-z,max-page-size=16384
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

GO_VERSION := 1.27.3
# Optional shared Linux toolchain on the native filesystem; default stays build-local.
TOOLCHAIN_DIR ?= $(if $(WHITEZIA_WG_GO_TOOLCHAIN),$(WHITEZIA_WG_GO_TOOLCHAIN),$(BUILDDIR)/go-$(GO_VERSION))
GO_PLATFORM := $(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m))
GO_TARBALL := go$(GO_VERSION).$(GO_PLATFORM).tar.gz
GO_HASH_darwin-amd64 := b6f8e931e4ab497fcde6da8ae4499a58f9d258e55c8a80824a516eae5950a750
GO_HASH_darwin-arm64 := dd3908cf20959d798c1e06ebe5b72213f0505ce01373b40ce3110975a6cd99c2
GO_HASH_linux-amd64 := 65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40

default: $(DESTDIR)/libwg-go.so

$(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL):
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	curl -o "$@.tmp" "https://dl.google.com/go/$(GO_TARBALL)" && \
	echo "$(GO_HASH_$(GO_PLATFORM))  $@.tmp" | sha256sum -c && \
	mv "$@.tmp" "$@"'

$(TOOLCHAIN_DIR)/.prepared: $(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL)
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	tar -C "$(dir $@)" --strip-components=1 -xzf "$^" && \
	patch -p1 -f -N -r- -d "$(dir $@)" < goruntime-boottime-over-monotonic.diff && \
	touch "$@"'

$(DESTDIR)/libwg-go.so: export PATH := $(TOOLCHAIN_DIR)/bin/:$(PATH)
$(DESTDIR)/libwg-go.so: $(TOOLCHAIN_DIR)/.prepared go.mod go.sum Makefile $(wildcard *.go)
	test "$$(GOTOOLCHAIN=local go env GOVERSION)" = "go$(GO_VERSION)"
	GOTOOLCHAIN=local go build -tags linux -ldflags="-X github.com/amnezia-vpn/amneziawg-go/v3/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/amneziawg -buildid=" -v -trimpath -buildvcs=false -o "$@" -buildmode c-shared

.DELETE_ON_ERROR:
