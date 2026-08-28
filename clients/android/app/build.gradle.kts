plugins {
    id("com.android.application")
}

val releaseStoreFile = providers.environmentVariable("YANJIAN_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("YANJIAN_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("YANJIAN_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("YANJIAN_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.server.smsforwarder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.server.smsforwarder"
        minSdk = 23
        targetSdk = 35
        versionCode = 16
        versionName = "1.1.1-beta.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseFromEnvironment") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseFromEnvironment")
            }
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

tasks.register("printVersionName") {
    doLast {
        print(android.defaultConfig.versionName)
    }
}

tasks.register("printVersionCode") {
    doLast {
        print(android.defaultConfig.versionCode)
    }
}

dependencies {
    // 1.17+ requires compileSdk 36 and AGP 8.9.1; this project intentionally
    // stays on the tested compileSdk 35 / AGP 8.7.3 compatibility baseline.
    //noinspection GradleDependency
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.joanzapata.iconify:android-iconify-material:2.2.2") {
        exclude(group = "com.android.support")
    }
    implementation("com.joanzapata.iconify:android-iconify-material-community:2.2.2") {
        exclude(group = "com.android.support")
    }
    implementation("com.google.re2j:re2j:1.8")
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
