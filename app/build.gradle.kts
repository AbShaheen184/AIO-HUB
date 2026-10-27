plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.movies.premium"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.movies.premium"
        minSdk = 24
        targetSdk = 36
        versionCode = 806
        versionName = "8.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK abis kept identical to the original release (libretro cores + ffmpeg + ppsspp).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = "cinemahq"
            keyAlias = "cinemahq"
            keyPassword = "cinemahq"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
            )
        }
        jniLibs {
            // The original APK ships its native libraries uncompressed and
            // declares android:extractNativeLibs="true", so the installer
            // extracts them itself instead of the loader mapping them straight
            // out of the APK. Keep that behaviour: the libretro cores are
            // mmap'd and PPSSPP loads its data files by path.
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            // The unit tests exercise the repositories against the live backend.
            // `android.util.Log` is not mocked in a JVM test, so it has to be
            // told to return a default value instead of throwing.
            isReturnDefaultValues = true
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.graphics.path)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.gson)
    implementation(libs.moshi.kotlin)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.common)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(libs.jsoup)
    implementation(libs.rhino)
    implementation(libs.androidsvg)

    // Firebase: messaging only. The original also shipped Crashlytics, Analytics and
    // Sessions; those are pure telemetry that would report the replica user's usage to
    // the *original developer's* Firebase project (project_id `cinemahq-kotlin`), so they
    // are deliberately not carried over. Messaging is kept so the push path stays
    // available, but it stays inert until a `google-services.json` for a project you
    // own is dropped in — see §10 Step 4 of G:\workdir\CINEMAHQ-HANDOFF.md.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.google.play.services.location)
    implementation(libs.google.play.services.ads.identifier)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    ksp(libs.androidx.room.compiler)
}

// Utility task: prints the resolved AAR artifacts for the debug runtime
// classpath so resource-stripping tooling can read each AAR's R.txt.
tasks.register("dumpAarArtifacts") {
    doLast {
        configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
            .filter { it.extension == "aar" }
            .forEach { println("AAR:" + it.file.absolutePath) }
    }
}
