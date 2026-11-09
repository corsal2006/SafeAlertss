<<<<<<< HEAD

import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Properties

=======
>>>>>>> origin/main
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

<<<<<<< HEAD
// Credentials stay on this machine or in the CI environment, never in the APK/repository.
val releaseSigningProperties = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val secretsProperties = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(name: String): String? = providers.environmentVariable(name).orNull
    ?.takeIf { it.isNotBlank() } ?: releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }
val releaseStorePath = signingValue("KEYSTORE_PATH")
val releaseStorePassword = signingValue("KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("KEY_ALIAS")
val releaseKeyPassword = signingValue("KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

fun mapsApiKeyValue(): String = providers.environmentVariable("MAPS_API_KEY").orNull?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: secretsProperties.getProperty("MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: "DEFAULT_API_KEY"
val mapsApiKey = mapsApiKeyValue()

tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Validate the existing production key without displaying credentials."
    doLast {
        check(releaseSigningReady) {
            "Release signing is not configured. Fill the ignored signing.properties file using signing.properties.example, or set the four KEYSTORE/KEY environment variables. Do not distribute an unsigned or debug APK."
        }
        val storeFile = rootProject.file(releaseStorePath!!)
        check(storeFile.isFile) { "The configured release keystore does not exist." }
        val keyStore = KeyStore.getInstance(storeFile, releaseStorePassword!!.toCharArray())
        check(keyStore.isKeyEntry(releaseKeyAlias)) { "The release alias must identify a private key." }
        check(keyStore.getKey(releaseKeyAlias, releaseKeyPassword!!.toCharArray()) != null) { "The signing key could not be opened." }
        val certificate = keyStore.getCertificate(releaseKeyAlias) as X509Certificate
        certificate.checkValidity()
        check(!certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)) {
            "An Android debug certificate cannot be used as the production signing identity."
        }
        logger.lifecycle("Existing release key validated. No credentials were printed.")
    }
}

=======
>>>>>>> origin/main
android {
    namespace = "com.siddesh.smsalerts"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.siddesh.smsalerts"
        minSdk = 24
        targetSdk = 34
<<<<<<< HEAD
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("boolean", "IS_LOCAL_QA", "false")
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey


        testInstrumentationRunner = "com.siddesh.smsalerts.QaTestRunner"
=======
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
>>>>>>> origin/main
        vectorDrawables {
            useSupportLibrary = true
        }
    }

<<<<<<< HEAD
    signingConfigs {
        if (releaseSigningReady) {
            create("production") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    testBuildType = "qa"
    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "IS_LOCAL_QA", "true")
        }
        release {
            isDebuggable = false
            isJniDebuggable = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("production")
            // Preserve the tested reflection/serialization paths. Shrinking is optional,
            // and is not a way to resolve a Play Protect classification.
=======
    buildTypes {
        release {
>>>>>>> origin/main
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

<<<<<<< HEAD
    // A configured release must pass key validation before it can be packaged.
    if (releaseSigningReady) {
        tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn("verifyReleaseSigning") }
    }

=======
>>>>>>> origin/main
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
<<<<<<< HEAD
        buildConfig = true
=======
>>>>>>> origin/main
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
<<<<<<< HEAD

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

dependencies {
    constraints {
        implementation("com.google.protobuf:protobuf-javalite:3.25.5") {
            because("Fix GHSA-735f-pc8j-v9w8 unknown-field recursion denial of service in the Firestore transitive runtime")
        }
    }
=======
}

dependencies {
>>>>>>> origin/main
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Location Service
    implementation("com.google.android.gms:play-services-location:21.3.0")

<<<<<<< HEAD
    // Google Maps SDK for Android & Maps Compose
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
=======
>>>>>>> origin/main
    // Google Sign-In & Credential Manager
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Coil for profile image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Firebase (Using BoM)
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.1.2")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
<<<<<<< HEAD
    implementation("com.google.firebase:firebase-messaging-ktx")
=======
>>>>>>> origin/main

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager for background safety check-in tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
<<<<<<< HEAD
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
=======
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
>>>>>>> origin/main
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
