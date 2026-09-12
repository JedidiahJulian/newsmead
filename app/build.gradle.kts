import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Make sure that you have the Google services Gradle plugin
    id("com.google.gms.google-services")

    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")

    // Safe args
    id("androidx.navigation.safeargs.kotlin")

    // Room
    id("kotlin-kapt")
}

android {
    namespace = "com.newsmead"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.newsmead"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2"

        // Frozen estimator baseline identity for the matched comparison harness.
        // The reference build applies the reviewed comparison patch to its exact
        // base snapshot and substitutes only these arm-specific constants.
        buildConfigField("String", "COMPARISON_ESTIMATOR_ID", "\"mgazenet\"")
        buildConfigField(
            "String",
            "COMPARISON_BASE_COMMIT",
            "\"bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22\"",
        )
        buildConfigField(
            "String",
            "COMPARISON_BASE_APK_SHA256",
            "\"131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9\"",
        )
        buildConfigField(
            "String",
            "COMPARISON_PROTOCOL_MANIFEST_SHA256",
            "\"41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    sourceSets["main"].apply {
        assets.srcDir(layout.buildDirectory.dir("generated/mgazenetAssets"))
        jniLibs.srcDir(rootProject.file("mgazenet-benchmark/vendor/jniLibs"))
    }
    // Preserve shrinking; extract the pinned native libraries with AGP 8.3.
    packaging { jniLibs { useLegacyPackaging = true } }

    // Enable viewBinding
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Import navigation dependencies
    implementation("androidx.navigation:navigation-fragment-ktx:2.4.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.4.1")

    // Import RecyclerView dependencies
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Import Splash Screen dependencies
    implementation("androidx.core:core-splashscreen:1.0.0")

    // Import Facebook Shimmer dependencies
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Import Room dependencies
    implementation("androidx.room:room-runtime:2.6.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    kapt("androidx.room:room-compiler:2.6.0")

    // Import Coroutines dependencies
    implementation("androidx.room:room-ktx:2.6.0")

    // Import Volley dependencies
    implementation("com.android.volley:volley:1.2.1")

    // Import Glide dependencies
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Import ZoomLayout dependencies
    implementation("com.otaliastudios:zoomlayout:1.9.0")

    implementation("org.opencv:opencv:4.11.0")

    // Local gaze tracking: CameraX front-camera frames + MediaPipe FaceLandmarker.
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    // Reversible hybrid-eye shadow: bundle the CPU runtime so testing does not require
    // Google Play Services. A universal debug APK carries every ABI; release app bundles do not.
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

val verifyVendor by tasks.registering {
    doLast {
        check(rootProject.file("mgazenet-benchmark/vendor/assets/base.mnn").isFile) {
            "Run tools/gaze/mgazenet/fetch_vendor.py explicitly before building NewsMead."
        }
        val pins = mapOf(
            "assets/base.mnn" to "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
            "assets/face_landmarker.task" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
            "jniLibs/arm64-v8a/libMNN.so" to "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
            "jniLibs/arm64-v8a/libmnncore.so" to "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
            "jniLibs/arm64-v8a/libc++_shared.so" to "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970"
        )
        pins.forEach { (path, expected) ->
            val hash = MessageDigest.getInstance("SHA-256").digest(rootProject.file("mgazenet-benchmark/vendor/$path").readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == expected) { "Vendor hash mismatch: $path" }
        }
        check(MessageDigest.getInstance("SHA-256").digest(file("src/main/assets/face_landmarker.task").readBytes())
            .joinToString("") { "%02x".format(it) } == pins.getValue("assets/face_landmarker.task"))
        check(rootProject.file("mgazenet-benchmark/vendor/assets/vendor-manifest.json").isFile)
        check(rootProject.file("mgazenet-benchmark/vendor/assets/notices/GazeFollower-CC-BY-NC-SA-4.0.txt").isFile)
        check(rootProject.file("mgazenet-benchmark/vendor/assets/notices/MNN-Apache-2.0.txt").isFile)
    }
}
val verifyComparisonProtocolManifest by tasks.registering {
    val manifest = file("src/main/assets/comparison/newsmead-current-vs-mgazenet-reading-v1.json")
    inputs.file(manifest)
    doLast {
        check(manifest.isFile) { "Missing frozen comparison protocol manifest asset." }
        val hash = MessageDigest.getInstance("SHA-256").digest(manifest.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(hash == "41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185") {
            "Frozen comparison protocol manifest changed."
        }
    }
}

val prepareMgazeNetAssets by tasks.registering(Sync::class) {
    dependsOn(verifyVendor)
    from(rootProject.file("mgazenet-benchmark/vendor/assets")) {
        include("base.mnn", "vendor-manifest.json", "notices/**")
    }
    into(layout.buildDirectory.dir("generated/mgazenetAssets"))
}
tasks.named("preBuild").configure {
    dependsOn(prepareMgazeNetAssets)
    dependsOn(verifyComparisonProtocolManifest)
}

// Verify the artifact, not just input directories (OpenCV also supplies libc++_shared).
tasks.register("verifyMgazeNetDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        val pins = mapOf(
            "assets/base.mnn" to "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
            "assets/face_landmarker.task" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
            "lib/arm64-v8a/libMNN.so" to "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
            "lib/arm64-v8a/libmnncore.so" to "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
            "lib/arm64-v8a/libc++_shared.so" to "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970"
        )
        ZipFile(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile).use { apk ->
            pins.forEach { (path,expected) ->
                val entry = checkNotNull(apk.getEntry(path)) { "Missing pinned APK entry: $path" }
                val hash = MessageDigest.getInstance("SHA-256").digest(apk.getInputStream(entry).use { it.readBytes() })
                    .joinToString("") { "%02x".format(it) }
                check(hash == expected) { "Packaged MGazeNet hash mismatch: $path" }
            }
            listOf("GazeFollower-CC-BY-NC-SA-4.0.txt","MNN-Apache-2.0.txt","NewsMead-MGazeNet-attribution.txt").forEach {
                check(apk.getEntry("assets/notices/$it") != null)
            }
        }
    }
}
