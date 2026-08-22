fun gitOutput(vararg command: String): String? = try {
    providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf(String::isNotEmpty)
} catch (_: Exception) {
    null
}

val airMapRevision = gitOutput("git", "rev-parse", "--short", "HEAD")
    ?: gitOutput("git", "--git-dir=${rootDir}/.airmap-local-git/.git", "rev-parse", "--short", "HEAD")
    ?: System.getenv("GIT_COMMIT")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.take(7)
    ?: "unknown"
val airMapDirty = gitOutput("git", "status", "--porcelain", "--untracked-files=normal") != null
val airMapBuildMetadata = airMapRevision + if (airMapDirty) "-dirty" else ""

project.ext.set("airMapVersion", libs.versions.airmap.get())
project.ext.set("buildNumber", airMapBuildMetadata)
project.ext.set("revision", airMapRevision)
