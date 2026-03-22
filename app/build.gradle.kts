plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "uk.co.maybeitsadam.literatureclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.maybeitsadam.literatureclock"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.watchface)
    implementation(libs.watchface.editor)
    implementation(libs.watchface.complications.data.source)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
