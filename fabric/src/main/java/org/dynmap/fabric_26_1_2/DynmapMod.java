package org.dynmap.fabric_26_1_2;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.fabric_26_1_2.event.PlayerEvents;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DynmapMod implements ModInitializer {
    private static final String MODID = "airmap";
    public static final File DATA_DIRECTORY = new File("AirMap");
    private static final ModContainer MOD_CONTAINER = FabricLoader.getInstance().getModContainer(MODID)
            .orElseThrow(() -> new RuntimeException("Failed to get mod container: " + MODID));

    public static DynmapMod instance;
    public static DynmapPlugin plugin;
    public static File jarfile;
    public static String ver;
    public static boolean useforcedchunks;

    @Override
    public void onInitialize() {
        instance = this;

        Path path = MOD_CONTAINER.getRootPath();
        try {
            jarfile = new File(DynmapCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            Log.severe("Unable to get DynmapCore jar path", e);
        }

        if (path.getFileSystem().provider().getScheme().equals("jar")) {
            path = Paths.get(path.getFileSystem().toString());
            jarfile = path.toFile();
        }

        ver = MOD_CONTAINER.getMetadata().getVersion().getFriendlyString();

        Log.setLogger(new FabricLogger());
        org.dynmap.modsupport.ModSupportImpl.init();
        PlayerEvents.registerFabricEvents();

        plugin = new DynmapPlugin();
    }
}
