import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
    `maven-publish`
}

//https://github.com/gradle/gradle/issues/15383
val libs = the<LibrariesForLibs>()

group = "us.dynmap"
version = libs.versions.dynmap.get()

repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.mikeprimm.com/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    mavenCentral()
    mavenLocal()
}

java {
    toolchain {
        languageVersion.set(
            libs.versions.java.map {
                JavaLanguageVersion.of(it.toInt())
            }
        )
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.compilerArgs.remove("-Werror")
        options.encoding = "UTF-8"
    }

    clean {
      delete("target")
    }
}
