package org.dynmap.common;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

/* Generic biome mapping */
public class BiomeMap {
	public static final int NO_INDEX = -2;
    private static BiomeMap[] biome_by_index = new BiomeMap[256];
    private static Map<String, BiomeMap> biome_by_rl = new HashMap<String, BiomeMap>(256);
    // Tracks registered IDs for O(1) uniqueness checks during initialization
    private static final HashSet<String> biome_ids = new HashSet<String>(256);
    public static final BiomeMap NULL = new BiomeMap(-1, "NULL", 0.5, 0.5, 0xFFFFFF, 0, 0, null);

    public static final BiomeMap OCEAN = new BiomeMap(0, "OCEAN", "minecraft:ocean");
    public static final BiomeMap PLAINS = new BiomeMap(1, "PLAINS", 0.8, 0.4, "minecraft:plains");
    public static final BiomeMap DESERT = new BiomeMap(2, "DESERT", 2.0, 0.0, "minecraft:desert");
    public static final BiomeMap EXTREME_HILLS = new BiomeMap(3, "EXTREME_HILLS", 0.2, 0.3, "minecraft:mountains");
    public static final BiomeMap FOREST = new BiomeMap(4, "FOREST", 0.7, 0.8, "minecraft:forest");
    public static final BiomeMap TAIGA = new BiomeMap(5, "TAIGA", 0.05, 0.8, "minecraft:taiga");
    public static final BiomeMap SWAMPLAND = new BiomeMap(6, "SWAMPLAND", 0.8, 0.9, 0xE0FFAE, 0x2e282a, 0x902c52, "minecraft:swamp");
    public static final BiomeMap RIVER = new BiomeMap(7, "RIVER", "minecraft:river");
    public static final BiomeMap HELL = new BiomeMap(8, "HELL", 2.0, 0.0, "minecraft:nether");
    public static final BiomeMap SKY = new BiomeMap(9, "SKY", "minecraft:the_end");
    public static final BiomeMap FROZEN_OCEAN = new BiomeMap(10, "FROZEN_OCEAN", 0.0, 0.5, "minecraft:frozen_ocean");
    public static final BiomeMap FROZEN_RIVER = new BiomeMap(11, "FROZEN_RIVER", 0.0, 0.5, "minecraft:frozen_river");
    public static final BiomeMap ICE_PLAINS = new BiomeMap(12, "ICE_PLAINS", 0.0, 0.5, "minecraft:snowy_tundra");
    public static final BiomeMap ICE_MOUNTAINS = new BiomeMap(13, "ICE_MOUNTAINS", 0.0, 0.5, "minecraft:snowy_mountains");
    public static final BiomeMap MUSHROOM_ISLAND = new BiomeMap(14, "MUSHROOM_ISLAND", 0.9, 1.0, "minecraft:mushroom_fields");
    public static final BiomeMap MUSHROOM_SHORE = new BiomeMap(15, "MUSHROOM_SHORE", 0.9, 1.0, "minecraft:mushroom_field_shore");
    public static final BiomeMap BEACH = new BiomeMap(16, "BEACH", 0.8, 0.4, "minecraft:beach");
    public static final BiomeMap DESERT_HILLS = new BiomeMap(17, "DESERT_HILLS", 2.0, 0.0, "minecraft:desert_hills");
    public static final BiomeMap FOREST_HILLS = new BiomeMap(18, "FOREST_HILLS", 0.7, 0.8, "minecraft:wooded_hills");
    public static final BiomeMap TAIGA_HILLS = new BiomeMap(19, "TAIGA_HILLS", 0.05, 0.8, "minecraft:taiga_hills");
    public static final BiomeMap SMALL_MOUNTAINS = new BiomeMap(20, "SMALL_MOUNTAINS", 0.2, 0.8, "minecraft:mountain_edge");
    public static final BiomeMap JUNGLE = new BiomeMap(21, "JUNGLE", 1.2, 0.9, "minecraft:jungle");
    public static final BiomeMap JUNGLE_HILLS = new BiomeMap(22, "JUNGLE_HILLS", 1.2, 0.9, "minecraft:jungle_hills");
    public static final BiomeMap JUNGLE_EDGE = new BiomeMap(23, "JUNGLE_EDGE", 0.95, 0.8, "minecraft:jungle_edge");
    public static final BiomeMap DEEP_OCEAN = new BiomeMap(24, "DEEP_OCEAN", "minecraft:deep_ocean");
    public static final BiomeMap STONE_BEACH = new BiomeMap(25, "STONE_BEACH", 0.2, 0.3, "minecraft:stone_shore");
    public static final BiomeMap COLD_BEACH = new BiomeMap(26, "COLD_BEACH", 0.05, 0.3, "minecraft:snowy_beach");
    public static final BiomeMap BIRCH_FOREST = new BiomeMap(27, "BIRCH_FOREST", 0.6, 0.6, "minecraft:birch_forest");
    public static final BiomeMap BIRCH_FOREST_HILLS = new BiomeMap(28, "BIRCH_FOREST_HILLS", 0.6, 0.6, "minecraft:birch_forest_hills");
    public static final BiomeMap ROOFED_FOREST = new BiomeMap(29, "ROOFED_FOREST", 0.7, 0.8, 0xFFFFFF, 0x28340A, 0, "minecraft:dark_forest");
    public static final BiomeMap COLD_TAIGA = new BiomeMap(30, "COLD_TAIGA", -0.5, 0.4, "minecraft:snowy_taiga");
    public static final BiomeMap COLD_TAIGA_HILLS = new BiomeMap(31, "COLD_TAIGA_HILLS", -0.5, 0.4, "minecraft:snowy_taiga_hills");
    public static final BiomeMap MEGA_TAIGA = new BiomeMap(32, "MEGA_TAIGA", 0.3, 0.8, "minecraft:giant_tree_taiga");
    public static final BiomeMap MEGA_TAIGA_HILLS = new BiomeMap(33, "MEGA_TAIGA_HILLS", 0.3, 0.8, "minecraft:giant_tree_taiga_hills");
    public static final BiomeMap EXTREME_HILLS_PLUS = new BiomeMap(34, "EXTREME_HILLS_PLUS", 0.2, 0.3, "minecraft:wooded_mountains");
    public static final BiomeMap SAVANNA = new BiomeMap(35, "SAVANNA", 1.2, 0.0, "minecraft:savanna");
    public static final BiomeMap SAVANNA_PLATEAU = new BiomeMap(36, "SAVANNA_PLATEAU", 1.0, 0.0, "minecraft:savanna_plateau");
    public static final BiomeMap MESA = new BiomeMap(37, "MESA", 2.0, 0.0, 0xFFFFFF, 0x624c46, 0x8e5e70, "minecraft:badlands");
    public static final BiomeMap MESA_FOREST = new BiomeMap(38, "MESA_FOREST", 2.0, 0.0, 0xFFFFFF, 0x624c46, 0x8e5e70, "minecraft:wooded_badlands");
    public static final BiomeMap SMALL_END_ISLANDS = new BiomeMap(40, "SMALL_END_ISLANDS", "minecraft:small_end_islands");
    public static final BiomeMap END_MIDLANDS = new BiomeMap(41, "END_MIDLANDS", "minecraft:end_midlands");
    public static final BiomeMap END_HIGHLANDS = new BiomeMap(42, "END_HIGHLANDS", "minecraft:end_highlands");
    public static final BiomeMap END_BARRENS = new BiomeMap(43, "END_BARRENS", "minecraft:end_barrens");
    public static final BiomeMap WARM_OCEAN = new BiomeMap(44, "WARM_OCEAN", "minecraft:warm_ocean");
    public static final BiomeMap LUKEWARM_OCEAN = new BiomeMap(45, "LUKEWARM_OCEAN", "minecraft:lukewarm_ocean");
    public static final BiomeMap COLD_OCEAN = new BiomeMap(46, "COLD_OCEAN", "minecraft:cold_ocean");
    public static final BiomeMap DEEP_WARM_OCEAN = new BiomeMap(47, "DEEP_WARM_OCEAN",  "minecraft:deep_warm_ocean");
    public static final BiomeMap DEEP_LUKEWARM_OCEAN = new BiomeMap(48, "DEEP_LUKEWARM_OCEAN", "minecraft:deep_lukewarm_ocean");
    public static final BiomeMap DEEP_COLD_OCEAN = new BiomeMap(49, "DEEP_COLD_OCEAN", "minecraft:deep_cold_ocean");
    public static final BiomeMap DEEP_FROZEN_OCEAN = new BiomeMap(50, "DEEP_FROZEN_OCEAN", "minecraft:deep_frozen_ocean");
    public static final BiomeMap THE_VOID = new BiomeMap(127, "THE_VOID", "minecraft:the_void");
    public static final BiomeMap SUNFLOWER_PLAINS = new BiomeMap(129, "SUNFLOWER_PLAINS", 0.8, 0.4, "minecraft:sunflower_plains");
    public static final BiomeMap DESERT_MOUNTAINS = new BiomeMap(130, "DESERT_MOUNTAINS", 2.0, 0.0, "minecraft:desert_lakes");
    public static final BiomeMap EXTREME_HILLS_MOUNTAINS = new BiomeMap(131, "EXTREME_HILLS_MOUNTAINS", 0.2, 0.3, "minecraft:gravelly_mountains");
    public static final BiomeMap FLOWER_FOREST = new BiomeMap(132, "FLOWER_FOREST", 0.7, 0.8, "minecraft:flower_forest");
    public static final BiomeMap TAIGA_MOUNTAINS = new BiomeMap(133, "TAIGA_MOUNTAINS", 0.05, 0.8, "minecraft:taiga_mountains");
    public static final BiomeMap ICE_PLAINS_SPIKES = new BiomeMap(140, "ICE_PLAINS_SPIKES", 0.0, 0.5, "minecraft:ice_spikes");
    public static final BiomeMap JUNGLE_MOUNTAINS = new BiomeMap(149, "JUNGLE_MOUNTAINS", 1.2, 0.9, "minecraft:modified_jungle");
    public static final BiomeMap JUNGLE_EDGE_MOUNTAINS = new BiomeMap(151, "JUNGLE_EDGE_MOUNTAINS", 0.95, 0.8, "minecraft:modified_jungle_edge");
    public static final BiomeMap BIRCH_FOREST_MOUNTAINS = new BiomeMap(155, "BIRCH_FOREST_MOUNTAINS", 0.6, 0.6, "minecraft:tall_birch_forest");
    public static final BiomeMap BIRCH_FOREST_HILLS_MOUNTAINS = new BiomeMap(156, "BIRCH_FOREST_HILLS_MOUNTAINS", 0.6, 0.6, "minecraft:tall_birch_hills");
    public static final BiomeMap ROOFED_FOREST_MOUNTAINS = new BiomeMap(157, "ROOFED_FOREST_MOUNTAINS", 0.7, 0.8, 0xFFFFFF, 0x28340A, 0, "minecraft:dark_forest_hills");
    public static final BiomeMap COLD_TAIGA_MOUNTAINS = new BiomeMap(158, "COLD_TAIGA_MOUNTAINS", -0.5, 0.4, "minecraft:snowy_taiga_mountains");
    public static final BiomeMap MEGA_SPRUCE_TAIGA = new BiomeMap(160, "MEGA_SPRUCE_TAIGA", 0.25, 0.8, "minecraft:giant_spruce_taiga");
    public static final BiomeMap MEGA_SPRUCE_TAIGA_HILLS = new BiomeMap(161, "MEGA_SPRUCE_TAIGA_HILLS", 0.3, 0.8, "minecraft:giant_spruce_taiga_hills");
    public static final BiomeMap EXTREME_HILLS_PLUS_MOUNTAINS = new BiomeMap(162, "EXTREME_HILLS_PLUS_MOUNTAINS", 0.2, 0.3, "minecraft:modified_gravelly_mountains");
    public static final BiomeMap SAVANNA_MOUNTAINS = new BiomeMap(163, "SAVANNA_MOUNTAINS", 1.2, 0.0, "minecraft:shattered_savanna");
    public static final BiomeMap SAVANNA_PLATEAU_MOUNTAINS = new BiomeMap(164, "SAVANNA_PLATEAU_MOUNTAINS", 1.0, 0.0, "minecraft:shattered_savanna_plateau");
    public static final BiomeMap MESA_BRYCE = new BiomeMap(165, "MESA_BRYCE", 2.0, 0.0,0xFFFFFF, 0x624c46, 0x8e5e70, "minecraft:eroded_badlands");
    public static final BiomeMap BAMBOO_JUNGLE = new BiomeMap(168, "BAMBOO_JUNGLE", "minecraft:bamboo_jungle");
    public static final BiomeMap BAMBOO_JUNGLE_HILLS = new BiomeMap(169, "BAMBOO_JUNGLE_HILLS", "minecraft:bamboo_jungle_hills");
    public static final BiomeMap SOUL_SAND_VALLEY = new BiomeMap(170, "SOUL_SAND_VALLEY", "minecraft:soul_sand_valley");
    public static final BiomeMap CRIMSON_FOREST = new BiomeMap(171, "CRIMSON_FOREST", "minecraft:crimson_forest");
    public static final BiomeMap WARPED_FOREST = new BiomeMap(172, "WARPED_FOREST", "minecraft:warped_forest");
    public static final BiomeMap BASALT_DELTAS = new BiomeMap(173, "BASALT_DELTAS", "minecraft:basalt_deltas");
    public static final BiomeMap DRIPSTONE_CAVES = new BiomeMap(174, "DRIPSTONE_CAVES", "minecraft:dripstone_caves");
    public static final BiomeMap LUSH_CAVES = new BiomeMap(175, "LUSH_CAVES", "minecraft:lush_caves");
    public static final BiomeMap NETHER_WASTES = new BiomeMap(176, "NETHER_WASTES", 2.0, 0.0, "minecraft:nether_wastes");
    public static final BiomeMap SNOWY_PLAINS = new BiomeMap(177, "SNOWY_PLAINS", 0.0, 0.5, "minecraft:snowy_plains");
    public static final BiomeMap STONY_SHORE = new BiomeMap(178, "STONY_SHORE", 0.2, 0.3, "minecraft:stony_shore");
    public static final BiomeMap OLD_GROWTH_BIRCH_FOREST = new BiomeMap(179, "OLD_GROWTH_BIRCH_FOREST", 0.6, 0.6, "minecraft:old_growth_birch_forest");
    public static final BiomeMap OLD_GROWTH_PINE_TAIGA = new BiomeMap(180, "OLD_GROWTH_PINE_TAIGA", 0.3, 0.8, "minecraft:old_growth_pine_taiga");
    public static final BiomeMap OLD_GROWTH_SPRUCE_TAIGA = new BiomeMap(181, "OLD_GROWTH_SPRUCE_TAIGA", 0.25, 0.8, "minecraft:old_growth_spruce_taiga");
    public static final BiomeMap WINDSWEPT_HILLS = new BiomeMap(182, "WINDSWEPT_HILLS", 0.2, 0.3, "minecraft:windswept_hills");
    public static final BiomeMap WINDSWEPT_GRAVELLY_HILLS = new BiomeMap(183, "WINDSWEPT_GRAVELLY_HILLS", 0.2, 0.3, "minecraft:windswept_gravelly_hills");
    public static final BiomeMap WINDSWEPT_FOREST = new BiomeMap(184, "WINDSWEPT_FOREST", 0.2, 0.3, "minecraft:windswept_forest");
    public static final BiomeMap WINDSWEPT_SAVANNA = new BiomeMap(185, "WINDSWEPT_SAVANNA", 1.1, 0.0, "minecraft:windswept_savanna");
    public static final BiomeMap SPARSE_JUNGLE = new BiomeMap(186, "SPARSE_JUNGLE", 0.95, 0.8, "minecraft:sparse_jungle");
    public static final BiomeMap MEADOW = new BiomeMap(187, "MEADOW", 0.5, 0.8, "minecraft:meadow");
    public static final BiomeMap GROVE = new BiomeMap(188, "GROVE", -0.2, 0.8, "minecraft:grove");
    public static final BiomeMap SNOWY_SLOPES = new BiomeMap(189, "SNOWY_SLOPES", -0.3, 0.9, "minecraft:snowy_slopes");
    public static final BiomeMap FROZEN_PEAKS = new BiomeMap(190, "FROZEN_PEAKS", -0.7, 0.9, "minecraft:frozen_peaks");
    public static final BiomeMap JAGGED_PEAKS = new BiomeMap(191, "JAGGED_PEAKS", -0.7, 0.9, "minecraft:jagged_peaks");
    public static final BiomeMap STONY_PEAKS = new BiomeMap(192, "STONY_PEAKS", 1.0, 0.3, "minecraft:stony_peaks");
    public static final BiomeMap MANGROVE_SWAMP = new BiomeMap(193, "MANGROVE_SWAMP", 0.8, 0.9, "minecraft:mangrove_swamp");
    public static final BiomeMap DEEP_DARK = new BiomeMap(194, "DEEP_DARK", 0.8, 0.4, "minecraft:deep_dark");
    public static final BiomeMap CHERRY_GROVE = new BiomeMap(195, "CHERRY_GROVE", 0.5, 0.8, "minecraft:cherry_grove");
    public static final BiomeMap PALE_GARDEN = new BiomeMap(196, "PALE_GARDEN", 0.7, 0.8, "minecraft:pale_garden");
    public static final BiomeMap SULFUR_CAVES = new BiomeMap(197, "SULFUR_CAVES", 2.0, 0.0, "minecraft:sulfur_caves");

    public static final int LAST_WELL_KNOWN = 197;
    
    private double tmp;
    private double rain;
    private int watercolormult;
    private int grassmult;
    private int foliagemult;
    private Optional<?> biomeObj = Optional.empty();
    private final String id;
    private final String resourcelocation;
    private final int index;
    private int biomeindex256; // Standard biome mapping index (for 256 x 256)
    private boolean isDef;
    
    static {
        for (int i = 0; i < biome_by_index.length; i++) {
            BiomeMap bm = BiomeMap.byBiomeID(i-1);
            if (bm == null) {
                bm = new BiomeMap(i-1, "BIOME_" + (i-1));
                bm.isDef = true;
            }
        }
    }

    private static boolean isUniqueID(String id) {
        return !biome_ids.contains(id);
    }

    /**
     * Encodes a grass/foliage color multiplier for efficient hot-path dispatch:
     *   == 0              → 0 (passthrough: return raw value unchanged)
     *   0 < val ≤ 0xFFFFFF → positive (blend: average with raw)
     *   val > 0xFFFFFF    → -(val & 0xFFFFFF) (negative sentinel: fixed override color)
     */
    private static int encodeColorMult(int val) {
        return (val > 0xFFFFFF) ? -(val & 0xFFFFFF) : val;
    }
    
    private static void resizeIfNeeded(int idx) {
		if ((idx >= biome_by_index.length) ) {
			int oldlen = biome_by_index.length;
			biome_by_index = Arrays.copyOf(biome_by_index, idx * 3 / 2);
			for (int i = oldlen; i < biome_by_index.length; i++) {
				if (biome_by_index[i] == null) {
	                BiomeMap bm = new BiomeMap(i-1, "BIOME_" + (i-1));
	                bm.isDef = true;
				}
			}
		}    	
    }
    
    private BiomeMap(int idx, String id, double tmp, double rain, int waterColorMultiplier, int grassmult, int foliagemult, String rl) {
        /* Clamp values : we use raw values from MC code, which are clamped during color mapping only */
        setTemperature(tmp);
        setRainfall(rain);
        this.watercolormult = waterColorMultiplier;
        this.grassmult = encodeColorMult(grassmult);
        this.foliagemult = encodeColorMult(foliagemult);
        // Handle null biome
        if (id == null) { id = "biome_" + idx; }
        id = id.toUpperCase().replace(' ', '_');
        if (isUniqueID(id) == false) {
            id = id + "_" + idx;
        }
        this.id = id;
        biome_ids.add(this.id);
        // If index is NO_INDEX, find one after the well known ones
        if (idx == NO_INDEX) {
        	idx = LAST_WELL_KNOWN;
        	while (true) {
           		idx++;
           		resizeIfNeeded(idx);
        		if (biome_by_index[idx].isDef) {
        			break;
        		}
        	}
        }
        else {
        	idx++;  /* Insert one after ID value - null is zero index */
        }
        this.index = idx;
        if (idx >= 0) {
        	resizeIfNeeded(idx);
        	biome_by_index[idx] = this;
        }
        this.resourcelocation = rl;
        if (rl != null) {
        	biome_by_rl.put(rl, this);
        }
    }
    public BiomeMap(int idx, String id) {
        this(idx, id, 0.5, 0.5, 0xFFFFFF, 0, 0, null);
    }
    public BiomeMap(int idx, String id, String rl) {
        this(idx, id, 0.5, 0.5, 0xFFFFFF, 0, 0, rl);
    }
    
    public BiomeMap(int idx, String id, double tmp, double rain) {
        this(idx, id, tmp, rain, 0xFFFFFF, 0, 0, null);
    }

    public BiomeMap(int idx, String id, double tmp, double rain, String rl) {
        this(idx, id, tmp, rain, 0xFFFFFF, 0, 0, rl);
    }

    public BiomeMap(String id, double tmp, double rain, String rl) {
        this(NO_INDEX, id, tmp, rain, 0xFFFFFF, 0, 0, rl);	// No index
    }

    private final int biomeLookup(int width) {
        int w = width-1;
        int t = (int)((1.0-tmp)*w);
        int h = (int)((1.0 - (tmp*rain))*w);
        return width*h + t;
    }

    public final int biomeLookup() {
        return this.biomeindex256;
    }
    
    public final int getModifiedGrassMultiplier(int rawgrassmult) {
        if (grassmult == 0) return rawgrassmult;          // common case: no override
        if (grassmult < 0) return -grassmult;             // fixed color (pre-masked at set-time)
        return ((rawgrassmult & 0xfefefe) + grassmult) >> 1;  // blend
    }

    public final int getModifiedFoliageMultiplier(int rawfoliagemult) {
        if (foliagemult == 0) return rawfoliagemult;       // common case: no override
        if (foliagemult < 0) return -foliagemult;          // fixed color (pre-masked at set-time)
        return ((rawfoliagemult & 0xfefefe) + foliagemult) >> 1;  // blend
    }
    public final int getWaterColorMult() {
        return watercolormult;
    }
    public final int ordinal() {
        return index;
    }
    public static final BiomeMap byBiomeID(int idx) {
        idx++;
        if((idx >= 0) && (idx < biome_by_index.length))
            return biome_by_index[idx];
        else
            return NULL;
    }
    public static final BiomeMap byBiomeResourceLocation(String resloc) {
        BiomeMap b = biome_by_rl.get(resloc);
        return (b != null) ? b : NULL;
    }
    public int getBiomeID() {
        return index - 1;   // Index of biome in MC biome table
    }
    public static final BiomeMap[] values() {
        return biome_by_index;
    }
    public void setWaterColorMultiplier(int watercolormult) {
        this.watercolormult = watercolormult;
    }
    public void setGrassColorMultiplier(int grassmult) {
        this.grassmult = encodeColorMult(grassmult);
    }
    public void setFoliageColorMultiplier(int foliagemult) {
        this.foliagemult = encodeColorMult(foliagemult);
    }
    public void setTemperature(double tmp) {
        if(tmp < 0.0) tmp = 0.0;
        if(tmp > 1.0) tmp = 1.0;
        this.tmp = tmp;
        this.biomeindex256 = this.biomeLookup(256);
    }
    public void setRainfall(double rain) {
        if(rain < 0.0) rain = 0.0;
        if(rain > 1.0) rain = 1.0;
        this.rain = rain;
        this.biomeindex256 = this.biomeLookup(256);
    }
    public final double getTemperature() {
        return this.tmp;
    }
    public final double getRainfall() {
        return this.rain;
    }
    public boolean isDefault() {
        return isDef;
    }
    public String getId() {
        return id;
    }
    public String toString() {
    	return String.format("%s(%s)", id, resourcelocation);
    }
    public @SuppressWarnings("unchecked") <T> Optional<T> getBiomeObject() {
        return (Optional<T>) biomeObj;
    }
    public void setBiomeObject(Object biomeObj) {
        this.biomeObj = Optional.of(biomeObj);
    }
}
