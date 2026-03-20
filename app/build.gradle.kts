plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "uk.co.maybeitsadam.literatureclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.maybeitsadam.literatureclock"
        minSdk = 33 // WearOS 4 minimum for Watch Face Format
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(libs.watchface.complications.data.source)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
