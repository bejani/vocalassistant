pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Optional mirror for restricted networks. Enable only if required:
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Optional mirror for restricted networks. Enable only if required:
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "VocalAssistant"
include(":app")
