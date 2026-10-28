plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.divepilot.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.divepilot.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "0.9.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/divepilot-release.jks")
            storePassword = "divepilot123"
            keyAlias = "divepilot"
            keyPassword = "divepilot123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)

    // CameraX (photo / vidéo)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // Sérialisation du journal de plongée
    implementation(libs.gson)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// CameraX embarque un artefact « listenablefuture » conflictuel avec celui d'AndroidX.
configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}
