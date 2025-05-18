plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.videoplayer.akii"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.videoplayer.akii"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}

// Explicitly set jvmTarget for Kapt tasks to 1.8
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
dependencies {
    implementation(libs.androidx.swiperefreshlayout)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation(libs.androidx.activity)
    implementation("androidx.core:core-ktx:1.12.0") // Or latest
    implementation("androidx.appcompat:appcompat:1.6.1") // Or latest
    implementation("com.google.android.material:material:1.11.0") // Or latest
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Or latest

    // ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1") // Or latest
    implementation("androidx.media3:media3-ui:1.2.1")       // For PlayerView and UI components
    implementation("androidx.media3:media3-session:1.2.1") // For background playback (optional for now)

    // For RecyclerView to list videos
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Or latest

    // For ViewModel and LiveData (good practice for managing UI-related data)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Or latest
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0") // Or latest
    implementation("androidx.activity:activity-ktx:1.8.2") // For by viewModels()

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation ("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation ("com.google.android.exoplayer:exoplayer-ui:2.19.1")
}

