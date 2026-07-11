import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.weifurry.spotchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.weifurry.spotchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.guava)
    implementation(libs.material.icons.extended)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.input)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.tiles)
    implementation(libs.wear.watchface.complications.data.source.ktx)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling)
}
