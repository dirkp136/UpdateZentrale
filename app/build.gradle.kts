import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.dirk.updatezentrale"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.dirk.updatezentrale"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "2.0"
        // Build date shown in the info dialog
        buildConfigField("String", "BUILD_DATE",
            "\"" + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK can be sideloaded directly
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    lint { checkReleaseBuilds = false }
}
