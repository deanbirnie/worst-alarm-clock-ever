plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.worstalarm.clock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.worstalarm.clock"
        minSdk = 26
        targetSdk = 34
        versionCode = 28
        versionName = "0.5.5"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Pin the debug key so every build — local AND CI — signs with the SAME
        // signature. Without this, AGP auto-generates a throwaway debug keystore on
        // each fresh CI runner, so consecutive downloaded APKs have mismatched
        // signatures and Android refuses to install one over another
        // (INSTALL_FAILED_UPDATE_INCOMPATIBLE) — forcing an uninstall every update.
        // The committed debug.keystore uses Android's well-known default debug
        // credentials; it is NOT secret and cannot sign Play releases. Real release
        // signing (Play App Signing) is set up at the V1 Play submission — see RELEASING.md.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module"
        )
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest/resources to host Room's SQLite in the
            // fast JVM `testDebugUnitTest` lane (no emulator) — see the Room DAO tests.
            isIncludeAndroidResources = true
        }
    }

    // Name APK files "WorstAlarmEver-<version>-<buildType>.apk" instead of the
    // default app-debug.apk / app-release.apk. AGP's newer public Variant API
    // (VariantOutput) doesn't expose a settable outputFileName without reaching
    // into an internal impl class, so this uses the older variant API, which
    // AGP keeps working specifically for cases like this.
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "WorstAlarmEver-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit barcode scanning (bundled, works offline)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Accompanist permissions (simpler runtime permission flow in Compose)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Settings storage (global alarm tone, intro-seen flag)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // QR code generation (offline, for the printable-code generator)
    implementation("com.google.zxing:core:3.5.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // Robolectric hosts Room's SQLite in JVM unit tests (Room/DAO/transaction coverage) so
    // the DB tests run in CI's existing `testDebugUnitTest` step, no emulator needed.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
