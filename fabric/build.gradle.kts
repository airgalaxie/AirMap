plugins {
    java
    `maven-publish`
    alias(libs.plugins.fabricLoom)
}

group = "us.dynmap"
version = libs.versions.airmap.get()

base {
    archivesName.set("AirMap")
}

val minecraftVersion = libs.versions.minecraft.get()
val localBuildNumber = rootProject.ext.get("buildNumber").toString()
val fabricJarName = "AirMap-${project.version}+build.$localBuildNumber-fabric.jar"

dependencies {
    // Minecraft-Skelett laden
    minecraft("com.mojang:minecraft:$minecraftVersion")

    // Standard-Abhängigkeiten im unverschlüsselten Namespace
    implementation(libs.fabricLoader)
    implementation(libs.fabricApi)

    implementation(project(":DynmapCore"))
//    implementation(project(":dynmap-api"))
    implementation(project(":DynmapCoreAPI"))
    include(project(path = ":DynmapCore", configuration = "shadowRuntimeElements"))
//    include(project(":dynmap-api"))
    include(project(":DynmapCoreAPI"))

    compileOnly(libs.jsr305)
    compileOnly(libs.fabricPermissionsApi)
    compileOnly(libs.luckpermsApi)
}

loom {
    accessWidenerPath.set(layout.projectDirectory.file("src/main/resources/dynmap.accesswidener"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", minecraftVersion)
    inputs.property("fabricLoaderVersion", libs.versions.fabricLoader.get())
    inputs.property("fabricApiVersion", libs.versions.fabricApi.get())
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "fabricLoaderVersion" to libs.versions.fabricLoader.get(),
            "fabricApiVersion" to libs.versions.fabricApi.get()
        )
    }
}

tasks.jar {
    archiveFileName.set(fabricJarName)
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("target"))
    dependsOn(project(":DynmapCore").tasks.named("processResources"))
    from(project(":DynmapCore").layout.buildDirectory.dir("resources/main")) {
        include("extracted/**", "deleted.txt")
    }
}
