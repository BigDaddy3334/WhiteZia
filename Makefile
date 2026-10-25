SHELL := /bin/bash

SDK_ROOT ?= $(or $(ANDROID_HOME),$(HOME)/Android/Sdk)
NDK_ROOT ?= $(SDK_ROOT)/ndk/29.0.14206865
GO_BIN ?= go
GRADLE ?= ./gradlew
COTTENDNS_SOURCE_DIR ?= $(CURDIR)/third_party/CottenDNS

.PHONY: all debug cotten check-cotten clean clean-app debug-outputs

all: debug

# Normal Gradle builds consume verified bundled runtimes, without fetching/building an engine.
debug: check-cotten
	$(GRADLE) :app:assembleDebug

# Both ARM ABIs are staged/verified together before either installed ABI is replaced.
cotten:
	COTTENDNS_SOURCE_DIR="$(COTTENDNS_SOURCE_DIR)" NDK_ROOT="$(NDK_ROOT)" GO_BIN="$(GO_BIN)" bash scripts/build-cotten-android.sh

check-cotten:
	python3 -m unittest discover -s scripts/tests -p test_cotten_package.py
	python3 scripts/check-cotten-package.py

debug-outputs:
	@find app/build/outputs/apk/debug -type f -name "*.apk" -print | sort

clean: clean-app

clean-app:
	$(GRADLE) :app:clean
