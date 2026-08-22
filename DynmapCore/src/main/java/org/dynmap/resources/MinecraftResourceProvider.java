package org.dynmap.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Platform-neutral view of the effective Minecraft resource pack stack. */
public interface MinecraftResourceProvider {
    Set<String> list(String pathPrefix, String suffix) throws IOException;
    InputStream open(String resourceId) throws IOException;
}
