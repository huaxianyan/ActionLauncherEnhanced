import java.util.Properties

plugins {
    id("com.android.application")
}

// 签名材料不入库：优先环境变量，其次 -P 属性，最后读本机 signing.properties
// （默认 ~/.android/signing/<项目名>/signing.properties，可用 -PsigningProperties=... 覆盖）。
val localSigningFile = providers.gradleProperty("signingProperties")
    .orNull
    ?.let(::file)
    ?: File(System.getProperty("user.home"), ".android/signing/ActionLauncherEnhanced/signing.properties")
val localSigning: Properties? = localSigningFile
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

fun signingValue(environmentName: String, propertyName: String, localKey: String): String? =
    providers.environmentVariable(environmentName)
        .orElse(providers.gradleProperty(propertyName))
        .orNull
        ?: localSigning?.getProperty(localKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "releaseStoreFile", "storeFile")
val releaseStorePassword =
    signingValue("RELEASE_STORE_PASSWORD", "releaseStorePassword", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "releaseKeyAlias", "keyAlias")
val releaseKeyPassword =
    signingValue("RELEASE_KEY_PASSWORD", "releaseKeyPassword", "keyPassword")
val releaseStoreType =
    signingValue("RELEASE_STORE_TYPE", "releaseStoreType", "storeType") ?: "PKCS12"
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
        versionCode = 9
        versionName = "0.6.0"
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
        debug {
            // 本机 Debug 包同样用正式身份签名，避免与发布包不能互相覆盖安装。
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
