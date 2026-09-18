package org.dynmap.hdmap;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

public class HDBlockPatchModel extends HDBlockModel {
    /* Patch model specific attributes */
    private PatchDefinition[] patches;
    private final List<WeightedPatchGroup> weightedGroups;
    private final int max_texture;
    private final boolean occluding;
    /**
     * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
     * (for patch models)
     * @param bs - block state
     * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
     * @param patches - list of patches (surfaces composing model)
     * @param blockset - ID of set of blocks defining model
     */
    public HDBlockPatchModel(DynmapBlockState bs, BitSet databits, PatchDefinition[] patches, String blockset) {
        this(bs, databits, patches, blockset, false);
    }
    /**
     * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
     * (for patch models)
     * @param bs - block state
     * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
     * @param patches - list of patches (surfaces composing model)
     * @param blockset - ID of set of blocks defining model
     * @param occluding - true if the model as a whole declares itself an opaque solid; missing subpixel
     *                    coverage is then not filled from any block behind the model
     */
    public HDBlockPatchModel(DynmapBlockState bs, BitSet databits, PatchDefinition[] patches, String blockset, boolean occluding) {
        super(bs, databits, blockset);
        this.occluding = occluding;
        this.patches = patches;
        this.weightedGroups = List.of();
        int max = 0;
        for (PatchDefinition patche : patches) {
            if ((patche != null) && (patche.textureindex > max)) {
                max = patche.textureindex;
            }
        }
        this.max_texture = max + 1;
    }

    HDBlockPatchModel(DynmapBlockState state, List<WeightedPatchGroup> groups, String blockset,
            boolean occluding) {
        super(state.baseState, stateBits(state), blockset);
        this.occluding = occluding;
        weightedGroups = List.copyOf(groups);
        patches = null;
        int max = 0;
        for (WeightedPatchGroup group : groups) {
            for (PatchDefinition[] alternative : group.alternatives) {
                for (PatchDefinition patch : alternative) {
                    if (patch != null && patch.textureindex > max) max = patch.textureindex;
                }
            }
        }
        max_texture = max + 1;
    }

    private static BitSet stateBits(DynmapBlockState state) {
        BitSet bits = new BitSet();
        bits.set(state.stateIndex);
        return bits;
    }
    /**
     * Get patches for block model (if patch model)
     * @return patches for model
     */
    public final PatchDefinition[] getPatches() {
        return getPatches(0, 0, 0);
    }

    public final PatchDefinition[] getPatches(int x, int y, int z) {
        if (weightedGroups.isEmpty()) return patches;
        Random random = new Random(positionSeed(x, y, z));
        int size = 0;
        PatchDefinition[][] selected = new PatchDefinition[weightedGroups.size()][];
        for (int i = 0; i < weightedGroups.size(); i++) {
            selected[i] = weightedGroups.get(i).select(random);
            size += selected[i].length;
        }
        PatchDefinition[] result = new PatchDefinition[size];
        int offset = 0;
        for (PatchDefinition[] part : selected) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    int getMaximumPatchCount() {
        if (weightedGroups.isEmpty()) return patches.length;
        int count = 0;
        for (WeightedPatchGroup group : weightedGroups) count += group.maximumPatchCount();
        return count;
    }

    static long positionSeed(int x, int y, int z) {
        long seed = (long)(x * 3129871) ^ (long)z * 116129781L ^ y;
        return (seed * seed * 42317861L + seed * 11L) >> 16;
    }

    static final class WeightedPatchGroup {
        private final PatchDefinition[][] alternatives;
        private final int[] cumulativeWeights;
        private final int totalWeight;

        WeightedPatchGroup(List<PatchDefinition[]> alternatives, List<Integer> weights) {
            this.alternatives = alternatives.toArray(PatchDefinition[][]::new);
            cumulativeWeights = new int[weights.size()];
            int total = 0;
            for (int i = 0; i < weights.size(); i++) {
                total = Math.addExact(total, weights.get(i));
                cumulativeWeights[i] = total;
            }
            totalWeight = total;
        }

        private PatchDefinition[] select(Random random) {
            int value = random.nextInt(totalWeight);
            for (int i = 0; i < cumulativeWeights.length; i++) {
                if (value < cumulativeWeights[i]) return alternatives[i];
            }
            throw new AssertionError(value);
        }

        private int maximumPatchCount() {
            int maximum = 0;
            for (PatchDefinition[] alternative : alternatives) maximum = Math.max(maximum, alternative.length);
            return maximum;
        }
    }
    /**
     * Whether this model is declared as an occluding solid: rays that hit its surface must NOT let a
     * block behind it fill subpixel coverage the model's texture does not provide.
     * @return true if occluding
     */
    public final boolean isOccluding() {
        return occluding;
    }
    /**
     * Set patches for block
     */
    public final void setPatches(PatchDefinition[] p) {
        patches = p;
    }
    
    @Override
    public int getTextureCount() {
        return max_texture;
    }
}
