import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// KPay's staging appId/appSecret are per-device credentials with nowhere to live in source
// control. Read from local.properties (already gitignored, Android-Studio-managed) as
// "kpay.appId"/"kpay.appSecret"; both default to empty strings when absent, e.g. on a fresh
// clone before the physical terminal (Phase 8a) is provisioned.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.kspay.forwarder"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kspay.forwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KPAY_APP_ID", "\"${localProperties.getProperty("kpay.appId", "")}\"")
        buildConfigField("String", "KPAY_APP_SECRET", "\"${localProperties.getProperty("kpay.appSecret", "")}\"")

        // KSPay backend target for simulated end-to-end forwarding tests (pre-terminal). The
        // ingest URL defaults to a syntactically-valid placeholder (not "") so Retrofit's
        // Builder.baseUrl() doesn't throw at construction on a fresh clone -- an unconfigured
        // device just gets connection-refused (IOException -> WorkManager retry) instead of a
        // crash. Override both via local.properties once a real/local KSPay backend is reachable.
        buildConfigField("String", "KSPAY_INGEST_URL", "\"${localProperties.getProperty("kspay.ingestUrl", "http://127.0.0.1:8000/")}\"")
        buildConfigField("String", "KSPAY_DEVICE_TOKEN", "\"${localProperties.getProperty("kspay.deviceToken", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Networking (KPay LAN client + KSPay backend client)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Local persistence (LocalTransaction state machine)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background sync (forward + reconciliation)
    implementation(libs.androidx.work.runtime.ktx)

    // Encrypted local storage (working key + device token)
    implementation(libs.androidx.security.crypto)

    // Feature config (safe defaults + persisted overrides from the KSPay backend)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Pure-JVM unit tests
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test> {
    // Lets tests locate the repo-root .env (KPay parity vector, gitignored) regardless of
    // Gradle's working directory for the test task.
    systemProperty("rootDir", rootProject.projectDir.absolutePath)

    // Robolectric's native-runtime loader mis-handles the %20-encoded space in this machine's
    // Windows user profile path ("AL GHANI COMPUTER"), throwing NoSuchFileException the first
    // time a Compose/native-graphics Robolectric test loads per JVM fork
    // (robolectric/robolectric#4589-class bug). Point it at a pre-populated, space-free offline
    // dir instead of its default ~/.m2/repository cache. Machine-local workaround only.
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", "C:/robolectric-deps")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}