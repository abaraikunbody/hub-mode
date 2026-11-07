plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.perso.hubmode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.perso.hubmode"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "2.0"
    }

    // Clé de signature fixe : permet d'installer les mises à jour par-dessus l'ancienne version.
    signingConfigs {
        create("perso") {
            storeFile = file("hubmode.jks")
            storePassword = "hubmode"
            keyAlias = "hubmode"
            keyPassword = "hubmode"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("perso")
        }
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
