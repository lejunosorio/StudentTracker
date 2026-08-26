import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "dev.soloistdev.studenttracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.soloistdev.studenttracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Shrink, obfuscate and strip unused code. This app holds identifiable data about
            // minors; shipping it with full symbol names and every unreachable branch intact
            // makes it needlessly easy to read.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // 16 KB page-size support needs native libraries stored UNCOMPRESSED and page-aligned, which
    // is useLegacyPackaging = false - the default. The previous `true` did the opposite: it
    // compressed them and had them extracted at install time, which is the packaging 16 KB
    // devices reject.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

ksp {
    // Writes schemas/<db>/<version>.json so migrations have a reference to test against
    arg("room.schemaLocation", "$projectDir/schemas")
}

// KOTLIN STDLIB FORCE RESOLUTION STRATEGY
// Forcefully resolves all Kotlin standard libraries down to 2.2.x compatible versions
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security (RootBeer - Free Root Detection)
    implementation(libs.rootbeer.lib)

    // Jetpack Security Cryptography
    implementation(libs.androidx.security.crypto)

    // Android Biometric Library (Fingerprint/Face Unlock)
    implementation(libs.androidx.biometric)

    // Pinned ahead of what biometric asks for.
    //
    // biometric 1.2.0-alpha05 pulls in fragment 1.2.5, whose FragmentActivity still validates that
    // a startActivityForResult request code fits in 16 bits. The modern ActivityResultRegistry in
    // activity 1.8.x deliberately generates codes above that range, so with MainActivity extending
    // FragmentActivity - which BiometricPrompt requires - every file picker, photo picker and
    // scanner launch threw "Can only use lower 16 bits for requestCode" and killed the app.
    implementation(libs.androidx.fragment)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler.v261)

    // SQLCipher Community Edition (Encrypted DB)
    implementation(libs.android.database.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)

    // Material Extended Icons
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    // Must come before the mockable android.jar so JSON calls run for real in unit tests.
    testImplementation(libs.org.json)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.zxing.core)

    // Camera barcode scanning for QR attendance
    implementation(libs.zxing.android.embedded)
}