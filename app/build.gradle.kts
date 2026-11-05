plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

// versionCode sempre crescente: número da execução do GitHub Actions (permite atualizar sobre a versão instalada).
val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersionCode = ciRun ?: 1
val appVersionName = "0.1.${ciRun ?: 0}"

android {
    namespace = "com.saintseiya.coach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saintseiya.coach"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"leandrozzy/saint-seiya-ai-coach\"")
    }

    signingConfigs {
        // Mesma chave em TODA build => o Android aceita instalar por cima (atualização). Ver docs/UPDATES.md.
        create("release") {
            val ks = System.getenv("COACH_KEYSTORE_FILE") ?: "${System.getProperty("user.home")}/.android/debug.keystore"
            storeFile = file(ks)
            storePassword = System.getenv("COACH_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("COACH_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("COACH_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = false }
}

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
