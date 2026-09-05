package org.dynmap.hdmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.dynmap.Log;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.resources.MinecraftResourceProvider;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.dynmap.utils.Vector3D;

/** Reads the vanilla blockstate/model JSON language into AirMap's render patches. */
final class MinecraftModelLoader {
    private static final Map<String, BlockSide> SIDES = Map.of(
            "down", BlockSide.BOTTOM, "up", BlockSide.TOP, "north", BlockSide.NORTH,
            "south", BlockSide.SOUTH, "west", BlockSide.WEST, "east", BlockSide.EAST);
    private static final BlockSide[] CUBE_SIDES = {
            BlockSide.BOTTOM, BlockSide.TOP, BlockSide.NORTH,
            BlockSide.SOUTH, BlockSide.WEST, BlockSide.EAST };
    private static final Map<String, BlockStep> SHADE_DIRECTIONS = Map.of(
            "down", BlockStep.Y_PLUS, "up", BlockStep.Y_MINUS,
            "north", BlockStep.Z_PLUS, "south", BlockStep.Z_MINUS,
            "west", BlockStep.X_PLUS, "east", BlockStep.X_MINUS);
    private final MinecraftResourceProvider resources;
    private final PatchDefinitionFactory patches;
    private final Map<String, JsonObject> rawModels = new HashMap<>();
    private final Map<String, JsonObject> models = new HashMap<>();
    private final Map<String, Integer> textureIds = new HashMap<>();
    private final Map<String, JsonObject> itemModels = new HashMap<>();
    private final Set<String> missingItemModels = new java.util.HashSet<>();
    private final Map<String, JsonObject> modelLayers = new HashMap<>();
    private final Set<String> missingModelLayers = new java.util.HashSet<>();
    private final Map<String, Boolean> opaqueSprites = new HashMap<>();

    MinecraftModelLoader(MinecraftResourceProvider resources, PatchDefinitionFactory patches) {
        this.resources = resources; this.patches = patches;
    }

    void load() throws IOException {
        for (String resource : resources.list("blockstates", ".json")) loadBlockstate(resource);
        registerFluids();
    }

    /** Fluids ship no element geometry in vanilla models, so install the shared fluid tessellator. */
    private void registerFluids() {
        // CLEARINSIDE op: internal fluid-fluid faces are culled (matchingBaseState /
        // waterFilled+onFace) and surviving faces fall through to COLORMOD_WATERTONED
        // inside readColor - this is what keeps water see-through to the floor.
        registerFluidRenderer("minecraft:water", "minecraft:block/water_still", "minecraft:block/water_flow", false);
        registerFluidRenderer("minecraft:flowing_water", "minecraft:block/water_still", "minecraft:block/water_flow", false);
        registerFluidRenderer("minecraft:lava", "minecraft:block/lava_still", "minecraft:block/lava_flow", true);
        registerFluidRenderer("minecraft:flowing_lava", "minecraft:block/lava_still", "minecraft:block/lava_flow", true);
    }

    private void registerFluidRenderer(String blockName, String stillTextureId, String flowingTextureId,
            boolean opaque) {
        DynmapBlockState base = DynmapBlockState.getBaseStateByName(blockName);
        if (base == DynmapBlockState.AIR) return;
        int still = texture(stillTextureId) + TexturePack.COLORMOD_CLEARINSIDE * TexturePack.COLORMOD_MULT_INTERNAL;
        int flowing = texture(flowingTextureId) + TexturePack.COLORMOD_CLEARINSIDE * TexturePack.COLORMOD_MULT_INTERNAL;
        BitSet states = new BitSet();
        for (int i = 0; i < base.getStateCount(); i++) states.set(base.getState(i).stateIndex);
        new CustomBlockModel(base, states, "org.dynmap.hdmap.renderer.FluidStateRenderer", Map.of(),
                "minecraft-json");
        int[] faces = { still, flowing };
        for (int i = 0; i < base.getStateCount(); i++) {
            TexturePack.registerMinecraftState(base.getState(i), faces,
                    opaque ? TexturePack.BlockTransparency.OPAQUE
                            : TexturePack.BlockTransparency.SEMITRANSPARENT);
        }
    }

    private void registerFluidCube(String blockName, String textureId, boolean opaque, int colorModifier) {
        DynmapBlockState base = DynmapBlockState.getBaseStateByName(blockName);
        if (base == DynmapBlockState.AIR) return;
        int tile = texture(textureId);
        if (colorModifier > 0) {
            tile += colorModifier * TexturePack.COLORMOD_MULT_INTERNAL;
        }
        PatchDefinition[] cube = new PatchDefinition[CUBE_SIDES.length];
        for (int i = 0; i < cube.length; i++) {
            PatchDefinition face = patches.getModelFace(new double[] {0, 0, 0}, new double[] {16, 16, 16},
                    CUBE_SIDES[i], null, ModelBlockModel.SideRotation.DEG0, true, i);
            if (face == null) return;
            cube[i] = face;
        }
        int[] faces = new int[cube.length];
        Arrays.fill(faces, tile);
        for (int i = 0; i < base.getStateCount(); i++) {
            DynmapBlockState state = base.getState(i);
            if (HDBlockStateTextureMap.getByBlockState(state) != HDBlockStateTextureMap.BLANK) continue;
            BitSet only = new BitSet(); only.set(state.stateIndex);
            new HDBlockPatchModel(base, only, cube, "minecraft-json");
            TexturePack.registerMinecraftState(state, faces,
                    opaque ? TexturePack.BlockTransparency.OPAQUE
                            : TexturePack.BlockTransparency.SEMITRANSPARENT);
        }
    }

    private void loadBlockstate(String resource) {
        String namespace = namespace(resource);
        String path = path(resource);
        String blockName = namespace + ":" + path.substring("blockstates/".length(), path.length() - 5);
        DynmapBlockState base = DynmapBlockState.getBaseStateByName(blockName);
        if (base == DynmapBlockState.AIR) return;
        JsonObject root;
        try {
            root = read(resource);
        } catch (Exception e) {
            Log.warning("Cannot read Minecraft blockstate " + resource + ": " + e.getMessage());
            return;
        }
        for (int i = 0; i < base.getStateCount(); i++) {
            DynmapBlockState state = base.getState(i);
            try {
                List<AppliedModel> selected = select(root, state.stateName, namespace);
                if (!selected.isEmpty()) install(state, selected);
            } catch (Exception e) {
                Log.warning("Cannot load Minecraft blockstate " + resource + " [" + state.stateName + "]: " + e.getMessage());
            }
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
        EnumSet<BlockStep> opaqueCubeFaces = EnumSet.noneOf(BlockStep.class);
        boolean[] occluding = new boolean[1];
        for (AppliedModel applied : selected) {
            JsonObject model = resolveModel(applied.id);
            JsonArray elements = model.getAsJsonArray("elements");
            Map<String, String> vars = textureVariables(model);
            if (elements != null) for (JsonElement elementValue : elements) {
                JsonObject element = elementValue.getAsJsonObject();
                boolean fullCube = isFullCube(element);
                double[] from = vector(element, "from", new double[] {0, 0, 0});
                double[] to = vector(element, "to", new double[] {16, 16, 16});
                boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
                BlockStep shadeDirection = element.has("shade_direction_override")
                        ? SHADE_DIRECTIONS.get(element.get("shade_direction_override").getAsString()) : null;
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces == null) continue;
                for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                    BlockSide side = SIDES.get(faceEntry.getKey()); if (side == null) continue;
                    JsonObject face = faceEntry.getValue().getAsJsonObject();
                    String texture = dereference(face.get("texture").getAsString(), vars);
                    int textureIndex = textures.size();
                    int tile = texture(texture);
                    tile = TexturePack.applyMinecraftTint(tile, state, integer(face, "tintindex", -1));
                    textures.add(tile);
                    double[] uv = face.has("uv") ? vector(face, "uv", null) : null;
                    int rotation = integer(face, "rotation", 0);
                    ModelBlockModel.SideRotation sideRotation = ModelBlockModel.SideRotation.valueOf("DEG" + rotation);
                    if (applied.uvlock) sideRotation = uvLockedRotation(sideRotation, applied.x, applied.y, side);
                    PatchDefinition patch = patches.getModelFace(
                            from, to, side, uv, sideRotation, shade, shadeDirection, textureIndex);
                    if (patch == null) continue;
                    if (element.has("rotation")) patch = rotateElement(patch, element.getAsJsonObject("rotation"));
                    if (patch != null && (applied.x != 0 || applied.y != 0))
                        patch = patches.getPatch(patch, applied.x, applied.y, 0, textureIndex);
                    if (patch != null) {
                        result.add(patch);
                        // Coverage is additive: a transparent overlay cannot remove an already
                        // opaque full-cube face, while missing/partial faces remain uncovered.
                        if (fullCube && isSpriteOpaque(texture)) opaqueCubeFaces.add(patch.step);
                    }
                }
            }
            installModelLayer(state, applied, result, textures, occluding);
        }
        if (result.isEmpty()) return;
        BitSet only = new BitSet(); only.set(state.stateIndex);
        new HDBlockPatchModel(state.baseState, only, result.toArray(PatchDefinition[]::new), "minecraft-json", occluding[0]);
        boolean opaqueCoverage = opaqueCubeFaces.size() == BlockStep.values().length;
        TexturePack.BlockTransparency transparency = state.getLightAttenuation() >= 15 && !state.isWaterFilled() && opaqueCoverage
                ? TexturePack.BlockTransparency.OPAQUE : TexturePack.BlockTransparency.SEMITRANSPARENT;
        TexturePack.registerMinecraftState(state, textures.stream().mapToInt(Integer::intValue).toArray(), transparency);
    }

    /** Adds Minecraft's baked model-layer faces for special/block-entity models. */
    private boolean installModelLayer(DynmapBlockState state, AppliedModel applied,
            List<PatchDefinition> result, List<Integer> textures, boolean[] occluding) throws IOException {
        SpecialModel special = itemSpecialModel(state);
        String textureId = null;
        Map<String, String> values = properties(state.stateName);
        JsonObject geometry = null;
        for (String candidate : modelLayerCandidates(special == null ? null : special.model, applied.id)) {
            JsonObject candidateGeometry = readLayer(candidate);
            if (candidateGeometry != null) {
                geometry = candidateGeometry;
                break;
            }
        }
        if (geometry == null) return false;
        JsonObject layerMetadata = geometry;
        if (special != null && special.model.has("texture")) {
            String textureDirectory = layerMetadata.has("texture_directory")
                    ? layerMetadata.get("texture_directory").getAsString() : "";
            textureId = normalizeEntityTexture(special.model.get("texture").getAsString(), textureDirectory);
            textureId = modelLayerTexture(textureId, layerMetadata, values);
        }
        if (layerMetadata.has("occluding") && layerMetadata.get("occluding").getAsBoolean()) occluding[0] = true;
        if ((textureId == null || textureId.isEmpty()) && geometry.has("texture")) textureId = geometry.get("texture").getAsString();
        if (textureId == null || textureId.isEmpty()) return false;
        int tile = texture(textureId);
        JsonArray faces = modelLayerFaces(geometry, values);
        if (faces == null) return false;
        String orientation = layerMetadata.has("orientation")
                ? layerMetadata.get("orientation").getAsString() : "model_rotation";
        double[] placement = geometry.has("translation") ? vector(geometry.get("translation").getAsJsonArray()) : null;
        for (JsonElement faceElement : faces) {
            JsonArray vertices = faceElement.getAsJsonObject().getAsJsonArray("vertices");
            if (special != null && special.transformation != null) vertices = transformVertices(vertices, special.transformation);
            if (placement != null) vertices = translateVertices(vertices, placement);
            PatchDefinition patch = modelLayerFace(vertices, textures.size());
            if (patch == null) continue;
            int[] rotation = layerRotation(orientation, values.get("facing"), applied);
            if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
                patch = patches.getPatch(patch, rotation[0], rotation[1], rotation[2],
                        new org.dynmap.utils.Vector3D(0.5, 0.5, 0.5), textures.size());
            }
            if (patch != null) {
                textures.add(tile);
                result.add(patch);
            }
        }
        return true;
    }

    static JsonArray modelLayerFaces(JsonObject geometry, Map<String, String> values) {
        if (!geometry.has("variants")) return geometry.getAsJsonArray("faces");
        JsonObject variants = geometry.getAsJsonObject("variants");
        String value = values.get(variants.get("property").getAsString());
        JsonObject faces = variants.getAsJsonObject("faces");
        return value != null && faces.has(value)
                ? faces.getAsJsonArray(value) : geometry.getAsJsonArray("faces");
    }

    static String modelLayerTexture(String texture, JsonObject geometry, Map<String, String> values) {
        if (!geometry.has("variants")) return texture;
        JsonObject variants = geometry.getAsJsonObject("variants");
        if (!variants.has("texture_suffix") || !variants.get("texture_suffix").getAsBoolean()) return texture;
        String value = values.get(variants.get("property").getAsString());
        return value != null && variants.getAsJsonObject("faces").has(value) ? texture + "_" + value : texture;
    }

    /** Names static model layers directly from Minecraft's selected special model. Primitive
     * special parameters provide optional variant suffixes; regular models use their basename. */
    static List<String> modelLayerCandidates(JsonObject specialModel, String modelId) {
        List<String> candidates = new ArrayList<>();
        if (specialModel != null && specialModel.has("type")) {
            String base = path(specialModel.get("type").getAsString());
            candidates.add(base);
            for (Map.Entry<String, JsonElement> entry : specialModel.entrySet()) {
                if (entry.getKey().equals("type") || entry.getKey().equals("texture")
                        || !entry.getValue().isJsonPrimitive()) continue;
                String candidate = base + "_" + entry.getValue().getAsString();
                if (!candidates.contains(candidate)) candidates.add(candidate);
            }
        } else {
            String path = path(modelId);
            candidates.add(path.substring(path.lastIndexOf('/') + 1));
        }
        return candidates;
    }

    private PatchDefinition modelLayerFace(JsonArray vertices, int textureIndex) {
        if (vertices.size() != 4) return null;
        double[][] v = new double[4][];
        for (int i = 0; i < 4; i++) v[i] = vector(vertices.get(i).getAsJsonArray());
        int origin = 0, uEnd = 0, vEnd = 0;
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
        for (double[] p : v) {
            minU = Math.min(minU, p[3]); maxU = Math.max(maxU, p[3]);
            double pv = 1.0 - p[4]; minV = Math.min(minV, pv); maxV = Math.max(maxV, pv);
        }
        double bestOrigin = Double.POSITIVE_INFINITY, bestU = Double.POSITIVE_INFINITY, bestV = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double pv = 1.0 - v[i][4];
            double d0 = Math.abs(v[i][3] - minU) + Math.abs(pv - minV);
            double du = Math.abs(v[i][3] - maxU) + Math.abs(pv - minV);
            double dv = Math.abs(v[i][3] - minU) + Math.abs(pv - maxV);
            if (d0 < bestOrigin) { bestOrigin = d0; origin = i; }
            if (du < bestU) { bestU = du; uEnd = i; }
            if (dv < bestV) { bestV = dv; vEnd = i; }
        }
        double[] o = v[origin], u = v[uEnd], w = v[vEnd];
        double uSpan = maxU - minU, vSpan = maxV - minV;
        if (uSpan <= 0 || vSpan <= 0) return null;
        double[] uBasis = {(u[0] - o[0]) / uSpan, (u[1] - o[1]) / uSpan, (u[2] - o[2]) / uSpan};
        double[] vBasis = {(w[0] - o[0]) / vSpan, (w[1] - o[1]) / vSpan, (w[2] - o[2]) / vSpan};
        double[] atlasOrigin = {
                o[0] - uBasis[0] * minU - vBasis[0] * minV,
                o[1] - uBasis[1] * minU - vBasis[1] * minV,
                o[2] - uBasis[2] * minU - vBasis[2] * minV};
        double[] basisNormal = cross(uBasis, vBasis);
        double[] windingNormal = cross(
                new double[] {v[1][0] - v[0][0], v[1][1] - v[0][1], v[1][2] - v[0][2]},
                new double[] {v[2][0] - v[0][0], v[2][1] - v[0][1], v[2][2] - v[0][2]});
        boolean flip = stepOf(basisNormal) == stepOf(windingNormal).opposite();
        if (flip) {
            return patches.getPatch(
                    atlasOrigin[0] + uBasis[0], atlasOrigin[1] + uBasis[1], atlasOrigin[2] + uBasis[2],
                    atlasOrigin[0], atlasOrigin[1], atlasOrigin[2],
                    atlasOrigin[0] + uBasis[0] + vBasis[0], atlasOrigin[1] + uBasis[1] + vBasis[1],
                    atlasOrigin[2] + uBasis[2] + vBasis[2],
                    1.0 - maxU, 1.0 - minU, minV, maxV, RenderPatchFactory.SideVisible.TOP, textureIndex,
                    minV, maxV, true);
        }
        return patches.getPatch(atlasOrigin[0], atlasOrigin[1], atlasOrigin[2],
                atlasOrigin[0] + uBasis[0], atlasOrigin[1] + uBasis[1], atlasOrigin[2] + uBasis[2],
                atlasOrigin[0] + vBasis[0], atlasOrigin[1] + vBasis[1], atlasOrigin[2] + vBasis[2],
                minU, maxU, minV, maxV, RenderPatchFactory.SideVisible.TOP, textureIndex,
                minV, maxV, true);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    /** Dominant-axis face classification, identical to PatchDefinition.update(). */
    private static BlockStep stepOf(double[] normal) {
        double cx = normal[0], cy = normal[1], cz = normal[2];
        if (Math.abs(cx) > (Math.abs(cy) * 0.9)) {
            if (Math.abs(cx) > Math.abs(cz)) return (cx > 0) ? BlockStep.X_PLUS : BlockStep.X_MINUS;
            return (cz > 0) ? BlockStep.Z_PLUS : BlockStep.Z_MINUS;
        }
        if ((Math.abs(cy) * 0.9) > Math.abs(cz)) return (cy > 0) ? BlockStep.Y_PLUS : BlockStep.Y_MINUS;
        return (cz > 0) ? BlockStep.Z_PLUS : BlockStep.Z_MINUS;
    }

    private SpecialModel itemSpecialModel(DynmapBlockState state) {
        String id = state.blockName;
        if (missingItemModels.contains(id)) return null;
        try {
            JsonObject item = itemModels.get(id);
            if (item == null) {
                item = read(namespace(id) + ":items/" + path(id) + ".json");
                itemModels.put(id, item);
            }
            return selectSpecial(item.get("model"), properties(state.stateName));
        } catch (Exception ignored) {
            missingItemModels.add(id);
            return null;
        }
    }

    private SpecialModel selectSpecial(JsonElement value, Map<String, String> state) {
        if (value == null || !value.isJsonObject()) return null;
        JsonObject node = value.getAsJsonObject();
        String type = node.has("type") ? node.get("type").getAsString() : "";
        if (type.endsWith(":special")) return new SpecialModel(node.getAsJsonObject("model"), node.getAsJsonObject("transformation"));
        if (type.endsWith(":select")) {
            String property = node.has("block_state_property") ? node.get("block_state_property").getAsString() : null;
            String selected = property == null ? null : state.get(property);
            if (selected != null && node.has("cases")) for (JsonElement caseValue : node.getAsJsonArray("cases")) {
                JsonObject candidate = caseValue.getAsJsonObject();
                JsonElement when = candidate.get("when");
                if (when != null && ((when.isJsonArray() && contains(when.getAsJsonArray(), selected))
                        || (!when.isJsonArray() && selected.equals(when.getAsString())))) {
                    return selectSpecial(candidate.get("model"), state);
                }
            }
            return selectSpecial(node.get("fallback"), state);
        }
        return null;
    }

    private static JsonArray transformVertices(JsonArray vertices, JsonObject transformation) {
        double[] translation = vector(transformation, "translation", new double[] {0, 0, 0});
        double[] scale = vector(transformation, "scale", new double[] {1, 1, 1});
        double[] left = vector(transformation, "left_rotation", new double[] {0, 0, 0, 1});
        double[] right = vector(transformation, "right_rotation", new double[] {0, 0, 0, 1});
        JsonArray result = new JsonArray();
        for (JsonElement vertexElement : vertices) {
            double[] vertex = vector(vertexElement.getAsJsonArray());
            double[] position = rotateQuaternion(new double[] {vertex[0], vertex[1], vertex[2]}, right);
            for (int i = 0; i < 3; i++) position[i] *= scale[i];
            position = rotateQuaternion(position, left);
            JsonArray transformed = new JsonArray();
            for (int i = 0; i < 3; i++) transformed.add(position[i] + translation[i]);
            for (int i = 3; i < vertex.length; i++) transformed.add(vertex[i]);
            result.add(transformed);
        }
        return result;
    }

    /** Shifts 3D face vertices by a constant block-space translation (Minecraft block-entity
     * placement, e.g. translate(0.5, 0, 0.5)), keeping UV coordinates untouched. */
    private static JsonArray translateVertices(JsonArray vertices, double[] translation) {
        JsonArray result = new JsonArray();
        for (JsonElement vertexElement : vertices) {
            double[] vertex = vector(vertexElement.getAsJsonArray());
            JsonArray transformed = new JsonArray();
            transformed.add(vertex[0] + translation[0]);
            transformed.add(vertex[1] + translation[1]);
            transformed.add(vertex[2] + translation[2]);
            for (int i = 3; i < vertex.length; i++) transformed.add(vertex[i]);
            result.add(transformed);
        }
        return result;
    }

    private static double[] rotateQuaternion(double[] v, double[] q) {
        double tx = 2 * (q[1] * v[2] - q[2] * v[1]);
        double ty = 2 * (q[2] * v[0] - q[0] * v[2]);
        double tz = 2 * (q[0] * v[1] - q[1] * v[0]);
        return new double[] {
                v[0] + q[3] * tx + q[1] * tz - q[2] * ty,
                v[1] + q[3] * ty + q[2] * tx - q[0] * tz,
                v[2] + q[3] * tz + q[0] * ty - q[1] * tx};
    }

    private record SpecialModel(JsonObject model, JsonObject transformation) {}

    private static boolean contains(JsonArray values, String selected) {
        for (JsonElement value : values) if (selected.equals(value.getAsString())) return true;
        return false;
    }

    private JsonObject readLayer(String layer) throws IOException {
        JsonObject cached = modelLayers.get(layer);
        if (cached != null) return cached;
        if (missingModelLayers.contains(layer)) return null;
        String resource = "/minecraft-model-layers/" + layer + ".json";
        try (InputStream in = MinecraftModelLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                missingModelLayers.add(layer);
                return null;
            }
            JsonObject value = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            modelLayers.put(layer, value);
            return value;
        }
    }

    private static String normalizeEntityTexture(String id, String textureDirectory) {
        String namespace = namespace(id);
        String value = path(id);
        if (value.startsWith("textures/")) value = value.substring("textures/".length());
        if (value.endsWith(".png")) value = value.substring(0, value.length() - 4);
        if (!value.contains("/") && !textureDirectory.isEmpty()) value = textureDirectory + value;
        return namespace + ":" + value;
    }

    static int[] layerRotation(String orientation, String facing, AppliedModel applied) {
        if (orientation.equals("model_rotation")) return new int[] {applied.x, applied.y, 0};
        if (facing == null) return new int[] {0, 0, 0};
        // ChestRenderer uses -Direction.toYRot() around the block center.  The baked chest
        // layer faces south before that transform, unlike the other horizontal model layers.
        if (orientation.equals("chest_facing")) return new int[] {0, switch (facing) {
            case "east" -> 90; case "north" -> 180; case "west" -> 270; default -> 0;
        }, 0};
        if (orientation.equals("horizontal_facing")) return new int[] {0, switch (facing) {
            case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0;
        }, 0};
        return switch (facing) {
            case "down" -> new int[] {180, 0, 0};
            case "north" -> new int[] {90, 0, 0};
            case "south" -> new int[] {-90, 0, 0};
            case "west" -> new int[] {0, 0, 90};
            case "east" -> new int[] {0, 0, -90};
            default -> new int[] {0, 0, 0};
        };
    }

    private static double[] vector(JsonArray values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsDouble();
        return result;
    }

    private PatchDefinition rotateElement(PatchDefinition patch, JsonObject rotation) {
        double[] origin = vector(rotation, "origin", new double[] {8,8,8});
        Vector3D center = new Vector3D(origin[0]/16, origin[1]/16, origin[2]/16);
        double rx, ry, rz;
        if (rotation.has("axis")) {
            String axis = rotation.get("axis").getAsString(); double angle = rotation.get("angle").getAsDouble();
            rx = axis.equals("x") ? angle : 0; ry = axis.equals("y") ? angle : 0; rz = axis.equals("z") ? angle : 0;
        }
        else {
            rx = rotation.has("x") ? rotation.get("x").getAsDouble() : 0;
            ry = rotation.has("y") ? rotation.get("y").getAsDouble() : 0;
            rz = rotation.has("z") ? rotation.get("z").getAsDouble() : 0;
        }
        if (rx == 0 && ry == 0 && rz == 0) return patch;
        /* Matrix4f.scale post-multiplies in vanilla: local-axis rescale is applied before the
         * rotation, both around the element origin. UV bounds remain unchanged. */
        if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
            double[] scale = rotationFactors(rx, ry, rz);
            patch = patches.getScaledPatch(patch, scale[0], scale[1], scale[2], center, patch.textureindex);
            if (patch == null) return null;
        }
        return patches.getPatch(patch, rx, ry, rz, center, patch.textureindex);
    }

    /** Local-axis rescale factors (= 1/maxAbs of each column of the rotation matrix). */
    private static double[] rotationFactors(double rx, double ry, double rz) {
        double[] factors = new double[] {1, 1, 1};
        for (int axis = 0; axis < 3; axis++) {
            Vector3D unit = new Vector3D(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
            PatchDefinition.rotateAround(unit, rx, ry, rz);
            double max = Math.max(Math.abs(unit.x), Math.max(Math.abs(unit.y), Math.abs(unit.z)));
            if (max > 1.0e-9) factors[axis] = 1.0 / max;
        }
        return factors;
    }

    /** Combines the JSON face rotation with vanilla's per-face inverse blockstate rotation. */
    static ModelBlockModel.SideRotation uvLockedRotation(ModelBlockModel.SideRotation rotation,
            int xRotation, int yRotation, BlockSide side) {
        int turns = UV_LOCK_TURNS[Math.floorMod(xRotation, 360) / 90]
                [Math.floorMod(yRotation, 360) / 90][faceIndex(side)];
        return ModelBlockModel.SideRotation.values()[(rotation.ordinal() + turns) & 3];
    }

    private static int faceIndex(BlockSide side) {
        return switch (side) {
            case BOTTOM -> 0;
            case TOP -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
            default -> throw new IllegalArgumentException("Not a Minecraft model face: " + side);
        };
    }

    /* Quarter-turns counter-clockwise in atlas UV space, indexed by x, y, then face. */
    private static final int[][][] UV_LOCK_TURNS = {
        {{0,0,0,0,0,0}, {3,1,0,0,0,0}, {2,2,0,0,0,0}, {1,3,0,0,0,0}},
        {{0,2,2,0,3,1}, {0,2,1,1,3,1}, {0,2,0,2,3,1}, {0,2,3,3,3,1}},
        {{0,0,2,2,2,2}, {1,3,2,2,2,2}, {2,2,2,2,2,2}, {3,1,2,2,2,2}},
        {{2,0,2,0,1,3}, {2,0,3,3,1,3}, {2,0,0,2,1,3}, {2,0,1,1,1,3}}
    };

    /** True when every pixel of the sprite tied to a texture reference is opaque (unknown/missing = false). */
    private boolean isSpriteOpaque(String textureId) {
        return opaqueSprites.computeIfAbsent(textureId, id -> {
            String[] p = id.indexOf(':') >= 0 ? id.split(":", 2) : new String[] {"minecraft", id};
            try (InputStream in = resources.open(p[0] + ":textures/" + p[1] + ".png")) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) return false;
                int[] argb = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
                for (int value : argb) if ((value >>> 24) != 0xFF) return false;
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        });
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
        if (values != null) for (Map.Entry<String, JsonElement> e : values.entrySet()) result.put(e.getKey(), textureReference(e.getValue()));
        return result;
    }
    private static String textureReference(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject texture = value.getAsJsonObject();
            return texture.has("sprite") ? texture.get("sprite").getAsString() : "minecraft:block/missingno";
        }
        return value.getAsString();
    }
    static String dereference(String value, Map<String,String> vars) {
        for (int i = 0; i < 32; i++) {
            String key = value.startsWith("#") ? value.substring(1) : value;
            if (!vars.containsKey(key)) return value;
            value = vars.get(key);
        }
        return "minecraft:block/missingno";
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
