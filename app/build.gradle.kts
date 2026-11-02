plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Release signing is opt-in: it comes from the environment (never from source control).
val releaseKeystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")

// The debug key is intentionally not tracked. Existing checkouts keep using their local file;
// fresh clones fall back to Android Gradle Plugin's standard ~/.android/debug.keystore.
val localDebugKeystore = file("${rootDir}/debug.keystore")

android {
  namespace = "com.vurka.emogy"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.vurka.emogy"
    minSdk = 24
    targetSdk = 37
    versionCode = 4
    versionName = "2.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Debug builds always sign with the local debug key so `installDebug` keeps working.
  // Release builds never fall back to it: if no upload key is configured, the packaging
  // tasks are blocked by the `verifyReleaseSigning` task defined at the bottom of this file.
  signingConfigs {
    create("release") {
      val storePass = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
      val keyPass = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
      if (releaseKeystoreFile.exists()) {
        storeFile = releaseKeystoreFile
        storePassword = storePass
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = keyPass
      }
    }
    create("debugConfig") {
      if (localDebugKeystore.exists()) {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Keep the local untracked debug key when present so existing installs can be updated in
      // place. A fresh clone has no debug.keystore, so AGP's default debug signing is used.
      if (localDebugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric needs access to a couple of JDK internals when running on JDK 21+.
      all {
        it.jvmArgs(
          "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
          "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
          "--enable-native-access=ALL-UNNAMED"
        )
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

/**
 * Guards release packaging so a build can never be signed with the public debug key.
 * It runs only for the tasks that actually produce a signed release artifact, which keeps
 * `assembleDebug` and `lintRelease` working without publishing credentials.
 */
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
  group = "verification"
  description = "Aborts unless a real upload keystore and its passwords are configured."
  // Captured as plain values so this action stays compatible with Gradle's configuration cache.
  val defaultKeystore = File(rootDir, "my-upload-key.jks").absolutePath
  doFirst {
    val keystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() } ?: defaultKeystore
    val missing = buildList {
      if (!File(keystorePath).exists()) add("keystore file ($keystorePath)")
    }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Release signing is not configured. Missing: ${missing.joinToString()}. " +
          "Set KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD (plus optional KEY_ALIAS) or place " +
          "my-upload-key.jks in the project root. See RELEASE.md. " +
          "The app is deliberately never signed with debug.keystore for release."
      )
    }
  }
}

/**
 * Single source of truth for the legal text: `docs/` is what GitHub Pages publishes, and this task
 * copies the same files into the app's offline assets before every build. A unit test
 * (LegalDocsTest) fails if the two ever differ.
 */
val syncLegalDocs = tasks.register<Copy>("syncLegalDocs") {
  group = "documentation"
  description = "Copies docs/privacy-policy.md, terms-of-service.md and credits.md into app assets."
  from(File(rootDir, "docs")) { include("privacy-policy.md", "terms-of-service.md", "credits.md") }
  into(File(projectDir, "src/main/assets/legal"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncLegalDocs) }

/**
 * A release must never ship with the REPLACE_WITH_* placeholders still in the legal pages or the
 * support address. Debug builds and tests are unaffected.
 */
val verifyReleaseContent = tasks.register("verifyReleaseContent") {
  group = "verification"
  description = "Aborts the release build while any REPLACE_WITH_* placeholder is left in the docs."
  val watched = listOf(
    File(rootDir, "docs/privacy-policy.md"),
    File(rootDir, "docs/terms-of-service.md"),
    File(rootDir, "docs/credits.md"),
    File(rootDir, "docs/index.md"),
    File(projectDir, "src/main/res/values/strings.xml"),
  )
  doFirst {
    val offenders = watched.filter { it.exists() && it.readText().contains("REPLACE_WITH_") }
    if (offenders.isNotEmpty()) {
      throw GradleException(
        "Placeholders are still present in: " + offenders.joinToString { it.name } +
          ". Search the project for REPLACE_WITH_ and fill in your developer name, support email " +
          "and country/state, then run ./gradlew syncLegalDocs. See EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md, section 6."
      )
    }
  }
}

tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }
  .configureEach { dependsOn(verifyReleaseSigning, verifyReleaseContent) }

// Lean dependency set: the game is fully offline, so there is no networking, analytics, ads,
// Firebase or location code in the app. That keeps the APK small and the Play "Data safety"
// form trivial ("no data collected, no data shared").
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
