# HANDOFF / Checkpoint: AirMap-future

**Version 7 --- 2026-09-19**

# MACHINE STATE CONTRACT

```text
DOCUMENT_TYPE = MACHINE_HANDOFF
PROJECT = AirMap-future

CORE_CHECKPOINT = a492c74
HANDOFF_STATUS = COMMIT_SNAPSHOT

TARGET = MINECRAFT
OUTPUT = WEB_MAP

PIPELINE = MINECRAFT -> ADAPTER -> COMMON_MINECRAFT_REPRESENTATION -> CORE -> WEB_MAP

CURRENT_STATE_AUTHORITY = CHECKOUT
VERSION_AUTHORITY = PROJECT_VERSION_SOURCE
HISTORY_IS_CURRENT_STATE_SOURCE = FALSE
HISTORY_CAN_DEFINE_VERSION = FALSE
HISTORY_CAN_DEFINE_VERSION_RANGE = FALSE
HISTORY_CAN_DEFINE_PLATFORM_SUPPORT = FALSE
HISTORY_CAN_DEFINE_OPEN_ISSUE = FALSE
HISTORY_CAN_CREATE_TASK = FALSE
UNVERIFIED_HISTORICAL_CLAIM = UNKNOWN_CURRENT

MINECRAFT_VERSION_IS_DATA = TRUE
MINECRAFT_VERSION_IS_SEMANTICS = FALSE
HARDCODED_MINECRAFT_VERSION_IN_CORE = FORBIDDEN

NEXT_TASK = NONE
AUTOMATIC_CODE_CHANGE = FALSE
AUTOMATIC_BUILD = FALSE
AUTOMATIC_SERVER_START = FALSE
AUTOMATIC_DEPLOY = FALSE
AUTOMATIC_NEW_INVESTIGATION = FALSE

ADAPTER.ROLE = DELIVERY_BOUNDARY
ADAPTER_IS_TARGET = FALSE
ADAPTER_DEFINES_CORE_ARCHITECTURE = FALSE
NEW_ADAPTER_REDEFINES_MINECRAFT_SEMANTICS = FALSE

ADAPTER_ERROR.OWNER = ADAPTER
CORE_ERROR.OWNER = CORE
CORE_COMPENSATES_FOR_ADAPTER_ERROR = FALSE

CORE.INPUT = COMMON_MINECRAFT_SEMANTICS
CORE.OUTPUT = WEB_MAP

MINECRAFT_DEFINES_OBJECT_IDENTITY = TRUE
RENDER_RESOURCES_DEFINE_OR_EXTEND_APPEARANCE = TRUE
RESOURCE_PACKS_MAY_CHANGE_APPEARANCE = TRUE
RESOURCE_PACKS_MAY_REDEFINE_MINECRAFT_IDENTITY = FALSE
CONFIG_AND_CUSTOM_ARE_USER_CONTRACTS = TRUE

UNREFERENCED_PLATFORM_TOOLS = FORBIDDEN
TOOL_OR_GENERATOR_ASSET_REQUIRES_ACTIVE_BUILD_OR_MAINTENANCE_CONSUMER = TRUE
```

## MACHINE DECISION RULES

```text
RULE_01:
  IF claim concerns CURRENT state
  THEN source = CURRENT_VERIFIED_STATE or CHECKOUT
  ELSE NOT_PROVEN_CURRENT

RULE_02:
  IF claim exists only in HISTORY
  THEN current_status = UNKNOWN_CURRENT

RULE_03:
  IF HISTORY says OPEN
  THEN CURRENT_OPEN = NOT_INFERRED

RULE_04:
  IF HISTORY names version/platform/adapter
  THEN CURRENT version/platform/support = NOT_INFERRED

RULE_05:
  IF user did not request work
  THEN NEXT_TASK = NONE

RULE_06:
  IF Minecraft semantics entering CORE are wrong
  AND error originates before CORE boundary
  THEN owner = ADAPTER
  AND core_fix = FORBIDDEN_AS_COMPENSATION

RULE_07:
  IF Minecraft semantics entering CORE are correct
  AND WEB_MAP result is wrong
  THEN owner = CORE_PATH

RULE_08:
  IF issue is common Minecraft/Core behavior
  THEN adapter_specific_core_patch = FORBIDDEN

RULE_09:
  IF task is Core/Minecraft-semantic
  THEN adapter selection = NOT_REQUIRED

RULE_10:
  IF task explicitly concerns adapter boundary or runtime acquisition
  THEN relevant adapter MAY be named/tested

RULE_11:
  HISTORY may be read as evidence only.
  HISTORY must not generate CURRENT facts.
  HISTORY must not generate NEXT_TASK.

RULE_VERSION:
  Minecraft version identifiers are replaceable build/runtime data.
  Core behavior MUST NOT depend on a fixed Minecraft version number.
  Version-specific verification may name the version used as evidence.
  Minecraft semantics MUST come from Minecraft resources, the common
  Minecraft representation, or another explicitly resolved Minecraft source.

RULE_TOOLS:
  Do not add or restore platform-local tool, spreadsheet, formatting, or
  generator assets unless an active build task or documented maintenance
  workflow requires them.
```

# CURRENT_VERIFIED_STATE @ a492c74

```text
VERIFY_SOURCE = implementation + regression_tests + full_core_tests + full_build + subsequent_read_only_checkout_review

CORE_PLATFORM_IMPORTS.net_minecraft = NONE_FOUND
CORE_PLATFORM_IMPORTS.org_bukkit = NONE_FOUND

TILE_SCALE_RANGE_FIX = PRESENT
TILE_SCALE_REGRESSION_TEST = PRESENT

BIOME_COMMON_CONTRACT = PRESENT
BIOME_CONTRACT_FIELDS = [grassColorOverride, foliageColorOverride, grassMode]
NATIVE_BIOME_OBJECT_IN_RENDER_PATH = FALSE
LEGACY_BIOME_OBJECT_ACCESSORS = PRESENT_UNUSED_COMPATIBILITY

MODEL_RESCALE_ROTATION_ORIGIN = [0,0,0]

X_ROTATION_CONTRACT =
  y' = y*cosX + z*sinX
  z' = z*cosX - y*sinX
X_ROTATION_TEST = PLUS_90_X_MAPS_PLUS_Y_TO_MINUS_Z

CHEST_FACING_ANGLES = VERIFIED_PRESENT
MODEL_LAYER_UV_PRESERVED_ACROSS_WINDING = VERIFIED_BY_TEST

HDBLOCKMODELS_SCALED_MODELS = IMMUTABLE_GENERATION_SNAPSHOT
OLD_STATIC_PATCH_CUSTOM_COPY_LAYER = REMOVED
VOLUMETRIC_SUBMODEL_PATH = REMOVED
LEGACY_UNREFERENCED_RENDERERS = REMOVED_EXCEPT_FLUID
FLUID_STATE_RENDERER = PRESENT_ACTIVE
UNREFERENCED_TF_COLOR_MULTIPLIERS = REMOVED

CUSTOM_BLOCK_MODEL_PUBLICATION = AFTER_SUCCESSFUL_INITIALIZATION

WEIGHTED_MODEL_ALTERNATIVES = PRESERVED_UNTIL_RENDER_SELECTION
WEIGHTED_MODEL_GENERAL_POSITION_SEED = IMPLEMENTED
WEIGHTED_MODEL_MULTIPART_RANDOM_STREAM = CONTINUED
WEIGHTED_MODEL_BLOCKSTATE_SEED_CONTRACT = INCOMPLETE

GRASS_BLOCK_OPAQUE_CLASSIFICATION = PRESENT
FLAT_PATCH_INTERVAL = [min,max)
SWAMP_SIMPLEX_SEED = 2345

CLIENT_DOWNLOAD_LOCAL_CHECK = PRESENT
CLIENT_DOWNLOAD_LOGGING = PRESENT

SMOOTH_GRASS_COLOR_MODE = 3x3_CHANNEL_AVERAGE_9_NEIGHBORS
SMOOTH_GRASS_REGRESSION_TEST_REQUIRES_3x3 = TRUE
HISTORICAL_OWN_BIOME_1_BLOCK_CLAIM_CURRENT = FALSE

PAPER_26_3_NBT_SCALAR_PALETTE_FIX = PRESENT
PAPER_26_3_NBT_SCALAR_PALETTE_FIX_SCOPE = BUKKIT_HELPER_NBT_ONLY
PAPER_26_3_SCALAR_DEFAULT_PROPERTIES = RESTORED_FROM_NATIVE_BLOCK_DEFAULT_STATE
PAPER_26_3_GRASS_BLOCK_SNOWY_DEFAULT = FALSE
PAPER_26_3_BIOME_PACKED_WIDTH = DERIVED_FROM_NATIVE_64_VALUE_STORAGE
PAPER_26_3_BUILD = SUCCESS
PAPER_26_3_RUNTIME_RENDER_AFTER_FIX = USER_CONFIRMED
CORE_CHANGED_BY_PAPER_26_3_FIX = FALSE
FABRIC_CHANGED_BY_PAPER_26_3_FIX = FALSE

PAPER_TOOLS_DIRECTORY = REMOVED
PAPER_TOOLS_RUNTIME_CONSUMERS = NONE
PAPER_TOOLS_BUILD_CONSUMERS = NONE
```

## PAPER TOOLS CLEANUP (2026-09-19)

The unreferenced `paper/tools` directory was removed. `Biomes.ods` was a
historical biome/color spreadsheet and `bukkit_formatting_profile.xml` was an
old Eclipse formatting profile. Neither file had a runtime, build, source-set,
code-generation, or documented maintenance consumer.

Do not restore these files or introduce comparable platform-local tool or
generator assets unless the repository has a concrete active consumer for
them. Gradle-owned output under `paper/build/generated` is unrelated and
continues to be produced from the Paper build configuration.

## PAPER 26.3 NBT PALETTE FIX (2026-09-19)

The Paper 26.3 render produced chunk-shaped black gaps while Fabric 26.3
produced the expected terrain. Checkout review located the fault at the Paper
adapter boundary: `bukkit-helper/.../NBT.java` converted scalar entries in a
26.3 block-state palette through `ListTag.getCompoundOrEmpty`, losing the
scalar block identifier. The common parser then received an empty block name
and resolved that palette entry as air.

Only the Paper NBT wrapper was changed for the palette repair. It now reads a
palette list entry with Paper's native `ListTag` accessors: compound entries
remain compounds and scalar entries retain their block identifier. A scalar
block identifier is Paper 26.3's compact representation of the complete native
default block state. The adapter therefore expands it with the properties from
`Block.defaultBlockState()` before handing it to the common NBT contract. This
preserves facts such as `minecraft:grass_block[snowy=false]`; passing only the
name allowed the common lookup to select a different base state and produced a
snow-covered appearance.

The Paper wrapper also normalizes the packed width of the native 64-entry
biome container. In particular, four longs hold 3-bit values (21 complete
values per long), not 4-bit values. Supplying the wrong width corrupted biome
indices and therefore grass tint input at the Paper-to-Core boundary.

Verification: `./gradlew :paper:build` completed successfully and the changed
Paper NBT file passes `git diff --check`. The user confirmed the corrected
Paper render after the final default-state repair. No Core or Fabric source was
changed for these repairs.

# CURRENT VERSION RESOLUTION

```text
CONCRETE_VERSION_VALUES_IN_THIS SECTION = NONE

RESOLVE_CURRENT_VERSION:
  1. READ current checkout.
  2. READ project version sources used by the relevant build/runtime path.
  3. REPORT values obtained from current project source.
  4. DO NOT substitute values from HISTORY.
  5. DO NOT encode a concrete Minecraft version into Core semantics.

MINECRAFT_VERSION_IDENTIFIER.status = REPLACEABLE_DATA
HISTORICAL_VERSION_VALUE.status = EVIDENCE_ONLY
```

# CURRENT OPEN ISSUES

```text
VERIFIED_CURRENT_OPEN_ISSUES = [
  WEIGHTED_MODEL_BLOCKSTATE_SEED_CONTRACT
]

WEIGHTED_MODEL_BLOCKSTATE_SEED_CONTRACT:
  General position-based weighted selection exists.
  Current Core selection derives the seed directly from block coordinates.
  Minecraft's effective model seed is a BlockState-level contract and may
  differ for states representing linked/multipart block structures.
  Therefore full Minecraft-equivalent weighted selection is NOT yet proven.
  Required correction is semantic transport of Minecraft's effective seed
  contract, not hardcoded special cases for named blocks and not a
  version-number-specific Core branch.

NOTE:
  This list contains only issues verified current by checkout review.
  Absence from this list does not prove absence of other bugs.
```

## CORE RENDER CLEANUP (2026-09-18)

The common Core cleanup in `a492c74` is implemented and build-verified.

Weighted block-model alternatives and their declared weights are retained until
render-time selection. The general coordinate-based seed path and continued
random consumption across matching multipart entries are implemented and
covered by regression tests. A subsequent read-only checkout review established
that this is not yet the complete Minecraft seed contract: Minecraft may derive
the effective model seed through BlockState semantics for linked block
structures. Full Minecraft-equivalent weighted selection therefore remains an
open Core semantic issue.

`HDScaledBlockModels` is an immutable snapshot of one completely loaded model
generation. Reload and snapshot creation are serialized. A successful reload
publishes the new generation for future snapshot acquisition. Already held
snapshot instances remain valid snapshots of their generation; they are not
actively invalidated.

`CustomBlockModel` is published only after renderer construction,
initialization, and tile-entity-field discovery have succeeded. The active
Minecraft fluid path continues to use `FluidStateRenderer`.

`HDBlockVolumetricModel`, the volumetric raytrace/export fallbacks, the
confirmed unreferenced legacy renderers except `FluidStateRenderer`, and the
three confirmed unreferenced TF color multipliers were removed.

The Dynmap Core API 3.8 surface and configuration-loaded perspective, shader,
lighting, and texture contracts were not intentionally changed by this cleanup.
This statement means the relevant contract surfaces were preserved in the
implementation scope; it is not an independent full API/ABI compatibility
certification.

Verification completed for the Core cleanup:
- regression tests added for weighted selection and custom-renderer lifecycle;
- existing fluid regression behavior retained;
- full `:DynmapCore:test` succeeded;
- full project `build` succeeded;
- subsequent read-only checkout review performed.

Runtime visual rendering after this cleanup is not claimed as verified by this
handoff.

# DO-NOT-AUTO-OPEN

```text
AUTO_OPEN = FALSE

TOPICS = [
  TXT_AS_RENDER_TRUTH,
  WATER,
  WORLD_IDENTITY_STORAGE,
  AMBIENTOCCLUSION,
  MODEL_LIGHT_EMISSION,
  BLOCK_SPECIFIC_SPECIAL_MODELS,
  GENERAL_RENDERER_REFACTOR,
  EXTRA_UV_TINT_ABSTRACTIONS,
  IMAGE_FORMAT_WEBP_Q80_FALLBACK
]
```

# TASK GATE

```text
ON_HANDOFF_LOAD:
  NEXT_TASK = NONE
  ACTION = READ_ONLY

ON_USER_TASK:
  EXECUTE_ONLY = USER_REQUESTED_SCOPE

DO_NOT_PROPOSE_TASK_FROM_HISTORY = TRUE
DO_NOT_PROMOTE_HISTORY_TO_CURRENT = TRUE
DO_NOT_REQUIRE_ADAPTER_FOR_CORE_TASK = TRUE
DO_NOT_PATCH_CORE_FOR_ADAPTER_ERROR = TRUE
DO_NOT_HARDCODE_MINECRAFT_VERSION_IN_CORE = TRUE
```

# HUMAN-READABLE ONE-LINE CONTRACT

**AirMap builds a web map from Minecraft. Minecraft is the target. Adapters
deliver Minecraft semantics to the common Core boundary. Version identifiers
are replaceable data, not Core semantics.**

------------------------------------------------------------------------

# B. HISTORICAL EVIDENCE / CHRONIK

**Ab hier folgt der bisherige Handoff-Inhalt als historisches
Beweisarchiv.**

Alle Datums- und Versionsangaben bleiben bewusst erhalten. Sie sagen,
**womit ein Befund damals gewonnen wurde**. Sie definieren nicht
automatisch den heutigen Buildstand. Auch historische Wörter wie
„aktuell", „offen", „Stand" und „nächster Schritt" sind relativ zum
jeweiligen Eintrag zu lesen.

------------------------------------------------------------------------

## Globaler Core-Fix: Model-Rescale rotiert Basisvektoren um den Ursprung (2026-09-10)

**Ursprünglicher Campfire-Befund:** Bei Vanilla-Modellen mit
Elementrotation `rescale:true` und Y=45° waren die beiden Feuer-Ebenen
des Campfires gegensätzlich verzerrt. Die gemessenen Skalierungsfaktoren
waren `sx=0.8284271247461902`, `sy=1.0`, `sz=2.0`; dadurch wurde eine
Diagonalebene auf ungefähr 59 % komprimiert und die andere auf ungefähr
141 % gestreckt.

**Ausgeschlossene Ursachen:** Facing und Blockstate-Y-Rotation sind
korrekt. Auch die Übergabe `State -> globalStateIndex -> Modell` ist
korrekt. An Facing-, State- und `globalStateIndex`-Logik wurde nichts
geändert.

**Root Cause und mathematische Ursache:** Die statische
`PatchDefinition.rotateAround(vec, rx, ry, rz)` rotierte Richtungs- und
Einheitsbasisvektoren fälschlich um `offsetCenter=(0.5,0.5,0.5)`.
`MinecraftModelLoader.rotationFactors` bestimmt seine lokalen
Rescale-Faktoren aus den Maximalbeträgen der rotierten
Einheitsbasisvektoren. Die dabei eingebrachte Translation verfälschte
deshalb die Spalten der Rotationsmatrix und erzeugte die asymmetrischen
Faktoren. Richtungs- und Basisvektoren müssen um `(0,0,0)` rotiert
werden.

**Minimaler Fix:** Ausschließlich der Ursprung des Aufrufs von
`rotatePrecomputed` in der statischen `rotateAround`-Methode wurde von
`offsetCenter` auf den Nullvektor geändert. Die benachbarten
Punkt-/Patch-Rotationen um explizite Zentren bleiben unverändert. Dies
ist ein globaler Core-Mathematikfix, kein Campfire-Sonderpatch und keine
Paper-, Fabric- oder Folia-spezifische Lösung. Implementierungs-Commit:
`1e0cb31` (`Fix model rescale rotation around origin`).

**Regressionstest:** `PatchDefinitionRotationConventionTest` sichert die
Ursprungssemantik für alle drei Achsen. Der neue Y=45°-Fall rotiert die
drei Einheitsbasisvektoren und leitet daraus `sqrt(2), 1, sqrt(2)` ab;
mit der früheren Mittelpunktrotation erkennt er reproduzierbar die
falsche Kombination. Der vorhandene Chest-Facing-Test verwendet
entsprechend einen Richtungsvektor und bestätigt weiterhin alle vier
Facing-Winkel.

**Campfire-/Stage-Gegenprobe:** Die vorhandene reale
`DumpLoaderArgs`-Probe wurde nach dem Fix gegen den lokalen
Minecraft-26.3-pre-3-Clientcache erneut ausgeführt. Der Loader meldete
für alle vier `rescale:true`, Y=45° Feuerflächen (NORTH, SOUTH, WEST,
EAST) identisch `sx=1.414213562373095`, `sy=1.0`,
`sz=1.414213562373095`. Damit besitzen beide Feuer-Ebenen symmetrische
Vanilla-Geometrie; `0.8284271247461902, 1.0, 2.0` trat nicht mehr auf.

**Validierung:** Gezielter Lauf von
`PatchDefinitionRotationConventionTest` und `MinecraftModelLoaderTest`:
`BUILD SUCCESSFUL`, 19 Tests, 0 Fehler. Vollständige DynmapCore-Suite
nach `:DynmapCore:cleanTest`: `BUILD SUCCESSFUL`, 74 Tests, 0 Failures,
0 Errors, 13 Skips. Anschließend `./gradlew build`: `BUILD SUCCESSFUL`
(36 Tasks; 7 ausgeführt, 29 up-to-date). `git diff --check` war sauber.

## Download-Consent für Minecraft-Client-Ressourcen (2026-09-08)

**Vertrag:** AirMap benötigt für den Vanilla-JSON-Ressourcenpfad den zur
gebauten AirMap-JAR passenden Minecraft-Client. Die benötigte
Minecraft-Version wird weiterhin ausschließlich durch den Build
beziehungsweise den bestehenden Versionsvertrag bestimmt. Es gibt keine
Runtime-Auswahl der Minecraft-Version durch den Administrator.
`accept-minecraft-client-download` beantwortet ausschließlich, ob AirMap
einen erforderlichen Minecraft-Client herunterladen darf. Dieser Consent
ist eine **Download-Erlaubnis**; er ist keine
Minecraft-Versionskonfiguration und keine allgemeine Lizenz- oder
Ressourcenfreigabe.

**Consent-Semantik:** Ein fehlender Schlüssel bedeutet keine
Download-Erlaubnis, `false` bedeutet keine Download-Erlaubnis und `true`
erlaubt den Download. Insbesondere ist ein fehlender Schlüssel in
bestehenden oder alten `configuration.txt` keine implizite Zustimmung.
Bestehende Konfigurationen werden nicht automatisch umgeschrieben oder
migriert. Die ausgelieferten Fabric- und Paper-Standardkonfigurationen
enthalten `accept-minecraft-client-download: false`. Die README
dokumentiert diesen Vertrag einschließlich des Upgrade-Verhaltens bei
einer alten `configuration.txt`.

**Lokaler Client und Downloadpfad:** Ein bereits vorhandener, zur
benötigten Version passender und durch die bestehende Validierung
gültiger lokaler Minecraft-Client wird unabhängig vom Consent
wiederverwendet; dafür findet kein neuer Download statt. Ist ein
Download erforderlich, wird die Zustimmung in
`MinecraftClientResources.provision()` unmittelbar vor dem jeweiligen
Netzwerkzugriff geprüft. Ohne Erlaubnis findet kein entsprechender
Netzwerkdownload statt. Bei erlaubtem Download bleibt der bestehende
Downloadpfad unverändert. Versionsbestimmung, Cache sowie Metadaten-,
SHA-1- und Größenvalidierung wurden nicht verändert.

**Verhalten ohne Erlaubnis:** Wenn ein benötigter Download nicht erlaubt
ist, protokolliert AirMap die notwendige Änderung an `configuration.txt`
und beendet seinen eigenen Startpfad sauber. Der Minecraft-Server wird
dadurch nicht beendet.

**Validierung:** `MinecraftClientResourcesTest`: 4 Tests, 0 Fehler, 0
Skips. Der erlaubte Download wurde ausschließlich gegen einen lokalen
Testserver getestet; kein Test griff auf Mojang zu. `git diff --check`
war sauber. Bereits vorhandene fachfremde uncommittete Änderungen wurden
nicht verändert, bereinigt oder zurückgesetzt.

**Bestehender Projektvertrag:** Config und Custom bleiben dauerhafte
Nutzerverträge und dürfen durch die Vanilla-Ressourcenintegration nicht
umgangen werden.

## Fix: `ConcurrentModificationException` beim Start beseitigt -- `HDBlockModels.scaledModels` ist jetzt `ConcurrentHashMap` (2026-09-08)

**Auftrag und STOP:** Diagnostizieren, warum das Plugin beim Start eine
übermäßige CPU-Auslastung zeigt und einen Render-Job sofort mit
`java.util.ConcurrentModificationException` abbricht. Keine weitere
Codeänderung über den bestätigten Fix hinaus; Handoff eintragen, STOP.

**Befund -- die Exception:** Das Server-Log zeigte im ersten Render-Job
nach dem Start (welt `minecraft:overworld`, `map=null` =
Full-World-Render-Stufe) einen Abbruch mit
`java.util.ConcurrentModificationException` bei
`HashMap.computeIfAbsent(HashMap.java:1230)` im Stacktrace über
`HDBlockModels.getModelsForScale(HDBlockModels.java:35)` →
`IsoHDPerspective.OurPerspectiveState.<init>` →
`IsoHDPerspective.render` → `HDMapTile.render`. Ursache:
`HDBlockModels.scaledModels` war ein statisches `HashMap`, das pro
Render-Zustand lazy über `computeIfAbsent` befüllt wird. Sobald die
Render-Queue startet, betreten mehrere **AirMap Render Threads**
dieselbe Map gleichzeitig; `HashMap.computeIfAbsent` ist nicht
thread-sicher und wirft dort die ConcurrentModificationException. Damit
brach jeder startende Render-Job auf der Stelle ab.

**Fix (genau eine Produktionsdatei,
`DynmapCore/.../hdmap/HDBlockModels.java`):** Der Typ des statischen
Caches `scaledModels` wurde von `HashMap` auf `ConcurrentHashMap`
umgestellt (Import `java.util.concurrent.ConcurrentHashMap`, Feld auf
Zeile 22). `getModelsForScale` und `loadModels` (das den Cache mit
`scaledModels.clear()` zurücksetzt -- ist unter `ConcurrentHashMap`
weiterhin zulässig) bleiben inhaltlich unverändert. `ConcurrentHashMap`
wird bewusst nur an dieser einen, im heißen Render-Pfad liegenden Stelle
eingesetzt; andere statische Caches wurden nicht spekulativ angefasst.

**Antwort auf die CPU-Frage:** Die übermäßige CPU beim Plugin-Laden ist
**kein Codefehler**, sondern erwartetes Startup-Verhalten von
`HDBlockModels.loadModels` → `MinecraftModelLoader.load()`: beim Enable
werden alle `blockstates/*.json` geparst, für jede Textur das PNG per
`ImageIO` geladen und jeder Pixel auf Alpha-Opazität geprüft
(`isSpriteOpaque`). Im Log sichtbar an den \~22 s zwischen `17:39:21`
(DB-Verbindung) und `17:39:43` ("Loaded block models directly from
Minecraft JSON resources"). Der ConcurrentModificationException-Abbruch
der Render-Jobs ist durch den obigen Fix beseitigt; die einmalige
Startup-Last beim Blockmodell-/Renderaufbau bleibt bestehen.

**Was im Startup zusätzlich lädt, das nicht (eigener) Plugin-Code ist
(Bezug auf die Frage "was gehört nicht in den Code"):** Die Log-Zeilen
`Using LuckPerms`, `MariaDB 192.168.178.43:3306` und
`cwebp/dwebp unter /usr/bin` belegen externe, über Adaptergrenzen
integrierte Komponenten (Zugriffskontrolle, Map-Speicher,
WebP-Enkodierung) -- sie gehören zur Plugin-Architektur, nicht zum
gemeinsamen Core. Der internal webserver ist deaktiviert ("Internal
webserver is disabled"), lädt also nichts. Die 49 `pending tile renders`
stammen aus `.pending`-Resten der Welt und laufen nach dem Start direkt
in die Render-Queue.

**Abgrenzung / Stand:** Uncommittet im Working Tree sind neben diesem
Fix weiterhin die zwei bereits im Handoff dokumentierten Änderungen aus
dem vorangegangenen Auftrag (`MinecraftModelLoader.modelLayerFace`
Flip-Umstellung und die `chest_facing`-Winkelkorrektur inkl.
`MinecraftModelLoaderTest`). Diese sind bewusst unverändert. Der
ConcurrentMap-Fix wurde auf `DynmapCore`-Ebene auf Dateikorrektheit
geprüft (keine Fehler/Probleme in der Datei); ein voller Gradle-Build
wurde nicht neu ausgeführt.

## Fix: Südlich gebackte Model-Layer rotieren für Ost/West in die korrekte Richtung (2026-09-08)

**Runtime-Ausgangsbefund des Users:** Double Chests waren auf Paper 26.2
und Fabric 26.3 in Nord/Süd korrekt und in Ost/West identisch falsch.
Gemäß Auftrag wurde daraus keine weitere Plattformanalyse abgeleitet und
kein Runtime-Test ausgeführt.

**Erste Core-Abweichung im N/S-vs-O/W-Vergleich:** `PatchDefinition`
bildet seine bestehende Y-Rotation allgemein so ab, dass +90° die
Nordrichtung nach Ost dreht. Das Chest-Layer ist dagegen nach Süd
gebacken. Die bisherige `orientation="chest_facing"`-Zuordnung
verwendete für Ost ebenfalls +90° und drehte die südliche Modellfront
dadurch nach West; für West galt entsprechend die Gegenrichtung. Süd mit
0° und Nord mit 180° waren korrekt, weil dort kein Vorzeichenunterschied
sichtbar wird. UV-Grenzen, `SideVisible.BOTTOM` und die Rotation der
Patchbasis selbst bleiben beim Rotieren unverändert und waren nicht die
erste Abweichungsstelle.

**Minimale allgemeine Korrektur:** Ausschließlich der vorhandene Vertrag
für südlich gebackte statische Layer (`layerRotation`, Orientierung
`chest_facing`) wurde berichtigt: South=0°, East=270°, North=180°,
West=90°. Es gibt keine Änderung an `chest.json`, keine UV-Offsets,
keine Änderung an Mojang-Grundwerten, `PatchDefinition`, Renderern,
Adaptern oder anderen Systemen. `model_rotation`, `horizontal_facing`
und `facing` bleiben unverändert.

**Regressionen:** `MinecraftModelLoaderTest` prüft nun sieben Fälle
insgesamt. Die vier `chest_facing`-Winkel und ein transformierter
südlicher Frontvektor sichern N/S sowie O/W. Der bestehende Test sichert
weiterhin Flip (`BOTTOM`, U=29..44/64) und Nicht-Flip (`TOP`,
U=14..29/64). Ein zusätzlicher Vertragstest erzeugt jede Fläche aller
vier Bell-Layer, der Shulker Box und aller vier
Copper-Golem-Statue-Posen, rotiert sie um Y und belegt, dass U/V-
Grenzen und `SideVisible` unverändert bleiben. Diese Layer verwenden
außerdem nicht die geänderte Orientierung `chest_facing`.

**Validierung:** Gezielter Lauf mit `MinecraftModelLoaderTest`,
`PatchDefinitionRotationConventionTest`,
`MinecraftStaticModelIntegrationTest` und `ChestGeometryContractTest`:
`BUILD SUCCESSFUL`; Loader 7/7 und Rotation 11/11 erfolgreich, die
beiden ressourcenabhängigen Klassen jeweils weiterhin skipped.
Anschließend vollständige DynmapCore-Suite nach `:DynmapCore:cleanTest`:
`BUILD SUCCESSFUL`, 69 Tests, 0 Failures, 0 Errors, 13 Skips.

**Runtime-Grenze:** Die Core-Verträge und Tests sind belegt. Ob der
korrigierte Stand Double Chests in Ost/West real sichtbar richtig
rendert, bleibt gemäß Auftrag dem Runtime-Test des Users auf
Paper/Fabric vorbehalten.

## Fix: Statische Model-Layer behalten Mojangs UV-Zuordnung bei entgegengesetzter Wicklung (2026-09-08)

**Ergebnis:** Die in `MinecraftModelLoader.modelLayerFace` nachgewiesene
allgemeine Ursache ist minimal korrigiert. Bei einer zur UV-Basis
entgegengesetzten Polygonwicklung bleiben jetzt die aus Mojangs
Positions-/UV-Vertices berechnete Patch-Geometrie und die Atlasgrenzen
unverändert; ausschließlich die sichtbare Patch-Seite wird von `TOP` auf
`BOTTOM` gesetzt. Damit beschreibt `SideVisible` die Polygonseite,
während U und V weiterhin unmittelbar Mojangs Zuordnung beschreiben. Der
frühere Flip-Zweig, der zusätzlich die geometrische U-Achse umkehrte und
`1-maxU .. 1-minU` einsetzte, ist entfernt. `modelLayerFace` ist nur
package-lokal statt privat, damit der Vertrag direkt getestet werden
kann. Es gibt keine Chest-Sonderbehandlung und `chest.json` ist
unverändert.

**Vorprüfung anderer statischer Layer:** Derselbe Flip-Pfad wird nicht
nur von Chest benutzt. Aus den eingecheckten Layer-Vertices erreichen
ihn Bell (`bell_between_walls`, `bell_ceiling`, `bell_floor`,
`bell_wall`: jeweils 2/12 Flächen), Shulker Box (2/12), Copper Golem
Statue (`running`, `standing`, `star`: jeweils 9/54; `sitting`: 11/66)
sowie Chest (Single 3/18, Left 3/15, Right 3/15). Für alle diese Flächen
gilt nun derselbe Vertex→Patch-Vertrag: Die Wicklung bestimmt nur die
sichtbare Seite; die Modell-UVs werden nicht nachträglich gespiegelt.
Normale Nicht-Flip-Flächen bleiben `TOP` mit unveränderter Geometrie und
UV-Zuordnung.

**Regressionstest:**
`MinecraftModelLoaderTest.preservesModelLayerUvsForBothPolygonWindings`
prüft direkt einen zuvor fehlerhaften Flip-Fall der linken
Chest-Deckeloberseite: sichtbare Seite `BOTTOM`, U weiterhin exakt
`29..44 / 64`. Als Kontrolle prüft er eine normale Nicht-Flip-Fläche:
sichtbare Seite `TOP`, U exakt `14..29 / 64`. Der Test verwendet die
bestehenden Mojang-abgeleiteten Layer-Vertices und keine manuellen
Produktions-Offsets.

**Validierung:** Gezielter Lauf mit `MinecraftModelLoaderTest`,
`MinecraftStaticModelIntegrationTest` und `ChestGeometryContractTest`:
`BUILD SUCCESSFUL`; Loader 5 Tests, 0 Skips/Fehler, die beiden
ressourcenabhängigen Klassen mangels gesetzter Umgebung jeweils
weiterhin skipped. Anschließend vollständige DynmapCore-Suite nach
`:DynmapCore:cleanTest`: `BUILD SUCCESSFUL`, 67 Tests, 0 Failures, 0
Errors, 13 Skips.

**Verbleibende Runtime-Beweisgrenze:** Unit- und Core-Suite belegen
Patch-Seite und UV-Vertrag sowie fehlende Core-Testregressionen. Ein
neuer realer Paper-/Fabric-Render der betroffenen Chest-, Bell-,
Shulker- und Copper-Golem-Flächen wurde in diesem Auftrag nicht erzeugt;
der sichtbare Runtime-Beweis bleibt daher offen.

## Forensische Core-Chest-Prüfung: Single / Left / Right (2026-09-07)

**Ergebnis:** Der aktuelle, uncommittete UV-Fix an den Deckeloberseiten
von `left` und `right` war nachweislich keine Minecraft-Zuordnung und
wurde exakt zurückgenommen. Die danach fortgesetzte reine Codeanalyse
hat die erste tatsächliche Core-Abweichung lokalisiert:
`MinecraftModelLoader.modelLayerFace` spiegelt bei entgegengesetzter
Polygonwicklung die Atlas-U-Koordinate. Weitere Chest-Codeänderungen
wurden nicht vorgenommen; eine Ausführung der unten beschriebenen
Korrektur darf erst nach ausdrücklicher Freigabe erfolgen.

**Minecraft-Grundlage:** Geprüft wurden die lokal vorhandenen,
deobfuskierten Mojang-Klassen aus Minecraft 26.2 und 26.3-snapshot-10.
In beiden Ständen erzeugt `ChestModel` die Single Chest sowie beide
Double-Chest-Hälften nach demselben Modellvertrag. Für Double Chest
bauen `createDoubleBodyLeftLayer` und `createDoubleBodyRightLayer`
jeweils Body und Lid mit `texOffs(0,19)` beziehungsweise `texOffs(0,0)`,
Breite 15, Höhe 10/5 und Tiefe 14. Die Hälften unterscheiden sich in
X-Lage und ausgelassener innerer Fläche, nicht im UV-Ursprung oder in
den Abmessungen. `ModelPart.Cube` berechnet die Lid-Oberseite daher für
beide Hälften mit U = 29..44 auf der 64 Pixel breiten Textur, also
normiert `0.453125..0.6875`.

### Getrennte Prüfstufen

1.  **Blockstate-Eingang -- entspricht im untersuchten Core-Pfad;
    vorgelagerte Lieferung nicht innerhalb dieses Auftrags beweisbar.**
    `MinecraftModelLoader` liest `facing` und `type` direkt aus
    `DynmapBlockState.stateName`. Mojangs Chest-Blockstate besitzt genau
    `facing`, `type=single|left|right` und `waterlogged`; `waterlogged`
    ändert Mojangs Chest-Modell nicht. Ob jeder Adapter jeden realen
    Weltzustand korrekt anliefert, wurde wegen des ausdrücklichen
    Adapterverbots nicht untersucht.
2.  **Auswahl single / left / right -- entspricht Minecraft.**
    `modelLayerFaces` wählt anhand der Blockstate-Eigenschaft `type`;
    `modelLayerTexture` behält bei `single` `normal` und wählt bei
    `left`/`right` `normal_left`/`normal_right`. Mojangs
    `ChestRenderer`, `MultiblockChestResources.select` und
    `Sheets.chooseSprite` wählen Modell und Textur mit demselben
    `ChestType`. Der alte `renderer/ChestRenderer`/`ChestStateRenderer`
    ist im aktuellen Minecraft-JSON-Pfad nicht die Modellquelle.
3.  **Geometrie -- entspricht der geprüften Mojang-Grundlage.** Single:
    Body 14×10×14, Lid 14×5×14, mittiges Schloss 2×4×1. Double-Hälften:
    Body/Lid jeweils 15 Blöckeinheiten breit und ohne innere WEST-
    beziehungsweise EAST-Fläche; das Schloss ist je Hälfte 1×4×1 am
    gemeinsamen Rand. Die JSON-Koordinaten entsprechen diesen Maßen nach
    der vorhandenen 1/16-Skalierung und geschlossenen Lid-Pose.
4.  **Facing / Weltorientierung -- entspricht Minecraft.** Das statische
    Layer ist in Süd-Ausrichtung gebacken. `chest_facing` bildet
    south/east/north/west auf 0/90/180/270 Grad um Y am Blockzentrum ab.
    Mojangs Renderer verwendet dazu `-Direction.toYRot()` am Zentrum;
    die resultierenden vier Ausrichtungen stimmen überein.
5.  **Zusammensetzung Double Chest -- entspricht auf der Modellgrenze.**
    Jeder bereits als `left` oder `right` gelieferte Blockstate erhält
    die gleichnamige 15-Flächen-Hälfte. Bei Nordausrichtung reicht
    `left` von X=1/16 bis 1 und `right` von X=0 bis 15/16; gemeinsam
    schließen sie die Blockgrenze ohne innere Trennfläche. Der Core
    rekonstruiert den Typ nicht heuristisch aus Nachbarn, weil Minecraft
    ihn bereits im Blockstate liefert.
6.  **Textur / UV / Atlas -- weicht im Core nach dem korrekten
    JSON-Eingang von Minecraft ab.** Die Layer-Auswahl bindet zwar die
    richtigen nativen Mojang-Entity-Texturen `entity/chest/normal`,
    `normal_left` und `normal_right`, und die Lid-Top-UVs im JSON
    `29..44 / 64` folgen exakt aus Mojangs Cube-Formel. Beim Umsetzen
    dieser Mojang-Vertices in eine `PatchDefinition` verändert
    `modelLayerFace` die UV-Zuordnung jedoch nachträglich; der genaue
    Fehler ist im folgenden Abschnitt belegt.
7.  **Übergabe an den Renderer -- strukturell korrekt, inhaltlich
    bereits verfälscht.** Der Loader erzeugt pro vollständigem
    Blockstate ein fertiges `HDBlockPatchModel` und registriert die
    zugehörige Texturfolge. `HDScaledBlockModels` reicht dessen Patches
    unverändert an `IsoHDPerspective`; dort wird kein Chest-Modell mehr
    ausgewählt oder zusammengesetzt. Die falsche U-Spiegelung ist zu
    diesem Zeitpunkt bereits Bestandteil des fertigen Core-Modells.

### Nachgewiesener Fehler und widerlegte Hypothese

Der verworfene Patch ersetzte links U=`29..44` durch `36..50` und rechts
durch `21..35` (normiert `0.5625..0.78125` beziehungsweise
`0.328125..0.546875`). Diese unterschiedlichen Offsets widersprechen
unmittelbar den identischen Mojang-Aufrufen `texOffs(0,0)` und den
identischen Lid-Maßen. Die behauptete Pixelpaarung
`normal_left[35] | normal_right[20]` und das Ziel `left[28] | right[29]`
beweisen keine Modellzuordnung: Pixelähnlichkeit ist kein Bestandteil
von Mojangs Cube-UV-Berechnung. Die Hypothese, diese vier Verschiebungen
seien aus Mojangs Chest-Modell/Texturlayout ableitbar, ist damit
widerlegt.

### Nachgewiesene Core-Ursache des Mittelbalkens

Die Deckeloberseite besitzt in `chest.json` Mojangs vier
zusammengehörige Positions-/UV- Vertices. Für beide Double-Hälften gilt
U=`0.453125..0.6875` (= Pixelgrenzen 29..44). In
`MinecraftModelLoader.modelLayerFace` wird daraus ein geometrischer
Atlas-U-/V-Basisrahmen aufgebaut. Bei der Deckeloberseite zeigt
`cross(uBasis,vBasis)` wegen Mojangs Vertexwicklung entgegengesetzt zum
aus der Vertexreihenfolge bestimmten `windingNormal`; daher ist dort
`flip=true`.

Der `flip`-Zweig dreht die geometrische Patch-U-Achse um und ersetzt
zusätzlich die Texturgrenzen durch `1-maxU .. 1-minU`. Die geometrische
Umkehr bildet denselben Ort bereits mit dem Patchparameter `U'=1-U` ab.
Weil der Renderer diesen Parameter unmittelbar als Atlas-U liest,
bewirkt die zusätzliche Grenzspiegelung eine echte Texturspiegelung um
die Atlasmitte: Aus `0.453125..0.6875` wird `0.3125..0.546875`, also aus
den Mojang-Grenzen 29..44 werden 20..35. An den beiden geometrisch
entgegengesetzten Nahtkanten werden dadurch `normal_left[35]` und
`normal_right[20]` adressiert.

Die zuvor beobachtete Paarung war somit ein korrektes Symptom. Die
daraus abgeleitete manuelle Verschiebung der JSON-UVs behandelte die
Folge statt der Core-Ursache.

**Erste nachgewiesene Abweichungsstelle:**
`MinecraftModelLoader.modelLayerFace`, `flip`- Zweig, bei der Umwandlung
der bereits korrekten Mojang-Vertices in `PatchDefinition`. Vor dieser
Stelle entsprechen Blockstate-Auswahl, Variantenauswahl, Geometrie,
Facing, Double-Chest-Hälften, Textur-ID und JSON-UVs der geprüften
Minecraft-Grundlage.

**Zwingende Korrektureigenschaft, noch nicht ausgeführt:** Eine
Korrektur muss bei entgegengesetzter Wicklung die sichtbare Patch-Seite
passend ausdrücken, ohne die bereits in den Mojang-Vertices vollständig
enthaltene UV-Zuordnung zu spiegeln. Eine konkrete Produktionsänderung
und ihre Auswirkung auf andere statische Model-Layer wurden in diesem
reinen Diagnoseauftrag nicht ausgeführt. Sie bedürfen der ausdrücklichen
Freigabe.

**Zwingende und ausgeführte Korrektur:** Nur die vier geänderten U-Werte
wurden auf den aus Mojangs Modell zwingend folgenden und in `HEAD`
bereits bekannten Stand `29..44 / 64` zurückgesetzt. Damit ist
`chest.json` wieder identisch zu `HEAD`.

**Offen / nicht bewiesen:** Nicht geprüft ist, welche anderen statischen
Model-Layer denselben `flip`-Zweig erreichen und welche konkrete
minimale Implementierungsform den allgemeinen Vertex-/Patch-Vertrag
sicher wahrt. Blockstate-Lieferung und spätere Renderer-Schritte sind
nicht als zusätzliche Fehlerursachen bewiesen. Es erfolgten keine
Produktionsänderung, keine weiteren JSON-UV-Verschiebungen, keine
Fallbacks, Sonderbehandlungen oder Adapteränderungen.

**Ausgeführte vorhandene Tests:** Der gezielte Gradle-Lauf für
`MinecraftModelLoaderTest`, `MinecraftStaticModelIntegrationTest` und
`ChestGeometryContractTest` endete mit `BUILD SUCCESSFUL`. Die vier
Loader-Tests liefen grün. Die beiden ressourcenabhängigen Testklassen
wurden wegen ihrer nicht gesetzten Umgebungs-Voraussetzungen jeweils als
`skipped` ausgewiesen; daraus wird kein Realwelt- oder
Double-Chest-Erfolgsbeweis abgeleitet.

## Fix: `PatchDefinition.rotatePrecomputed` -- X- und Z-Rotation auf die Mojang-Rechtshand-Konvention korrigiert (Y unverändert) (2026-09-03)

**Auftrag und STOP:** Nur `PatchDefinition.rotatePrecomputed()`
korrigieren, damit X und Z die gleiche Mojang-kompatible
Rechtshand-Konvention nutzen wie das bereits korrekte Y. **Y bleibt
unverändert.** Keine Sonderbehandlung nur für Torch, keine
Import-Patches, keine zusätzlichen Workarounds. Verifikation über
Unittests (Z: wall_torch-Lehne, X: axis=x-Szenario, Y-Kontrollfall
„gerichtetes Modell mit Y-Rotation bleibt korrekt"). Danach alle Server
stoppen, Gradle sauber stoppen, keine weiteren Testläufe/Analysen,
HANDOFF aktualisieren, STOP.

**Konvention (Rechtshand, +x=Ost, +y=oben, +z=Süd; aktives Paar
`a' = a·cos − b·sin; b' = a·sin + b·cos`):** - X (um +X):
`y' = y·cosX − z·sinX; z' = y·sinX + z·cosX` - Y (um +Y, UNVERÄNDERT):
`x' = x·cosY − z·sinY; z' = x·sinY + z·cosY` - Z (um +Z):
`x' = x·cosZ − y·sinZ; y' = x·sinZ + y·cosZ`

**Umsetzung (genau eine Produktionsdatei, `PatchDefinition.java`
`rotatePrecomputed`, L137--154):** Die X- und Z-Zweige wurden von der
gespiegelten Vorzeichenwahl (vorher u. a.
`y = z·sin + y·cos; z = z·cos − y·sin`) auf die obigen
Rechtshand-Formeln umgestellt. Y-Zweig (L144--148) inhaltlich
unverändert. Die Methode rotiert in X→Y→Z-Reihenfolge um
`offsetCenter = (0.5,0.5,0.5)` (siehe `rotateAround` L171); Testvektoren
müssen daher mittenzentriert gewählt werden.

**Neuer Test (eine neue Datei):**
`PatchDefinitionRotationConventionTest.java` -- deckt Rechtshand für
X/Y/Z mit mittenzentrierten Offset-Vektoren ab (z. B. Z=+90:
(1.5,0.5,0.5)→(0.5,1.5,0.5); Y=+90: (1.5,0.5,0.5)→(0.5,0.5,1.5); plus
„Achse lässt die eigene Offset-Komponente unverändert"). Existierender
Y-Kontrollfall bleibt
`PatchDefinitionShadeStepTest.rotatesShadeDirectionWithTheModel`
(`getPatch(north,0,90,0,0)` → shadeStep Z_PLUS→X_MINUS).

**Validierung:** Gezielter Lauf
(`PatchDefinitionRotationConventionTest` +
`PatchDefinitionShadeStepTest`) BUILD SUCCESSFUL; anschließend komplette
DynmapCore-Suite BUILD SUCCESSFUL. Tipp für spätere Läufe: Gradle kann
testtask-Ergebnisse cachen/stale wiederverwenden -- nach Quelländerungen
`:DynmapCore:cleanTest` voranstellen.

**Abgrenzung:** Produktiv berührt wurde ausschließlich
`PatchDefinition.java`. Nur produktive Z-Branch-Aufrufer sind die
Mojang-JSON-Pfade in `MinecraftModelLoader` (`rotateElement` für axis=z
z. B. wall_torch, `layerRotation` west `{0,0,90}`/east `{0,0,−90}`).
Legacy-Renderer nutzen nur Y. Ein weiterhin offener (nicht beauftragter)
Punkt bleibt die reale Laufzeit-Verifikation (Fabric dev server + RCON)
für wall_torch/Z und ein axis=x-Modell.

## Fix: `getSmoothGrassColorMultiplier` nutzt die Grasfarbe des eigenen Bioms statt der 3×3-Nachbar-Mittelung (2026-09-01)

**Auftrag und STOP:** Nur `getSmoothGrassColorMultiplier` reparieren:
die nachgewiesene 3×3-Farbmittelung so entfernen/korrigieren, dass die
Grasfarbe der Minecraft-Semantik entspricht. **Keine** Änderungen an
`BiomeMap`, am gerade reparierten Vanilla-Sumpf-Noise, an Paper/Fabric,
Shading, Modellen, Config/Custom oder anderen Farbpfaden
(`getSmoothColorMultiplier`, `getSmoothFoliageColorMultiplier`,
`getSmoothWaterColorMultiplier`, `getGrassColor` unverändert). Keine
Architekturarbeit, kein Refactoring, keine weiteren Untersuchungen.
Minimaler Regressionstest nur für diese Änderung. Bestehende
funktionierende Semantik unverändert. Handoff aktualisiert, STOP.

**Semantik:** Vanilla tintet jeden `grass_block` mit der Grasfarbe
**seines eigenen Bioms an seiner eigenen Position**
(`Biome#getGrassColor(pos)`: Sumpf = fester Vanilla-Ton aus
`applyGrassColorModifier(x, z)`; sonst Colormap-Pixel des Bioms). Es
gibt **keine** Nachbar- Glättung der Display-Farben. Die alte
3×3-Kanalmittelung mischte an der Biomkante Sumpf-Töne mit Außen-Grün ⇒
der gesicherte sichtbare Kontrastband-Mangel aus dem vorigen Befund.

**Umsetzung (genau eine Stelle, `GenericMapChunkCache.java`,
`OurMapIterator`, Zeilen 193--221):** Der 3×3-Loop über
`getBiomeRel(dx, dz)` + Kanalmittelung
`((raccum/cnt)<<16)|((gaccum/cnt)<<8)|baccum/cnt` wurde ersetzt durch
den 1-Block-Fall: `getBiome()` des Blocks und
`getGrassColor(bm, colormap, getX(), getZ())` (= exakt der frühere
Zentrumsbeitrag). Erhalten blieben: try/catch-Fallback `0xFFFFFF` via
`logMultiplierError`, `BiomeMap.NULL`-Guard mit `logNullBiomeGrass`
(NULL ⇒ `0xFFFFFF` + Warnung statt vorher 0/„leere Mittelung"),
Methodensignatur und -name. In einem uniformen Biom ist der alte
9er-Mittelwert == Zentrumsfarbe, d.h. alle funktionierenden Regionen
liefern identische Werte wie zuvor; geändert wird ausschließlich der
Kanten-/Mischfall. `getBiomeRel` bleibt von den übrigen Smooth-Methoden
genutzt (kein toter Code).

**Regressionstest (minimal, eine neue Testmethode):**
`BiomeColorPipelineTest` `cacheWithBiomeBoundary(swamp, plains)` baut
programmatisch einen Chunk (0,0) auf
(`GenericChunkSection.Builder.xzBiome`: x\<8 Sumpf mit
`setGrassColorModifier("swamp")`, x≥8 Plains, Sektion bei
`WORLD.sealevel>>4`, Einschub in `snaparray[0]` via Reflektion wie im
`GrassSideStripeDiagnosticTest`-Muster). Neue
`smoothGrassColorUsesOwnBiomeAtEdgeNot3x3Average`: - Kante (7, sealevel,
7): `getSmoothGrassColorMultiplier` == `getGrassColor(swamp, …, 7, 7)`
(eigener Biom-Ton), NICHT die 3×3-Mischung (Guard: 6× Sumpf + 3× Plains
≠ eigener Ton). - Innen (3, sealevel, 3): Ergebnis == eigener
per-Block-Wert ⇒ funktionierende Region unverändert. Der Test würde vor
dem Fix fehlschlagen (alte Methode liefert an der Kante den Mischwert).

**Validierung:** `:DynmapCore:test --tests "…BiomeColorPipelineTest"`
BUILD SUCCESSFUL (7 Tests, 0 Failures/Errors). Komplette
DynmapCore-Suite: **44 Tests, 0 Failures, 0 Errors, 12 vorbestehende
Skips** (vorher 43 ohne den neuen Test).

**Abgrenzung:** Berührt wurde ausschließlich die eine
Methodendefinition; `getGrassColor`, `BiomeMap` (inkl.
Vanilla-Simplex-Port), alle übrigen Smooth-Color-Methoden, TexturePack
und die Adapter sind unverändert. Der Paper-Befund „grass_color_modifier
wird auf Paper nicht geladen" bleibt als eigenständiger, offener Punkt
bestehen.

## Gesicherter Befund: Sichtbarer Kanten-Kontrast am Sumpf entsteht durch die biome-abhängige 3×3-Mittelung `getSmoothGrassColorMultiplier`; wörtliche SWAMP-Zweige sind im Stock nicht aktiv (2026-09-01)

**Auftrag und STOP:** Erklären, warum **derselbe grass_block an der
Kante im Sumpf einen anderen sichtbaren Kontrast erhält als außerhalb**.
Gesucht wurde **ausschließlich ein biome-/SWAMP-abhängiger Pfad im
gemeinsamen Core**. Keine Änderungen, keine neue
Test-/Debugger-/Diagnose-Infrastruktur. Erste konkrete Sonderbehandlung
gefunden bzw. ausgeschlossen ⇒ Befund gesichert, STOP.

**Befund -- die erste konkrete, im Stock-Pfad aktive
biome-/SWAMP-abhängige Stelle:**
`GenericMapChunkCache.getSmoothGrassColorMultiplier`
(`GenericMapChunkCache.java:193–222`). Sie ist über
`TexturePack.processBlock` (`TexturePack.java:1806`, Zweig
`COLORMOD_GRASSTONED/-270`, `do_biome_shading=true`, kein
`misc/swampgrasscolor.png`) die Standard-Gras-Nachbar-Mittelung:

-   Für jede der 9 Nachbarzellen (3×3 um den Block) wird die Grasfarbe
    **des jeweiligen Nachbar-Bioms** geholt:
    `getGrassColor(bm, colormap, x+dx, z+dz)`
    (`GenericMapChunkCache.java:581–586`) →
    `bm.applyGrassColorModifier(x, z, base)` (`BiomeMap.java:286–295`).
    Sumpf-Nachbar ⇒ einer der beiden festen Vanilla-Töne
    `0xFF4C763C`/`0xFF6A7039`; Nicht-Sumpf-Nachbar ⇒ Colormap-Pixel des
    Nachbar-Bioms.
-   Kanäle werden per ganzzahliger Division gemittelt:
    `mult = ((raccum/cnt)<<16)|((gaccum/cnt)<<8)|(baccum/cnt)`.
-   An der **Biomkante** (Abstand 0--1 Blöcke zur Grenze) enthält das
    Fenster eine Mischung aus Sumpf-Tönen und Außen-Grün. Das
    Mischverhältnis ändert sich pro Blockposition (±1--2 Blöcke
    einschließlich des Grenzbereichs), also erhält **derselbe
    grass_block an der Kante eine vom reinen Ton und vom reinen
    Außen-Grün verschiedene, positionsabhängige Mischfarbe** ⇒ anderer
    sichtbarer Kontrast/Kontrastband.

**Numerische Illustration (Arithmetik, kein Test):** reiner dunkler
Sumpf-Ton `0x4C763C` gegenüber Außen-Grün (Plains-Colormap) `≈0x91BD59`.
Grenzfenster mit 3× Außen + 6× Ton ergibt Kanalmittel `≈0x638D45` --
deutlich heller als `0x4C763C` und deutlich anders als `0x91BD59`;
tiefer im Sumpf (nur Töne im Fenster) mischt die Mitteilung die beiden
Töne zu Zwischentönen. Die 3×3-Mittelung ist damit die Stelle, die aus
der (seit dem letzten Fix vanilla-exakten) harten 1-Pixel-Ton-Grenze ein
sichtbares Misch-/Kontrastband macht.

**Wörtliche SWAMP-Sonderbehandlung im gemeinsamen Core -- geprüft und
für Stock ausgeschlossen:** Die einzigen Biome-Vergleiche im gemeinsamen
Core sind zusätzlich nur `BiomeMap.NULL`-Guards
(`GenericMapChunkCache.java:204/236/266/300/330`,
`GenericChunkSection.java:385`), ohne Farbwirkung. Die einzigen
wörtlichen `== BiomeMap.SWAMPLAND`-Zweige sind: 1.
`getSmoothColorMultiplier` (`GenericMapChunkCache.java:268`) -- Sumpf
speist aus `swampmap` statt `colormap` -- **nur erreichbar über
`imgs[IMG_SWAMPGRASSCOLOR]`/`IMG_SWAMPFOLIAGECOLOR]`**, d.h. custom
`misc/swampgrasscolor.png`/`swampfoliagecolor.png`
(`TexturePack.java:1803–1804/1815–1816`); in Stock-26.2/26.3 nicht
vorhanden ⇒ **nicht aktiv**. 2. `TexturePack.java:2403`
(`(bio == BiomeMap.SWAMPLAND) && (imgs[IMG_SWAMPGRASSCOLOR] != null)`)
-- ebenfalls Image-gegated ⇒ **nicht aktiv**. Zusätzlich: der
Vanilla-`swamp`-Modifier selbst (`BiomeMap.applyGrassColorModifier`
SWAMP-Zweig) ist seit dem letzten Fix exakter 1:1-Port und bewusst nicht
Teil dieser Befundung.

**Abgrenzung:** Dies ist die in der vorigen Runde als „zusätzliche,
später liegende Abweichung" notierte, aber nicht bewertete 3×3-Mittelung
(`getSmoothGrassColorMultiplier`). Sie ist generisch (gilt für Gras und
Laub), aber in der Wirkung biome-/SWAMP-abhängig. In diesem
Beweisauftrag wurde nichts geändert; der Fix folgte im obersten
Abschnitt.

## Gesicherter Befund + Fix: SWAMP-Gras-Modifier nutzt jetzt den echten Vanilla-Perlin-Simplex (Seed 2345) statt `swampPatchNoise` (2026-09-01)

**Auftrag und STOP:** Nur die bewiesene erste gemeinsame Core-Abweichung
im SWAMP-Zweig von `BiomeMap.applyGrassColorModifier` fixen -- exakt die
Vanilla-Positionsauswahl (`PerlinSimplexNoise`, Seed 2345, Oktave `[0]`,
Faktor 0.0225, Schwelle `< -0.1`). Beide Töne (`-11766212` =
`0xFF4C763C` dunkel, `-9801671` = `0xFF6A7039` hell) unverändert. **Nur
gemeinsamer Core** -- keine Paper-/Fabric-spezifische Lösung. Keine
Änderung an funktionierender
Tint-/Config-/Custom-/Model-/Renderer-Pipeline, kein 3×3-Smoothing,
keine anderen Biome, kein Generisches Refactoring, keine neue
Diagnose-Infrastruktur. Bestehende Tests unverändert; ein minimaler
Regressionstest genau für diese korrigierte Sumpf-Berechnung ergänzt.
Danach Vergleich gegen Vanilla 26.2 UND 26.3-Snapshot-10 an festen
Koordinaten (beide müssen dort dasselbe Vanilla-Ergebnis liefern). Keine
Erweiterung der Problemklasse.

**Umsetzung (ausschließlich `DynmapCore`):** - `BiomeMap.java`: der
SWAMP-Zweig behält
`return swampPatchNoise(x, z) < -0.1 ? -11766212 : -9801671;`. -
`swampPatchNoise(x,z)` ruft jetzt
`SWAMP_NOISE.getValue(x*0.0225, z*0.0225)` auf. -
`SWAMP_NOISE = VanillaSimplexNoise(2345L)` -- privater statischer
1:1-Port von Vanilla-26.2 `SimplexNoise` (2D) +
`LegacyRandomSource`/`WorldgenRandom` (aus Bytecode abgeleitet):

-   `LegacySimplexRandom`: Java-Legacy-LCG,
    `seed = (seedIn ^ 0x5DEECE66D) & (2^48−1)`,
    `next(bits) = (seed*0x5DEECE66D + 0xB) & (2^48−1) >>> (48−bits)`,
    `nextDouble = ((next(26)<<27)+next(27)) * 2^-53`, `nextInt(bound)`
    exakt wie `java.util.Random` (2er-Potenz-Pfad + Rejection-Loop). Das
    entspricht genau der Vanilla-Weiche
    `WorldgenRandom.next → inneres LegacyRandomSource.next` (per
    Bytecode bestätigt).
-   Konstruktor konsumiert exakt wie Vanilla: 3× `nextDouble` (xo/yo/zo
    werden konsumiert, für die 2D-Auswertung ungenutzt), danach
    Fisher-Yates `nextInt(256−i)` über `p[0..255]` (`p` Länge 512, obere
    Hälfte bleibt 0 wie in Vanilla, Zugriff nur über `p[i & 255]`).
-   `getValue(x,y)`: Standard-Simplex-2D mit `F2 = 0.5*(√3−1)`,
    `G2 = (3−√3)/6`, 16er-Gradienten- tabelle, `gi = p(ii+p(jj)) % 12`
    usw., Eckenbeitrag `e⁴·dot`, `e = 0.5−x²−y²−z²`, Summe ·70.
-   Thread-sicher: nach der statischen Initialisierung nur noch lesend.

**Validierung -- vorgeschriebener Vergleich gegen BEIDE
Vanilla-Versionen an festen Koordinaten:** - Goldstandard = echte
Vanilla-Klassen direkt aus den Client-JARs ausgeführt
(`/tmp/opencode/vtest/vanilla{26.2,26.3}`): 16 feste Weltkoordinaten,
jeweils `getValue(x·0.0225, z·0.0225)`. 26.2:
`SimplexNoise(WorldgenRandom(LegacyRandomSource(2345)))` gibt exakte
Doubles; 26.3:
`SimplexNoise(WorldgenRandom(LegacyRandomSource(2345)), true)` gibt
Float -- **beide Versionen liefern an jeder festen Koordinate denselben
Vanilla-Ton.** - Der 1:1-Port (PortCheck, identische Logik wie in
`BiomeMap`) ist gegen echtes 26.2 **bit-identisch**, inkl. RNG-Anker
xo/yo/zo = 239.02472691166943 / 143.76131717417817 /
13.914271125085236. - Ton-Ground-Truth an den 16 Koordinaten: dunkel nur
bei (256,0), (-45,-45), (8192,-8192), sonst hell.

**Tests (gezielt, minimal):** neuer
`swampModifierMatchesVanillaSimplexAtFixedCoordinates` in
`BiomeColorPipelineTest` prüft die exakten Vanilla-Töne an denselben 16
festen Koordinaten über `BiomeMap.applyGrassColorModifier`. Bestehender
`swampModifierProducesBothVanillaTones` unverändert. Gesamte
DynmapCore-Suite: 43 Tests, 0 Failures, 0 Errors (12 vorbestehende
Skips).

**Abgrenzung:** In diesem Auftrag NICHT angefasst: DARK_FOREST-Zweig,
3×3-Mittelung `getSmoothGrassColorMultiplier`, Foliage-Tint,
Colormaps/Overrides, Config/Custom/Model/Renderer, Paper-/Fabric-Adapter
(der Paper-Befund „grass_color_modifier wird nicht geladen" bleibt
separat offen). Die nachgelagerte 3×3-Smoothing-Abweichung ist weiterhin
als eigene, bewertbare Abweichung offen -- nicht Teil dieses Fixes.

## Gesicherter Befund: Erste gemeinsame Core-Abweichung der Sumpf-Tint-Bestimmung -- `swampPatchNoise` ersetzt die Vanilla-Positionsauswahl (2026-09-01)

**Auftrag und STOP:** Der Paper-Befund erklärt den sichtbaren Fehler
nicht vollständig -- Fabric liefert den `grassColorModifier` bereits und
zeigt laut Nutzerbefund trotzdem denselben falschen Sumpf. Deshalb wurde
**ausschließlich die gemeinsame Core-Tint-Pipeline** für `grass_block`
im Sumpf geprüft. Ziel war die erste Stelle, an der die
Minecraft-Sumpf-Semantik (swamp modifier: zwei Vanilla-Töne +
positionsabhängige Auswahl) im gemeinsamen Core verloren geht oder
falsch umgesetzt wird. Kein Fix (es war Beweisaufgabe); keine neue
Diagnose-Infrastruktur; kein allgemeines Tint-/Renderer-Refactoring.

**Zuerst festgestellt: Die relevante Vanilla-Semantik ist zwischen
beiden Versionen gleich.** Sowohl die 26.2- als auch die
26.3-snapshot-10-Client-JAR enthalten für `swamp.json`
`grass_color_modifier: "swamp"`, `foliage_color: #6a7039`, kein
`grass_color`-Override, Temperatur 0.8, Downfall 0.9. Der Bytecode des
Vanilla-Modifiers ist ebenfalls identisch:

-   26.2:
    `PerlinSimplexNoise.getValue(x*0.0225, z*0.0225, false) < -0.1 ? -11766212 : -9801671`
    mit
    `BIOME_INFO_NOISE = PerlinSimplexNoise(WorldgenRandom(LegacyRandomSource(2345L)), [0])`.
-   26.3-snapshot-10: gleiche Tonwerte (-11766212 / -9801671), gleiche
    Skalierung (0.0225), gleicher Schwellwert (-0.1), gleicher Seed
    (2345, `BIOME_INFO_NOISE`), nur als
    `Noise.get(x,z)`-Interface-Aufruf eingepackt.

Damit ist kein Versions-/Plattformunterschied für die
Sumpf-Tint-Pipeline vorhanden; jede Abweichung liegt in der Umsetzung
des gemeinsamen Core.

**Erste Abweichung im gemeinsamen Core:
`BiomeMap.applyGrassColorModifier` (SWAMP-Zweig) mit der Hilfsfunktion
`swampPatchNoise` (`BiomeMap.java:286–312`).** Analyse der Executable
chain von der echten Textur-Operation bis zur Tint-Bestimmung:

1.  `TexturePack.processBlock` wählt für `COLORMOD_GRASSTONED` bei
    `do_biome_shading=true` (Default) und ohne
    `misc/swampgrasscolor.png` (in Stock-26.2/26.3 nicht vorhanden) den
    Pfad `mapiter.getSmoothGrassColorMultiplier(colormap)`
    (`TexturePack.java:1802–1806`).
2.  Diese Methode ruft je 3×3-Nachbarblock
    `getGrassColor(bm, colormap, x+dx, z+dz)` auf
    (`GenericMapChunkCache.java:193–222`), das auf
    `bm.applyGrassColorModifier(x, z, base)` mündet (`getGrassColor`,
    Zeile 581--586).
3.  In `applyGrassColorModifier` sind die **beiden Vanilla-Töne
    korrekt** (`-11766212` = dunkel `0xFF4C763C`, `-9801671` = hell
    `0xFF6A7039`), ebenso Schwellwert `< -0.1` und Skalierung `*0.0225`.
    **Die positionsabhängige Auswahl ist falsch umgesetzt:** Sie nutzt
    `swampPatchNoise`, eine handgerollte bilineare Value-Noise auf
    ganzzahligem Hash (`hashCell`), **nicht** die
    Vanilla-`BIOME_INFO_NOISE` (= `PerlinSimplexNoise`,
    LegacyRandomSource-Seed 2345, einzelne Oktave `[0]`).

**Konkreter Unterschied der Positionsauswahl (numerisch belegt, ohne
neuen Test):** `swampPatchNoise` erzeugt achsenausgerichtete bilineare
Patches mit Wellenlänge \~44 Blöcke (0.0225-Teilung, Hash-Gitter bei
ganzzahligen `sx/sz`), Wertbereich nur \~\[-1.0, 0.986\], Dunkelanteil
\~39--41 %. Vanilla `PerlinSimplexNoise` erzeugt dagegen diagonale
Perlin-Simplex-Muster mit demselben Wellenlängen-Maßstab, aber anderer
Form und anderem Seed; damit liegen die dunklen und hellen Sumpf-Flecken
an **anderen Positionen** als in Vanilla. Die Farbtöne selbst stimmen;
der räumliche Layout ist nicht Vanilla-identisch.

**Beweisgrenzen / Abgrenzung:** Die Tonwerte, der Schwellwert und die
Skalierung wurden gegen den echten Bytecode beider Client-JARs geprüft
(26.2 und 26.3-snapshot-10 unter
`/home/jens/.gradle/caches/fabric-loom/26.2/...` bzw.
`/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`).
Es wurde kein neuer Test, kein Debugger und kein Laufzeit-Render
angelegt; die exakte 1:1-Pixel-Reproduktion des
Vanilla-Perlin-Simplex(seed 2345) ist nicht ausgeführt. Die
3×3-Mittelung in `getSmoothGrassColorMultiplier` ist eine zusätzliche,
später liegende Abweichung und wurde für dieses erst-Abweichungs-Ziel
nicht bewertet. Ein Fix wird wie vertraglich erst nach einem neuen,
ausdrücklichen Auftrag mit klarem Umfang umgesetzt.

## Gesicherter Befund: AirMap 26.2 nutzt bei `grass_block` NICHT dieselbe Sumpf-Tint-Bestimmung wie Minecraft 26.2 (2026-09-01)

**Auftrag und STOP:** Nur feststellen, ob AirMap für **Minecraft 26.2
(Paper)** bei `grass_block` dieselbe biomeabhängige
Tint-/BlockColor-Bestimmung verwendet wie Minecraft 26.2. Kein Fix,
keine neue Test-/Debugger-/Diagnose-Infrastruktur, keine allgemeine
Renderer-Analyse. Der Nachweis war **mit vorhandenen Mitteln unmittelbar
möglich** (26.2-Client-JAR + bestehender Code + bestehendes Testwissen).
Ergebnis: **Nein, die Bestimmung ist nicht dieselbe.** Erste konkrete
Abweichung bewiesen; kein Produktionsfix implementiert.

**Ablauf der Feststellung:**

1.  **Vanilla 26.2 definiert Sumpf ohne `grass_color`, aber mit
    Modifier:** `data/minecraft/worldgen/biome/swamp.json` aus
    `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`
    enthält `"grass_color_modifier": "swamp"` (und
    `foliage_color: #6a7039`, kein `grass_color`-Override). Damit
    bestimmt Vanilla die `grass_block`-Gras-Tinte im Sumpf **nicht**
    über die Standard-`grass.png`-Colormap, sondern ausschließlich über
    den `swamp`-Modifier: exakt **zwei feste Töne** `0xFF4C763C`
    (dunkel) und `0xFF6A7039` (hell), ausgewählt durch deterministisches
    Flecken-Rauschen (Simplex, Seed 2345). Beleg zusätzlich im
    bestehenden
    `BiomeColorPipelineTest.swampModifierProducesBothVanillaTones`
    (`-11766212 = 0xFF4C763C`, `-9801671 = 0xFF6A7039`).

2.  **Der Paper-26.2-Adapter lädt den Modifier nicht:**
    `paper/.../DynmapPlugin.loadExtraBiomes` (Zeilen \~608--655) und
    `bukkit-helper/.../BukkitVersionHelper` legen je Biome nur
    **Wasserfarbe, Basistemperatur und Luftfeuchte (downfall)** ab. Im
    Gegensatz dazu liest der **Fabric**-Adapter dieselben Felder *und
    zusätzlich* `grassColorOverride`, `foliageColorOverride` und
    `grassColorModifier` (Zeilen 369--372). Auf dem Paper-Pfad bleiben
    deshalb auf `BiomeMap.SWAMPLAND` (`BiomeMap.java:24`)
    `grassColorOverride=-1` und `grassMode=NONE`.

3.  **Folge in der aktiven Render-Bestimmung:**
    `getSmoothGrassColorMultiplier` → `getGrassColor`
    (`GenericMapChunkCache.java:581–586`) rechnet für Sumpf
    `getModifiedGrassMultiplier(colormap[biomeLookup])` mit
    `grassmult=0x2e282a` (statische Blend-Konstante) und dann
    `applyGrassColorModifier` → wegen `grassMode=NONE` unverändert.
    Konkret: Sumpf-`biomeLookup256=18226` (t=50, h=71);
    `grass.png`-Pixel (50,71) = RGB(106,196,78)=`0x6AC44E`; Blend
    `((0x6AC44E & 0xFEFEFE) + 0x2e282a) >> 1 = 0x4C763C`. Das ist exakt
    der **dunkle** Vanilla-Ton.

4.  **Wesentliche Abweichung:** Die statische Blend ist so kalibriert,
    dass sie nur den **dunklen** Ton `0x4C763C` reproduziert. Der helle
    Vanilla-Ton `0x6A7039` und die zweitönige Flecken-Patchigkeit des
    `swamp`-Modifiers werden **gar nicht erzeugt** -- AirMap rendert den
    Sumpf als flächig einheitliches dunkles `0x4C763C`. Das ist eine
    andere Tint-Bestimmung als Vanilla 26.2. (Hinweis: In Stock-26.2
    existiert kein `misc/swampgrasscolor.png`, daher läuft der Gras-Pfad
    in `TexturePack.java:1806` über
    `getSmoothGrassColorMultiplier`/`getGrassColor` und nicht über den
    separaten MCPatcher-Sumpf-`getSmoothColorMultiplier`-Zweig.)

**Abgrenzung/Beweisgrenze:** Nur die *Bestimmung* der Farbe ist geprüft
(26.2-Ressourcen + Code + bestehende Test-Referenzwerte). Es wurde kein
neuer Test, kein Debugger und kein Laufzeit-Render angelegt. Ein neues
reales Sumpf-Tile auf Paper 26.2 wurde nicht gerendert; die räumliche
Verteilung der Abweichung am sichtbaren Biome-Übergang
(3×3-Nachbarschaftsglättung in `getSmoothGrassColorMultiplier`) wurde
nicht separat quantifiziert. Diese offene Punktwirkung gehört gemäß
Auftrag nicht in diesen Feststellungs-Auftrag; es war nur die
Bestimmungsgleichheit zu klären, und die ist widerlegt.

## Gesicherter Befund: `dirt_path` im Flat-Render mischt den Untergrund nicht ein (2026-09-01)

**Ergebnis und STOP:** Die reduzierte Vanilla-Modellhöhe von `dirt_path`
(15/16 Block) führt im Minecraft-JSON-Loader erwartungsgemäß zur
Klassifikation `SEMITRANSPARENT`. Diese Klassifikation wählt jedoch
keinen Wasser-artigen Farb-Composite-Pfad und lässt im senkrechten
Flat-Render keinen darunterliegenden Block beitragen. Die vermutete
erste Abweichung ist im untersuchten Pfad nicht vorhanden; deshalb wurde
kein Produktionsfix implementiert und die Problemklasse nicht erweitert.

**Erster konkreter Laufzeitübergang:** Ein echter Flat-Strahl trifft
Patch/Texturindex 1, die obere `dirt_path`-Fläche. Direkt vor dem
Shaderabschluss wurden per nicht suspendierendem Debugger-Logpoint
`sampledAlpha=255`, `accumulatedAlpha=0` und `textureIndex=1`
beobachtet. Unmittelbar nach `processBlock` meldete der zweite Logpoint
für denselben Treffer `shaderDone=true`, `patch=1`, `step=Y_MINUS`.
Damit beendet bereits der Dirt-Path-Treffer den Strahl; die
darunterliegende Darstellung erreicht den Composite-Schritt nicht.

**Direkter Beleg:** Das reale 26.3-Modell besitzt eine 15/16 hohe, über
die gesamte X/Z-Fläche reichende Oberseite; deren reale Textur ist
vollständig alpha-opaque. Ein einmaliger Flat-Lauf durch die bestehende
`IsoHDPerspective.OurPerspectiveState.raytrace`-Pipeline mit
Produktionsparametern (`azimuth=180`, `inclination=90`, `scale=4`,
resampelte Standard-Textur) ergab:

-   registrierte Transparenz: `SEMITRANSPARENT`;
-   16 Flat-Treffer auf `dirt_path`, alle auf `Y_MINUS`;
-   Alpha-Histogramm: 16-mal `255`;
-   16-mal letzter opaker Produzent `dirt_path`;
-   **0-mal** Dirt-Path-Treffer mit anschließendem Beitrag des Blocks
    darunter;
-   der Lauf endete erfolgreich.

Die dafür einmalig verwendete Probe wurde nach Sicherung dieser Werte
wieder entfernt; es wird keine neue Dirt-Path-Debug- oder
Testinfrastruktur im Projekt behalten.

**Kausalkette:** `MinecraftModelLoader` verlangt für `OPAQUE` sechs opak
texturierte Vollwürfelflächen. Das 15/16-Modell erfüllt diese
Geometriebedingung nicht und wird deshalb `SEMITRANSPARENT`.
`TexturePackHDShader` entscheidet über das tatsächliche Weiterlaufen
aber nicht anhand dieses Enums, sondern anhand des gesampelten
Farb-Alphas. Die voll opake obere Textur liefert 255; bei bisher
transparenter Akkumulationsfarbe übernimmt der Shader diese Farbe und
gibt sofort `true` zurück. `IsoHDPerspective.handlePatches` beendet
daraufhin den Strahl. Die Klassifikation beeinflusst hier unter anderem
Lichtbehandlung, erzeugt aber keine Farbmischung.

**Abgrenzung:** Wasser wurde ausschließlich als gewünschter
Vergleichseffekt betrachtet und weder analysiert noch geändert. Gras,
Tint und allgemeine Renderersemantik wurden nicht geöffnet. Ein
sichtbarer Dirt-Path-Unterschied müsste mit einem neuen konkreten Befund
außerhalb dieser widerlegten Layer-/Composite-Hypothese separat
lokalisiert werden.

## Gesicherter Befund: `grass_block` OPAQUE vs. SEMITRANSPARENT (2026-08-31)

**Fix abgeschlossen:** Der Test ist richtig; die AirMap-Klassifikation
war für dieses mehrlagige Vollmodell falsch. Betroffen war der
gemeinsame Core-Pfad und damit sowohl Vanilla **26.2** als auch
**26.3-snapshot-10**.

Beide realen Client-JARs enthalten dasselbe `grass_block`-Modell:

-   ein erstes Element von `[0,0,0]` bis `[16,16,16]` mit allen sechs
    Grundflächen;
-   ein zweites, deckungsgleiches Vollblock-Element mit ausschließlich
    vier seitlichen Gras-Overlay-Flächen.

Die echten PNGs sind in beiden Versionen gleich klassifiziert: `dirt`,
`grass_block_top` und `grass_block_side` sind vollständig alpha-opaque;
nur `grass_block_side_overlay` besitzt transparente Pixel. Das Overlay
legt Farbe auf die bereits deckende opaque Grundseite und erzeugt daher
weder ein Loch in der Blockdeckung noch Teilblockgeometrie.

Die Implementierung hatte zwei lokale Fehler im selben
Klassifikationspfad:

1.  `isSpriteOpaque` übergab dem `MinecraftResourceProvider` den
    internen ZIP-Pfad `assets/<namespace>/...` statt der vertraglichen
    Resource-ID `<namespace>:textures/...`. Der Provider wies ihn ab;
    der Catch-Pfad speicherte deshalb sogar für `dirt` und
    `grass_block_side` fälschlich `false`.
2.  Danach verknüpfte AirMap die Alpha-Opazität **jedes** Face-Sprites
    per `&=`. Selbst bei korrektem Ressourcenlesen hätte das
    transparente Overlay den gesamten Zustand auf `SEMITRANSPARENT`
    gekippt, obwohl jede Seite bereits durch das erste Element opaque
    gedeckt ist.

Dies widersprach auch der lokalen Bedeutung von `BlockTransparency`:
`SEMITRANSPARENT` ist dort für opaque Blöcke definiert, die wegen
Teilgeometrie nicht alle Strahlen blockieren (z. B. Stufen/Slabs);
`grass_block` blockiert sie durch den Grundwürfel.

**Konkrete allgemeine Änderung:** `isSpriteOpaque` verwendet jetzt die
korrekte Provider-ID. `install` aggregiert nicht mehr destruktiv über
alle Sprites, sondern sammelt additiv die durch opak texturierte,
unrotierte Vollwürfelflächen tatsächlich gedeckten sechs
`BlockStep`-Richtungen. Nur vollständige Deckung aller sechs Außenseiten
kann zusammen mit der bestehenden Lichtdämpfungs-/Waterlogged-Prüfung
`OPAQUE` ergeben. Transparente Zusatzlagen entfernen keine vorhandene
Deckung; fehlende oder Teilblockflächen erzeugen keine erfundene
Deckung. Es gibt keinen Block-Sonderfall; Config/Custom-Pfade wurden
nicht verändert oder umgangen.

**Beweisquellen:**

-   26.2:
    `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`,
    `assets/minecraft/models/block/grass_block.json` und die vier
    genannten Texturen.
-   26.3-snapshot-10:
    `/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`,
    dieselben Ressourcenpfade.
-   Implementierung: `MinecraftModelLoader.java`, Berechnung von
    `opaqueSprites` und Registrierung der `BlockTransparency`
    unmittelbar nach Installation des Patchmodells.
-   Debuggerbeleg vor der Korrektur des Provider-Aufrufs: für den realen
    `minecraft:grass_block[snowy=false]` war `opaqueCubeFaces=[]`; der
    Cache enthielt für `block/dirt`, `block/grass_block_top`,
    `block/grass_block_side` und das Overlay jeweils `false`.

**Regression und Tests:** `MinecraftModelTintRegistrationTest` prüft nun
zusätzlich den realen `heavy_core`: `grass_block` wird `OPAQUE`;
`heavy_core` bleibt trotz opakem Sprite und Lichtdämpfung 15 wegen
seiner Teilblockgeometrie `SEMITRANSPARENT`. Isolierter Regressionstest
und die gemeinsame relevante Gruppe aus
`MinecraftModelTintRegistrationTest`,
`MinecraftStaticModelIntegrationTest` und `MinecraftModelLoaderTest`
endeten jeweils mit `BUILD SUCCESSFUL`.

**Verbleibende Runtime-Beweisgrenze:** Der Fix ist gegen die echten
konfigurierten 26.3-snapshot-10-Ressourcen getestet und die identischen
relevanten 26.2-Modell-/PNG-Fakten sind direkt aus dem 26.2-Client-JAR
belegt. Ein reales neues Paper-/Fabric-Tile wurde in diesem Auftrag
nicht gerendert.

## Laufende enge Untersuchung: Zäune im Flat-Render (2026-08-31)

**Fix abgeschlossen:** Nach ausdrücklicher Freigabe der allgemeinen
Flat-/Patch-Schnittsemantik wurde die Patchfläche in
`IsoHDPerspective.handlePatch` auf eine halb offene Randkonvention
`[min, max)` umgestellt, gemeinsam gekapselt in `isWithinPatchBounds`.
Es gibt keinen Zaun-Sonderfall und keine Sampling-, Projektions- oder
Surface-Sonderlogik.

Geometrische Begründung: Die vorherige offene Konvention `(min, max)`
erzeugt Rasterlöcher, wenn eine schmale Fläche genau zwischen
Pixelzentren liegt. Vollständig geschlossene Grenzen würden dagegen
gemeinsame Kanten doppelt besitzen. Halb offen nimmt die Min-Kante auf
und weist die Max-Kante ab; bei zwei angrenzenden Parameterintervallen
gehört die gemeinsame Kante damit genau einem Patch.

**Tests nach Produktionsänderung:** Das reale Vanilla-Zaunmodell (26
Patches) ergibt mit der Produktionskonvention Flat **1** Treffer statt
0; die Surface-Kontrolle bleibt unverändert bei **205** Treffern.
`IsoHDPerspectivePatchBoundaryTest` belegt Min-/Max-Ränder und exakt
einen Besitzer einer gemeinsamen Kante. Gemeinsam erfolgreich
(`BUILD SUCCESSFUL`): `FenceFlatVisibilityProbeTest`,
`IsoHDPerspectivePatchBoundaryTest`,
`MinecraftStaticModelIntegrationTest`, `MinecraftModelLoaderTest` und
`MinecraftModelUvLockTableTest`.

Der damals zusätzlich isoliert rote `MinecraftModelTintRegistrationTest`
war keine Regression der Patch-Randänderung. Seine Ursache und der
inzwischen erfolgreiche lokale Fix sind im vorangestellten
`grass_block`-Abschnitt gesichert.

**Verbleibende Beweisgrenze:** Die Geometrie ist durch reale
Client-Patches und echte Perspektivtransformationen im Test belegt. Ein
neu erzeugtes reales Flat-Tile auf Paper/Fabric wurde in diesem Auftrag
nicht gerendert; der sichtbare Runtime-Beweis bleibt daher offen.

**Auftrag und Grenze:** Ausschließlich lokalisieren, warum Zäune in der
senkrechten Flat-Perspektive unsichtbar sind, obwohl die
Surface-Perspektive sie rendert. Keine allgemeine
Flat-/Renderer-Analyse. Ein Fix wird nur umgesetzt, wenn er lokal und
ohne Architekturänderung eindeutig ist; andernfalls wird mit gesichertem
Befund gestoppt.

**Historischer Ausgangsbefund vor der späteren Fix-Freigabe:** Die
Vanilla-JSON-Integration liefert für
`minecraft:oak_fence[east=true,north=false,south=true,waterlogged=false,west=false]`
ein Multipart-Patchmodell mit 26 Patches
(`MinecraftStaticModelIntegrationTest` erfolgreich).
`FenceFlatVisibilityProbeTest` verwendet genau diese realen
Loader-Patches, die echte `IsoHDPerspective`-Transformation und echte
Pixelzentren. Ergebnis:

-   Flat `iso_S_90_lowres` (`azimuth=180`, `inclination=90`, `scale=4`):
    **0** Patchtreffer.
-   Dieselben Flat-Strahlen bei ausschließlich inklusiv gerechneten
    `u/v`-Patchrändern: **4** Treffer.
-   Surface-Kontrolle (`azimuth=135`, `inclination=30`, `scale=16`):
    **205** Patchtreffer.
-   Gezielter Testlauf: `BUILD SUCCESSFUL`.

Damit liegt die erste Verluststelle in `IsoHDPerspective.handlePatch`:
Beim senkrechten Scale-4-Raster liegen die relevanten Zaunstrahlen exakt
auf den Rändern der nur 1/4 Block breiten Zaunflächen; die bestehenden
offenen Intervallprüfungen `u <= umin || u >= umax` beziehungsweise
`v <= vmin || v >= vmax` verwerfen alle davon. Die schräge/höher
aufgelöste Surface-Abtastung trifft dagegen Flächeninneres.

**Damals noch kein Produktionsfix implementiert:** `<=`/`>=` global zu
ändern würde die Schnitt-/Kantensemantik sämtlicher Patchmodelle
erweitern (inklusive Mehrfachtreffern an gemeinsamen Kanten); ein
Pixeloffset oder Supersampling würde das Sampling aller Flat-Inhalte
ändern; eine Zaun-Sonderbehandlung wäre ein blockweiser Sonderfall.
Damit müsste die Problemklasse über Zäune hinaus erweitert werden. Gemäß
damaligem Auftrag wurde dort gestoppt; der nachfolgende ausdrückliche
Auftrag hat genau diese allgemeine Untersuchung freigegeben. Der
Probe-Test bleibt als Nachweis erhalten.

**Stand (2026-08-31, aktuell):** Weltidentitäts-Fix, Paper-26.2-Adapter
und die enge Vanilla-Gegenprüfung des JSON-Renderers für `rescale`,
`uvlock`, Tint-Kopplung und Render-Type sind abgeschlossen. Paper 26.2
wurde real gestartet; AirMap und Webclient funktionieren, und ein
`radiusrender 100` hat sichtbar neue JSON-basierte Tiles erzeugt.

**Übergabestatus:** Es besteht kein automatisch auszuführender
Folgeauftrag. Insbesondere autorisiert dieser Handoff weder Serverstarts
noch Builds, Deployments, Render-Kommandos, Runtime-Vergleiche oder neue
Untersuchungen. Solche Arbeiten beginnen ausschließlich nach einem
neuen, ausdrücklichen Nutzerauftrag mit klarer Plattform und klarem
Umfang.

------------------------------------------------------------------------

## 0. Verbindlicher Projektvertrag

-   AirMap liest die Realität der jeweils eingesetzten
    Minecraft-Version. Paper und Fabric lesen die jeweils in
    `gradle/libs.versions.toml` festgelegte Minecraft-Realität (Version
    Catalog, `[versions]`); dort ist die einzige maßgebliche Angabe. Im
    Vertrag wird keine konkrete, veraltbare Versionsnummer
    festgeschrieben.
-   Gleiche Vanilla-Semantik bleibt im gemeinsamen Core. Tatsächliche
    Versions- oder Plattformunterschiede werden an der bestehenden
    Adaptergrenze behandelt.
-   Der historische TXT-Renderer ist nur alte Referenz, nicht
    Render-Wahrheit.
-   Sichtbare Unterschiede zwischen TXT und JSON oder zwischen 26.2 und
    26.3 sind zunächst Abweichungen, keine Fehlerbeweise. Zuerst gegen
    die jeweilige Vanilla-Version prüfen.
-   **Config und Custom sind dauerhafte Nutzerverträge.** Die Umstellung
    auf Vanilla-Ressourcen darf diese Mechanismen nicht umgehen.
-   Keine erfundenen IDs, blockweisen Sonderfälle oder Heuristiken, wenn
    Minecraft die Information selbst liefert.
-   Dieser Handoff dokumentiert gesicherte Fakten, Grenzen und mögliche
    Übergabepunkte. Er ist kein Arbeitsplan und keine implizite
    Erlaubnis, offene oder mögliche Schritte selbständig abzuarbeiten.

------------------------------------------------------------------------

## 1. Versionsstände und Build-Basis

**Maßgeblich ist ausschließlich `gradle/libs.versions.toml`** (Version
Catalog, `[versions]`). Laufzeit-/Build-Versionen nicht hart kodieren
oder erfinden; wo im Runtime-Paket vorhanden, gelten immer die
Catalog-Angaben. Alle nachfolgend gelisteten Werte sind Stand 2026-09-08
direkt aus dem Catalog entnommen:

-   **Core:** gemeinsamer DynmapCore; er liest den echten
    Vanilla-Client-JAR der konfigurierten Ressourcenversion
    (`MinecraftClientResources.configuredVersion()` aus
    `airmap-minecraft-version.properties`, gespeist von
    `libs.versions.minecraft`).
-   **Fabric-Adapter** (`minecraft`, `minecraftRuntime`, `fabricLoader`,
    `fabricApi`, `fabricLoom`):
    -   Minecraft Build `26.3-pre-2` → erzeugte Ressource
        `minecraft-client-26.3-pre-2.jar`
    -   Laufzeit-Label `26.3-pre.2` (Fabric Loader-Deklaration)
    -   Fabric Loader `0.19.5`, Fabric API `0.159.4+26.3`, Loom `1.18+`
-   **Paper-Adapter** (`paper`, `airmapPaper`, `paperweightUserdev`,
    `pluginYmlPaper`):
    -   Paper zentral `26.2.build.+`
    -   `airmapPaper` (JAR-Name + paper-plugin.yml) `2.0.0-paper-26.2`
    -   paperweight `2.0.0-beta.21`, plugin-yml Paper `0.9.0`
    -   Support-Deps zentral: LuckPerms `5.5`, Vault `1.7.1`,
        GroupManager `2.10.1`
-   **Java:** Build `26`, Target `25`; **Gradle Wrapper:** laut
    `gradle/wrapper/gradle-wrapper.properties`.
-   Module: `:paper`, `:fabric`, `:bukkit-helper`, `:dynmap-api`,
    `:DynmapCore`, `:DynmapCoreAPI`.

Architektur:

``` text
gemeinsamer Core
 ├─ Fabric-Adapter → Minecraft 26.3-pre-2 (Runtime-Label 26.3-pre.2)
 └─ Paper-Adapter  → Minecraft 26.2
```

**Hinweis zur früheren Bezeichnung `26.3-snapshot-10`:**
`libs.versions.toml` nutzt seit dem Wechsel auf `26.3-pre-2` /
`26.3-pre.2` keine `-snapshot-10`-Kennung mehr. Die
`minecraft-client-26.3-snapshot-10.jar` ist eine historische Datei aus
früheren Builds; der aktuelle Build erzeugt strikt
`minecraft-client-26.3-pre-2.jar`. `snapshot-10`-Referenzen in
Befund-/Test-Kommentaren sind historisch und beziehen sich auf jenen
früheren Ressourcenstand.

Vorherige Fabric- und Paper-Builds waren erfolgreich. Das dokumentiert
Kompilierung und Tests, nicht das Laufzeitverhalten.

------------------------------------------------------------------------

## 2. Paper 26.2 (integriert und real gestartet)

-   Paper-Zuordnung liegt im Paper-/Bukkit-Adapter; gemeinsame
    Vanilla-Rendersemantik bleibt im Core.
-   Anpassungen gegenüber der 26.2-Referenz:
    -   `ModSupportImpl.init()` entfernt (Future-Core besitzt dieses
        historische Modsupport-Modul nicht).
    -   `getWorldAliases()` auf die Future-Core-API `getNameAliases()`
        umgestellt.
    -   `BukkitWorld.getLegacyWorldNames` identitätskonform, ohne
        erfundene Hardcodes.
-   Die Ressourcenversion für Paper ist korrigiert: Paper 26.2 verwendet
    Vanilla-Ressourcen 26.2, nicht die Fabric-Ressourcenversion.
-   **Runtime belegt:** Paper 26.2 startet AirMap, der Webclient
    funktioniert, und `radiusrender 100` erzeugte sichtbar neue
    JSON-basierte Tiles neben altem TXT-Bestand. Damit ist der
    JSON-Ressourcenpfad auf Paper 26.2 praktisch nachgewiesen.
-   Dieser Runtime-Beleg bewertet nicht automatisch jede einzelne
    Rendersemantik. Daraus folgt jedoch kein selbständig auszuführender
    Vergleichs- oder Folgeauftrag.
-   Die 26.2-Referenzquelle liegt unter `/tmp/opencode/AirMap-ref`.

Das zuvor dokumentierte Paper-JAR
`target/AirMap-1.0.10-paper-26.2+9e2616a-dirty-paper.jar` war ein
damaliges Build-Ergebnis. Nach den Renderer-Korrekturen ist es nicht
automatisch der aktuelle Runtime-Artefaktstand.

------------------------------------------------------------------------

## 3b. Aktueller Befund: Richtungs-Shading-Multiplikatoren vs. Vanilla (in Untersuchung)

**Status:** neu untersucht (2026-08-31), gesicherte Fakten unten. **Kein
Fix, keine Config-, Custom- oder Adapterentscheidung abgeleitet.** Die
bereits geschlossene Frage „geht Shade-Information beim Patch verloren?"
wird NICHT wieder geöffnet --- die Information ist beim Patch
angekommen. Neu ist eine **Wertübersetzungs-Abweichung** (welche
Multiplikatoren ein Shade-Step liefert).

### Aktivpfad (Default)

`useBrightnessTable` ist per Default **false** (`MapManager.java`).
Damit läuft der **else-Zweig** ohne brightness table in
`TexturePackHDShader.processBlock` (Zeilen 231--248). Die gemessenen
Multiplikatoren (ausführender Scratch-Probe `ScratchShadeKeyProbeTest`,
BUILD SUCCESSFUL):

  ------------------------------------------------------------------------------
  Shade-Step   Fläche              Aktivpfad       Table-Pfad   Vanilla-Faktor
  (Key)        (Vanilla-Begriff)
  ------------ ------------------- --------------- ------------ ----------------
  `Y_MINUS`    Top                 **85,1 %        95,3 % / 100 **1.0**
                                   (gerade Y) /    %
                                   90,2 %
                                   (ungerade Y)**

  `Y_PLUS`     Bottom              **85,1 % / 90,2 50,2 %       **0.5**
                                   % (identisch
                                   mit Top!)**

  `Z_*`        North/South         **100 % (keine  80,4 %       **0.8**
                                   Abdunklung)**

  `X_*`        East/West           62,7 %          60 %         0.6
  ------------------------------------------------------------------------------

Abweichungen: Top ist im Aktivpfad 10--15 % zu dunkel **und** abhängig
von `getY() & 1` (Paritäts-Banding, das Vanilla nicht kennt); Bottom ist
im Aktivpfad von Top nicht unterscheidbar (beide 85/90 %) und massiv zu
hell (Vanilla 0.5); North/South ist 20 % zu hell (100 % statt 0.8). Der
Table-Pfad behält zusätzlich das Paritäts-Banding auf Top.

### Versionsabhängiger Weg in den Y_MINUS-Pfad: `shade_direction_override`

Reale Ressourcen aus den Client-JARs (nicht geraten):

-   **26.2 `assets/minecraft/models/block/cross.json`** (Pflanze/Blume):
    beide Elemente `"shade": false` → `getShade()=false` → **keine
    Abdunklung, 100 %**. Damit ist der 26.2-Pfad hier Vanilla-konform.
-   **26.3-snapshot-10 `cross.json`**: stattdessen
    `"shade_direction_override": "up"`.
    `MinecraftModelLoader.SHADE_DIRECTIONS` bildet `"up"` auf
    `BlockStep.Y_MINUS` ab; dieser überlebt `rescale`+Rotation (Y-Achse,
    bleibt `Y_MINUS`) und gelangt über `patch_shade_step` →
    `cur_shade_step` → `getShadeStep()` in den Shader. Folge im
    **Aktivpfad: 85,1 % (gerade Y) / 90,2 % (ungerade Y)** statt des
    Vanilla-Konstantfaktors `up = 1.0`; im Table-Pfad 95,3 %/100 %.

Damit wird eine Pflanze in 26.2 zu 100 %, in 26.3 zu \~86--90 % mit
Paritäts-Banding gerendert. Das bandförmige 5‑%‑Schrittmuster entlang
von Höhenstufen und die hellen Nord/Süd-Kanten (Z 100 % an Top/Kanten
mit 85--90 %) sind der plausibelste technische Ursprung der beobachteten
hellen Streifen im JSON-Pfad; ein Laufzeit-Screenshot-Blob fehlt bisher
für den Verbindungsbeweis.

### Beweislage

-   `ScratchShadeKeyProbeTest` (unversioniert, wie die übrigen
    Scratch-Tests) rechnet Würfel (alle 6 Seiten) und beide
    Cross-Varianten durch der realen JSON-Semantik durch
    (`PatchDefinitionFactory.getModelFace` → `getScaledPatch` →
    `getPatch`, exakt wie
    `MinecraftModelLoader.install`/`rotateElement`).
-   26.2- und 26.3-`cross.json` + `cube.json` wurden aus den realen
    Client-JARs extrahiert:
    `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`
    bzw.
    `/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`.

------------------------------------------------------------------------

## 4. Test- und Laufzeitstatus

-   Gezielter `MinecraftModelUvLockTableTest`: erfolgreich.
-   Gezielter `MinecraftModelTintRegistrationTest`: erfolgreich.
-   Der dafür ausgeführte Gradle-Testlauf für `:DynmapCore:test` endete
    mit `BUILD SUCCESSFUL`.
-   Frühere vollständige Core-Testläufe hatten ausschließlich den
    unversionierten Scratch-Test `ChestFlatVsSurfaceRenderTest` rot.
-   Frühere Fabric-/Paper-Builds waren erfolgreich.
-   **Keiner dieser Build-/Testerfolge ist ein Runtime-Beweis.** Der
    konkrete Paper-Runtime-Beleg steht separat in Abschnitt 2.
-   Als mögliche, derzeit nicht beauftragte Validierung bleibt offen,
    den aktuellen Stand auf Paper 26.2 und Fabric 26.3-snapshot-10
    jeweils gegen die passende Vanilla-Version zu prüfen. Diese
    Feststellung legt weder Reihenfolge noch Umfang eines späteren
    Auftrags fest.

------------------------------------------------------------------------

## 5. Weltidentität und bestehende Ruhepunkte

### Weltidentität (abgeschlossen)

Verbindliche Regel: Identität = Weltname + heutige Minecraft-Dimension.

-   Historische Dimensionsnamen dienen nur als Kompatibilitätseingang
    und werden auf heutige Dimensionen abgebildet.
-   Keine erfundene ID oder Raterei; unbekannte Namen bleiben
    unverändert.
-   Alias-Listen enthalten keine hartkodierten „world"-Behauptungen.
-   Fabric: `FabricWorld.getCanonicalLevelName` und
    `getLegacyLevelNames`.
-   Paper: `BukkitWorld.getLegacyWorldNames`;
    `DynmapPlugin.getWorldByName` verwendet `getNameAliases()`.

Weltidentität und Storage nicht ohne neuen konkreten Befund wieder
öffnen.

### Frühere reale Fabric-Bestätigungen

-   Normale JSON-Modelle, Pflanzen und der Positivpfad für
    Chest/Shulker.
-   Copper Golem Statue sichtbar im realen Surface-Render.
-   Fließendes Wasser sichtbar und vom Nutzer als scheinbar richtig
    bestätigt.

Wasser ist Ruhepunkt und gehört nicht in die nächste Renderer-Runde.

### Arbeitsbaum schützen

-   Nichts resetten oder verwerfen.
-   Unversionierte Scratch-Tests bleiben als Nachweis:
    `ChestFlatVsSurfaceRenderTest`, `FlatLossProbeTest`,
    `ScratchLayerProbeTest`, `ScratchShadeKeyProbeTest` (Befund 3b).
-   Im Arbeitsbaum liegen außerdem Änderungen zu Wasser, Weltidentität
    und Paper-Adapter; sie gehören nicht zum engen
    JSON-Renderer-Auftrag.
-   Occlusion bleibt experimentell/offen und wird nur bei neuem
    konkretem Befund wieder aufgenommen.

------------------------------------------------------------------------

## 6. Nicht ohne konkreten Befund wieder öffnen

-   TXT als Render-Wahrheit
-   Wasser
-   Weltidentität / Storage
-   `ambientocclusion`
-   modellbasierte `light_emission`
-   blockweise Sondermodelle
-   allgemeines Renderer-Refactoring
-   zusätzliche UV-/Tint-Abstraktionen
-   Config-Default `image-format: webp-q80` und dessen
    Fallback-Verhalten; niemals ohne genaue ausdrückliche Anweisung
    ändern

**Arbeitsprinzip:** Die Minecraft-Version liefert ihre Realität. Der
Core übersetzt gemeinsame Minecraft-Semantik. Adapter übersetzen
Plattformfakten. Config und Custom bleiben Nutzervertrag. Keine
erfundene Wahrheit dazwischen.

------------------------------------------------------------------------

## Sichtbare Logmeldung beim Minecraft-Client-Download (2026-09-13)

Im gemeinsamen Downloadpfad von `MinecraftClientResources.provision()`
wird unmittelbar vor dem Client-Download
`Downloading Minecraft <version> client...` und nach dessen
erfolgreichem Abschluss `Minecraft <version> client download complete`
protokolliert. Cache-Nutzung, Consent, Versionsbestimmung, Validierung
und Netzwerkverhalten bleiben unverändert.
