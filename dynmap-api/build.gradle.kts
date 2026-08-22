plugins {
    alias(libs.plugins.shadow)
    id("dynmap.java-conventions")
}

description = "dynmap-api"

dependencies {
    compileOnly(project(":DynmapCoreAPI"))
    compileOnly(libs.paperApi)
}

tasks {
    jar {
       archiveClassifier = "unshaded"
    }

    shadowJar {
        dependencies {
            include(dependency(":DynmapCoreAPI"))
        }
        destinationDirectory = file("../target")
        archiveClassifier = ""
    }

    artifacts {
        archives(shadowJar)
    }
}
