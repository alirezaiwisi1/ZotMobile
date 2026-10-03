plugins {
    id("org.jetbrains.kotlin.android") version "2.0.20"
    id("com.android.library")
}
android {
    namespace = "com.zotmobile.core.model"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
