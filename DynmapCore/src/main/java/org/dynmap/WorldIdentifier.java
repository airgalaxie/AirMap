package org.dynmap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Central handling of world identity at trust boundaries.
 *
 * <p>The canonical ID is an opaque Minecraft registry key and must never be
 * rewritten for display or lookup.  The storage ID is a fixed-size,
 * deterministic path component derived from it; it is deliberately not a
 * lossy "sanitized" form, so distinct registry keys cannot collapse through
 * character replacement.</p>
 */
public final class WorldIdentifier {
    private static final Pattern SAFE_STORAGE_ID = Pattern.compile("world-[0-9a-f]{64}");

    private WorldIdentifier() {
    }

    public static String canonicalId(String id) {
        Objects.requireNonNull(id, "World ID must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("World ID must not be blank");
        }
        if (!id.equals(id.trim()) || id.indexOf('\0') >= 0 || id.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid world ID: " + id);
        }
        return id;
    }

    public static String storageId(String canonicalId) {
        String id = canonicalId(canonicalId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(StandardCharsets.UTF_8));
            return "world-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static boolean isSafeStorageId(String id) {
        return id != null && SAFE_STORAGE_ID.matcher(id).matches();
    }

    /** Accept a historical name only as one relative path component. */
    public static boolean isSafeLegacyPathComponent(String value) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")) {
            return false;
        }
        return value.indexOf('/') < 0 && value.indexOf('\\') < 0 && value.indexOf('\0') < 0;
    }
}
