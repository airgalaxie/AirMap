package org.dynmap.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A Minecraft resource-pack stack ordered from highest to lowest priority. */
public final class LayeredMinecraftResourceProvider implements MinecraftResourceProvider {
    private final List<MinecraftResourceProvider> layers;

    public LayeredMinecraftResourceProvider(MinecraftResourceProvider... layers) {
        this.layers = List.of(layers);
    }

    @Override
    public Set<String> list(String pathPrefix, String suffix) throws IOException {
        Set<String> resources = new LinkedHashSet<>();
        for (MinecraftResourceProvider layer : layers) resources.addAll(layer.list(pathPrefix, suffix));
        return resources;
    }

    @Override
    public InputStream open(String resourceId) throws IOException {
        IOException missing = null;
        for (MinecraftResourceProvider layer : layers) {
            try {
                return layer.open(resourceId);
            } catch (IOException exception) {
                missing = exception;
            }
        }
        throw new IOException("Missing Minecraft resource " + resourceId, missing);
    }
}
