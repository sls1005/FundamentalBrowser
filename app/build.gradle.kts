plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.mikepenz.aboutlibraries.plugin")
}

android {
    namespace = "test.sls1005.projects.fundamentalbrowser"
    compileSdk = 35

    defaultConfig {
        applicationId = "test.sls1005.projects.fundamentalbrowser"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "0.8.0"
        resourceConfigurations += arrayOf("en", "en-rGB", "en-rUS", "zh-rCN", "zh-rHK", "zh-rTW", "b+zh+Hant")
    }
    androidResources {
        generateLocaleConfig = true
    }
    signingConfigs {
        register("release") {
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries)
    //implementation(libs.kotlinx.coroutine.android)
    //implementation(libs.androidx.constraintlayout)
    //implementation(libs.androidx.lifecycle.livedata.ktx)
    //implementation(libs.androidx.lifecycle.viewmodel.ktx)
    //implementation(libs.androidx.navigation.fragment.ktx)
    //implementation(libs.androidx.navigation.ui.ktx)
    //implementation(libs.androidx.webkit)
}

tasks.register<Copy>("Include license") {
    include("LICENSE")
    from("..")
    into("src/main/assets/")
}.also {
    val task = it.get()
    afterEvaluate {
        tasks.named("preReleaseBuild") {
            dependsOn(task)
        }
    }
}
