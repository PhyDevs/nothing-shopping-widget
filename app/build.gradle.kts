plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.phydevs.shopping"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phydevs.shopping"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // CI may supply the same private debug key on each run to make APKs update-compatible.
    val persistentKey = providers.environmentVariable("SHOPPING_KEYSTORE_PATH")
    if (persistentKey.isPresent) {
        signingConfigs.getByName("debug") {
            storeFile = file(persistentKey.get())
            storePassword = providers.environmentVariable("SHOPPING_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("SHOPPING_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("SHOPPING_KEY_PASSWORD").get()
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
