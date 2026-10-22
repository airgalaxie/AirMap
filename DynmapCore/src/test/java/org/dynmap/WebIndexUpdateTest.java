package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebIndexUpdateTest {
    @TempDir
    Path webDirectory;

    @Test
    void missingIndexIsLeftForTheExistingInstallationMechanism() throws IOException {
        Path index = webDirectory.resolve("index.html");

        assertFalse(DynmapCore.updateWebIndexVersion(index.toFile(), "2.1.0-new"));
        assertFalse(Files.exists(index));
        assertFalse(Files.exists(webDirectory.resolve("index.html.old")));
    }

    @Test
    void matchingVersionCookiesAreNotAnUpdateEvenForDevVersions() {
        assertFalse(DynmapCore.isVersionUpdate("3.9-Dev", "3.9-Dev", "3.9-Dev"));
        assertTrue(DynmapCore.isVersionUpdate("3.8", "3.8", "3.9"));
        assertTrue(DynmapCore.isVersionUpdate("3.9", "3.8", "3.9"));
    }

    @Test
    void updatesOnlyAssetVersionsAndPreservesCustomContentAndFormatting() throws IOException {
        Path index = webDirectory.resolve("index.html");
        String original = "<!doctype html>\n"
                + "<!-- custom comment -->\n"
                + "<link href=\"custom.css?_=old-build\">\n"
                + "<!-- <script src='disabled.js?_=older&debug=true'></script> -->\n"
                + "<style>body { color: rebeccapurple; }</style>\n"
                + "<script>window.customSetting = true;</script>\n";
        Files.writeString(index, original, StandardCharsets.UTF_8);
        Object fileKey = Files.readAttributes(index, BasicFileAttributes.class).fileKey();

        assertTrue(DynmapCore.updateWebIndexVersion(index.toFile(), "2.1.0-new"));

        String expected = original
                .replace("?_=old-build", "?_=2.1.0-new")
                .replace("?_=older", "?_=2.1.0-new");
        assertEquals(expected, Files.readString(index, StandardCharsets.UTF_8));
        assertEquals(original, Files.readString(webDirectory.resolve("index.html.old"), StandardCharsets.UTF_8));
        if (fileKey != null) {
            assertEquals(fileKey, Files.readAttributes(index, BasicFileAttributes.class).fileKey());
        }
    }

    @Test
    void repeatedUpdateReplacesTheSingleBackupWithThePreviousIndex() throws IOException {
        Path index = webDirectory.resolve("index.html");
        String first = "<script src=\"app.js?_=one\"></script>\n";
        Files.writeString(index, first, StandardCharsets.UTF_8);
        DynmapCore.updateWebIndexVersion(index.toFile(), "two");
        String second = Files.readString(index, StandardCharsets.UTF_8);

        DynmapCore.updateWebIndexVersion(index.toFile(), "three");

        assertEquals("<script src=\"app.js?_=three\"></script>\n",
                Files.readString(index, StandardCharsets.UTF_8));
        assertEquals(second,
                Files.readString(webDirectory.resolve("index.html.old"), StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(webDirectory)) {
            assertEquals(1, files
                    .filter(path -> path.getFileName().toString().endsWith(".old"))
                    .count());
        }
    }

    @Test
    void preservesPosixOwnerGroupAndPermissions() throws IOException {
        assumeTrue(Files.getFileStore(webDirectory).supportsFileAttributeView("posix"));
        Path index = webDirectory.resolve("index.html");
        Files.writeString(index, "<script src=\"app.js?_=old\"></script>\n", StandardCharsets.UTF_8);
        Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(index, permissions);
        PosixFileAttributes before = Files.readAttributes(index, PosixFileAttributes.class);

        DynmapCore.updateWebIndexVersion(index.toFile(), "new");

        PosixFileAttributes after = Files.readAttributes(index, PosixFileAttributes.class);
        assertEquals(before.owner(), after.owner());
        assertEquals(before.group(), after.group());
        assertEquals(before.permissions(), after.permissions());
    }

    @Test
    void deniedDirectWriteLeavesOriginalIndexInPlace() throws IOException {
        assumeTrue(Files.getFileStore(webDirectory).supportsFileAttributeView("posix"));
        Path index = webDirectory.resolve("index.html");
        String original = "<script src=\"app.js?_=old\"></script>\n";
        Files.writeString(index, original, StandardCharsets.UTF_8);
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(index);
        Files.setPosixFilePermissions(index, EnumSet.of(PosixFilePermission.OWNER_READ));
        assumeFalse(Files.isWritable(index));
        Object fileKey = Files.readAttributes(index, BasicFileAttributes.class).fileKey();
        try {
            assertThrows(IOException.class,
                    () -> DynmapCore.updateWebIndexVersion(index.toFile(), "new"));
            assertEquals(original, Files.readString(index, StandardCharsets.UTF_8));
            if (fileKey != null) {
                assertEquals(fileKey, Files.readAttributes(index, BasicFileAttributes.class).fileKey());
            }
        } finally {
            Files.setPosixFilePermissions(index, originalPermissions);
        }
    }
}
