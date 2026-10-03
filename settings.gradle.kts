pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ZotMobile"
include(":app")
include(":core-ai")
include(":core-agent")
include(":core-git")
include(":core-github")
include(":core-model")
include(":core-project")
include(":core-security")
include(":core-termux")
