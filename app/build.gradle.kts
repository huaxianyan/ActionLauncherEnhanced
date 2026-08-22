plugins {
    id("com.android.application")
}

fun signingValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName)
        .orElse(providers.gradleProperty(propertyName))
        .orNull

val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "releaseStoreFile")
val releaseStorePassword = signingValue("RELEASE_STORE_PASSWORD", "releaseStorePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "releaseKeyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "releaseKeyPassword")
val releaseStoreType = signingValue("RELEASE_STORE_TYPE", "releaseStoreType") ?: "PKCS12"
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.neko7ina.alenhanced"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neko7ina.alenhanced"
        minSdk = 35
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                storeType = releaseStoreType
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("androidx.annotation:annotation:1.9.1")

    testImplementation("junit:junit:4.13.2")
}
