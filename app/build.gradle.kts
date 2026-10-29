plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.xailla.vpn"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.xailla.vpn"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    splits { abi {
        isEnable = true; reset()
        include("armeabi-v7a", "arm64-v8a")
        isUniversalApk = true
    } }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" } }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
