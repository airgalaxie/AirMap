package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.LightLevels;

public class ShadowHDLighting extends DefaultHDLighting {

    private static final int BLOCK_LIGHT_FACTOR_NUMERATOR = 14;
    private static final int BLOCK_LIGHT_FACTOR_DENOMINATOR = 10;

    protected final int   defLightingTable[];  /* index=skylight level, value = 256 * scaling value */
    protected final int   lightscale[];   /* scale skylight level (light = lightscale[skylight] */
    protected final boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected final boolean smooth;
    protected final boolean useWorldBrightnessTable;
    
    public ShadowHDLighting(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        double shadowweight = configuration.getDouble("shadowstrength", 0.0);
        // See if we're using world's lighting table, or our own
        useWorldBrightnessTable = configuration.getBoolean("use-brightness-table", MapManager.mapman.useBrightnessTable());

        defLightingTable = new int[16];
        defLightingTable[15] = 256;
        /* Normal brightness weight in MC is a 20% relative dropoff per step */
        for(int i = 14; i >= 0; i--) {
            double v = defLightingTable[i+1] * (1.0 - (0.2 * shadowweight));
            defLightingTable[i] = (int)v;
            if(defLightingTable[i] > 256) defLightingTable[i] = 256;
            if(defLightingTable[i] < 0) defLightingTable[i] = 0;
        }
        int v = configuration.getInteger("ambientlight", -1);
        if(v < 0) v = 15;
        if(v > 15) v = 15;
        night_and_day = configuration.getBoolean("night-and-day", false);
        lightscale = new int[16];
        for(int i = 0; i < 16; i++) {
            if(i < (15-v))
                lightscale[i] = 0;
            else
                lightscale[i] = i - (15-v);
        }
        smooth = configuration.getBoolean("smooth-lighting", MapManager.mapman.getSmoothLighting());
    }
    
    private void applySmoothLighting(HDPerspectiveState ps, Color incolor, Color[] outcolor, int[] shadowscale) {
        int[] xyz = ps.getSubblockCoord();
        int scale = (int)ps.getScale();
        int mid = scale / 2;
        BlockStep s1, s2;
        int w1, w2;
        /* Figure out which two neighbor directions to sample */
        switch(ps.getShadeStep()) {
        case X_MINUS:
        case X_PLUS:
            s1 = (xyz[1] < mid) ? BlockStep.Y_MINUS : BlockStep.Y_PLUS;
            w1 = Math.abs(xyz[1] - mid);
            s2 = (xyz[2] < mid) ? BlockStep.Z_MINUS : BlockStep.Z_PLUS;
            w2 = Math.abs(xyz[2] - mid);
            break;
        case Z_MINUS:
        case Z_PLUS:
            s1 = (xyz[0] < mid) ? BlockStep.X_MINUS : BlockStep.X_PLUS;
            w1 = Math.abs(xyz[0] - mid);
            s2 = (xyz[1] < mid) ? BlockStep.Y_MINUS : BlockStep.Y_PLUS;
            w2 = Math.abs(xyz[1] - mid);
            break;
        default:
            s1 = (xyz[0] < mid) ? BlockStep.X_MINUS : BlockStep.X_PLUS;
            w1 = Math.abs(xyz[0] - mid);
            s2 = (xyz[2] < mid) ? BlockStep.Z_MINUS : BlockStep.Z_PLUS;
            w2 = Math.abs(xyz[2] - mid);
            break;
        }
        /* Fetch the 3 needed light levels (once, shared by both night and day passes) */
        LightLevels ll0 = ps.getCachedLightLevels(0);
        ps.getLightLevels(ll0);
        LightLevels ll1 = ps.getCachedLightLevels(1);
        ps.getLightLevelsAtStep(s1, ll1);
        LightLevels ll2 = ps.getCachedLightLevels(2);
        ps.getLightLevelsAtStep(s2, ll2);

        /* Night (ambient) pass */
        applySmoothedLightToColor(outcolor[0], incolor, ll0, ll1, ll2, true, w1, w2, scale, shadowscale);
        /* Day pass (only when night/day rendering is active) */
        if(outcolor.length > 1) {
            applySmoothedLightToColor(outcolor[1], incolor, ll0, ll1, ll2, false, w1, w2, scale, shadowscale);
        }
    }

    /** Apply smooth lighting to a single output color for one pass (ambient or day). */
    private void applySmoothedLightToColor(Color out, Color incolor,
            LightLevels ll0, LightLevels ll1, LightLevels ll2,
            boolean useambient, int w1, int w2, int scale, int[] shadowscale) {
        int c0 = getLightScale(ll0, useambient, shadowscale);
        int c1 = getLightScale(ll1, useambient, shadowscale);
        int c2 = getLightScale(ll2, useambient, shadowscale);
        out.setColor(incolor);
        int cscale = computeSmoothedCscale(c0, c1, c2, w1, w2, scale);
        if(cscale < 256) {
            out.scaleRGB(cscale);
        }
    }

    /** Interpolate the complete light contribution from the two sampled neighbors. */
    private int computeSmoothedCscale(int c0, int c1, int c2, int w1, int w2, int scale) {
        int result = c0 + ((c1 - c0) * w1 + (c2 - c0) * w2) / scale;
        return Math.max(0, Math.min(256, result));
    }

    private int getLightScale(final LightLevels ll, boolean useambient, int[] shadowscale) {
        int skylight = useambient ? lightscale[ll.sky] : ll.sky;
        return computeLightScale(ll.emitted, skylight, shadowscale);
    }

    /**
     * Combine sky and block light as a deterministic scalar approximation of
     * Minecraft's lightmap: one ambient base plus separate sky and block
     * contributions. The stable part of Minecraft's block-light factor is 1.4;
     * client tint, flicker, and gamma are deliberately absent.
     */
    static int computeLightScale(int emitted, int skylight, int[] lightingTable) {
        int ambient = lightingTable[0];
        int skyContribution = Math.max(0, lightingTable[skylight] - ambient);
        int blockContribution = Math.max(0, lightingTable[emitted] - ambient);
        int boostedBlock = (blockContribution * BLOCK_LIGHT_FACTOR_NUMERATOR
                + BLOCK_LIGHT_FACTOR_DENOMINATOR / 2) / BLOCK_LIGHT_FACTOR_DENOMINATOR;
        return Math.min(256, ambient + skyContribution + boostedBlock);
    }

    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    @Override
    public void applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        int[] shadowscale = ss.getLightingTable();
        if(shadowscale == null) {
            shadowscale = defLightingTable;
        }
        if(smooth && ps.getShade()) {
            applySmoothLighting(ps, incolor, outcolor, shadowscale);
            checkGrayscale(outcolor);
            return;
        }
        /* Non-smooth: fetch light levels and apply flat shadow */
        LightLevels ll = ps.getCachedLightLevels(0);
        ps.getLightLevels(ll);
        int lightscaleNight = computeLightScale(ll.emitted, lightscale[ll.sky], shadowscale);
        int lightscaleDay = computeLightScale(ll.emitted, ll.sky, shadowscale);
        outcolor[0].setColor(incolor);
        if(lightscaleNight < 256) {
            outcolor[0].scaleRGB(lightscaleNight);
        }
        if(outcolor.length > 1) {
            if(lightscaleDay == lightscaleNight) {
                outcolor[1].setColor(outcolor[0]);
            }
            else {
                outcolor[1].setColor(incolor);
                if(lightscaleDay < 256) {
                    outcolor[1].scaleRGB(lightscaleDay);
                }
            }
        }
        checkGrayscale(outcolor);
    }


    /* Test if night/day is enabled for this renderer */
    @Override
    public boolean isNightAndDayEnabled() { return night_and_day; }
    
    /* Test if sky light level needed */
    @Override
    public boolean isSkyLightLevelNeeded() { return true; }
    
    /* Test if emitted light level needed */
    @Override
    public boolean isEmittedLightLevelNeeded() { return true; }    

    @Override
    public int[] getBrightnessTable(DynmapWorld world) {
        if (useWorldBrightnessTable) {
            return world.getBrightnessTable();
        }
        else {
            return null;
        }
    }
}
