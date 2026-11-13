plugins {
    id("dynmap.java-conventions")
    alias(libs.plugins.fabricLoom)
}

group = "us.dynmap"
version = libs.versions.airmap.get()

base {
    archivesName.set("AirMap")
}

val minecraftVersion = libs.versions.minecraft.get()
val buildMetadata = rootProject.ext.get("buildNumber").toString()
val fabricJarName = "AirMap-${project.version}+$buildMetadata-fabric.jar"

dependencies {
    // Minecraft-Skelett laden
    minecraft("com.mojang:minecraft:$minecraftVersion")

    // Standard-Abhängigkeiten im unverschlüsselten Namespace
    implementation(libs.fabricLoader)

    implementation(project(":DynmapCore"))
//    implementation(project(":dynmap-api"))
    implementation(project(":DynmapCoreAPI"))
    include(project(path = ":DynmapCore", configuration = "shadowRuntimeElements"))
//    include(project(":dynmap-api"))
    include(project(":DynmapCoreAPI"))

    compileOnly(libs.jsr305)
    implementation(libs.fabricApi)
    compileOnly(libs.luckpermsApi)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

loom {
    accessWidenerPath.set(layout.projectDirectory.file("src/main/resources/airmap.accesswidener"))
}

tasks.processResources {
    val modProperties = mapOf(
        "version" to project.version.toString(),
        "minecraftRuntime" to libs.versions.minecraftRuntime.get(),
        "fabricApiVersion" to libs.versions.fabricApi.get()
    )
    val mariaDbDriverVersion = libs.versions.mariadbJavaClient.get()
    inputs.properties(modProperties)
    inputs.property("mariaDbDriverVersion", mariaDbDriverVersion)
    filesMatching("fabric.mod.json") {
        expand(modProperties)
    }
    filesMatching("configuration.yaml") {
        expand("mariadbdriverversion" to mariaDbDriverVersion)
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

tasks.test {
    useJUnitPlatform()
}
