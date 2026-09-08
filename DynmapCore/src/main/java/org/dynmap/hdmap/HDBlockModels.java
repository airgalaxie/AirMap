package org.dynmap.hdmap;

import java.io.IOException;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.PatchDefinitionFactory;

/** State-indexed rendering models populated exclusively from Minecraft resources. */
public final class HDBlockModels {
    private static int maxPatches = 6;
    static HDBlockModel[] models_by_id_data = new HDBlockModel[0];
    static PatchDefinitionFactory pdf = new PatchDefinitionFactory();
    static BitSet customModelsRequestingTileData = new BitSet();
    private static final BitSet changeIgnoredBlocks = new BitSet();
    private static final Map<Integer, HDScaledBlockModels> scaledModels = new ConcurrentHashMap<>();
    public static final int[] boxPatchList = { 1, 4, 0, 3, 2, 5 };


    private HDBlockModels() { }
    public static int getMaxPatchCount() { return maxPatches; }
    public static PatchDefinitionFactory getPatchDefinitionFactory() { return pdf; }
    public static boolean isChangeIgnoredBlock(DynmapBlockState block) { return changeIgnoredBlocks.get(block.globalStateIndex); }
    public static int getNeededTextureCount(DynmapBlockState block) { HDBlockModel m=model(block); return m == null ? 6 : m.getTextureCount(); }
    private static HDBlockModel model(DynmapBlockState b) { return b.globalStateIndex < models_by_id_data.length ? models_by_id_data[b.globalStateIndex] : null; }
    public static boolean isModelOccluding(DynmapBlockState b) { HDBlockModel m=model(b); return (m instanceof HDBlockPatchModel p) && p.isOccluding(); }
    public static boolean resetIfNotBlockSet(DynmapBlockState block, String blockset) { HDBlockModel m=model(block); if(m != null && !m.getBlockSet().equals(blockset)){models_by_id_data[block.globalStateIndex]=null;return true;} return false; }
    public static String[] getTileEntityFieldsNeeded(DynmapBlockState block) { HDBlockModel m=model(block); return m instanceof CustomBlockModel c ? c.render.getTileEntityFieldsNeeded() : null; }
    public static HDScaledBlockModels getModelsForScale(int scale) { return scaledModels.computeIfAbsent(scale, HDScaledBlockModels::new); }

    public static void loadModels(DynmapCore core, ConfigurationNode ignored) {
        maxPatches=6; models_by_id_data=new HDBlockModel[DynmapBlockState.getGlobalIndexMax()]; scaledModels.clear();
        changeIgnoredBlocks.clear(); customModelsRequestingTileData.clear(); pdf=new PatchDefinitionFactory();
        TexturePack.resetFiles(); HDBlockStateTextureMap.initializeTable();
        try {
            new MinecraftModelLoader(core.getMinecraftResourceProvider(), pdf).load();
            for(HDBlockModel model:models_by_id_data) if(model instanceof HDBlockPatchModel p) maxPatches=Math.max(maxPatches,p.getPatches().length);
        } catch(IOException e) { throw new IllegalStateException("Cannot load Minecraft model resources",e); }
        Log.info("Loaded block models directly from Minecraft JSON resources");
    }

    public static void handleBlockAlias() { Set<String> aliases=MapManager.mapman.getAliasedBlocks(); for(String from:aliases){String to=MapManager.mapman.getBlockAlias(from);if(!from.equals(to))remap(from,to);} }
    private static void remap(String from,String to){DynmapBlockState f=DynmapBlockState.getBaseStateByName(from),t=DynmapBlockState.getBaseStateByName(to);int n=f.getStateCount();for(int i=0;i<t.getStateCount();i++)models_by_id_data[t.getState(i).globalStateIndex]=models_by_id_data[f.getState(i%n).globalStateIndex];}
}
