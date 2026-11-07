plugins {
    alias(libs.plugins.paperweightUserdev)
    id("dynmap.java-conventions")
}

description = "bukkit-helper"

dependencies {
    implementation(project(":DynmapCore", "shadow"))
    implementation(project(":dynmap-api"))

    paperweightDevelopmentBundle(libs.paperDevBundle)
    compileOnly(libs.paperApi)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}
