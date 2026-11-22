plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.silora.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.silora.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 23
        versionName = "1.14.1"
        manifestPlaceholders["appLabel"] = "静律"
        testInstrumentationRunner = providers.gradleProperty("testRunner").getOrElse("com.silora.app.NativeChecks")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    // Opt-in isolated device validation: separate Android UID, database and credentials.
    buildTypes.getByName("debug") {
        if (providers.gradleProperty("deviceCheckBuild").orNull == "true") {
            applicationIdSuffix = ".devicecheck"
            manifestPlaceholders["appLabel"] = "静律验证"
        }
    }
}
layout.buildDirectory.set(rootProject.file("Temp/build/app"))
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
