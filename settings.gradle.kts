pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "AirMap"

include(":fabric")
include(":DynmapCore")
include(":DynmapCoreAPI")

project(":fabric").projectDir = file("$rootDir/fabric")
project(":DynmapCore").projectDir = file("$rootDir/DynmapCore")
project(":DynmapCoreAPI").projectDir = file("$rootDir/DynmapCoreAPI")
