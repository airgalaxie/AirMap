package org.dynmap.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import org.dynmap.Log;

/** Locates and verifies the Mojang client JAR for AirMap's configured Minecraft version. */
public final class MinecraftClientResources {
    private static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String VERSION_RESOURCE = "/airmap-minecraft-version.properties";

    private MinecraftClientResources() { }

    public static final class DownloadNotPermittedException extends IOException {
        private static final long serialVersionUID = 1L;

        private DownloadNotPermittedException(String message) {
            super(message);
        }
    }

    public static String configuredVersion() throws IOException {
        Properties properties = loadProperties();
        String version = properties.getProperty("minecraft.version", "").trim();
        if (version.isEmpty() || version.contains("${")) throw new IOException("Invalid configured Minecraft version");
        return version;
    }

    public static boolean isSupportedPlatform(String platformVersion) throws IOException {
        if (platformVersion == null) return false;
        Properties properties = loadProperties();
        if (platformVersion.equals(properties.getProperty("minecraft.version", "").trim())) return true;
        for (String compatible : properties.getProperty("minecraft.compatible", "").split(",")) {
            if (platformVersion.equals(compatible.trim())) return true;
        }
        return false;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = MinecraftClientResources.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) throw new IOException("Missing " + VERSION_RESOURCE);
            properties.load(input);
        }
        return properties;
    }

    public static MinecraftResourceProvider provision(Path cacheDirectory, String requiredVersion,
            boolean acceptDownload) throws IOException {
        Files.createDirectories(cacheDirectory);
        String fileVersion = requiredVersion.replaceAll("[^A-Za-z0-9._-]", "_");
        Path metadataFile = cacheDirectory.resolve("minecraft-" + fileVersion + ".json");
        JsonObject metadata = readVerifiedMetadata(metadataFile, requiredVersion);
        if (metadata == null) {
            requireDownloadPermission(requiredVersion, acceptDownload);
            metadata = downloadMetadata(requiredVersion);
            if (!requiredVersion.equals(metadata.get("id").getAsString())) {
                throw new IOException("Mojang metadata version does not match " + requiredVersion);
            }
            writeAtomically(metadataFile, metadata.toString().getBytes(StandardCharsets.UTF_8));
        }
        JsonObject client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
        String sha1 = client.get("sha1").getAsString();
        long size = client.get("size").getAsLong();
        Path clientJar = cacheDirectory.resolve("minecraft-client-" + fileVersion + ".jar");
        if (!matches(clientJar, sha1, size)) {
            requireDownloadPermission(requiredVersion, acceptDownload);
            download(client.get("url").getAsString(), clientJar, sha1, size);
        }
        return new ZipMinecraftResourceProvider(clientJar);
    }

    private static void requireDownloadPermission(String requiredVersion, boolean acceptDownload) throws IOException {
        if (acceptDownload) return;
        String message = "Minecraft client download requires explicit permission. "
                + "Set 'accept-minecraft-client-download: true' in configuration.txt "
                + "to permit AirMap to download the required Minecraft client resources "
                + "from the official Mojang/Microsoft source. "
                + "The required Minecraft client " + requiredVersion + " is missing or unusable.";
        Log.warning(message);
        throw new DownloadNotPermittedException(message);
    }

    private static JsonObject readVerifiedMetadata(Path file, String version) {
        if (!Files.isRegularFile(file)) return null;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
            if (!version.equals(metadata.get("id").getAsString())) return null;
            JsonObject client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
            return client.has("url") && client.has("sha1") && client.has("size") ? metadata : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject downloadMetadata(String version) throws IOException {
        JsonObject manifest = getJson(VERSION_MANIFEST);
        for (var value : manifest.getAsJsonArray("versions")) {
            JsonObject entry = value.getAsJsonObject();
            if (version.equals(entry.get("id").getAsString())) return getJson(URI.create(entry.get("url").getAsString()));
        }
        throw new IOException("Minecraft version is not present in Mojang's manifest: " + version);
    }

    private static JsonObject getJson(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "AirMap").build();
        try {
            HttpResponse<InputStream> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + uri);
            try (InputStream body = response.body(); var reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + uri, exception);
        }
    }

    private static void download(String url, Path target, String sha1, long size) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).header("User-Agent", "AirMap").build();
            HttpResponse<Path> response;
            try {
                response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + url, exception);
            }
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
            if (!matches(temporary, sha1, size)) throw new IOException("Downloaded Minecraft client failed Mojang SHA-1/size verification");
            replaceAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean matches(Path file, String expectedSha1, long expectedSize) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) != expectedSize) return false;
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-1 is unavailable", exception); }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedSha1);
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            Files.write(temporary, content);
            replaceAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
