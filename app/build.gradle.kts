import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.brotik.dshcloud"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brotik.dshcloud"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.6"
    }

    val keystoreFile = rootProject.file("keystore.jks")
    if (keystoreFile.exists()) {
        val props = Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        signingConfigs {
            create("dsh") {
                storeFile = keystoreFile
                storePassword = props.getProperty("storePassword") ?: "dshcloud"
                keyAlias = props.getProperty("keyAlias") ?: "dshcloud"
                keyPassword = props.getProperty("keyPassword") ?: "dshcloud"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("dsh")
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("dsh")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // РљР°Рє Рё РІ РґСЂСѓРіРёС… РїСЂРѕРµРєС‚Р°С… СЌС‚РѕР№ РјР°С€РёРЅС‹: РєР°С‚Р°Р»РѕРі release РёРЅРѕРіРґР° Р±Р»РѕРєРёСЂСѓРµС‚СЃСЏ
        // СЃРёСЃС‚РµРјРЅС‹Рј С„РёР»СЊС‚СЂРѕРј, РїРѕСЌС‚РѕРјСѓ APK СЃРѕР±РёСЂР°РµС‚СЃСЏ РІ СЃРІРѕР№ РєР°С‚Р°Р»РѕРі (app-prod.apk).
        create("prod") {
            initWith(getByName("release"))
            isDebuggable = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("dsh")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
