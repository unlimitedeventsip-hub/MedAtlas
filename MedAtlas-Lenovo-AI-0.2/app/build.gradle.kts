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
        versionCode = 21
        versionName = "2.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // Same stable signing certificate as previous MedAtlas installs:
            // updates retain local notes and keep Google Classroom OAuth valid.
            if (stableKeystore.exists()) {
                signingConfig = signingConfigs.getByName("medatlas")
            }
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
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}

val generatedAnatomy = layout.buildDirectory.dir("generated/body-assets")
val prepareAnatomy by tasks.registering(Exec::class) {
    val generator = rootProject.file("tools/build_anatomy.py")
    inputs.files(generator, rootProject.file("tools/sticker-models.tsv"))
    outputs.dir(generatedAnatomy)
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        generator.absolutePath, generatedAnatomy.get().asFile.absolutePath)
}
android.sourceSets.getByName("main").assets.srcDir(generatedAnatomy)
tasks.named("preBuild").configure { dependsOn(prepareAnatomy) }
