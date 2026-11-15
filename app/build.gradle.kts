plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.silora.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.silora.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
layout.buildDirectory.set(rootProject.file("Temp/build/app"))
dependencies { implementation("androidx.webkit:webkit:1.12.1") }
