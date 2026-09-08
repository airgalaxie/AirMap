package org.dynmap.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.dynmap.ConfigurationNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftClientResourcesTest {
    @TempDir
    Path cacheDirectory;

    @Test
    void usesValidCachedClientWhenDownloadIsNotAccepted() throws Exception {
        String version = "test-version";
        Path client = cacheDirectory.resolve("minecraft-client-" + version + ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(client))) {
            output.putNextEntry(new ZipEntry("assets/minecraft/test.txt"));
            output.write("cached".getBytes());
            output.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(client);
        String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        Files.writeString(cacheDirectory.resolve("minecraft-" + version + ".json"),
                "{\"id\":\"" + version + "\",\"downloads\":{\"client\":{"
                        + "\"url\":\"https://invalid.example/client.jar\",\"sha1\":\"" + sha1
                        + "\",\"size\":" + bytes.length + "}}}");

        MinecraftResourceProvider resources = MinecraftClientResources.provision(cacheDirectory, version, false);
        ConfigurationNode configurationWithoutKey = new ConfigurationNode();
        MinecraftResourceProvider resourcesWithMissingKey = MinecraftClientResources.provision(
                cacheDirectory, version,
                configurationWithoutKey.getBoolean("accept-minecraft-client-download", false));

        assertNotNull(resources);
        assertNotNull(resourcesWithMissingKey);
        try (InputStream input = resources.open("minecraft:test.txt")) {
            assertTrue(input.read() >= 0);
        }
    }

    @Test
    void refusesDownloadWhenClientIsMissingAndDownloadIsNotAccepted() {
        String version = "missing-client";
        writeMetadata(version, "https://invalid.example/client.jar", "0000000000000000000000000000000000000000", 1);

        MinecraftClientResources.DownloadNotPermittedException exception = assertThrows(
                MinecraftClientResources.DownloadNotPermittedException.class,
                () -> MinecraftClientResources.provision(cacheDirectory, version, false));

        assertTrue(exception.getMessage().contains("accept-minecraft-client-download: true"));
    }

    @Test
    void missingConfigurationKeyDoesNotPermitDownload() {
        String version = "missing-key";
        writeMetadata(version, "https://invalid.example/client.jar", "0000000000000000000000000000000000000000", 1);
        ConfigurationNode configuration = new ConfigurationNode();

        assertThrows(MinecraftClientResources.DownloadNotPermittedException.class,
                () -> MinecraftClientResources.provision(cacheDirectory, version,
                        configuration.getBoolean("accept-minecraft-client-download", false)));
    }

    @Test
    void acceptedDownloadUsesExistingDownloadPath() throws Exception {
        byte[] clientBytes = clientJarBytes();
        String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(clientBytes));
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/client.jar", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, clientBytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(clientBytes);
            }
        });
        server.start();
        try {
            String version = "accepted";
            writeMetadata(version, "http://localhost:" + server.getAddress().getPort() + "/client.jar",
                    sha1, clientBytes.length);

            MinecraftResourceProvider resources = MinecraftClientResources.provision(cacheDirectory, version, true);

            assertNotNull(resources);
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private void writeMetadata(String version, String url, String sha1, long size) {
        try {
            Files.writeString(cacheDirectory.resolve("minecraft-" + version + ".json"),
                    "{\"id\":\"" + version + "\",\"downloads\":{\"client\":{"
                            + "\"url\":\"" + url + "\",\"sha1\":\"" + sha1
                            + "\",\"size\":" + size + "}}}");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static byte[] clientJarBytes() throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("assets/minecraft/test.txt"));
            output.write("downloaded".getBytes());
            output.closeEntry();
        }
        return bytes.toByteArray();
    }
}
