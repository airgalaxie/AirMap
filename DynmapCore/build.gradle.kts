plugins {
    alias(libs.plugins.shadow)
    id("dynmap.java-conventions")
}

description = "DynmapCore"

val jdbcDrivers by configurations.creating {
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
}

tasks {
    processResources {
        inputs.property("airMapVersion", project.parent!!.ext.get("airMapVersion").toString())
        inputs.property("buildNumber", project.parent!!.ext.get("buildNumber").toString())
        inputs.property("dynmapCompatibilityVersion", project.version.toString())
        inputs.property("revision", project.parent!!.ext.get("revision").toString())

        from(jdbcDrivers) {
            into("extracted/drivers")
        }

        // replace stuff in mcmod.info, nothing else
        filesMatching(
            listOf(
            "core.yml",
            "lightings.txt",
            "perspectives.txt",
            "shaders.txt",
            "extracted/web/version.js",
            "extracted/web/index.html"
                )) {
            // replace version and mcversion
            expand(
                    "buildnumber" to project.parent!!.ext.get("buildNumber").toString(),
                    "airmapversion" to project.parent!!.ext.get("airMapVersion").toString(),
                    "revision" to project.parent!!.ext.get("revision").toString(),
                    "version" to project.version
            )
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

    artifacts {
        archives(shadowJar)
    }
}
