plugins {
    id("com.android.application")
}

android {
    namespace = "com.server.smsforwarder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.server.smsforwarder"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-beta.1"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/NOTICE.md"
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.re2j:re2j:1.8")
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
    testImplementation("junit:junit:4.13.2")
}
