import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Llave de firma de KyrnForge (vive en aurasound-dsp, fuera de este repo).
val signingDir = rootProject.file("../../aurasound-dsp")
val keystoreProperties = Properties().apply {
    val f = File(signingDir, "keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.kyrnforge.kyrndeck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kyrnforge.kyrndeck"
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "0.5.3"

        // Se puede apuntar a otro servidor para probar: -PupdateUrl=http://...
        val updateUrl = (project.findProperty("updateUrl") as String?)
            ?: "https://api.github.com/repos/devlwte/kyrndeck/releases/latest"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        manifestPlaceholders["cleartext"] = updateUrl.startsWith("http://").toString()
    }

    signingConfigs {
        create("release") {
            storeFile = File(signingDir, "kyrnforge-release.jks")
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias", "kyrnforge")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
}
