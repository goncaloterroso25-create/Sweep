import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "0.5.0"

/**
 * Release signing details, read from `keystore.properties` at the project root or from the
 * matching environment variables. Neither is in version control.
 *
 * If nothing is configured the release build still assembles, just unsigned, so cloning the
 * repository and building it never requires a key that only one machine has.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingDetail(property: String, environment: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(environment))?.takeIf(String::isNotBlank)

val releaseStorePath = signingDetail("storeFile", "SWEEP_STORE_FILE")
val releaseStorePassword = signingDetail("storePassword", "SWEEP_STORE_PASSWORD")
val releaseKeyAlias = signingDetail("keyAlias", "SWEEP_KEY_ALIAS")
val releaseKeyPassword = signingDetail("keyPassword", "SWEEP_KEY_PASSWORD")

val releaseKeystore = releaseStorePath?.let { path ->
    rootProject.file(path).takeIf { it.exists() } ?: file(path).takeIf { it.exists() }
}
val canSignRelease = releaseKeystore != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

// Produces Sweep-v0.5.0-release.apk rather than app-release.apk, so a tester can tell at a
// glance which build they were sent.
base {
    archivesName.set("Sweep-v$appVersionName")
}

android {
    namespace = "dev.sweep"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sweep"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
            // AGP otherwise embeds the git commit and repository details in the APK. A build
            // handed to a tester has no use for that, and it makes the file depend on which
            // machine produced it.
            vcsInfo { include = false }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // The coroutines debug agent's marker file. Sweep never enables the agent, and a
            // release build has no business shipping a file with "Debug" in the name.
            excludes += "DebugProbesKt.bin"
        }
    }
}

/**
 * Prints the SHA-256 of the release APK, and whether it was signed.
 *
 * A tester who receives the file over a chat app can compare the hash before installing, which is
 * the only part of "is this the file you meant to send me" that is actually verifiable by hand.
 */
private fun Project.reportArtifacts(directory: java.io.File, signed: Boolean, extension: String) {
    val files = directory.listFiles()?.filter { it.extension == extension }.orEmpty()
    if (files.isEmpty()) {
        logger.lifecycle("No .$extension found in $directory")
        return
    }
    files.forEach { artifact ->
        val digest = MessageDigest.getInstance("SHA-256").digest(artifact.readBytes())
        logger.lifecycle("")
        logger.lifecycle("File     ${artifact.name}")
        logger.lifecycle("Size     ${"%.1f".format(artifact.length() / 1_000_000.0)} MB")
        logger.lifecycle(
            "Signed   " + if (signed) {
                "yes, with the configured release key"
            } else {
                "NO, keystore.properties is missing"
            }
        )
        logger.lifecycle("SHA-256  ${digest.joinToString("") { "%02x".format(it) }}")
        logger.lifecycle("Path     ${artifact.absolutePath}")
    }
}

tasks.register("releaseApkInfo") {
    group = "distribution"
    description = "Assembles the release APK and prints its name, size and SHA-256 checksum."
    dependsOn("assembleRelease")

    val outputDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val signed = canSignRelease

    doLast { reportArtifacts(outputDirectory.get().asFile, signed, "apk") }
}

/**
 * The artifact Google Play actually wants.
 *
 * Play has required an App Bundle rather than an APK for new apps since 2021, and it re-signs
 * whatever is uploaded with its own key through Play App Signing. The key configured here is
 * therefore the *upload* key in that world, and the same one that signs the direct-download APK
 * in this one, which is fine: they serve different distribution paths and neither is the other.
 */
tasks.register("releaseBundleInfo") {
    group = "distribution"
    description = "Builds the signed release App Bundle for Play and prints its checksum."
    dependsOn("bundleRelease")

    val outputDirectory = layout.buildDirectory.dir("outputs/bundle/release")
    val signed = canSignRelease

    doLast { reportArtifacts(outputDirectory.get().asFile, signed, "aab") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
