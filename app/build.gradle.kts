import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.Properties

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

val mgazeNetDirectory = rootProject.file("third_party/gaze/mgazenet")
val faceLandmarker = rootProject.file("app/src/main/assets/face_landmarker.task")
val faceLandmarkerSha256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
val mgazeNetPins = mapOf(
    "assets/base.mnn" to "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
    "assets/vendor-manifest.json" to "56d075aa1b36b574ecb359da741f56c51a39635165bed8f64a1f77ec2275b063",
    "assets/notices/GazeFollower-CC-BY-NC-SA-4.0.txt" to "9e502ba75fd0fd30cea5497b438a4bc12f07f02bc932a7a6041fd6a18b8f46a0",
    "assets/notices/MNN-Apache-2.0.txt" to "a830d59c4f98d110e0540d0581efada06192ba6e798434307d1794b07a290302",
    "jniLibs/arm64-v8a/libMNN.so" to "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
    "jniLibs/arm64-v8a/libmnncore.so" to "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
    "jniLibs/arm64-v8a/libc++_shared.so" to "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970",
    "jniLibs/armeabi-v7a/libMNN.so" to "3504fe856a208ead295536ff8398dcdd210e5cd7fc51d7cd34a9e1fcf084c2f7",
    "jniLibs/armeabi-v7a/libmnncore.so" to "966aa04af136c8c99fe84309bc845e16478cee215a2b717b829635bfc20b1f9c",
    "jniLibs/armeabi-v7a/libc++_shared.so" to "4df8b2e81690dd1393a94f65c18b41e5b91494b10b21415823a94ab6bb81018a",
)
val gazeExperimentPins = mapOf(
    "GazeNet_Next.mnn" to "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0",
    "candidate.json" to "e5a2ecbe665a8024fc601d875d8f58f63ff26c5ea14e69e36e0a1f5a7b9132c5",
    "NOTICE.txt" to "1ce8acc97a4c9afeae0eddcca2fe5d8368079a68dfcc03754cdbe4f4a4d099f1",
    "GazeFollower2-LICENSE.txt" to "846fe2d3b8891afd3552f7a221d1c745a8f190c90e0d6a20ccd8bc723e45353c",
)

val localProperties = Properties()
val gazeExperimentDirectory = providers.gradleProperty("gazefollower2CandidateDir").orNull?.takeIf { it.isNotBlank() }
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
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
        buildConfigField("boolean", "GAZE_EXPERIMENT_ENABLED", (gazeExperimentDirectory != null).toString())

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



        // NEWSDATA_API_KEY from the ignored local.properties; empty if missing.
        buildConfigField(
            "String",
            "NEWSDATA_API_KEY",
            "\"${localProperties.getProperty("NEWSDATA_API_KEY", "")}\""
        )
    }

    buildTypes {
        getByName("debug") {
            // Development devices may expose only the 32-bit ARM ABI.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        release {
            // Preserve the frozen release/comparison target.
            ndk { abiFilters += "arm64-v8a" }
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
        jniLibs.srcDir(mgazeNetDirectory.resolve("jniLibs"))
    }
    // Preserve shrinking; extract the pinned native libraries with AGP 8.3.
    packaging { jniLibs { useLegacyPackaging = true } }

    // Enable viewBinding
    buildFeatures {
        viewBinding = true
        // AGP 8 no longer generates BuildConfig by default; AppLog gates its
        // debug-level output on BuildConfig.DEBUG.
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

    // Full-text extraction. jsoup pinned: Readability4J's 1.11.2 has CVE-2021-37714.
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.15.4")

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
    // android.jar's org.json is stubbed in unit tests.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

val verifyVendor by tasks.registering {
    doLast {
        mgazeNetPins.forEach { (path, expected) ->
            val source = mgazeNetDirectory.resolve(path)
            check(source.isFile) { "Missing tracked MGazeNet input: $source" }
            val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == expected) { "Vendor hash mismatch: $path" }
        }
        check(MessageDigest.getInstance("SHA-256").digest(faceLandmarker.readBytes())
            .joinToString("") { "%02x".format(it) } == faceLandmarkerSha256) {
            "Face landmarker hash mismatch: $faceLandmarker"
        }
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
    from(mgazeNetDirectory.resolve("assets")) {
        include("base.mnn", "vendor-manifest.json", "notices/**")
    }
    into(layout.buildDirectory.dir("generated/mgazenetAssets"))
}
tasks.named("preBuild").configure {
    dependsOn(prepareMgazeNetAssets)
    dependsOn(verifyComparisonProtocolManifest)
}

// The configured candidate is included unless the property is explicitly empty. Verification runs
// even if the subsequent Sync has no source files or is up to date.
gazeExperimentDirectory?.let { path ->
    val candidate = rootProject.file(path)
    val destination = layout.buildDirectory.dir("generated/gazeExperimentAssets")
    val verifyGazeExperiment by tasks.registering {
        doLast {
            check(candidate.isDirectory) { "Candidate directory is missing: $candidate" }
            gazeExperimentPins.forEach { (name, expected) ->
                val source = candidate.resolve(name)
                check(source.isFile) { "Missing experiment asset: $source" }
                val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
                    .joinToString("") { "%02x".format(it) }
                check(hash == expected) { "Unreviewed experiment asset: $name" }
            }
        }
    }
    val prepareGazeExperiment by tasks.registering(Sync::class) {
        dependsOn(verifyGazeExperiment)
        from(candidate) {
            include("GazeNet_Next.mnn", "candidate.json", "GazeFollower2-LICENSE.txt", "NOTICE.txt")
            into("gazefollower2")
        }
        into(destination)
    }
    android.sourceSets["main"].assets.srcDir(destination)
    tasks.named("preBuild").configure { dependsOn(prepareGazeExperiment) }
}

// Verify the artifact, not just input directories (OpenCV also supplies libc++_shared).
tasks.register("verifyMgazeNetDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        val pins = mgazeNetPins.mapKeys { (path, _) -> path.replaceFirst("jniLibs/", "lib/") } + mapOf(
            "assets/face_landmarker.task" to faceLandmarkerSha256,
            "assets/notices/NewsMead-MGazeNet-attribution.txt" to "4bdc9ad30439efca14bdb5f6b961d142627b56f4abc0fb9ce0b7273601fbd7a0",
        ) + if (gazeExperimentDirectory != null) {
            gazeExperimentPins.mapKeys { (name, _) -> "assets/gazefollower2/$name" }
        } else emptyMap()
        ZipFile(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile).use { apk ->
            pins.forEach { (path,expected) ->
                val entry = checkNotNull(apk.getEntry(path)) { "Missing pinned APK entry: $path" }
                val hash = MessageDigest.getInstance("SHA-256").digest(apk.getInputStream(entry).use { it.readBytes() })
                    .joinToString("") { "%02x".format(it) }
                check(hash == expected) { "Packaged MGazeNet hash mismatch: $path" }
            }
            if (gazeExperimentDirectory == null) {
                check(apk.entries().asSequence().none { it.name.startsWith("assets/gazefollower2/") }) {
                    "Baseline-only APK contains candidate assets."
                }
            }
        }
    }
}
