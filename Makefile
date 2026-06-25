SHELL := /bin/bash

SDK_ROOT ?= $(HOME)/Library/Android/sdk
NDK_VERSION ?= 29.0.14206865
NDK_ROOT ?= $(SDK_ROOT)/ndk/$(NDK_VERSION)
NDK_HOST ?= darwin-x86_64
NDK_BIN := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(NDK_HOST)/bin
ANDROID_API ?= 26

GO ?= go
GRADLE ?= ./gradlew
STORMDNS_DIR := third_party/StormDNS
STORMDNS_CMD := ./cmd/client
STORMDNS_BUILD_DIR := $(STORMDNS_DIR)/build/android
JNI_LIBS_DIR := app/src/main/jniLibs
GO_CACHE := $(STORMDNS_DIR)/.gocache
STORMDNS_LDFLAGS := -s -w -linkmode external -extldflags "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

.PHONY: all debug stormdns stormdns-arm64 stormdns-armv7 stormdns-x86_64 stormdns-x86 clean clean-stormdns clean-app check-ndk debug-outputs

all: debug

debug: stormdns
	$(GRADLE) :app:assembleDebug

stormdns: stormdns-arm64 stormdns-armv7 stormdns-x86_64 stormdns-x86

check-ndk:
	@test -x "$(NDK_BIN)/aarch64-linux-android$(ANDROID_API)-clang" || (echo "Android NDK not found at $(NDK_ROOT). Install NDK $(NDK_VERSION) or set NDK_ROOT=/path/to/ndk."; exit 1)

stormdns-arm64: check-ndk
	@mkdir -p "$(STORMDNS_BUILD_DIR)/arm64-v8a" "$(JNI_LIBS_DIR)/arm64-v8a" "$(GO_CACHE)"
	cd "$(STORMDNS_DIR)" && GOCACHE="$$PWD/.gocache" CGO_ENABLED=1 CC="$(NDK_BIN)/aarch64-linux-android$(ANDROID_API)-clang" GOOS=android GOARCH=arm64 $(GO) build -trimpath -ldflags='$(STORMDNS_LDFLAGS)' -o "build/android/arm64-v8a/stormdns-client" "$(STORMDNS_CMD)"
	cp "$(STORMDNS_BUILD_DIR)/arm64-v8a/stormdns-client" "$(JNI_LIBS_DIR)/arm64-v8a/libstormdns_client.so"
	chmod 755 "$(STORMDNS_BUILD_DIR)/arm64-v8a/stormdns-client" "$(JNI_LIBS_DIR)/arm64-v8a/libstormdns_client.so"

stormdns-armv7: check-ndk
	@mkdir -p "$(STORMDNS_BUILD_DIR)/armeabi-v7a" "$(JNI_LIBS_DIR)/armeabi-v7a" "$(GO_CACHE)"
	cd "$(STORMDNS_DIR)" && GOCACHE="$$PWD/.gocache" CGO_ENABLED=1 CC="$(NDK_BIN)/armv7a-linux-androideabi$(ANDROID_API)-clang" GOOS=android GOARCH=arm GOARM=7 $(GO) build -trimpath -ldflags='$(STORMDNS_LDFLAGS)' -o "build/android/armeabi-v7a/stormdns-client" "$(STORMDNS_CMD)"
	cp "$(STORMDNS_BUILD_DIR)/armeabi-v7a/stormdns-client" "$(JNI_LIBS_DIR)/armeabi-v7a/libstormdns_client.so"
	chmod 755 "$(STORMDNS_BUILD_DIR)/armeabi-v7a/stormdns-client" "$(JNI_LIBS_DIR)/armeabi-v7a/libstormdns_client.so"

stormdns-x86_64: check-ndk
	@mkdir -p "$(STORMDNS_BUILD_DIR)/x86_64" "$(JNI_LIBS_DIR)/x86_64" "$(GO_CACHE)"
	cd "$(STORMDNS_DIR)" && GOCACHE="$$PWD/.gocache" CGO_ENABLED=1 CC="$(NDK_BIN)/x86_64-linux-android$(ANDROID_API)-clang" GOOS=android GOARCH=amd64 $(GO) build -trimpath -ldflags='$(STORMDNS_LDFLAGS)' -o "build/android/x86_64/stormdns-client" "$(STORMDNS_CMD)"
	cp "$(STORMDNS_BUILD_DIR)/x86_64/stormdns-client" "$(JNI_LIBS_DIR)/x86_64/libstormdns_client.so"
	chmod 755 "$(STORMDNS_BUILD_DIR)/x86_64/stormdns-client" "$(JNI_LIBS_DIR)/x86_64/libstormdns_client.so"

stormdns-x86: check-ndk
	@mkdir -p "$(STORMDNS_BUILD_DIR)/x86" "$(JNI_LIBS_DIR)/x86" "$(GO_CACHE)"
	cd "$(STORMDNS_DIR)" && GOCACHE="$$PWD/.gocache" CGO_ENABLED=1 CC="$(NDK_BIN)/i686-linux-android$(ANDROID_API)-clang" GOOS=android GOARCH=386 $(GO) build -trimpath -ldflags='$(STORMDNS_LDFLAGS)' -o "build/android/x86/stormdns-client" "$(STORMDNS_CMD)"
	cp "$(STORMDNS_BUILD_DIR)/x86/stormdns-client" "$(JNI_LIBS_DIR)/x86/libstormdns_client.so"
	chmod 755 "$(STORMDNS_BUILD_DIR)/x86/stormdns-client" "$(JNI_LIBS_DIR)/x86/libstormdns_client.so"

debug-outputs:
	@find app/build/outputs/apk/debug -type f -name '*.apk' -print | sort

clean: clean-app clean-stormdns

clean-app:
	$(GRADLE) :app:clean

clean-stormdns:
	rm -rf "$(STORMDNS_BUILD_DIR)" "$(GO_CACHE)"
