package org.dynmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void proprietaryTxtResourcesRemainTxtOnly() {
        assertNotNull(getClass().getResource("/extracted/colorschemes/default.txt"));
        assertNull(getClass().getResource("/extracted/colorschemes/default.yaml"));
        assertNotNull(getClass().getResource("/extracted/web/robots.txt"));
        assertNull(getClass().getResource("/extracted/web/robots.yaml"));
    }
}
