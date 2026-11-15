plugins {
    id("com.android.application")
}

android {
    namespace = "dev.roel.tvhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.roel.tvhome"
        minSdk = 26
        targetSdk = 36
    }

    // Same package: full installs over lite (keeps settings, Home key service, default Home).
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            versionCode = 2
            versionName = "1.0.1"
        }
        create("full") {
            dimension = "edition"
            versionCode = 201
            versionName = "2.0.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
