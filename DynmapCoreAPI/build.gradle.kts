plugins {
    alias(libs.plugins.shadow)
    id("dynmap.java-conventions")
}

description = "DynmapCoreAPI"

tasks {
    jar {
        destinationDirectory = file("../target")
    }

    artifacts {
        archives(jar)
    }
}
