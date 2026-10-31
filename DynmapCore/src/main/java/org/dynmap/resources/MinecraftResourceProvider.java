package org.dynmap.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.security.NoSuchAlgorithmException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.HexFormat;

/** Platform-neutral view of the effective Minecraft resource pack stack. */
public interface MinecraftResourceProvider {
    Set<String> list(String pathPrefix, String suffix) throws IOException;
    InputStream open(String resourceId) throws IOException;

    /** Content identity of the complete effective asset set, including additions and removals. */
    default String fingerprint() throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            for (String id : list("", "").stream().sorted().toList()) {
                output.writeUTF(id);
                // Frame the content hash so adjacent resources cannot produce ambiguous input.
                MessageDigest content;
                try { content = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
                try (var input = new java.security.DigestInputStream(open(id), content)) {
                    input.transferTo(OutputStream.nullOutputStream());
                }
                output.write(content.digest());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
