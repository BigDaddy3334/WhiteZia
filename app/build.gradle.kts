import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val whiteZiaVersionCode = providers.gradleProperty("WHITEZIA_VERSION_CODE")
    .map { it.toInt() }
    .orElse(29)
val whiteZiaVersionName = providers.gradleProperty("WHITEZIA_VERSION_NAME")
    .orElse("1.5.8.2")

val releasePropertiesPath = providers.gradleProperty("WHITEZIA_RELEASE_PROPERTIES")
    .orElse(providers.environmentVariable("WHITEZIA_RELEASE_PROPERTIES"))
    .orElse("/home/biba/.whitezia/signing/release.properties")
val releaseProperties = Properties().apply {
    val propertiesFile = file(releasePropertiesPath.get())
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}
val releaseStoreFile = releaseProperties.getProperty("storeFile").orEmpty()
val releaseStorePassword = releaseProperties.getProperty("storePassword").orEmpty()
val releaseKeyAlias = releaseProperties.getProperty("keyAlias").orEmpty()
val releaseKeyPassword = releaseProperties.getProperty("keyPassword").orEmpty()
val releaseCertificateSha256 = releaseProperties.getProperty("certificateSha256").orEmpty().lowercase()
val bootstrapPropertiesPath = providers.gradleProperty("WHITEZIA_BOOTSTRAP_PROPERTIES")
    .orElse(providers.environmentVariable("WHITEZIA_BOOTSTRAP_PROPERTIES"))
    .orElse("/home/biba/.whitezia/bootstrap.properties")
val bootstrapProperties = Properties().apply {
    val propertiesFile = file(bootstrapPropertiesPath.get())
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}
val bootstrapXrayUri = bootstrapProperties.getProperty("bootstrapXrayUri").orEmpty().trim()
val releaseSigningConfigured =
    releaseStoreFile.isNotBlank() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank() &&
        releaseCertificateSha256.matches(Regex("[0-9a-f]{64}"))

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
        buildConfigField("String", "ACCOUNT_API_BASE", "\"https://whitezia.su/api\"")
        buildConfigField("String", "RECOVERY_API_BASE", "\"https://whitezia.su/api\"")
        buildConfigField(
            "String",
            "BOOTSTRAP_XRAY_URI",
            "\"${bootstrapXrayUri.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("whiteziaRelease") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "UPDATE_METADATA_URL", "\"\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"debug\"")
            buildConfigField("String", "UPDATE_APPLICATION_ID", "\"shop.whitezia.client.debug\"")
            buildConfigField("String", "UPDATE_CERTIFICATE_SHA256", "\"\"")
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
            signingConfig = signingConfigs.getByName("whiteziaRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "String",
                "UPDATE_METADATA_URL",
                "\"https://whitezia.su/api/app/releases/android\"",
            )
            buildConfigField("String", "UPDATE_CHANNEL", "\"production\"")
            buildConfigField("String", "UPDATE_APPLICATION_ID", "\"shop.whitezia.client\"")
            buildConfigField(
                "String",
                "UPDATE_CERTIFICATE_SHA256",
                "\"$releaseCertificateSha256\"",
            )
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
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf("**/x86/*.so", "**/x86_64/*.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.configureEach {
    if (name.startsWith("package") && name.endsWith("Release")) {
        doFirst {
            check(releaseSigningConfigured) {
                "Release signing is not configured. Set WHITEZIA_RELEASE_PROPERTIES."
            }
            check(bootstrapXrayUri.startsWith("vless://")) {
                "Bootstrap Xray is not configured. Set WHITEZIA_BOOTSTRAP_PROPERTIES."
            }
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
    implementation("androidx.browser:browser:1.8.0")
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
