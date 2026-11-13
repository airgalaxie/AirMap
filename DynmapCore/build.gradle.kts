plugins {
    alias(libs.plugins.shadow)
    id("dynmap.java-conventions")
}

description = "DynmapCore"

val jdbcDrivers = configurations.create("jdbcDrivers") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(project(":DynmapCoreAPI"))
    implementation(libs.snakeYaml)
    implementation(libs.owaspHtmlSanitizer)
    implementation(libs.owaspJava8Shim)
    implementation(libs.owaspJava10Shim)
    implementation(libs.gson)
    add(jdbcDrivers.name, libs.mariadbJavaClient)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
    inputs.property("minecraftResourceTestCache", providers.environmentVariable("AIRMAP_MINECRAFT_TEST_CACHE").orElse(""))
}

tasks {
    processResources {
        val resourceProperties = mapOf(
            "buildnumber" to rootProject.ext.get("buildNumber").toString(),
            "airmapversion" to rootProject.ext.get("airMapVersion").toString(),
            "minecraftversion" to libs.versions.minecraft.get(),
            "minecraftcompatible" to libs.versions.minecraftRuntime.get(),
            "mariadbdriverversion" to libs.versions.mariadbJavaClient.get(),
            "revision" to rootProject.ext.get("revision").toString(),
            "version" to project.version.toString()
        )
        inputs.properties(resourceProperties)

        from(jdbcDrivers) {
            into("extracted/drivers")
        }

        // replace stuff in mcmod.info, nothing else
        filesMatching(
            listOf(
            "core.yml",
            "airmap-minecraft-version.properties",
            "lightings.yaml",
            "perspectives.yaml",
            "shaders.yaml",
            "extracted/web/version.js",
            "extracted/web/index.html"
                )) {
            // replace version and mcversion
            expand(resourceProperties)
        }
    }

    jar {
       archiveClassifier = "unshaded"
    }

    shadowJar {
        dependencies {
            include(dependency(libs.snakeYaml))
            include(dependency(libs.owaspHtmlSanitizer))
            include(dependency(libs.owaspJava8Shim))
            include(dependency(libs.owaspJava10Shim))
            include(dependency(":DynmapCoreAPI"))
        }
        exclude("META-INF/maven/**")
        exclude("META-INF/services/**")

        relocate("org.yaml.snakeyaml", "org.dynmap.snakeyaml")
        relocate("org.owasp.html", "org.dynmap.org.owasp.html")
        relocate("org.owasp.shim", "org.dynmap.org.owasp.shim")

        destinationDirectory = file("../target")
        archiveClassifier = ""
    }

    assemble {
        dependsOn(shadowJar)
    }
}
