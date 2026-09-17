plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayat64.airvpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ayat64.airvpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Official embeddable WireGuard tunnel library from Maven Central.
    implementation("com.wireguard.android:tunnel:1.0.20230427")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
