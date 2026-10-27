plugins {
  alias(libs.plugins.hilt)
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kover)
}

android {
  namespace = "com.expenseinterceptor"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.expenseinterceptor"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "1.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Signing secrets come from the environment (CI) or ~/.gradle/gradle.properties, never source.
  val keystoreFile = file("${rootDir}/release-key.jks")
  val keystorePassword =
    providers.environmentVariable("KEYSTORE_PASSWORD").orNull
      ?: providers.gradleProperty("keystorePassword").orNull
  val keyAliasName =
    providers.environmentVariable("KEY_ALIAS").orNull
      ?: providers.gradleProperty("keyAlias").orNull
      ?: "expenseinterceptor"
  val keyPasswordValue =
    providers.environmentVariable("KEY_PASSWORD").orNull
      ?: providers.gradleProperty("keyPassword").orNull
      ?: keystorePassword
  val canSign = keystoreFile.exists() && keystorePassword != null
  signingConfigs {
    if (canSign) {
      create("release") {
        storeFile = keystoreFile
        storePassword = keystorePassword
        keyAlias = keyAliasName
        keyPassword = keyPasswordValue
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      if (canSign) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures { compose = true }

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
  }

  lint {
    abortOnError = true
    checkDependencies = false
    lintConfig = file("lint.xml")
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

// Exported schemas let migrations be tested against the real historical database shapes.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)

  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)

  // ViewModel Compose
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Hilt Dependency Injection
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // Excel import / export
  implementation(libs.apache.poi)

  // Vico charts
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.room.testing)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.compiler)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

kover {
  reports {
    filters {
      excludes {
        // Compose UI, generated Room/KSP code and DI wiring are not exercised by JVM unit tests.
        packages("com.expenseinterceptor.ui.*")
        classes(
          "*_Impl",
          "*_Impl\$*",
          "*ComposableSingletons*",
          "*.BuildConfig",
          "hilt_aggregated_deps.*",
          "dagger.hilt.internal.*",
          "*_HiltModules*",
          "*Hilt_*",
        )
      }
    }
    verify { rule("Minimum line coverage") { minBound(75) } }
  }
}
