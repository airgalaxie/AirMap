package org.dynmap.hdmap;

import java.util.BitSet;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinition;

public class HDBlockPatchModel extends HDBlockModel {
    /* Patch model specific attributes */
    private PatchDefinition[] patches;
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
        int max = 0;
        for (PatchDefinition patche : patches) {
            if ((patche != null) && (patche.textureindex > max)) {
                max = patche.textureindex;
            }
        }
        this.max_texture = max + 1;
    }
    /**
     * Get patches for block model (if patch model)
     * @return patches for model
     */
    public final PatchDefinition[] getPatches() {
        return patches;
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

