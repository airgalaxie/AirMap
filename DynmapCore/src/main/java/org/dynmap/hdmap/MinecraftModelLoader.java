package org.dynmap.hdmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dynmap.Log;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.resources.MinecraftResourceProvider;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

/** Reads the vanilla blockstate/model JSON language into AirMap's render patches. */
final class MinecraftModelLoader {
    private static final Map<String, BlockSide> SIDES = Map.of(
            "down", BlockSide.BOTTOM, "up", BlockSide.TOP, "north", BlockSide.NORTH,
            "south", BlockSide.SOUTH, "west", BlockSide.WEST, "east", BlockSide.EAST);
    private final MinecraftResourceProvider resources;
    private final PatchDefinitionFactory patches;
    private final Map<String, JsonObject> rawModels = new HashMap<>();
    private final Map<String, JsonObject> models = new HashMap<>();
    private final Map<String, Integer> textureIds = new HashMap<>();

    MinecraftModelLoader(MinecraftResourceProvider resources, PatchDefinitionFactory patches) {
        this.resources = resources; this.patches = patches;
    }

    void load() throws IOException {
        for (String resource : resources.list("blockstates", ".json")) loadBlockstate(resource);
    }

    private void loadBlockstate(String resource) {
        String namespace = namespace(resource);
        String path = path(resource);
        String blockName = namespace + ":" + path.substring("blockstates/".length(), path.length() - 5);
        DynmapBlockState base = DynmapBlockState.getBaseStateByName(blockName);
        if (base == DynmapBlockState.AIR) return;
        try {
            JsonObject root = read(resource);
            for (int i = 0; i < base.getStateCount(); i++) {
                DynmapBlockState state = base.getState(i);
                List<AppliedModel> selected = select(root, state.stateName, namespace);
                if (!selected.isEmpty()) install(state, selected);
            }
        } catch (Exception e) {
            Log.warning("Cannot load Minecraft blockstate " + resource + ": " + e.getMessage());
        }
    }

    private List<AppliedModel> select(JsonObject root, String stateName, String namespace) {
        Map<String, String> values = properties(stateName);
        List<AppliedModel> out = new ArrayList<>();
        JsonObject variants = root.getAsJsonObject("variants");
        if (variants != null) for (Map.Entry<String, JsonElement> e : variants.entrySet())
            if (matchesVariant(e.getKey(), values)) { out.add(best(e.getValue(), namespace)); break; }
        JsonArray multipart = root.getAsJsonArray("multipart");
        if (multipart != null) for (JsonElement partElement : multipart) {
            JsonObject part = partElement.getAsJsonObject();
            if (!part.has("when") || matchesWhen(part.get("when"), values)) out.add(best(part.get("apply"), namespace));
        }
        out.removeIf(x -> x == null);
        return out;
    }

    private AppliedModel best(JsonElement value, String namespace) {
        JsonObject obj;
        if (value.isJsonArray()) {
            obj = null; int weight = -1;
            for (JsonElement e : value.getAsJsonArray()) {
                JsonObject candidate = e.getAsJsonObject(); int w = integer(candidate, "weight", 1);
                if (w > weight) { weight = w; obj = candidate; }
            }
        } else obj = value.getAsJsonObject();
        if (obj == null || !obj.has("model")) return null;
        return new AppliedModel(qualify(obj.get("model").getAsString(), namespace), integer(obj, "x", 0), integer(obj, "y", 0),
                obj.has("uvlock") && obj.get("uvlock").getAsBoolean());
    }

    private void install(DynmapBlockState state, List<AppliedModel> selected) throws IOException {
        List<PatchDefinition> result = new ArrayList<>();
        List<Integer> textures = new ArrayList<>();
        boolean fullCube = selected.size() == 1;
        for (AppliedModel applied : selected) {
            JsonObject model = resolveModel(applied.id);
            JsonArray elements = model.getAsJsonArray("elements");
            if (elements == null) continue;
            Map<String, String> vars = textureVariables(model);
            fullCube &= elements.size() == 1 && isFullCube(elements.get(0).getAsJsonObject());
            for (JsonElement elementValue : elements) {
                JsonObject element = elementValue.getAsJsonObject();
                double[] from = vector(element, "from", new double[] {0, 0, 0});
                double[] to = vector(element, "to", new double[] {16, 16, 16});
                boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces == null) continue;
                for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                    BlockSide side = SIDES.get(faceEntry.getKey()); if (side == null) continue;
                    JsonObject face = faceEntry.getValue().getAsJsonObject();
                    String texture = dereference(face.get("texture").getAsString(), vars);
                    int textureIndex = textures.size();
                    int tile = texture(texture);
                    tile = TexturePack.applyMinecraftTint(tile, state, integer(face, "tintindex", -1));
                    textures.add(applied.uvlock ? TexturePack.applyMinecraftUvLock(tile, applied.y) : tile);
                    double[] uv = face.has("uv") ? vector(face, "uv", null) : null;
                    int rotation = integer(face, "rotation", 0);
                    ModelBlockModel.SideRotation sideRotation = ModelBlockModel.SideRotation.valueOf("DEG" + rotation);
                    PatchDefinition patch = patches.getModelFace(from, to, side, uv, sideRotation, shade, textureIndex);
                    if (patch != null) {
                        if (element.has("rotation")) patch = rotateElement(patch, element.getAsJsonObject("rotation"));
                        if (applied.x != 0 || applied.y != 0) patch = patches.getPatch(patch, applied.x, applied.y, 0, textureIndex);
                        if (patch != null) result.add(patch);
                    }
                }
            }
        }
        if (result.isEmpty()) return;
        BitSet only = new BitSet(); only.set(state.stateIndex);
        new HDBlockPatchModel(state.baseState, only, result.toArray(PatchDefinition[]::new), "minecraft-json");
        TexturePack.registerMinecraftState(state, textures.stream().mapToInt(Integer::intValue).toArray(), fullCube);
    }

    private PatchDefinition rotateElement(PatchDefinition patch, JsonObject rotation) {
        String axis = rotation.get("axis").getAsString(); double angle = rotation.get("angle").getAsDouble();
        double[] origin = vector(rotation, "origin", new double[] {8,8,8});
        return patches.getPatch(patch, axis.equals("x") ? angle : 0, axis.equals("y") ? angle : 0,
                axis.equals("z") ? angle : 0, new org.dynmap.utils.Vector3D(origin[0]/16, origin[1]/16, origin[2]/16), patch.textureindex);
    }

    private JsonObject resolveModel(String id) throws IOException {
        JsonObject cached = models.get(id); if (cached != null) return cached;
        JsonObject own = rawModels.computeIfAbsent(id, key -> { try { return read(modelResource(key)); } catch (IOException e) { throw new ModelException(e); } });
        JsonObject merged = new JsonObject();
        if (own.has("parent") && !own.get("parent").getAsString().equals("builtin/generated") && !own.get("parent").getAsString().equals("builtin/entity"))
            copy(resolveModel(qualify(own.get("parent").getAsString(), namespace(id))), merged);
        copy(own, merged); models.put(id, merged); return merged;
    }

    private static void copy(JsonObject source, JsonObject target) {
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            if (e.getKey().equals("textures") && target.has("textures")) {
                JsonObject combined = target.getAsJsonObject("textures").deepCopy(); copy(e.getValue().getAsJsonObject(), combined); target.add("textures", combined);
            } else target.add(e.getKey(), e.getValue().deepCopy());
        }
    }
    private Map<String,String> textureVariables(JsonObject model) {
        Map<String,String> result = new HashMap<>(); JsonObject values = model.getAsJsonObject("textures");
        if (values != null) values.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString())); return result;
    }
    private static String dereference(String value, Map<String,String> vars) {
        for (int i=0; value.startsWith("#") && i<32; i++) value = vars.getOrDefault(value.substring(1), "minecraft:block/missingno"); return value;
    }
    private int texture(String id) { return textureIds.computeIfAbsent(id, TexturePack::registerMinecraftTexture); }
    private JsonObject read(String id) throws IOException { try (var in = resources.open(id); var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); } }
    private static boolean matchesVariant(String key, Map<String,String> values) { if (key.isEmpty()) return true; for (String term:key.split(",")) { String[] p=term.split("=",2); if (p.length!=2 || !List.of(p[1].split("\\|")).contains(values.get(p[0]))) return false; } return true; }
    private static boolean matchesWhen(JsonElement when, Map<String,String> values) { JsonObject o=when.getAsJsonObject(); if(o.has("OR")) { for(JsonElement e:o.getAsJsonArray("OR")) if(matchesWhen(e,values)) return true; return false; } if(o.has("AND")) { for(JsonElement e:o.getAsJsonArray("AND")) if(!matchesWhen(e,values)) return false; return true; } for(var e:o.entrySet()) if(!List.of(e.getValue().getAsString().split("\\|")).contains(values.get(e.getKey()))) return false; return true; }
    private static Map<String,String> properties(String state) { Map<String,String> r=new LinkedHashMap<>(); if(!state.isEmpty()) for(String v:state.split(",")){String[] p=v.split("=",2); if(p.length==2)r.put(p[0],p[1]);} return r; }
    private static double[] vector(JsonObject object,String key,double[] fallback){ if(!object.has(key))return fallback; JsonArray a=object.getAsJsonArray(key); double[] r=new double[a.size()]; for(int i=0;i<r.length;i++)r[i]=a.get(i).getAsDouble(); return r; }
    private static int integer(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    private static boolean isFullCube(JsonObject e){double[] f=vector(e,"from",null),t=vector(e,"to",null);return f!=null&&t!=null&&f[0]==0&&f[1]==0&&f[2]==0&&t[0]==16&&t[1]==16&&t[2]==16&&!e.has("rotation");}
    private static String namespace(String id){return id.substring(0,id.indexOf(':'));} private static String path(String id){return id.substring(id.indexOf(':')+1);}
    private static String qualify(String id,String namespace){return id.indexOf(':')>=0?id:namespace+":"+id;}
    private static String modelResource(String id){return namespace(id)+":models/"+path(id)+".json";}
    private record AppliedModel(String id,int x,int y,boolean uvlock){}
    private static final class ModelException extends RuntimeException { ModelException(IOException cause){super(cause);} }
}
