package org.dynmap.resources;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only view of a Minecraft resource-pack ZIP or client JAR. */
public final class ZipMinecraftResourceProvider implements MinecraftResourceProvider {
    private final Path archive;

    public ZipMinecraftResourceProvider(Path archive) {
        this.archive = archive.toAbsolutePath().normalize();
    }

    @Override
    public String fingerprint() throws IOException {
        // Hash the archive once instead of reopening it for every model and texture.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = new java.security.DigestInputStream(java.nio.file.Files.newInputStream(archive), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Override
    public Set<String> list(String pathPrefix, String suffix) throws IOException {
        String prefix = normalize(pathPrefix);
        Set<String> resources = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("assets/") || !name.endsWith(suffix)) continue;
                String[] parts = name.substring("assets/".length()).split("/", 2);
                if (parts.length == 2 && parts[1].startsWith(prefix)) resources.add(parts[0] + ":" + parts[1]);
            }
        }
        return resources;
    }

    @Override
    public InputStream open(String resourceId) throws IOException {
        String[] parts = resourceId.split(":", 2);
        if (parts.length != 2 || !safe(parts[0]) || !safe(parts[1])) throw new IOException("Invalid Minecraft resource id " + resourceId);
        ZipFile zip = new ZipFile(archive.toFile());
        ZipEntry entry = zip.getEntry("assets/" + parts[0] + "/" + parts[1]);
        if (entry == null || entry.isDirectory()) {
            zip.close();
            throw new IOException("Missing Minecraft resource " + resourceId + " in " + archive);
        }
        return new FilterInputStream(zip.getInputStream(entry)) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { zip.close(); }
            }
        };
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static boolean safe(String path) {
        return !path.isEmpty() && !path.startsWith("/") && !path.contains("\\") && !path.contains("../") && !path.equals("..");
    }
}
