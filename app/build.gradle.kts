plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.silora.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.silora.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 6
        versionName = "1.3.1"
        testInstrumentationRunner = providers.gradleProperty("testRunner").getOrElse("com.silora.app.NativeChecks")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
layout.buildDirectory.set(rootProject.file("Temp/build/app"))
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
