import java.security.MessageDigest
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

android {
    namespace = "com.newsmead.mgazenetbenchmark"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.newsmead.mgazenetbenchmark"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-audit"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    sourceSets["main"].apply {
        assets.srcDir(layout.buildDirectory.dir("generated/mgazenetAssets"))
        jniLibs.srcDir(mgazeNetDirectory.resolve("jniLibs"))
    }
    // Avoid relying on APK ZIP alignment with the project's existing AGP 8.3.
    packaging { jniLibs { useLegacyPackaging = true } }
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

val prepareMgazeNetAssets by tasks.registering(Sync::class) {
    dependsOn(verifyVendor)
    from(mgazeNetDirectory.resolve("assets")) {
        include("base.mnn", "vendor-manifest.json", "notices/**")
    }
    from(faceLandmarker)
    into(layout.buildDirectory.dir("generated/mgazenetAssets"))
}
tasks.named("preBuild").configure { dependsOn(prepareMgazeNetAssets) }

// Use the configured candidate, including the project default. An explicitly empty
// property omits this source directory even after an earlier candidate build.
providers.gradleProperty("gazefollower2CandidateDir").orNull?.takeIf { it.isNotBlank() }?.let { path ->
    val candidateDirectory = rootProject.file(path)
    val generatedAssets = layout.buildDirectory.dir("generated/gazefollower2Assets")
    val fixtureFiles = listOf("ramp", "clipped", "channels").flatMap { fixture ->
        listOf("face", "left", "right", "rect").map { "${fixture}_${it}.f32" }
    }
    // No inputs/outputs: validation runs even when Sync would be NO-SOURCE or
    // UP-TO-DATE. An explicitly requested missing candidate must fail closed.
    val verifyGazeFollower2Candidate by tasks.registering {
        doLast {
            check(candidateDirectory.isDirectory) { "Candidate directory is missing: $candidateDirectory" }
            fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash(candidateDirectory.resolve("GazeNet_Next.mnn")) ==
                "4547cc4685a7176aad75c50a25dc9a3368d1dd1fafe0d8ea5c32421af93a49b0") { "Unreviewed candidate model" }
            check(hash(candidateDirectory.resolve("candidate.json")) ==
                "e5a2ecbe665a8024fc601d875d8f58f63ff26c5ea14e69e36e0a1f5a7b9132c5") { "Unreviewed parity reference" }
            val report = JsonSlurper().parse(candidateDirectory.resolve("candidate.json")) as Map<*, *>
            check(report["outcome"] == "host_parity_passed")
            val fixtures = report["fixtures"] as List<*>
            fixtures.forEach { entry ->
                val fixture = entry as Map<*, *>
                listOf("face", "left", "right", "rect").forEach { name ->
                    val tensor = fixture[name] as Map<*, *>
                    val file = candidateDirectory.resolve(tensor["file"] as String)
                    check(file.name in fixtureFiles && hash(file) == tensor["sha256"]) { "Altered synthetic fixture" }
                    check(file.length() == (tensor["count"] as Number).toLong() * 4)
                }
            }
            check(hash(candidateDirectory.resolve("NOTICE.txt")) ==
                "1ce8acc97a4c9afeae0eddcca2fe5d8368079a68dfcc03754cdbe4f4a4d099f1") { "Altered candidate notice" }
            check(hash(candidateDirectory.resolve("GazeFollower2-LICENSE.txt")) ==
                "846fe2d3b8891afd3552f7a221d1c745a8f190c90e0d6a20ccd8bc723e45353c") { "Altered candidate license" }
        }
    }
    val prepareGazeFollower2Candidate by tasks.registering(Sync::class) {
        dependsOn(verifyGazeFollower2Candidate)
        from(candidateDirectory) {
            include(fixtureFiles + listOf("GazeNet_Next.mnn", "candidate.json", "NOTICE.txt", "GazeFollower2-LICENSE.txt"))
            into("gazefollower2")
        }
        into(generatedAssets)
    }
    android.sourceSets["main"].assets.srcDir(generatedAssets)
    tasks.named("preBuild").configure { dependsOn(prepareGazeFollower2Candidate) }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    implementation("org.opencv:opencv:4.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
