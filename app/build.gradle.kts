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
        versionCode = 36
        versionName = "4.9"
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
        // Р С™Р В°Р С” Р С‘ Р Р† Р Т‘РЎР‚РЎС“Р С–Р С‘РЎвЂ¦ Р С—РЎР‚Р С•Р ВµР С”РЎвЂљР В°РЎвЂ¦ РЎРЊРЎвЂљР С•Р в„– Р СР В°РЎв‚¬Р С‘Р Р…РЎвЂ№: Р С”Р В°РЎвЂљР В°Р В»Р С•Р С– release Р С‘Р Р…Р С•Р С–Р Т‘Р В° Р В±Р В»Р С•Р С”Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ
        // РЎРѓР С‘РЎРѓРЎвЂљР ВµР СР Р…РЎвЂ№Р С РЎвЂћР С‘Р В»РЎРЉРЎвЂљРЎР‚Р С•Р С, Р С—Р С•РЎРЊРЎвЂљР С•Р СРЎС“ APK РЎРѓР С•Р В±Р С‘РЎР‚Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р† РЎРѓР Р†Р С•Р в„– Р С”Р В°РЎвЂљР В°Р В»Р С•Р С– (app-prod.apk).
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
