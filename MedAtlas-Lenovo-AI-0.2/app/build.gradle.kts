plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ro.medatlas.study"
    compileSdk = 35
    defaultConfig {
        applicationId = "ro.medatlas.study"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }
    val stableKeystore = file("medatlas-signing.jks")
    if (stableKeystore.exists()) {
        signingConfigs {
            create("medatlas") {
                storeFile = stableKeystore
                storePassword = System.getenv("MEDATLAS_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("MEDATLAS_KEY_ALIAS") ?: "medatlas"
                keyPassword = System.getenv("MEDATLAS_KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (stableKeystore.exists()) signingConfig = signingConfigs.getByName("medatlas")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
