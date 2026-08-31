package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Renderer for vanilla fluids.  Surface heights follow Minecraft's FluidRenderer:
 * FluidState own height (amount / 9) plus its weighted four-corner averaging.
 */
public class FluidStateRenderer extends CustomRenderer {
    private static final int PATCH_STILL = 0;
    private static final int PATCH_FLOWING = 1;
    
    private static final int[] still_patches = { PATCH_STILL, PATCH_STILL, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING };

    private static boolean didIinit = false;
    
    private static RenderPatch bottom = null; 	// Common bottom patch

    private static Map<String, RenderPatch[]> meshcache = null;
    
    private static Map<Integer, RenderPatch[]> fullculledcache = null;
    
    private static synchronized void init(RenderPatchFactory rpf) {
        if (didIinit) return;
        ArrayList<RenderPatch> list = new ArrayList<>();
        meshcache = new ConcurrentHashMap<>();
    	bottom = rpf.getPatch(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, SideVisible.TOP, PATCH_STILL);
        // For full height, build culled cache - eliminate surfaces adjacent to other fluid blocks
        fullculledcache = new ConcurrentHashMap<>();
        CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, still_patches);
            RenderPatch[] fullblkmodel = list.toArray(new RenderPatch[0]);
        for (int i = 0; i < 64; i++) {
        	list.clear();
        	for (int f = 0; f < 6; f++) {
        		if ((i & (1 << f)) != 0) {	// Index by face list order: see which we need to keep (bit N=1 means keep face N patch)
        			list.add(fullblkmodel[f]);
        		}
        	}
        	fullculledcache.put(i, list.toArray(new RenderPatch[0]));
        }
        didIinit = true;
    }
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        if (!didIinit) {
        	init(rpf);
        }
        return true;
    }
    
    @Override
    public int getMaximumTextureCount() {
        return 2;
    }

    private static DynmapBlockState getFluidState(MapDataContext ctx, int dx, int dy, int dz) {
    	DynmapBlockState bs;
    	if ((dx == 0) && (dy == 0) && (dz == 0)) {
    		bs = ctx.getBlockType();
    	}
    	else {
    		bs = ctx.getBlockTypeAt(dx, dy, dz);
		}
    	DynmapBlockState fbs = bs.getLiquidState();
    	return (fbs != null) ? fbs : bs;
    }
    
    // Minecraft LiquidBlock level -> FluidState amount -> own height.
    static double getOwnHeight(DynmapBlockState bs) {
        int level = 0;
        for (String value : bs.stateList) {
            if (value.startsWith("level=")) {
                try {
                    level = Integer.parseInt(value.substring(6));
                } catch (NumberFormatException ignored) {
                    level = 0;
                }
                break;
            }
        }
        int amount = (level == 0 || level >= 8) ? 8 : 8 - level;
        return amount / 9.0;
    }
    
    private static String getKey(double h_1_1, double h_n1_1, double h_1_n1, double h_n1_n1) {
        return Double.doubleToLongBits(h_1_1) + ":" + Double.doubleToLongBits(h_n1_1) + ":"
                + Double.doubleToLongBits(h_1_n1) + ":" + Double.doubleToLongBits(h_n1_n1);
    }

    // Get cached model
    private static RenderPatch[] getCachedModel(double h_1_1, double h_n1_1, double h_1_n1, double h_n1_n1) {
            return meshcache.get(getKey(h_1_1, h_n1_1, h_1_n1, h_n1_n1));
    }
    
    // Put cached model
    private static void putCachedModel(double h_1_1, double h_n1_1, double h_1_n1, double h_n1_n1, RenderPatch[] model) {
            meshcache.put(getKey(h_1_1, h_n1_1, h_1_n1, h_n1_n1), model);
    }

    // Get culled full model
    private static RenderPatch[]  getFullCulledModel(MapDataContext ctx, DynmapBlockState bs_0_0_0, DynmapBlockState bs_0_1_0) {
    	DynmapBlockState bs_n1_0_0 = getFluidState(ctx, -1, 0, 0);
    	DynmapBlockState bs_1_0_0 = getFluidState(ctx, 1, 0, 0);
    	DynmapBlockState bs_0_0_n1 = getFluidState(ctx, 0, 0, -1);
    	DynmapBlockState bs_0_0_1 = getFluidState(ctx, 0, 0, 1);
    	return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0, bs_n1_0_0, bs_1_0_0, bs_0_0_n1, bs_0_0_1);
    }
    // Get culled full model
    private static RenderPatch[]  getFullCulledModel(MapDataContext ctx, DynmapBlockState bs_0_0_0, 
    		DynmapBlockState bs_0_1_0, DynmapBlockState bs_n1_0_0, DynmapBlockState bs_1_0_0,
    		 DynmapBlockState bs_0_0_n1, DynmapBlockState bs_0_0_1) {
    	int idx = 0;
    	// Check bottom - keep if not match
    	if (!bs_0_0_0.matchingBaseState(getFluidState(ctx, 0, -1, 0))) {
    		idx += 1;
    	}
    	// Check top - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_1_0)) {
    		idx += 2;
    	}
        // Check minX side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_n1_0_0)) {
    		idx += 4;
    	}
        // Check maxX side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_1_0_0)) {
    		idx += 8;
    	}
        // Check minZ side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_0_n1)) {
    		idx += 16;
    	}
        // Check maxZ side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_0_1)) {
    		idx += 32;
    	}
            return fullculledcache.get(idx);
    }

    private static double getHeight(DynmapBlockState fluid, DynmapBlockState block,
            DynmapBlockState above) {
        if (block.matchingBaseState(fluid)) {
            return above.matchingBaseState(fluid) ? 1.0 : getOwnHeight(block);
        }
        return block.isSolid() ? -1.0 : 0.0;
    }

    private static void addWeightedHeight(double[] total, double height) {
        if (height >= 0.8) {
            total[0] += height * 10.0;
            total[1] += 10.0;
        } else if (height >= 0.0) {
            total[0] += height;
            total[1] += 1.0;
        }
    }

    // Exact Minecraft FluidRenderer.calculateAverageHeight semantics.
    static double getCornerHeight(DynmapBlockState fluid, double center,
            double side1, double side2, DynmapBlockState diagonal, DynmapBlockState diagonalAbove) {
        if (side1 >= 1.0 || side2 >= 1.0) return 1.0;
        double[] total = new double[2];
        if (side1 > 0.0 || side2 > 0.0) {
            double diagonalHeight = getHeight(fluid, diagonal, diagonalAbove);
            if (diagonalHeight >= 1.0) return 1.0;
            addWeightedHeight(total, diagonalHeight);
        }
        addWeightedHeight(total, center);
        addWeightedHeight(total, side2);
        addWeightedHeight(total, side1);
        return total[0] / total[1];
    }
    
    
    @Override
    public final RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	DynmapBlockState bs_0_0_0 = getFluidState(ctx, 0, 0, 0);	// Get own state
    	// Check above block - if matching fluid, block will be full
    	DynmapBlockState bs_0_1_0 = getFluidState(ctx, 0, 1, 0);
    	if (bs_0_1_0.matchingBaseState(bs_0_0_0)) {
    		return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0);
    	}
    	// Get other above blocks
    	DynmapBlockState bs_0_1_1 = getFluidState(ctx, 0, 1, 1);
    	DynmapBlockState bs_1_1_0 = getFluidState(ctx, 1, 1, 0);
    	DynmapBlockState bs_1_1_1 = getFluidState(ctx, 1, 1, 1);
    	DynmapBlockState bs_0_1_n1 = getFluidState(ctx, 0, 1, -1);
    	DynmapBlockState bs_n1_1_0 = getFluidState(ctx, -1, 1, 0);
    	DynmapBlockState bs_n1_1_n1 = getFluidState(ctx, -1, 1, -1);
    	DynmapBlockState bs_1_1_n1 = getFluidState(ctx, 1, 1, -1);
    	DynmapBlockState bs_n1_1_1 = getFluidState(ctx, -1, 1, 1);
    	// Get other neighbors to figure out corner heights
    	DynmapBlockState bs_0_0_1 = getFluidState(ctx, 0, 0, 1);
    	DynmapBlockState bs_1_0_0 = getFluidState(ctx, 1, 0, 0);
    	DynmapBlockState bs_1_0_1 = getFluidState(ctx, 1, 0, 1);
    	DynmapBlockState bs_0_0_n1 = getFluidState(ctx, 0, 0, -1);
    	DynmapBlockState bs_n1_0_0 = getFluidState(ctx, -1, 0, 0);
    	DynmapBlockState bs_n1_0_n1 = getFluidState(ctx, -1, 0, -1);
    	DynmapBlockState bs_1_0_n1 = getFluidState(ctx, 1, 0, -1);
    	DynmapBlockState bs_n1_0_1 = getFluidState(ctx, -1, 0, 1);
        double center = getHeight(bs_0_0_0, bs_0_0_0, bs_0_1_0);
        double xp = getHeight(bs_0_0_0, bs_1_0_0, bs_1_1_0);
        double xm = getHeight(bs_0_0_0, bs_n1_0_0, bs_n1_1_0);
        double zp = getHeight(bs_0_0_0, bs_0_0_1, bs_0_1_1);
        double zm = getHeight(bs_0_0_0, bs_0_0_n1, bs_0_1_n1);
        // Minecraft orders each corner as center, its two cardinal neighbors, diagonal.
        double bh_1_1 = getCornerHeight(bs_0_0_0, center, zp, xp, bs_1_0_1, bs_1_1_1);
        double bh_1_n1 = getCornerHeight(bs_0_0_0, center, zm, xp, bs_1_0_n1, bs_1_1_n1);
        double bh_n1_1 = getCornerHeight(bs_0_0_0, center, zp, xm, bs_n1_0_1, bs_n1_1_1);
        double bh_n1_n1 = getCornerHeight(bs_0_0_0, center, zm, xm, bs_n1_0_n1, bs_n1_1_n1);
        // Minecraft lowers every exposed top vertex slightly to avoid z-fighting.
        bh_1_1 -= 0.001;
        bh_1_n1 -= 0.001;
        bh_n1_1 -= 0.001;
        bh_n1_n1 -= 0.001;
    	// Do cached lookup of model
    	RenderPatch[] mod = getCachedModel(bh_1_1, bh_n1_1, bh_1_n1, bh_n1_n1);
    	// If not found, create model
    	if (mod == null) {
        	RenderPatchFactory rpf = ctx.getPatchFactory();
			ArrayList<RenderPatch> list = new ArrayList<>();
			list.add(bottom);	// All models have bottom patch
			// Add side for each face
			addSide(list, rpf, 0, 0, 0, 1, bh_n1_n1, bh_n1_1); // Xminus
			addSide(list, rpf, 1, 1, 1, 0, bh_1_1, bh_1_n1); // Xplus
			addSide(list, rpf, 1, 0, 0, 0, bh_1_n1, bh_n1_n1); // Zminus
			addSide(list, rpf, 0, 1, 1, 1, bh_n1_1, bh_1_1); // Zplus

			double edge_xm = bh_n1_n1 + bh_n1_1;
			double edge_xp = bh_1_n1 + bh_1_1;
			double edge_zm = bh_n1_n1 + bh_1_n1;
			double edge_zp = bh_1_1 + bh_n1_1;
			
			// See which edge is lowest
			if ((edge_xp <= edge_xm) && (edge_xp <= edge_zm) && (edge_xp <= edge_zp)) { // bh_1_1 and bh_1_n1 (Xplus)
				addTop(list, rpf, 1, 1, 1, 0, bh_1_1, bh_1_n1, bh_n1_1, bh_n1_n1);
			}
			else if ((edge_zp <= edge_zm) && (edge_zp <= edge_xm) && (edge_zp <= edge_xp)) {//  bh_n1_1 and bh_1_1 (zPlus)
				addTop(list, rpf, 0, 1, 1, 1, bh_n1_1, bh_1_1, bh_n1_n1, bh_1_n1);
			}
			else if ((edge_xm <= edge_xp) && (edge_xm <= edge_zm) && (edge_xm <= edge_zp)) {	// bh_n1_n1 and bh_n1_1 (xMinus)
				addTop(list, rpf, 0, 0, 0, 1, bh_n1_n1, bh_n1_1, bh_1_n1, bh_1_1);
			}
			else {	// bh_1_n1 and bh_n1_n1 (zMinus)
				addTop(list, rpf, 1, 0, 0, 0, bh_1_n1, bh_n1_n1, bh_1_1, bh_n1_1);
			}
			mod = list.toArray(new RenderPatch[0]);
	    	putCachedModel(bh_1_1, bh_n1_1, bh_1_n1, bh_n1_n1, mod);
	    	
	    	//Log.info(String.format("%d:%d:%d::bh_1_1=%d,bh_1_n1=%d,bh_n1_1=%d,bh_n1_n1=%d", ctx.getX(), ctx.getY(), ctx.getZ(), bh_1_1, bh_1_n1, bh_n1_1, bh_n1_n1));
	    	//for (RenderPatch rp : list) {
	    	//	Log.info(rp.toString());
	    	//}
    	}
    	return mod;
    }
    
    private static void addSide(ArrayList<RenderPatch> list, RenderPatchFactory rpf, double x0, double z0, double x1, double z1, double h0, double h1) {
    	if ((h0 == 0) && (h1 == 0))
    		return;
            list.add(rpf.getPatch(x0, 0, z0, x1, 0, z1, x0, 1, z0, 0, 1, 0, 0, h0, h1, SideVisible.TOP, PATCH_FLOWING));
    }
    
    private static void addTop(ArrayList<RenderPatch> list, RenderPatchFactory rpf, double x0, double z0, double x1, double z1, double h0, double h1, double h2, double h3) {
            double h0_upper = h1 + h2 - h3;
    	if (x0 == x1) {	// edge is Z+/-
    		if (h0_upper == h0) {	// If single surface
                    list.add(rpf.getPatch(x0, h0, z0, x1, h1, z1, 1-x0, h2, z0, 0, 1, 0, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    		else {
    			// Lower triangle
                    list.add(rpf.getPatch(x0, h0, z0, x1, h1, z1, 1-x0, h2, z0, 0, 1, 0, 0, 1, 0, SideVisible.TOP, PATCH_FLOWING));
    			// Upper triangle
                    list.add(rpf.getPatch(x0, h0_upper, z0, x1, h1, z1, 1-x0, h2, z0, 0, 1, 1, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    	}
    	else {
    		if (h0_upper == h0) {	// If single surface
                    list.add(rpf.getPatch(x0, h0, z0, x1, h1, z1, x0, h2, 1 - z0, 0, 1, 0, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    		else {
    			// Lower triangle
                    list.add(rpf.getPatch(x0, h0, z0, x1, h1, z1, x0, h2, 1 - z0, 0, 1, 0, 0, 1, 0, SideVisible.TOP, PATCH_FLOWING));
    			// Upper triangle
                    list.add(rpf.getPatch(x0, h0_upper, z0, x1, h1, z1, x0, h2, 1 - z0, 0, 1, 1, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    	}
    }
}
