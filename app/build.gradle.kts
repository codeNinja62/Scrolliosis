import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.devtools.ksp)
    // Use Koin for lightweight DI; avoid annotation processors
}

// Read signing credentials from local.properties (local dev) or environment variables (CI)
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}
fun signingProp(name: String): String? {
    val v = localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
    return v
}

android {
    namespace = "com.saltatoryimpulse.braingate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saltatoryimpulse.braingate"
        minSdk = 26 // Supports 95%+ of Android devices
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val keyFile = signingProp("SIGNING_STORE_FILE")
            storeFile = keyFile?.let { rootProject.file(it) }
            storePassword = signingProp("SIGNING_STORE_PASSWORD")
            keyAlias = signingProp("SIGNING_KEY_ALIAS")
            keyPassword = signingProp("SIGNING_KEY_PASSWORD")
        }
    }

    // THE FIX: Production-grade R8 compiler instructions
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true // Enables code shrinking, obfuscation, and optimization
            isShrinkResources = true // Removes unused resources (XML, PNGs) from the final APK
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false // Keep false for fast local development
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Force a single version of annotation-experimental to prevent the D8 "defined multiple times"
// duplicate class error that arises when Koin and Compose pull in different versions transitively.
configurations.all {
    resolutionStrategy {
        force("androidx.annotation:annotation-experimental:1.4.1")
    }
}

dependencies {
    // --- 1. CORE ANDROID ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // --- 2. JETPACK COMPOSE ---
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")

    // --- 3. LIFECYCLE ---
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // --- 4. ROOM DATABASE ---
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // --- 5. NAVIGATION ---
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // --- 6. TESTING ---
    testImplementation("junit:junit:4.13.2")
    // Robolectric + AndroidX test core to enable JVM in-memory Room tests
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Koin test helpers
    testImplementation("io.insert-koin:koin-test:3.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.compose.material:material-icons-extended")

    // Koin DI (no annotation processors)
    implementation("io.insert-koin:koin-android:3.4.0")
    implementation("io.insert-koin:koin-androidx-compose:3.4.0")
}

kotlin {
    jvmToolchain(11)
}