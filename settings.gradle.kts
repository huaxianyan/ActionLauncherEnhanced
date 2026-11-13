// 镜像只在本机加速；CI 上镜像不可用时 5xx 会直接让整个解析失败，因此按环境跳过。
pluginManagement {
    val useMirrors = System.getenv("CI").isNullOrEmpty()
    repositories {
        if (useMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useMirrors = System.getenv("CI").isNullOrEmpty()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ActionLauncherEnhanced"
include(":app")
