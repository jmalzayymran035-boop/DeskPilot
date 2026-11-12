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
rootProject.name = "DeskPilot"
include(":app")
include(":freeRDPCore")
project(":freeRDPCore").projectDir = file("FreeRDP/client/Android/Studio/freeRDPCore")
