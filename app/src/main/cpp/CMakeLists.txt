cmake_minimum_required(VERSION 3.22.1)
project("WhiteDNSNative")

add_custom_target(libwg-go.so
    WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg/libwg-go"
    COMMENT "Building AmneziaWG userspace backend"
    VERBATIM
    COMMAND "${ANDROID_HOST_PREBUILTS}/bin/make"
        ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
        ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
        GRADLE_USER_HOME=${GRADLE_USER_HOME}
        CC=${CMAKE_C_COMPILER}
        CFLAGS=${CMAKE_C_FLAGS}
        LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}
        SYSROOT=${CMAKE_SYSROOT}
        TARGET=${CMAKE_C_COMPILER_TARGET}
        DESTDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        BUILDDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../generated-src/amneziawg
)
