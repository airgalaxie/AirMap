# HANDOFF / Checkpoint: AirMap-future

## Fix: `PatchDefinition.rotatePrecomputed` – X- und Z-Rotation auf die Mojang-Rechtshand-Konvention korrigiert (Y unverändert) (2026-09-03)

**Auftrag und STOP:** Nur `PatchDefinition.rotatePrecomputed()` korrigieren, damit X und Z die
gleiche Mojang-kompatible Rechtshand-Konvention nutzen wie das bereits korrekte Y. **Y bleibt
unverändert.** Keine Sonderbehandlung nur für Torch, keine Import-Patches, keine zusätzlichen
Workarounds. Verifikation über Unittests (Z: wall_torch-Lehne, X: axis=x-Szenario, Y-Kontrollfall
„gerichtetes Modell mit Y-Rotation bleibt korrekt"). Danach alle Server stoppen, Gradle sauber
stoppen, keine weiteren Testläufe/Analysen, HANDOFF aktualisieren, STOP.

**Konvention (Rechtshand, +x=Ost, +y=oben, +z=Süd; aktives Paar `a' = a·cos − b·sin;
b' = a·sin + b·cos`):**
- X (um +X): `y' = y·cosX − z·sinX; z' = y·sinX + z·cosX`
- Y (um +Y, UNVERÄNDERT): `x' = x·cosY − z·sinY; z' = x·sinY + z·cosY`
- Z (um +Z): `x' = x·cosZ − y·sinZ; y' = x·sinZ + y·cosZ`

**Umsetzung (genau eine Produktionsdatei, `PatchDefinition.java` `rotatePrecomputed`, L137–154):**
Die X- und Z-Zweige wurden von der gespiegelten Vorzeichenwahl (vorher u. a. `y = z·sin + y·cos;
z = z·cos − y·sin`) auf die obigen Rechtshand-Formeln umgestellt. Y-Zweig (L144–148) inhaltlich
unverändert. Die Methode rotiert in X→Y→Z-Reihenfolge um `offsetCenter = (0.5,0.5,0.5)`
(siehe `rotateAround` L171); Testvektoren müssen daher mittenzentriert gewählt werden.

**Neuer Test (eine neue Datei):** `PatchDefinitionRotationConventionTest.java` – deckt Rechtshand
für X/Y/Z mit mittenzentrierten Offset-Vektoren ab (z. B. Z=+90: (1.5,0.5,0.5)→(0.5,1.5,0.5);
Y=+90: (1.5,0.5,0.5)→(0.5,0.5,1.5); plus „Achse lässt die eigene Offset-Komponente unverändert").
Existierender Y-Kontrollfall bleibt `PatchDefinitionShadeStepTest.rotatesShadeDirectionWithTheModel`
(`getPatch(north,0,90,0,0)` → shadeStep Z_PLUS→X_MINUS).

**Validierung:** Gezielter Lauf (`PatchDefinitionRotationConventionTest` + `PatchDefinitionShadeStepTest`)
BUILD SUCCESSFUL; anschließend komplette DynmapCore-Suite BUILD SUCCESSFUL. Tipp für spätere Läufe:
Gradle kann testtask-Ergebnisse cachen/stale wiederverwenden – nach Quelländerungen
`:DynmapCore:cleanTest` voranstellen.

**Abgrenzung:** Produktiv berührt wurde ausschließlich `PatchDefinition.java`. Nur produktive
Z-Branch-Aufrufer sind die Mojang-JSON-Pfade in `MinecraftModelLoader` (`rotateElement` für
axis=z z. B. wall_torch, `layerRotation` west `{0,0,90}`/east `{0,0,−90}`). Legacy-Renderer nutzen
nur Y. Ein weiterhin offener (nicht beauftragter) Punkt bleibt die reale Laufzeit-Verifikation
(Fabric dev server + RCON) für wall_torch/Z und ein axis=x-Modell.

## Fix: `getSmoothGrassColorMultiplier` nutzt die Grasfarbe des eigenen Bioms statt der 3×3-Nachbar-Mittelung (2026-09-01)

**Auftrag und STOP:** Nur `getSmoothGrassColorMultiplier` reparieren: die nachgewiesene
3×3-Farbmittelung so entfernen/korrigieren, dass die Grasfarbe der Minecraft-Semantik entspricht.
**Keine** Änderungen an `BiomeMap`, am gerade reparierten Vanilla-Sumpf-Noise, an Paper/Fabric,
Shading, Modellen, Config/Custom oder anderen Farbpfaden (`getSmoothColorMultiplier`,
`getSmoothFoliageColorMultiplier`, `getSmoothWaterColorMultiplier`, `getGrassColor` unverändert).
Keine Architekturarbeit, kein Refactoring, keine weiteren Untersuchungen. Minimaler
Regressionstest nur für diese Änderung. Bestehende funktionierende Semantik unverändert. Handoff
aktualisiert, STOP.

**Semantik:** Vanilla tintet jeden `grass_block` mit der Grasfarbe **seines eigenen Bioms an
seiner eigenen Position** (`Biome#getGrassColor(pos)`: Sumpf = fester Vanilla-Ton aus
`applyGrassColorModifier(x, z)`; sonst Colormap-Pixel des Bioms). Es gibt **keine** Nachbar-
Glättung der Display-Farben. Die alte 3×3-Kanalmittelung mischte an der Biomkante Sumpf-Töne
mit Außen-Grün ⇒ der gesicherte sichtbare Kontrastband-Mangel aus dem vorigen Befund.

**Umsetzung (genau eine Stelle, `GenericMapChunkCache.java`, `OurMapIterator`, Zeilen 193–221):**
Der 3×3-Loop über `getBiomeRel(dx, dz)` + Kanalmittelung `((raccum/cnt)<<16)|((gaccum/cnt)<<8)|baccum/cnt`
wurde ersetzt durch den 1-Block-Fall: `getBiome()` des Blocks und
`getGrassColor(bm, colormap, getX(), getZ())` (= exakt der frühere Zentrumsbeitrag). Erhalten
blieben: try/catch-Fallback `0xFFFFFF` via `logMultiplierError`, `BiomeMap.NULL`-Guard mit
`logNullBiomeGrass` (NULL ⇒ `0xFFFFFF` + Warnung statt vorher 0/„leere Mittelung“), Methodensignatur
und -name. In einem uniformen Biom ist der alte 9er-Mittelwert == Zentrumsfarbe, d.h. alle
funktionierenden Regionen liefern identische Werte wie zuvor; geändert wird ausschließlich der
Kanten-/Mischfall. `getBiomeRel` bleibt von den übrigen Smooth-Methoden genutzt (kein toter Code).

**Regressionstest (minimal, eine neue Testmethode):** `BiomeColorPipelineTest`
`cacheWithBiomeBoundary(swamp, plains)` baut programmatisch einen Chunk (0,0) auf
(`GenericChunkSection.Builder.xzBiome`: x<8 Sumpf mit `setGrassColorModifier("swamp")`, x≥8
Plains, Sektion bei `WORLD.sealevel>>4`, Einschub in `snaparray[0]` via Reflektion wie im
`GrassSideStripeDiagnosticTest`-Muster). Neue `smoothGrassColorUsesOwnBiomeAtEdgeNot3x3Average`:
- Kante (7, sealevel, 7): `getSmoothGrassColorMultiplier` == `getGrassColor(swamp, …, 7, 7)`
  (eigener Biom-Ton), NICHT die 3×3-Mischung (Guard: 6× Sumpf + 3× Plains ≠ eigener Ton).
- Innen (3, sealevel, 3): Ergebnis == eigener per-Block-Wert ⇒ funktionierende Region unverändert.
Der Test würde vor dem Fix fehlschlagen (alte Methode liefert an der Kante den Mischwert).

**Validierung:** `:DynmapCore:test --tests "…BiomeColorPipelineTest"` BUILD SUCCESSFUL (7 Tests,
0 Failures/Errors). Komplette DynmapCore-Suite: **44 Tests, 0 Failures, 0 Errors, 12 vorbestehende
Skips** (vorher 43 ohne den neuen Test).

**Abgrenzung:** Berührt wurde ausschließlich die eine Methodendefinition; `getGrassColor`,
`BiomeMap` (inkl. Vanilla-Simplex-Port), alle übrigen Smooth-Color-Methoden, TexturePack und die
Adapter sind unverändert. Der Paper-Befund „grass_color_modifier wird auf Paper nicht geladen“
bleibt als eigenständiger, offener Punkt bestehen.

## Gesicherter Befund: Sichtbarer Kanten-Kontrast am Sumpf entsteht durch die biome-abhängige 3×3-Mittelung `getSmoothGrassColorMultiplier`; wörtliche SWAMP-Zweige sind im Stock nicht aktiv (2026-09-01)

**Auftrag und STOP:** Erklären, warum **derselbe grass_block an der Kante im Sumpf einen anderen
sichtbaren Kontrast erhält als außerhalb**. Gesucht wurde **ausschließlich ein biome-/SWAMP-abhängiger
Pfad im gemeinsamen Core**. Keine Änderungen, keine neue Test-/Debugger-/Diagnose-Infrastruktur.
Erste konkrete Sonderbehandlung gefunden bzw. ausgeschlossen ⇒ Befund gesichert, STOP.

**Befund – die erste konkrete, im Stock-Pfad aktive biome-/SWAMP-abhängige Stelle:**
`GenericMapChunkCache.getSmoothGrassColorMultiplier` (`GenericMapChunkCache.java:193–222`).
Sie ist über `TexturePack.processBlock` (`TexturePack.java:1806`, Zweig `COLORMOD_GRASSTONED/-270`,
`do_biome_shading=true`, kein `misc/swampgrasscolor.png`) die Standard-Gras-Nachbar-Mittelung:

- Für jede der 9 Nachbarzellen (3×3 um den Block) wird die Grasfarbe **des jeweiligen Nachbar-Bioms**
  geholt: `getGrassColor(bm, colormap, x+dx, z+dz)` (`GenericMapChunkCache.java:581–586`) →
  `bm.applyGrassColorModifier(x, z, base)` (`BiomeMap.java:286–295`). Sumpf-Nachbar ⇒ einer der beiden
  festen Vanilla-Töne `0xFF4C763C`/`0xFF6A7039`; Nicht-Sumpf-Nachbar ⇒ Colormap-Pixel des Nachbar-Bioms.
- Kanäle werden per ganzzahliger Division gemittelt: `mult = ((raccum/cnt)<<16)|((gaccum/cnt)<<8)|(baccum/cnt)`.
- An der **Biomkante** (Abstand 0–1 Blöcke zur Grenze) enthält das Fenster eine Mischung aus
  Sumpf-Tönen und Außen-Grün. Das Mischverhältnis ändert sich pro Blockposition (±1–2 Blöcke einschließlich
  des Grenzbereichs), also erhält **derselbe grass_block an der Kante eine vom reinen Ton und vom reinen
  Außen-Grün verschiedene, positionsabhängige Mischfarbe** ⇒ anderer sichtbarer Kontrast/Kontrastband.

**Numerische Illustration (Arithmetik, kein Test):** reiner dunkler Sumpf-Ton `0x4C763C` gegenüber
Außen-Grün (Plains-Colormap) `≈0x91BD59`. Grenzfenster mit 3× Außen + 6× Ton ergibt Kanalmittel
`≈0x638D45` – deutlich heller als `0x4C763C` und deutlich anders als `0x91BD59`; tiefer im Sumpf
(nur Töne im Fenster) mischt die Mitteilung die beiden Töne zu Zwischentönen. Die 3×3-Mittelung ist
damit die Stelle, die aus der (seit dem letzten Fix vanilla-exakten) harten 1-Pixel-Ton-Grenze ein
sichtbares Misch-/Kontrastband macht.

**Wörtliche SWAMP-Sonderbehandlung im gemeinsamen Core – geprüft und für Stock ausgeschlossen:**
Die einzigen Biome-Vergleiche im gemeinsamen Core sind zusätzlich nur `BiomeMap.NULL`-Guards
(`GenericMapChunkCache.java:204/236/266/300/330`, `GenericChunkSection.java:385`), ohne Farbwirkung.
Die einzigen wörtlichen `== BiomeMap.SWAMPLAND`-Zweige sind:
1. `getSmoothColorMultiplier` (`GenericMapChunkCache.java:268`) – Sumpf speist aus `swampmap` statt
   `colormap` – **nur erreichbar über `imgs[IMG_SWAMPGRASSCOLOR]`/`IMG_SWAMPFOLIAGECOLOR]`**,
   d.h. custom `misc/swampgrasscolor.png`/`swampfoliagecolor.png` (`TexturePack.java:1803–1804/1815–1816`);
   in Stock-26.2/26.3 nicht vorhanden ⇒ **nicht aktiv**.
2. `TexturePack.java:2403` (`(bio == BiomeMap.SWAMPLAND) && (imgs[IMG_SWAMPGRASSCOLOR] != null)`) –
   ebenfalls Image-gegated ⇒ **nicht aktiv**.
Zusätzlich: der Vanilla-`swamp`-Modifier selbst (`BiomeMap.applyGrassColorModifier` SWAMP-Zweig) ist
seit dem letzten Fix exakter 1:1-Port und bewusst nicht Teil dieser Befundung.

**Abgrenzung:** Dies ist die in der vorigen Runde als „zusätzliche, später liegende Abweichung“
notierte, aber nicht bewertete 3×3-Mittelung (`getSmoothGrassColorMultiplier`). Sie ist generisch
(gilt für Gras und Laub), aber in der Wirkung biome-/SWAMP-abhängig. In diesem Beweisauftrag
wurde nichts geändert; der Fix folgte im obersten Abschnitt.

## Gesicherter Befund + Fix: SWAMP-Gras-Modifier nutzt jetzt den echten Vanilla-Perlin-Simplex (Seed 2345) statt `swampPatchNoise` (2026-09-01)

**Auftrag und STOP:** Nur die bewiesene erste gemeinsame Core-Abweichung im SWAMP-Zweig von
`BiomeMap.applyGrassColorModifier` fixen – exakt die Vanilla-Positionsauswahl
(`PerlinSimplexNoise`, Seed 2345, Oktave `[0]`, Faktor 0.0225, Schwelle `< -0.1`). Beide Töne
(`-11766212` = `0xFF4C763C` dunkel, `-9801671` = `0xFF6A7039` hell) unverändert. **Nur gemeinsamer
Core** – keine Paper-/Fabric-spezifische Lösung. Keine Änderung an funktionierender
Tint-/Config-/Custom-/Model-/Renderer-Pipeline, kein 3×3-Smoothing, keine anderen Biome, kein
Generisches Refactoring, keine neue Diagnose-Infrastruktur. Bestehende Tests unverändert; ein
minimaler Regressionstest genau für diese korrigierte Sumpf-Berechnung ergänzt. Danach Vergleich
gegen Vanilla 26.2 UND 26.3-Snapshot-10 an festen Koordinaten (beide müssen dort dasselbe
Vanilla-Ergebnis liefern). Keine Erweiterung der Problemklasse.

**Umsetzung (ausschließlich `DynmapCore`):**
- `BiomeMap.java`: der SWAMP-Zweig behält `return swampPatchNoise(x, z) < -0.1 ? -11766212 : -9801671;`.
- `swampPatchNoise(x,z)` ruft jetzt `SWAMP_NOISE.getValue(x*0.0225, z*0.0225)` auf.
- `SWAMP_NOISE = VanillaSimplexNoise(2345L)` – privater statischer 1:1-Port von Vanilla-26.2
  `SimplexNoise` (2D) + `LegacyRandomSource`/`WorldgenRandom` (aus Bytecode abgeleitet):

  - `LegacySimplexRandom`: Java-Legacy-LCG, `seed = (seedIn ^ 0x5DEECE66D) & (2^48−1)`,
    `next(bits) = (seed*0x5DEECE66D + 0xB) & (2^48−1) >>> (48−bits)`,
    `nextDouble = ((next(26)<<27)+next(27)) * 2^-53`, `nextInt(bound)` exakt wie
    `java.util.Random` (2er-Potenz-Pfad + Rejection-Loop). Das entspricht genau der Vanilla-Weiche
    `WorldgenRandom.next → inneres LegacyRandomSource.next` (per Bytecode bestätigt).
  - Konstruktor konsumiert exakt wie Vanilla: 3× `nextDouble` (xo/yo/zo werden konsumiert, für die
    2D-Auswertung ungenutzt), danach Fisher-Yates `nextInt(256−i)` über `p[0..255]` (`p` Länge 512,
    obere Hälfte bleibt 0 wie in Vanilla, Zugriff nur über `p[i & 255]`).
  - `getValue(x,y)`: Standard-Simplex-2D mit `F2 = 0.5*(√3−1)`, `G2 = (3−√3)/6`, 16er-Gradienten-
    tabelle, `gi = p(ii+p(jj)) % 12` usw., Eckenbeitrag `e⁴·dot`, `e = 0.5−x²−y²−z²`, Summe ·70.
  - Thread-sicher: nach der statischen Initialisierung nur noch lesend.

**Validierung – vorgeschriebener Vergleich gegen BEIDE Vanilla-Versionen an festen Koordinaten:**
- Goldstandard = echte Vanilla-Klassen direkt aus den Client-JARs ausgeführt
  (`/tmp/opencode/vtest/vanilla{26.2,26.3}`): 16 feste Weltkoordinaten, jeweils
  `getValue(x·0.0225, z·0.0225)`. 26.2: `SimplexNoise(WorldgenRandom(LegacyRandomSource(2345)))`
  gibt exakte Doubles; 26.3: `SimplexNoise(WorldgenRandom(LegacyRandomSource(2345)), true)`
  gibt Float – **beide Versionen liefern an jeder festen Koordinate denselben Vanilla-Ton.**
- Der 1:1-Port (PortCheck, identische Logik wie in `BiomeMap`) ist gegen echtes 26.2 **bit-identisch**,
  inkl. RNG-Anker xo/yo/zo = 239.02472691166943 / 143.76131717417817 / 13.914271125085236.
- Ton-Ground-Truth an den 16 Koordinaten: dunkel nur bei (256,0), (-45,-45), (8192,-8192), sonst hell.

**Tests (gezielt, minimal):** neuer `swampModifierMatchesVanillaSimplexAtFixedCoordinates` in
`BiomeColorPipelineTest` prüft die exakten Vanilla-Töne an denselben 16 festen Koordinaten über
`BiomeMap.applyGrassColorModifier`. Bestehender `swampModifierProducesBothVanillaTones` unverändert.
Gesamte DynmapCore-Suite: 43 Tests, 0 Failures, 0 Errors (12 vorbestehende Skips).

**Abgrenzung:** In diesem Auftrag NICHT angefasst: DARK_FOREST-Zweig, 3×3-Mittelung
`getSmoothGrassColorMultiplier`, Foliage-Tint, Colormaps/Overrides, Config/Custom/Model/Renderer,
Paper-/Fabric-Adapter (der Paper-Befund „grass_color_modifier wird nicht geladen“ bleibt separat
offen). Die nachgelagerte 3×3-Smoothing-Abweichung ist weiterhin als eigene, bewertbare Abweichung
offen – nicht Teil dieses Fixes.

## Gesicherter Befund: Erste gemeinsame Core-Abweichung der Sumpf-Tint-Bestimmung – `swampPatchNoise` ersetzt die Vanilla-Positionsauswahl (2026-09-01)

**Auftrag und STOP:** Der Paper-Befund erklärt den sichtbaren Fehler nicht vollständig – Fabric
liefert den `grassColorModifier` bereits und zeigt laut Nutzerbefund trotzdem denselben falschen
Sumpf. Deshalb wurde **ausschließlich die gemeinsame Core-Tint-Pipeline** für `grass_block` im
Sumpf geprüft. Ziel war die erste Stelle, an der die Minecraft-Sumpf-Semantik (swamp modifier:
zwei Vanilla-Töne + positionsabhängige Auswahl) im gemeinsamen Core verloren geht oder falsch
umgesetzt wird. Kein Fix (es war Beweisaufgabe); keine neue Diagnose-Infrastruktur; kein
allgemeines Tint-/Renderer-Refactoring.

**Zuerst festgestellt: Die relevante Vanilla-Semantik ist zwischen beiden Versionen gleich.**
Sowohl die 26.2- als auch die 26.3-snapshot-10-Client-JAR enthalten für `swamp.json`
`grass_color_modifier: "swamp"`, `foliage_color: #6a7039`, kein `grass_color`-Override,
Temperatur 0.8, Downfall 0.9. Der Bytecode des Vanilla-Modifiers ist ebenfalls identisch:

- 26.2: `PerlinSimplexNoise.getValue(x*0.0225, z*0.0225, false) < -0.1 ? -11766212 : -9801671`
  mit `BIOME_INFO_NOISE = PerlinSimplexNoise(WorldgenRandom(LegacyRandomSource(2345L)), [0])`.
- 26.3-snapshot-10: gleiche Tonwerte (-11766212 / -9801671), gleiche Skalierung (0.0225),
  gleicher Schwellwert (-0.1), gleicher Seed (2345, `BIOME_INFO_NOISE`), nur als
  `Noise.get(x,z)`-Interface-Aufruf eingepackt.

Damit ist kein Versions-/Plattformunterschied für die Sumpf-Tint-Pipeline vorhanden; jede
Abweichung liegt in der Umsetzung des gemeinsamen Core.

**Erste Abweichung im gemeinsamen Core: `BiomeMap.applyGrassColorModifier` (SWAMP-Zweig) mit
der Hilfsfunktion `swampPatchNoise` (`BiomeMap.java:286–312`).** Analyse der Executable chain
von der echten Textur-Operation bis zur Tint-Bestimmung:

1. `TexturePack.processBlock` wählt für `COLORMOD_GRASSTONED` bei `do_biome_shading=true`
   (Default) und ohne `misc/swampgrasscolor.png` (in Stock-26.2/26.3 nicht vorhanden) den Pfad
   `mapiter.getSmoothGrassColorMultiplier(colormap)` (`TexturePack.java:1802–1806`).
2. Diese Methode ruft je 3×3-Nachbarblock `getGrassColor(bm, colormap, x+dx, z+dz)` auf
   (`GenericMapChunkCache.java:193–222`), das auf
   `bm.applyGrassColorModifier(x, z, base)` mündet (`getGrassColor`, Zeile 581–586).
3. In `applyGrassColorModifier` sind die **beiden Vanilla-Töne korrekt** (`-11766212` = dunkel
   `0xFF4C763C`, `-9801671` = hell `0xFF6A7039`), ebenso Schwellwert `< -0.1` und Skalierung
   `*0.0225`. **Die positionsabhängige Auswahl ist falsch umgesetzt:** Sie nutzt `swampPatchNoise`,
   eine handgerollte bilineare Value-Noise auf ganzzahligem Hash (`hashCell`),
   **nicht** die Vanilla-`BIOME_INFO_NOISE` (= `PerlinSimplexNoise`, LegacyRandomSource-Seed 2345,
   einzelne Oktave `[0]`).

**Konkreter Unterschied der Positionsauswahl (numerisch belegt, ohne neuen Test):**
`swampPatchNoise` erzeugt achsenausgerichtete bilineare Patches mit Wellenlänge ~44 Blöcke
(0.0225-Teilung, Hash-Gitter bei ganzzahligen `sx/sz`), Wertbereich nur ~[-1.0, 0.986], Dunkelanteil
~39–41 %. Vanilla `PerlinSimplexNoise` erzeugt dagegen diagonale Perlin-Simplex-Muster mit
demselben Wellenlängen-Maßstab, aber anderer Form und anderem Seed; damit liegen die dunklen und
hellen Sumpf-Flecken an **anderen Positionen** als in Vanilla. Die Farbtöne selbst stimmen; der
räumliche Layout ist nicht Vanilla-identisch.

**Beweisgrenzen / Abgrenzung:** Die Tonwerte, der Schwellwert und die Skalierung wurden gegen den
echten Bytecode beider Client-JARs geprüft (26.2 und 26.3-snapshot-10 unter
`/home/jens/.gradle/caches/fabric-loom/26.2/...` bzw.
`/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`). Es wurde kein
neuer Test, kein Debugger und kein Laufzeit-Render angelegt; die exakte 1:1-Pixel-Reproduktion des
Vanilla-Perlin-Simplex(seed 2345) ist nicht ausgeführt. Die 3×3-Mittelung in
`getSmoothGrassColorMultiplier` ist eine zusätzliche, später liegende Abweichung und wurde für
dieses erst-Abweichungs-Ziel nicht bewertet. Ein Fix wird wie vertraglich erst nach einem neuen,
ausdrücklichen Auftrag mit klarem Umfang umgesetzt.

## Gesicherter Befund: AirMap 26.2 nutzt bei `grass_block` NICHT dieselbe Sumpf-Tint-Bestimmung wie Minecraft 26.2 (2026-09-01)

**Auftrag und STOP:** Nur feststellen, ob AirMap für **Minecraft 26.2 (Paper)** bei `grass_block`
dieselbe biomeabhängige Tint-/BlockColor-Bestimmung verwendet wie Minecraft 26.2. Kein Fix, keine
neue Test-/Debugger-/Diagnose-Infrastruktur, keine allgemeine Renderer-Analyse. Der Nachweis war
**mit vorhandenen Mitteln unmittelbar möglich** (26.2-Client-JAR + bestehender Code + bestehendes
Testwissen). Ergebnis: **Nein, die Bestimmung ist nicht dieselbe.** Erste konkrete Abweichung
bewiesen; kein Produktionsfix implementiert.

**Ablauf der Feststellung:**

1. **Vanilla 26.2 definiert Sumpf ohne `grass_color`, aber mit Modifier:**
   `data/minecraft/worldgen/biome/swamp.json` aus `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`
   enthält `"grass_color_modifier": "swamp"` (und `foliage_color: #6a7039`, kein `grass_color`-Override).
   Damit bestimmt Vanilla die `grass_block`-Gras-Tinte im Sumpf **nicht** über die Standard-`grass.png`-Colormap,
   sondern ausschließlich über den `swamp`-Modifier: exakt **zwei feste Töne** `0xFF4C763C` (dunkel) und
   `0xFF6A7039` (hell), ausgewählt durch deterministisches Flecken-Rauschen (Simplex, Seed 2345).
   Beleg zusätzlich im bestehenden `BiomeColorPipelineTest.swampModifierProducesBothVanillaTones`
   (`-11766212 = 0xFF4C763C`, `-9801671 = 0xFF6A7039`).

2. **Der Paper-26.2-Adapter lädt den Modifier nicht:** `paper/.../DynmapPlugin.loadExtraBiomes`
   (Zeilen ~608–655) und `bukkit-helper/.../BukkitVersionHelper` legen je Biome nur **Wasserfarbe,
   Basistemperatur und Luftfeuchte (downfall)** ab. Im Gegensatz dazu liest der **Fabric**-Adapter
   dieselben Felder *und zusätzlich* `grassColorOverride`, `foliageColorOverride` und
   `grassColorModifier` (Zeilen 369–372). Auf dem Paper-Pfad bleiben deshalb auf `BiomeMap.SWAMPLAND`
   (`BiomeMap.java:24`) `grassColorOverride=-1` und `grassMode=NONE`.

3. **Folge in der aktiven Render-Bestimmung:** `getSmoothGrassColorMultiplier` →
   `getGrassColor` (`GenericMapChunkCache.java:581–586`) rechnet für Sumpf
   `getModifiedGrassMultiplier(colormap[biomeLookup])` mit `grassmult=0x2e282a` (statische Blend-Konstante)
   und dann `applyGrassColorModifier` → wegen `grassMode=NONE` unverändert. Konkret:
   Sumpf-`biomeLookup256=18226` (t=50, h=71); `grass.png`-Pixel (50,71) = RGB(106,196,78)=`0x6AC44E`;
   Blend `((0x6AC44E & 0xFEFEFE) + 0x2e282a) >> 1 = 0x4C763C`. Das ist exakt der **dunkle** Vanilla-Ton.

4. **Wesentliche Abweichung:** Die statische Blend ist so kalibriert, dass sie nur den **dunklen**
   Ton `0x4C763C` reproduziert. Der helle Vanilla-Ton `0x6A7039` und die zweitönige
   Flecken-Patchigkeit des `swamp`-Modifiers werden **gar nicht erzeugt** – AirMap rendert den
   Sumpf als flächig einheitliches dunkles `0x4C763C`. Das ist eine andere Tint-Bestimmung als
   Vanilla 26.2. (Hinweis: In Stock-26.2 existiert kein `misc/swampgrasscolor.png`, daher läuft
   der Gras-Pfad in `TexturePack.java:1806` über `getSmoothGrassColorMultiplier`/`getGrassColor`
   und nicht über den separaten MCPatcher-Sumpf-`getSmoothColorMultiplier`-Zweig.)

**Abgrenzung/Beweisgrenze:** Nur die *Bestimmung* der Farbe ist geprüft (26.2-Ressourcen + Code +
bestehende Test-Referenzwerte). Es wurde kein neuer Test, kein Debugger und kein Laufzeit-Render
angelegt. Ein neues reales Sumpf-Tile auf Paper 26.2 wurde nicht gerendert; die räumliche
Verteilung der Abweichung am sichtbaren Biome-Übergang (3×3-Nachbarschaftsglättung in
`getSmoothGrassColorMultiplier`) wurde nicht separat quantifiziert. Diese offene Punktwirkung
gehört gemäß Auftrag nicht in diesen Feststellungs-Auftrag; es war nur die Bestimmungsgleichheit
zu klären, und die ist widerlegt.

## Gesicherter Befund: `dirt_path` im Flat-Render mischt den Untergrund nicht ein (2026-09-01)

**Ergebnis und STOP:** Die reduzierte Vanilla-Modellhöhe von `dirt_path` (15/16 Block)
führt im Minecraft-JSON-Loader erwartungsgemäß zur Klassifikation `SEMITRANSPARENT`. Diese
Klassifikation wählt jedoch keinen Wasser-artigen Farb-Composite-Pfad und lässt im senkrechten
Flat-Render keinen darunterliegenden Block beitragen. Die vermutete erste Abweichung ist im
untersuchten Pfad nicht vorhanden; deshalb wurde kein Produktionsfix implementiert und die
Problemklasse nicht erweitert.

**Erster konkreter Laufzeitübergang:** Ein echter Flat-Strahl trifft Patch/Texturindex 1, die
obere `dirt_path`-Fläche. Direkt vor dem Shaderabschluss wurden per nicht suspendierendem
Debugger-Logpoint `sampledAlpha=255`, `accumulatedAlpha=0` und `textureIndex=1` beobachtet.
Unmittelbar nach `processBlock` meldete der zweite Logpoint für denselben Treffer
`shaderDone=true`, `patch=1`, `step=Y_MINUS`. Damit beendet bereits der Dirt-Path-Treffer den
Strahl; die darunterliegende Darstellung erreicht den Composite-Schritt nicht.

**Direkter Beleg:** Das reale 26.3-Modell besitzt eine 15/16 hohe, über die gesamte X/Z-Fläche
reichende Oberseite; deren reale Textur ist vollständig alpha-opaque. Ein einmaliger
Flat-Lauf durch die bestehende `IsoHDPerspective.OurPerspectiveState.raytrace`-Pipeline mit
Produktionsparametern (`azimuth=180`, `inclination=90`, `scale=4`, resampelte Standard-Textur)
ergab:

- registrierte Transparenz: `SEMITRANSPARENT`;
- 16 Flat-Treffer auf `dirt_path`, alle auf `Y_MINUS`;
- Alpha-Histogramm: 16-mal `255`;
- 16-mal letzter opaker Produzent `dirt_path`;
- **0-mal** Dirt-Path-Treffer mit anschließendem Beitrag des Blocks darunter;
- der Lauf endete erfolgreich.

Die dafür einmalig verwendete Probe wurde nach Sicherung dieser Werte wieder entfernt; es
wird keine neue Dirt-Path-Debug- oder Testinfrastruktur im Projekt behalten.

**Kausalkette:** `MinecraftModelLoader` verlangt für `OPAQUE` sechs opak texturierte
Vollwürfelflächen. Das 15/16-Modell erfüllt diese Geometriebedingung nicht und wird deshalb
`SEMITRANSPARENT`. `TexturePackHDShader` entscheidet über das tatsächliche Weiterlaufen aber
nicht anhand dieses Enums, sondern anhand des gesampelten Farb-Alphas. Die voll opake obere
Textur liefert 255; bei bisher transparenter Akkumulationsfarbe übernimmt der Shader diese
Farbe und gibt sofort `true` zurück. `IsoHDPerspective.handlePatches` beendet daraufhin den
Strahl. Die Klassifikation beeinflusst hier unter anderem Lichtbehandlung, erzeugt aber keine
Farbmischung.

**Abgrenzung:** Wasser wurde ausschließlich als gewünschter Vergleichseffekt betrachtet und
weder analysiert noch geändert. Gras, Tint und allgemeine Renderersemantik wurden nicht
geöffnet. Ein sichtbarer Dirt-Path-Unterschied müsste mit einem neuen konkreten Befund außerhalb
dieser widerlegten Layer-/Composite-Hypothese separat lokalisiert werden.

## Gesicherter Befund: `grass_block` OPAQUE vs. SEMITRANSPARENT (2026-08-31)

**Fix abgeschlossen:** Der Test ist richtig; die AirMap-Klassifikation war für dieses
mehrlagige Vollmodell falsch. Betroffen war der gemeinsame Core-Pfad und damit sowohl Vanilla
**26.2** als auch **26.3-snapshot-10**.

Beide realen Client-JARs enthalten dasselbe `grass_block`-Modell:

- ein erstes Element von `[0,0,0]` bis `[16,16,16]` mit allen sechs Grundflächen;
- ein zweites, deckungsgleiches Vollblock-Element mit ausschließlich vier seitlichen
  Gras-Overlay-Flächen.

Die echten PNGs sind in beiden Versionen gleich klassifiziert: `dirt`, `grass_block_top` und
`grass_block_side` sind vollständig alpha-opaque; nur `grass_block_side_overlay` besitzt
transparente Pixel. Das Overlay legt Farbe auf die bereits deckende opaque Grundseite und
erzeugt daher weder ein Loch in der Blockdeckung noch Teilblockgeometrie.

Die Implementierung hatte zwei lokale Fehler im selben Klassifikationspfad:

1. `isSpriteOpaque` übergab dem `MinecraftResourceProvider` den internen ZIP-Pfad
   `assets/<namespace>/...` statt der vertraglichen Resource-ID `<namespace>:textures/...`.
   Der Provider wies ihn ab; der Catch-Pfad speicherte deshalb sogar für `dirt` und
   `grass_block_side` fälschlich `false`.
2. Danach verknüpfte AirMap die Alpha-Opazität **jedes** Face-Sprites per `&=`. Selbst bei
   korrektem Ressourcenlesen hätte das transparente Overlay den gesamten Zustand auf
   `SEMITRANSPARENT` gekippt, obwohl jede Seite bereits durch das erste Element opaque gedeckt
   ist.

Dies widersprach auch der lokalen Bedeutung von `BlockTransparency`:
`SEMITRANSPARENT` ist dort für opaque Blöcke definiert, die wegen Teilgeometrie nicht alle
Strahlen blockieren (z. B. Stufen/Slabs); `grass_block` blockiert sie durch den Grundwürfel.

**Konkrete allgemeine Änderung:** `isSpriteOpaque` verwendet jetzt die korrekte Provider-ID.
`install` aggregiert nicht mehr destruktiv über alle Sprites, sondern sammelt additiv die durch
opak texturierte, unrotierte Vollwürfelflächen tatsächlich gedeckten sechs `BlockStep`-Richtungen.
Nur vollständige Deckung aller sechs Außenseiten kann zusammen mit der bestehenden
Lichtdämpfungs-/Waterlogged-Prüfung `OPAQUE` ergeben. Transparente Zusatzlagen entfernen keine
vorhandene Deckung; fehlende oder Teilblockflächen erzeugen keine erfundene Deckung. Es gibt
keinen Block-Sonderfall; Config/Custom-Pfade wurden nicht verändert oder umgangen.

**Beweisquellen:**

- 26.2: `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`,
  `assets/minecraft/models/block/grass_block.json` und die vier genannten Texturen.
- 26.3-snapshot-10:
  `/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`, dieselben
  Ressourcenpfade.
- Implementierung: `MinecraftModelLoader.java`, Berechnung von `opaqueSprites` und
  Registrierung der `BlockTransparency` unmittelbar nach Installation des Patchmodells.
- Debuggerbeleg vor der Korrektur des Provider-Aufrufs: für den realen
  `minecraft:grass_block[snowy=false]` war `opaqueCubeFaces=[]`; der Cache enthielt für
  `block/dirt`, `block/grass_block_top`, `block/grass_block_side` und das Overlay jeweils
  `false`.

**Regression und Tests:** `MinecraftModelTintRegistrationTest` prüft nun zusätzlich den realen
`heavy_core`: `grass_block` wird `OPAQUE`; `heavy_core` bleibt trotz opakem Sprite und
Lichtdämpfung 15 wegen seiner Teilblockgeometrie `SEMITRANSPARENT`. Isolierter Regressionstest
und die gemeinsame relevante Gruppe aus `MinecraftModelTintRegistrationTest`,
`MinecraftStaticModelIntegrationTest` und `MinecraftModelLoaderTest` endeten jeweils mit
`BUILD SUCCESSFUL`.

**Verbleibende Runtime-Beweisgrenze:** Der Fix ist gegen die echten konfigurierten
26.3-snapshot-10-Ressourcen getestet und die identischen relevanten 26.2-Modell-/PNG-Fakten
sind direkt aus dem 26.2-Client-JAR belegt. Ein reales neues Paper-/Fabric-Tile wurde in diesem
Auftrag nicht gerendert.

## Laufende enge Untersuchung: Zäune im Flat-Render (2026-08-31)

**Fix abgeschlossen:** Nach ausdrücklicher Freigabe der allgemeinen Flat-/Patch-Schnittsemantik
wurde die Patchfläche in `IsoHDPerspective.handlePatch` auf eine halb offene Randkonvention
`[min, max)` umgestellt, gemeinsam gekapselt in `isWithinPatchBounds`. Es gibt keinen
Zaun-Sonderfall und keine Sampling-, Projektions- oder Surface-Sonderlogik.

Geometrische Begründung: Die vorherige offene Konvention `(min, max)` erzeugt Rasterlöcher,
wenn eine schmale Fläche genau zwischen Pixelzentren liegt. Vollständig geschlossene Grenzen
würden dagegen gemeinsame Kanten doppelt besitzen. Halb offen nimmt die Min-Kante auf und
weist die Max-Kante ab; bei zwei angrenzenden Parameterintervallen gehört die gemeinsame
Kante damit genau einem Patch.

**Tests nach Produktionsänderung:** Das reale Vanilla-Zaunmodell (26 Patches) ergibt mit der
Produktionskonvention Flat **1** Treffer statt 0; die Surface-Kontrolle bleibt unverändert bei
**205** Treffern. `IsoHDPerspectivePatchBoundaryTest` belegt Min-/Max-Ränder und exakt einen
Besitzer einer gemeinsamen Kante. Gemeinsam erfolgreich (`BUILD SUCCESSFUL`):
`FenceFlatVisibilityProbeTest`, `IsoHDPerspectivePatchBoundaryTest`,
`MinecraftStaticModelIntegrationTest`, `MinecraftModelLoaderTest` und
`MinecraftModelUvLockTableTest`.

Der damals zusätzlich isoliert rote `MinecraftModelTintRegistrationTest` war keine Regression
der Patch-Randänderung. Seine Ursache und der inzwischen erfolgreiche lokale Fix sind im
vorangestellten `grass_block`-Abschnitt gesichert.

**Verbleibende Beweisgrenze:** Die Geometrie ist durch reale Client-Patches und echte
Perspektivtransformationen im Test belegt. Ein neu erzeugtes reales Flat-Tile auf Paper/Fabric
wurde in diesem Auftrag nicht gerendert; der sichtbare Runtime-Beweis bleibt daher offen.

**Auftrag und Grenze:** Ausschließlich lokalisieren, warum Zäune in der senkrechten
Flat-Perspektive unsichtbar sind, obwohl die Surface-Perspektive sie rendert. Keine allgemeine
Flat-/Renderer-Analyse. Ein Fix wird nur umgesetzt, wenn er lokal und ohne Architekturänderung
eindeutig ist; andernfalls wird mit gesichertem Befund gestoppt.

**Historischer Ausgangsbefund vor der späteren Fix-Freigabe:** Die Vanilla-JSON-Integration liefert für
`minecraft:oak_fence[east=true,north=false,south=true,waterlogged=false,west=false]` ein
Multipart-Patchmodell mit 26 Patches (`MinecraftStaticModelIntegrationTest` erfolgreich).
`FenceFlatVisibilityProbeTest` verwendet genau diese realen Loader-Patches, die echte
`IsoHDPerspective`-Transformation und echte Pixelzentren. Ergebnis:

- Flat `iso_S_90_lowres` (`azimuth=180`, `inclination=90`, `scale=4`): **0** Patchtreffer.
- Dieselben Flat-Strahlen bei ausschließlich inklusiv gerechneten `u/v`-Patchrändern: **4**
  Treffer.
- Surface-Kontrolle (`azimuth=135`, `inclination=30`, `scale=16`): **205** Patchtreffer.
- Gezielter Testlauf: `BUILD SUCCESSFUL`.

Damit liegt die erste Verluststelle in `IsoHDPerspective.handlePatch`: Beim senkrechten
Scale-4-Raster liegen die relevanten Zaunstrahlen exakt auf den Rändern der nur 1/4 Block
breiten Zaunflächen; die bestehenden offenen Intervallprüfungen
`u <= umin || u >= umax` beziehungsweise `v <= vmin || v >= vmax` verwerfen alle davon.
Die schräge/höher aufgelöste Surface-Abtastung trifft dagegen Flächeninneres.

**Damals noch kein Produktionsfix implementiert:** `<=`/`>=` global zu ändern würde die
Schnitt-/Kantensemantik sämtlicher Patchmodelle erweitern (inklusive Mehrfachtreffern an
gemeinsamen Kanten); ein Pixeloffset oder Supersampling würde das Sampling aller Flat-Inhalte
ändern; eine Zaun-Sonderbehandlung wäre ein blockweiser Sonderfall. Damit müsste die
Problemklasse über Zäune hinaus erweitert werden. Gemäß damaligem Auftrag wurde dort
gestoppt; der nachfolgende ausdrückliche Auftrag hat genau diese allgemeine Untersuchung
freigegeben. Der Probe-Test bleibt als Nachweis erhalten.

**Stand (2026-08-31, aktuell):** Weltidentitäts-Fix, Paper-26.2-Adapter und die enge
Vanilla-Gegenprüfung des JSON-Renderers für `rescale`, `uvlock`, Tint-Kopplung und
Render-Type sind abgeschlossen. Paper 26.2 wurde real gestartet; AirMap und Webclient
funktionieren, und ein `radiusrender 100` hat sichtbar neue JSON-basierte Tiles erzeugt.

**Übergabestatus:** Es besteht kein automatisch auszuführender Folgeauftrag. Insbesondere
autorisiert dieser Handoff weder Serverstarts noch Builds, Deployments, Render-Kommandos,
Runtime-Vergleiche oder neue Untersuchungen. Solche Arbeiten beginnen ausschließlich nach
einem neuen, ausdrücklichen Nutzerauftrag mit klarer Plattform und klarem Umfang.

---

## 0. Verbindlicher Projektvertrag

- AirMap liest die Realität der jeweils eingesetzten Minecraft-Version:
  - **Paper: Minecraft 26.2**
  - **Fabric: Minecraft 26.3-snapshot-10** (Laufzeit-Label `26.3-alpha.10`)
- Gleiche Vanilla-Semantik bleibt im gemeinsamen Core. Tatsächliche Versions- oder
  Plattformunterschiede werden an der bestehenden Adaptergrenze behandelt.
- Der historische TXT-Renderer ist nur alte Referenz, nicht Render-Wahrheit.
- Sichtbare Unterschiede zwischen TXT und JSON oder zwischen 26.2 und 26.3 sind zunächst
  Abweichungen, keine Fehlerbeweise. Zuerst gegen die jeweilige Vanilla-Version prüfen.
- **Config und Custom sind dauerhafte Nutzerverträge.** Die Umstellung auf Vanilla-Ressourcen
  darf diese Mechanismen nicht umgehen.
- Keine erfundenen IDs, blockweisen Sonderfälle oder Heuristiken, wenn Minecraft die
  Information selbst liefert.
- Dieser Handoff dokumentiert gesicherte Fakten, Grenzen und mögliche Übergabepunkte. Er ist
  kein Arbeitsplan und keine implizite Erlaubnis, offene oder mögliche Schritte selbständig
  abzuarbeiten.

---

## 1. Versionsstände und Build-Basis

- **Core:** gemeinsamer DynmapCore; er liest den echten Vanilla-Client-JAR der konfigurierten
  Ressourcenversion.
- **Fabric-Adapter:**
  - Minecraft `26.3-snapshot-10` / Laufzeit-Label `26.3-alpha.10`
  - Fabric Loader `0.19.3`, API `0.158.3+26.3`, Loom `1.17-SNAPSHOT`
- **Paper-Adapter:**
  - Paper zentral als `paper = "26.2.build.+"` definiert
  - `airmapPaper = "1.0.10-paper-26.2"`
  - paperweight `2.0.0-beta.21`, plugin-yml Paper `0.9.0`
  - Support-Deps zentral: LuckPerms `5.5`, Vault `1.7.1`, GroupManager `2.10.1`
- **Java:** Build `26`, Target `25`; **Gradle Wrapper:** `9.7.1`.
- Module: `:paper`, `:fabric`, `:bukkit-helper`, `:dynmap-api`, `:DynmapCore`,
  `:DynmapCoreAPI`.

Architektur:

```text
gemeinsamer Core
 ├─ Fabric-Adapter → Minecraft 26.3-snapshot-10
 └─ Paper-Adapter  → Minecraft 26.2
```

Vorherige Fabric- und Paper-Builds waren erfolgreich. Das dokumentiert Kompilierung und Tests,
nicht das Laufzeitverhalten.

---

## 2. Paper 26.2 (integriert und real gestartet)

- Paper-Zuordnung liegt im Paper-/Bukkit-Adapter; gemeinsame Vanilla-Rendersemantik bleibt im
  Core.
- Anpassungen gegenüber der 26.2-Referenz:
  - `ModSupportImpl.init()` entfernt (Future-Core besitzt dieses historische Modsupport-Modul
    nicht).
  - `getWorldAliases()` auf die Future-Core-API `getNameAliases()` umgestellt.
  - `BukkitWorld.getLegacyWorldNames` identitätskonform, ohne erfundene Hardcodes.
- Die Ressourcenversion für Paper ist korrigiert: Paper 26.2 verwendet Vanilla-Ressourcen 26.2,
  nicht die Fabric-Ressourcenversion.
- **Runtime belegt:** Paper 26.2 startet AirMap, der Webclient funktioniert, und
  `radiusrender 100` erzeugte sichtbar neue JSON-basierte Tiles neben altem TXT-Bestand.
  Damit ist der JSON-Ressourcenpfad auf Paper 26.2 praktisch nachgewiesen.
- Dieser Runtime-Beleg bewertet nicht automatisch jede einzelne Rendersemantik. Daraus folgt
  jedoch kein selbständig auszuführender Vergleichs- oder Folgeauftrag.
- Die 26.2-Referenzquelle liegt unter `/tmp/opencode/AirMap-ref`.

Das zuvor dokumentierte Paper-JAR
`target/AirMap-1.0.10-paper-26.2+9e2616a-dirty-paper.jar` war ein damaliges Build-Ergebnis.
Nach den Renderer-Korrekturen ist es nicht automatisch der aktuelle Runtime-Artefaktstand.

---

## 3b. Aktueller Befund: Richtungs-Shading-Multiplikatoren vs. Vanilla (in Untersuchung)

**Status:** neu untersucht (2026-08-31), gesicherte Fakten unten. **Kein Fix, keine Config-,
Custom- oder Adapterentscheidung abgeleitet.** Die bereits geschlossene Frage „geht
Shade-Information beim Patch verloren?“ wird NICHT wieder geöffnet — die Information ist beim
Patch angekommen. Neu ist eine **Wertübersetzungs-Abweichung** (welche Multiplikatoren ein
Shade-Step liefert).

### Aktivpfad (Default)

`useBrightnessTable` ist per Default **false** (`MapManager.java`). Damit läuft der **else-Zweig**
ohne brightness table in `TexturePackHDShader.processBlock` (Zeilen 231–248). Die gemessenen
Multiplikatoren (ausführender Scratch-Probe `ScratchShadeKeyProbeTest`, BUILD SUCCESSFUL):

| Shade-Step (Key) | Fläche (Vanilla-Begriff) | Aktivpfad | Table-Pfad | Vanilla-Faktor |
|---|---|---|---|---|
| `Y_MINUS` | Top | **85,1 % (gerade Y) / 90,2 % (ungerade Y)** | 95,3 % / 100 % | **1.0** |
| `Y_PLUS` | Bottom | **85,1 % / 90,2 % (identisch mit Top!)** | 50,2 % | **0.5** |
| `Z_*` | North/South | **100 % (keine Abdunklung)** | 80,4 % | **0.8** |
| `X_*` | East/West | 62,7 % | 60 % | 0.6 |

Abweichungen: Top ist im Aktivpfad 10–15 % zu dunkel **und** abhängig von
`getY() & 1` (Paritäts-Banding, das Vanilla nicht kennt); Bottom ist im Aktivpfad von Top
nicht unterscheidbar (beide 85/90 %) und massiv zu hell (Vanilla 0.5); North/South ist 20 % zu
hell (100 % statt 0.8). Der Table-Pfad behält zusätzlich das Paritäts-Banding auf Top.

### Versionsabhängiger Weg in den Y_MINUS-Pfad: `shade_direction_override`

Reale Ressourcen aus den Client-JARs (nicht geraten):

- **26.2 `assets/minecraft/models/block/cross.json`** (Pflanze/Blume): beide Elemente
  `"shade": false` → `getShade()=false` → **keine Abdunklung, 100 %**. Damit ist der 26.2-Pfad
  hier Vanilla-konform.
- **26.3-snapshot-10 `cross.json`**: stattdessen `"shade_direction_override": "up"`.
  `MinecraftModelLoader.SHADE_DIRECTIONS` bildet `"up"` auf `BlockStep.Y_MINUS` ab; dieser
  überlebt `rescale`+Rotation (Y-Achse, bleibt `Y_MINUS`) und gelangt über
  `patch_shade_step` → `cur_shade_step` → `getShadeStep()` in den Shader. Folge im
  **Aktivpfad: 85,1 % (gerade Y) / 90,2 % (ungerade Y)** statt des Vanilla-Konstantfaktors
  `up = 1.0`; im Table-Pfad 95,3 %/100 %.

Damit wird eine Pflanze in 26.2 zu 100 %, in 26.3 zu ~86–90 % mit Paritäts-Banding gerendert.
Das bandförmige 5‑%‑Schrittmuster entlang von Höhenstufen und die hellen Nord/Süd-Kanten
(Z 100 % an Top/Kanten mit 85–90 %) sind der plausibelste technische Ursprung der beobachteten
hellen Streifen im JSON-Pfad; ein Laufzeit-Screenshot-Blob fehlt bisher für den Verbindungsbeweis.

### Beweislage

- `ScratchShadeKeyProbeTest` (unversioniert, wie die übrigen Scratch-Tests) rechnet Würfel
  (alle 6 Seiten) und beide Cross-Varianten durch der realen JSON-Semantik durch
  (`PatchDefinitionFactory.getModelFace` → `getScaledPatch` → `getPatch`, exakt wie
  `MinecraftModelLoader.install`/`rotateElement`).
- 26.2- und 26.3-`cross.json` + `cube.json` wurden aus den realen Client-JARs extrahiert:
  `/home/jens/.gradle/caches/fabric-loom/26.2/minecraft-client.jar` bzw.
  `/home/jens/.minecraft/minecraft-resources/minecraft-client-26.3-snapshot-10.jar`.

---

## 4. Test- und Laufzeitstatus

- Gezielter `MinecraftModelUvLockTableTest`: erfolgreich.
- Gezielter `MinecraftModelTintRegistrationTest`: erfolgreich.
- Der dafür ausgeführte Gradle-Testlauf für `:DynmapCore:test` endete mit
  `BUILD SUCCESSFUL`.
- Frühere vollständige Core-Testläufe hatten ausschließlich den unversionierten Scratch-Test
  `ChestFlatVsSurfaceRenderTest` rot.
- Frühere Fabric-/Paper-Builds waren erfolgreich.
- **Keiner dieser Build-/Testerfolge ist ein Runtime-Beweis.** Der konkrete Paper-Runtime-Beleg
  steht separat in Abschnitt 2.
- Als mögliche, derzeit nicht beauftragte Validierung bleibt offen, den aktuellen Stand auf
  Paper 26.2 und Fabric 26.3-snapshot-10 jeweils gegen die passende Vanilla-Version zu prüfen.
  Diese Feststellung legt weder Reihenfolge noch Umfang eines späteren Auftrags fest.

---

## 5. Weltidentität und bestehende Ruhepunkte

### Weltidentität (abgeschlossen)

Verbindliche Regel: Identität = Weltname + heutige Minecraft-Dimension.

- Historische Dimensionsnamen dienen nur als Kompatibilitätseingang und werden auf heutige
  Dimensionen abgebildet.
- Keine erfundene ID oder Raterei; unbekannte Namen bleiben unverändert.
- Alias-Listen enthalten keine hartkodierten „world“-Behauptungen.
- Fabric: `FabricWorld.getCanonicalLevelName` und `getLegacyLevelNames`.
- Paper: `BukkitWorld.getLegacyWorldNames`; `DynmapPlugin.getWorldByName` verwendet
  `getNameAliases()`.

Weltidentität und Storage nicht ohne neuen konkreten Befund wieder öffnen.

### Frühere reale Fabric-Bestätigungen

- Normale JSON-Modelle, Pflanzen und der Positivpfad für Chest/Shulker.
- Copper Golem Statue sichtbar im realen Surface-Render.
- Fließendes Wasser sichtbar und vom Nutzer als scheinbar richtig bestätigt.

Wasser ist Ruhepunkt und gehört nicht in die nächste Renderer-Runde.

### Arbeitsbaum schützen

- Nichts resetten oder verwerfen.
- Unversionierte Scratch-Tests bleiben als Nachweis:
  `ChestFlatVsSurfaceRenderTest`, `FlatLossProbeTest`, `ScratchLayerProbeTest`,
  `ScratchShadeKeyProbeTest` (Befund 3b).
- Im Arbeitsbaum liegen außerdem Änderungen zu Wasser, Weltidentität und Paper-Adapter; sie
  gehören nicht zum engen JSON-Renderer-Auftrag.
- Occlusion bleibt experimentell/offen und wird nur bei neuem konkretem Befund wieder
  aufgenommen.

---

## 6. Nicht ohne konkreten Befund wieder öffnen

- TXT als Render-Wahrheit
- Wasser
- Weltidentität / Storage
- `ambientocclusion`
- modellbasierte `light_emission`
- blockweise Sondermodelle
- allgemeines Renderer-Refactoring
- zusätzliche UV-/Tint-Abstraktionen
- Config-Default `image-format: webp-q80` und dessen Fallback-Verhalten; niemals ohne genaue
  ausdrückliche Anweisung ändern

**Arbeitsprinzip:** Die Minecraft-Version liefert ihre Realität. Der Core übersetzt gemeinsame
Minecraft-Semantik. Adapter übersetzen Plattformfakten. Config und Custom bleiben
Nutzervertrag. Keine erfundene Wahrheit dazwischen.
