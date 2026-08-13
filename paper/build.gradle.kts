import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginYmlPaper)
    id("dynmap.java-conventions")
}

description = "dynmap"
version = libs.versions.airmap.get()

val minecraftVersion = libs.versions.minecraft.get()
val localBuildNumber = rootProject.ext.get("buildNumber").toString()

val paperApiVersion = Regex("""^\d{1,2}\.[1-9][0-9]*(?:\.[1-9][0-9]*)?""")
    .find(libs.versions.paper.get())
    ?.value
    ?: error("Cannot derive Paper API version from '${libs.versions.paper.get()}'")

dependencies {
    implementation(project(":dynmap-api"))
    implementation(project(":DynmapCore", "shadow"))
    implementation(project(":bukkit-helper"))

    compileOnly(libs.paperApi)
    compileOnly(libs.luckpermsApi)
    compileOnly(libs.vaultApi) { exclude("org.bukkit", "bukkit") }
    compileOnly(libs.groupManager)
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            expand(
                "buildnumber" to localBuildNumber,
                "version" to project.version
            )
        }
    }

    jar {
        archiveClassifier = "unshaded"
    }

    shadowJar {
        dependencies {
            include(dependency(":dynmap-api"))
            include(dependency(":DynmapCore"))
            include(dependency(":bukkit-helper"))
        }

        destinationDirectory = rootProject.layout.projectDirectory.dir("target")
        archiveFileName = "AirMap-${project.version}+build.$localBuildNumber-paper.jar"
    }

    artifacts {
        archives(shadowJar)
    }
}

paper {
    name = "AirMap"
    provides = listOf("dynmap")
    main = "org.dynmap.bukkit.DynmapPlugin"

    // Das Plugin behält intern die originale, saubere Version ohne Datums-Suffix
    version = "${project.version}"

    apiVersion = paperApiVersion
    authors = listOf("Jim (AnEnragedPigeon)", "mikeprimm")
    description = "AirMap - an independent project based on Dynmap®"

    serverDependencies {
        register("GroupManager") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("LuckPerms") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
        register("Vault") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
    }

    permissions {
        register("dynmap.render") {
            description = "Allows /dynmap render command"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.show.self") {
            description = "Allows /dynmap show (on self)"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.show.others") {
            description = "Allows /dynmap show <player>"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.hide.self") {
            description = "Allows /dynmap hide (on self)"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.hide.others") {
            description = "Allows /dynmap hide <player>"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.fullrender") {
            description = "Allows /dynmap fullrender or /dynmap fullrender <world>"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.radiusrender") {
            description = "Allows /dynmap radiusrender"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.updaterender") {
            description = "Allows /dynmap updaterender"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.cancelrender") {
            description = "Allows /dynmap cancelrender <world>"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.reload") {
            description = "Allows /dynmap reload"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.stats") {
            description = "Allows /dynmap stats, /dynmap stats <world>, or /dynmap triggerstats"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.resetstats") {
            description = "Allows /dynmap resetstats or /dynmap resetstats <world>"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.sendtoweb") {
            description = "Allows /dynmap sendtoweb"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.purgequeue") {
            description = "Allows /dynmap purgequeue"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.purgemap") {
            description = "Allows /dynmap purgemap"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.purgeworld") {
            description = "Allows /dynmap purgeworld"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.pause") {
            description = "Allows /dynmap pause"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.quiet") {
            description = "Allows /dynmap quiet"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.dumpmemory") {
            description = "Allows /dynmap dumpmemory"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.url") {
            description = "Allows /dynmap url"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.marker.add") {
            description = "Allows /dmarker add"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.update") {
            description = "Allows /dmarker update"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.movehere") {
            description = "Allows /dmarker movehere"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.delete") {
            description = "Allows /dmarker delete"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.list") {
            description = "Allows /dmarker list"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.marker.icons") {
            description = "Allows /dmarker icons"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.marker.sign") {
            description = "Allows creation of markers using signs"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.addset") {
            description = "Allows /dmarker addset"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.updateset") {
            description = "Allows /dmarker updateset"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.deleteset") {
            description = "Allows /dmarker deleteset"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.listsets") {
            description = "Allows /dmarker listsets"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("dynmap.marker.addicon") {
            description = "Allows /dmarker addicon"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("dynmap.marker.updateicon") {
            description = "Allows /dmarker updateicon"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}
