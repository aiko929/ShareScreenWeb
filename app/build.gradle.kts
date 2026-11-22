import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing for Google Play. Only used when keystore.properties exists (it is git-ignored),
// so F-Droid and other builds from source are unaffected.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// AdMob IDs for the "play" flavor. Put your real IDs in admob.properties (keys: appId, bannerId,
// interstitialId). Without that file, Google's official test IDs are used. Debug builds always
// use test ad units (see AdIds.kt).
val admobProps = Properties().apply {
    val f = rootProject.file("admob.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val admobAppId = admobProps.getProperty("appId", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = admobProps.getProperty("bannerId", "ca-app-pub-3940256099942544/9214589741")
val admobInterstitialId = admobProps.getProperty("interstitialId", "ca-app-pub-3940256099942544/1033173712")

android {
    namespace = "com.sharescreenweb"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aiko929.sharescreenweb"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    buildFeatures {
        buildConfig = true
    }

    // foss: no ads, no proprietary code (F-Droid, GitHub). play: optional AdMob ads (Google Play).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
        }
        create("play") {
            dimension = "distribution"
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        }
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // F-Droid: don't embed the Google-signed dependency metadata blob in APKs.
    // App bundles (.aab) only go to Google Play, which uses it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    "playImplementation"("com.google.android.gms:play-services-ads:24.4.0")
    "playImplementation"("com.google.android.ump:user-messaging-platform:3.2.0")
}
