plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val whiteZiaVersionCode = providers.gradleProperty("WHITEZIA_VERSION_CODE")
    .map { it.toInt() }
    .orElse(18)
val whiteZiaVersionName = providers.gradleProperty("WHITEZIA_VERSION_NAME")
    .orElse("1.5.7")

android {
    namespace = "shop.whitezia.client"
    compileSdk = 36
    ndkVersion = "26.3.11579264"

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    defaultConfig {
        applicationId = "shop.whitezia.client"
        minSdk = 26
        targetSdk = 34
        versionCode = whiteZiaVersionCode.get()
        versionName = whiteZiaVersionName.get()

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            externalNativeBuild {
                cmake {
                    targets("libwg-go.so")
                    arguments(
                        "-DANDROID_PACKAGE_NAME=shop.whitezia.client.debug",
                        "-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}",
                    )
                }
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            externalNativeBuild {
                cmake {
                    targets("libwg-go.so")
                    arguments(
                        "-DANDROID_PACKAGE_NAME=shop.whitezia.client",
                        "-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}",
                    )
                }
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    val cameraXVersion = "1.4.2"

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.zxing:core:3.5.3")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
