package org.dynmap.fabric;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;
import org.dynmap.resources.MinecraftResourceProvider;

/** Exposes Minecraft's effective resource stack without interpreting model data. */
final class FabricMinecraftResources implements MinecraftResourceProvider {
    private final List<ModContainer> containers;
    FabricMinecraftResources(MinecraftServer server) { this.containers = List.copyOf(FabricLoader.getInstance().getAllMods()); }

    @Override public Set<String> list(String pathPrefix, String suffix) {
        Set<String> result = new LinkedHashSet<>();
        for (ModContainer container : containers) for (Path root : container.getRootPaths()) {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) continue;
            try (var namespaces = Files.list(assets)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    Path start = namespace.resolve(pathPrefix); if (!Files.isDirectory(start)) continue;
                    try (var files = Files.walk(start)) { files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix))
                            .forEach(p -> result.add(namespace.getFileName() + ":" + namespace.relativize(p).toString().replace('\\','/'))); }
                }
            } catch (IOException e) { throw new IllegalStateException("Cannot enumerate Minecraft assets in " + container.getMetadata().getId(), e); }
        }
        return result;
    }

    @Override public InputStream open(String resourceId) throws IOException {
        String[] id = resourceId.split(":", 2);
        String relative = "assets/" + id[0] + "/" + id[1];
        for (int i = containers.size() - 1; i >= 0; i--) {
            var path = containers.get(i).findPath(relative);
            if (path.isPresent()) return Files.newInputStream(path.get());
        }
        throw new IOException("Missing Minecraft resource " + resourceId);
    }
}
