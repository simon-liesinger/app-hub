import java.util.Properties

plugins {
    id("com.android.application")
}

val versionProps = Properties().apply {
    load(file("../version.properties").inputStream())
}

android {
    namespace = "com.simon.apphub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.simon.apphub"
        minSdk = 24
        targetSdk = 34
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = versionProps["VERSION_NAME"].toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
