import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** A Gradle property (`-Pname=value` or gradle.properties), or [default]. */
fun prop(name: String, default: String? = null): String =
    (findProperty(name) as String?) ?: default ?: error("Missing Gradle property $name")

/** Quotes a value for use in a `buildConfigField` of type String. */
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Release signing is read from keystore.properties (local) or the environment (CI).
// Neither exists in the repository: without them the release build is left unsigned.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "app.unlimitedmessaging.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.unlimitedmessaging.android"
        minSdk = 26
        targetSdk = 35
        // CI passes the build number (see .github/workflows/release.yml), so
        // every release installs over the previous one. Local builds stay 1.
        versionCode = prop("um.versionCode", "1").toInt()
        versionName = prop("um.versionName", "0.1.0-local")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OAUTH_CLIENT_ID", quoted(prop("um.oauthClientId")))
        buildConfigField("String", "OAUTH_SCOPE", quoted(prop("um.oauthScope")))
    }

    signingConfigs {
        val storeFile = signing("storeFile", "UM_ANDROID_KEYSTORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = signing("storePassword", "UM_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "UM_ANDROID_KEY_ALIAS")
                keyPassword = signing("keyPassword", "UM_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField(
                "String", "API_BASE_URL",
                quoted(prop("um.apiBaseUrl", prop("um.apiBaseUrl.debug"))),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField(
                "String", "API_BASE_URL",
                quoted(prop("um.apiBaseUrl", prop("um.apiBaseUrl.release"))),
            )
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        // SEND_SMS / RECEIVE_SMS are the whole point of the app, which is
        // distributed outside Google Play on purpose (see README).
        disable += "ProtectedPermissions"
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
