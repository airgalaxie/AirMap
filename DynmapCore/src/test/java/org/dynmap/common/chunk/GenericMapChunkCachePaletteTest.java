package org.dynmap.common.chunk;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import org.dynmap.renderer.DynmapBlockState;
import org.junit.jupiter.api.Test;

class GenericMapChunkCachePaletteTest {
    private static final String BLOCK_NAME = "airmap_test:palette_block";
    private static final DynmapBlockState ENABLED;

    static {
        DynmapBlockState base = new DynmapBlockState.Builder()
                .setBlockName(BLOCK_NAME)
                .setStateIndex(0)
                .setStateName("enabled=false")
                .build();
        ENABLED = new DynmapBlockState.Builder()
                .setBaseState(base)
                .setBlockName(BLOCK_NAME)
                .setStateIndex(1)
                .setStateName("enabled=true")
                .build();
    }

    @Test
    void readsLegacyNameAndPropertiesUnchanged() {
        GenericNBTCompound entry = compound(Map.of(
                "Name", BLOCK_NAME,
                "Properties", compound(Map.of("enabled", "true"))));

        assertSame(ENABLED, GenericMapChunkCache.getPaletteBlockState(entry));
    }

    @Test
    void readsMinecraft263IdAndProperties() {
        GenericNBTCompound entry = compound(Map.of(
                "id", BLOCK_NAME,
                "properties", compound(Map.of("enabled", "true"))));

        assertSame(ENABLED, GenericMapChunkCache.getPaletteBlockState(entry));
    }

    @Test
    void prefersMinecraft263KeysWhenBothFormatsArePresent() {
        GenericNBTCompound entry = compound(Map.of(
                "id", BLOCK_NAME,
                "Name", "minecraft:air",
                "properties", compound(Map.of("enabled", "true")),
                "Properties", compound(Map.of("enabled", "false"))));

        assertSame(ENABLED, GenericMapChunkCache.getPaletteBlockState(entry));
    }

    private static GenericNBTCompound compound(Map<String, Object> values) {
        Map<String, Object> ordered = new LinkedHashMap<>(values);
        return (GenericNBTCompound) Proxy.newProxyInstance(
                GenericNBTCompound.class.getClassLoader(),
                new Class<?>[] { GenericNBTCompound.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "contains" -> ordered.containsKey(args[0]);
                    case "getAllKeys" -> ordered.keySet();
                    case "getString", "getAsString" -> String.valueOf(ordered.get(args[0]));
                    case "getCompound" -> ordered.get(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
