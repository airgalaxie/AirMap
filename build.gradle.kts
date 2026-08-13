val airMapBuildNumber = System.getenv("BUILD_NUMBER")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "local"

fun gitRevision(vararg command: String): String? = try {
    providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf(String::isNotEmpty)
} catch (_: Exception) {
    null
}

val airMapRevision = System.getenv("GIT_COMMIT")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.take(7)
    ?: gitRevision("git", "--git-dir=${rootDir}/.airmap-local-git/.git", "rev-parse", "--short=7", "HEAD")
    ?: gitRevision("git", "rev-parse", "--short=7", "HEAD")
    ?: "unknown"

project.ext.set("airMapVersion", libs.versions.airmap.get())
project.ext.set("buildNumber", airMapBuildNumber)
project.ext.set("revision", airMapRevision)
