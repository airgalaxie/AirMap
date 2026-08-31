pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "AirMap"

include(":paper")
include(":fabric")
include(":bukkit-helper")
include(":dynmap-api")
include(":DynmapCore")
include(":DynmapCoreAPI")

project(":paper").projectDir = file("$rootDir/paper")
project(":fabric").projectDir = file("$rootDir/fabric")
project(":bukkit-helper").projectDir = file("$rootDir/bukkit-helper")
project(":dynmap-api").projectDir = file("$rootDir/dynmap-api")
project(":DynmapCore").projectDir = file("$rootDir/DynmapCore")
project(":DynmapCoreAPI").projectDir = file("$rootDir/DynmapCoreAPI")
