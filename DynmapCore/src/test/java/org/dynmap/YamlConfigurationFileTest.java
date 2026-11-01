package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlConfigurationFileTest {
    @TempDir
    Path directory;

    @Test
    void yamlIsPreferredWhenBothExtensionsExist() throws IOException {
        Files.writeString(directory.resolve("configuration.txt"), "legacy: true\n");
        Files.writeString(directory.resolve("configuration.yaml"), "current: true\n");

        File selected = DynmapCore.getYamlConfigurationFile(directory.toFile(), "configuration");

        assertEquals("configuration.yaml", selected.getName());
    }

    @Test
    void txtIsUsedAsFallback() throws IOException {
        Files.writeString(directory.resolve("worlds.txt"), "worlds: []\n");

        File selected = DynmapCore.getYamlConfigurationFile(directory.toFile(), "worlds");

        assertEquals("worlds.txt", selected.getName());
    }

    @Test
    void existingTxtPreventsDefaultYamlExtraction() throws IOException {
        Path legacy = directory.resolve("worlds.txt");
        Files.writeString(legacy, "worlds: []\n");
        DynmapCore core = new DynmapCore();
        File selected = DynmapCore.getYamlConfigurationFile(directory.toFile(), "worlds");

        assertTrue(core.createDefaultFileFromResource("/worlds.yaml", selected));

        assertEquals("worlds: []\n", Files.readString(legacy));
        assertFalse(Files.exists(directory.resolve("worlds.yaml")));
    }

    @Test
    void missingConfigurationIsInstalledWithYamlExtension() {
        DynmapCore core = new DynmapCore();
        File selected = DynmapCore.getYamlConfigurationFile(directory.toFile(), "worlds");

        assertTrue(core.createDefaultFileFromResource("/worlds.yaml", selected));

        assertEquals("worlds.yaml", selected.getName());
        assertTrue(selected.isFile());
    }

    @Test
    void legacyTxtTemplateStillLoads() throws IOException {
        Path templates = Files.createDirectory(directory.resolve("templates"));
        Files.writeString(templates.resolve("custom-example.txt"),
                "templates:\n  legacy-template:\n    enabled: true\n");
        DynmapCore core = new DynmapCore();
        core.setDataFolder(directory.toFile());
        core.configuration = new ConfigurationNode();

        core.loadTemplates();

        assertTrue(core.configuration.getNode("templates").getNode("legacy-template").getBoolean("enabled", false));
    }

    @Test
    void defaultReferenceUpdatesOnlyWhenChangedAndPreservesUserConfigurations() throws IOException {
        Path yaml = directory.resolve("configuration.yaml");
        Path legacy = directory.resolve("configuration.txt");
        Files.writeString(yaml, "render-performance-auto: aggressive\n");
        Files.writeString(legacy, "render-performance-auto: false\n");
        Path reference = directory.resolve("configuration.default.yaml");
        byte[] defaults = "render-performance-auto: normal\n".getBytes(StandardCharsets.UTF_8);

        DynmapCore.writeDefaultConfigurationTemplate(reference, "2.2.0-test", defaults);

        String content = Files.readString(reference);
        assertTrue(content.startsWith("# AirMap 2.2.0-test"));
        assertTrue(content.endsWith(new String(defaults, StandardCharsets.UTF_8)));
        FileTime unchangedTime = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(reference, unchangedTime);
        DynmapCore.writeDefaultConfigurationTemplate(reference, "2.2.0-test", defaults);
        assertEquals(unchangedTime, Files.getLastModifiedTime(reference));

        byte[] updatedDefaults = ("render-performance-auto: normal\n"
                + "render-performance-auto-log-changes: false\n").getBytes(StandardCharsets.UTF_8);
        DynmapCore.writeDefaultConfigurationTemplate(reference, "2.2.0-test", updatedDefaults);
        assertTrue(Files.readString(reference).endsWith(new String(updatedDefaults, StandardCharsets.UTF_8)));
        DynmapCore.writeDefaultConfigurationTemplate(reference, "2.2.1-test", updatedDefaults);
        assertTrue(Files.readString(reference).startsWith("# AirMap 2.2.1-test"));
        assertEquals("render-performance-auto: aggressive\n", Files.readString(yaml));
        assertEquals("render-performance-auto: false\n", Files.readString(legacy));
        try (var files = Files.list(directory)) {
            assertEquals(3L, files.count());
        }
    }

    @Test
    void proprietaryTxtResourcesRemainTxtOnly() {
        assertNotNull(getClass().getResource("/extracted/colorschemes/default.txt"));
        assertNull(getClass().getResource("/extracted/colorschemes/default.yaml"));
        assertNotNull(getClass().getResource("/extracted/web/robots.txt"));
        assertNull(getClass().getResource("/extracted/web/robots.yaml"));
    }
}
