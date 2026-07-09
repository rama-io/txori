import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
}

val currentYear = LocalDate.now().year

android {
    namespace = "com.rama.txori"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rama.txori"
        minSdk = 21
        targetSdk = 37
        versionCode = 46
        versionName = "$currentYear.$versionCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/*.version"
            excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("txori_${variant.name}.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":bohio"))
}