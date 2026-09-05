package org.dynmap.hdmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MinecraftModelLayerRegistryTest {
    private final MinecraftModelLayerRegistry registry = loadRegistry();

    @Test
    void resolvesTwoIndependentSpecialModelFamilies() {
        assertEquals(new MinecraftModelLayerRegistry.Binding("chest", "entity/chest/"),
                registry.resolve("chest", "minecraft:block/chest", Map.of("facing", "north")));
        assertEquals(new MinecraftModelLayerRegistry.Binding("shulker_box", "entity/shulker/"),
                registry.resolve("shulker_box", "minecraft:block/shulker_box", Map.of("facing", "up")));
    }

    @Test
    void appendsBellLayerSelectedByRegularBlockModel() {
        assertEquals(new MinecraftModelLayerRegistry.Binding("bell_floor", ""),
                registry.resolve(null, "minecraft:block/bell_floor",
                        Map.of("attachment", "floor", "facing", "east")));
        assertEquals(new MinecraftModelLayerRegistry.Binding("bell_between_walls", ""),
                registry.resolve(null, "minecraft:block/bell_between_walls",
                        Map.of("attachment", "double_wall", "facing", "north")));
    }

    @Test
    void selectsStaticCopperGolemPoseFromState() {
        assertEquals(new MinecraftModelLayerRegistry.Binding("copper_golem_statue_running", ""),
                registry.resolve("copper_golem_statue", "minecraft:block/copper_golem_statue",
                        Map.of("special.pose", "running")));
        assertEquals(new MinecraftModelLayerRegistry.Binding("copper_golem_statue_standing", ""),
                registry.resolve("copper_golem_statue", "minecraft:block/copper_golem_statue", Map.of()));
    }

    @Test
    void leavesUnknownSpecialModelsUnsupportedUntilARegistryEntryAndLayerExist() {
        assertNull(registry.resolve("future_model", "minecraft:block/future_model", Map.of()));
    }

    private static MinecraftModelLayerRegistry loadRegistry() {
        try {
            return MinecraftModelLayerRegistry.load();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
