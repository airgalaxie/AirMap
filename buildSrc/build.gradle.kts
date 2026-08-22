plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

// https://github.com/gradle/gradle/issues/15383
dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}