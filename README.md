# AirMap – based on Dynmap®

AirMap is an independent project based on the [Dynmap® project](https://github.com/webbukkit/dynmap), incorporating work from [JLyne's fork](https://github.com/JLyne/dynmap) and [airgalaxie/dynmap-paper](https://github.com/airgalaxie/dynmap-paper). Dynmap® is the original project and remains the foundation of AirMap. AirMap shares one hardened Dynmap core between the Paper plugin and Fabric mod.

> [!IMPORTANT]
> AirMap is not an official Dynmap release and is not affiliated with, endorsed by, or supported by the Dynmap project. Support for AirMap and its modifications is the responsibility of the AirMap project and its maintainers, not the Dynmap project or its maintainers.

> [!NOTE]
> **Versions:** The authoritative Minecraft, platform, Java, plugin and dependency versions are in `gradle/libs.versions.toml`.<br>
> **Foundation:** AirMap was established on the Minecraft `26.2` codebase and is designed to evolve with later Minecraft versions. This foundation does not define an upper supported-version limit.<br>
> **Java:** The development JDK may be newer than the centrally configured Java runtime/toolchain target.<br>
> **Platforms:** Paper and Fabric are first-class platform modules built on the same `DynmapCore`.<br>
> **Plugin API Goal:** Preserve Bukkit/Dynmap API compatibility for external plugin integrations where practical.<br>
> **Release:** AirMap `1.0.0`. Consult the version catalog for the exact current technical targets.

---

## Side-by-Side Comparison of Project Scope


| Original dynmap Repository                          | This project                                                                                  |
|:----------------------------------------------------|:--------------------------------------------------------------------------------------------|
| **Target Platform:** Platform-specific baseline     | **Target Platforms:** Paper and Fabric from one centrally versioned multi-project           |
| **Compiler / Runtime:** Legacy Java target          | **Compiler / Runtime:** Centrally selected Java target; development JDK may be newer         |
| **Build System:** Outdated Gradle build             | **Build System:** Gradle with a shared version catalog                                       |
| **Web Framework:** Deprecated/legacy web assets     | **Web Framework:** Maintained bundled web assets                                            |
| **Map Engine:** Outdated map library                | **Map Engine:** Maintained bundled map library                                               |
| **Render Configurations:** Outdated model txt-files | **Render Configurations:** Fixed critical bugs in bundled model definition files            |

---

## Main Changes in this Fork (Summary)

*   **Version-line flexibility**: Minecraft and platform versions are selected centrally. The current baseline does not define the permanent upper version limit.
*   **Bukkit/Dynmap API Compatibility Goal**: Keeps the public Dynmap Bukkit API and common Paper integration points stable where practical, while Fabric uses its platform adapter over the shared core.
*   **Central Java target**: The Java toolchain target is read from the version catalog, allowing a newer development JDK and a deliberate future runtime transition.
*   **Maintained web dependencies**: Bundled frontend assets are updated deliberately; their files and dependency metadata are authoritative for exact versions.
*   **Model Text Fixes**: Corrected specific structure and block rendering bugs in the `Modelsxx.txt` configuration data.

---

Changes include:
- Shared runtime support for Paper and Fabric
- Now a Mojang mapped Paper plugin
- Removal of web chat
- Removal of login support
- Replacement of the legacy internal webserver with a small static webserver plus documented storage-backed endpoints for SQL storage
- Removal of obsolete permission providers and SkinsRestorer integration
- Removal of various outdated workarounds (Log4Shell, Spout lighting, etc)
- Removal of Postgres and S3 storage types

## Configuration and Platform Notes

*   **Configuration:** Dynmap's configuration is primarily managed through `configuration.txt` and related files in the platform data folder. General upstream configuration guidance still applies, with the canonical world-ID and storage-migration differences documented below.
*   **Webserver Default:** The Dynmap webserver configuration is present, and the bundled default configuration follows the original Dynmap default with `disable-webserver: false`. Deploy the generated web files to an external web server (e.g., Nginx, Apache), or use the internal static webserver if you want Dynmap to serve the generated web UI itself.
*   **Platform Support:** Paper and Fabric are built as separate artifacts against versions selected in the central catalog. A version change can still require source, mapping or API adaptations and must be verified on both platforms. The Paper artifact is not a Spigot server build, although Bukkit-facing Dynmap API compatibility should be preserved where practical.

## Dynmap compatibility documentation

`/airmap ...` and `/dynmap ...` use the same command implementation, including subcommands, arguments, permissions, and completion. The existing [upstream Dynmap® documentation](https://github.com/webbukkit/dynmap/wiki) therefore currently applies to AirMap's shared command interface; it is Dynmap® documentation, not AirMap support documentation.

## Modules and artifacts

The shared core and API modules are internal building blocks. End-user platform JARs are written to `target/` using version-derived names:

```text
AirMap-<version>+<rev>-paper.jar
AirMap-<version>+<rev>-fabric.jar
```

`<rev>` is Git's short form of the current commit. Builds with staged, unstaged, or untracked files append `-dirty` after the revision. The AirMap product version remains defined exclusively in `gradle/libs.versions.toml`.

## World identity and storage

World configuration uses the canonical Minecraft registry ID, without a leading slash. Examples include `minecraft:overworld`, `minecraft:the_nether` and `minecraft:the_end`; mod dimensions retain their complete namespace and path.

Historical save names such as `world`, `DIM-1` and `DIM1` are compatibility aliases only. The public canonical ID is separate from Dynmap's internal filesystem-safe storage ID. Integrations must not derive file or directory names from `DynmapWorld#getName()` and must not depend on the internal storage-ID format.

Filetree storage migrates an unambiguous legacy world directory to the safe storage ID without overwriting existing data. Ambiguous aliases or multiple matching legacy directories are reported and are not migrated automatically.

## Web Chat Boundary

Browser-to-server web chat is disabled in this fork. The web UI message display remains enabled: `chat`, `chatbox`, and `chatballoon` client components are still used for server-to-web messages, join/quit notices, player chat display, and map chat balloons. Do not remove those components when disabling browser chat input. Marker popups and labels are handled separately by the marker component and marker JSON files.

## JDBC Drivers

Database storage classes for MySQL and MariaDB are present. The centrally selected MariaDB Connector/J is included as an extracted driver resource. Dynmap first checks whether the configured driver is already available on the server classpath. Otherwise, configure `storage/driver-jar` and optionally `storage/driver-class`; the same mechanism can load a separately supplied MySQL Connector/J.

## External Web Server and Live Data URLs

This fork keeps the existing Dynmap-style external web server workflow intact. The generated web UI reads live data through the URLs written to `standalone/config.js`. By default, those URLs point to the generated standalone JSON files:

```yaml
url:
    # configuration: "standalone/dynmap_config.json?_={timestamp}"
    # update: "standalone/dynmap_{world}.json?_={timestamp}"
```

Existing configuration remains supported, but deployments that directly reference historical world-derived filenames or tile directories must account for the storage-ID migration. Advanced deployments that route live data through a reverse proxy or another endpoint can override these URLs in `configuration.txt`:

```yaml
url:
    configuration: "up/configuration"
    update: "up/world/{world}/{timestamp}"
```

This is the supported compatibility path for custom routing. The project should not require users to rebuild a working Nginx, Apache, or CDN setup just to follow the fork. If a deployment serves the web UI from one machine while the backend writes live JSON over NFS or another shared filesystem, short-lived `404` responses for live JSON usually indicate infrastructure timing or cache visibility issues. In that case, prefer local filesystem/NFS/Nginx tuning or URL overrides over changing the global project defaults.

In generated live-data URLs, `{world}` is a transport placeholder supplied by Dynmap. External rules should pass it through unchanged and must not assume that it is the public canonical registry ID.

## Internal Webserver Storage Endpoints

The internal Java webserver still serves normal web assets directly from `webpath`. For SQL-style storage, it also exposes optional storage-backed endpoints so users can run MySQL/MariaDB without an external PHP webserver and without exporting duplicate tile files.

This is a deliberate fork-level change from original Dynmap and is controlled by:

```yaml
webserver-storage-endpoints: true
```

When the internal webserver is enabled and the storage default URLs would otherwise point at `standalone/*.php`, `standalone/config.js` is generated with Java-backed routes:

```yaml
configuration: "up/configuration"
update: "up/world/{world}/{timestamp}"
tiles: "storage/tiles/"
markers: "storage/markers/"
```

Those endpoints read through the existing `MapStorage` API:

- live configuration and world updates from standalone storage
- map tiles from storage by world and tile URI
- marker JSON, marker icons, and player faces from storage

External deployments are kept compatible. If `disable-webserver: true`, or if explicit `url:` values are configured, the original external URLs remain in use, including the original SQL PHP endpoints such as `standalone/MySQL_update.php` and `standalone/MySQL_tiles.php`.

## External PHP SQL Endpoints

The bundled MySQL standalone PHP endpoints are kept for external Apache/Nginx/PHP deployments. MariaDB storage uses the same MySQL-compatible endpoint names and request parameters:

```text
standalone/MySQL_configuration.php
standalone/MySQL_update.php?world={world}&ts={timestamp}
standalone/MySQL_tiles.php?tile=
standalone/MySQL_markers.php?marker=
```

The PHP code uses modern PHP conventions with strict types, `__DIR__` includes, typed helper functions, `http_response_code`, consistent JSON error responses for JSON endpoints, prepared statements, `utf8mb4`, and path validation. `MySQL_config.php` is still generated by Dynmap and remains compatible with the original variable names.

---

### Support Disclaimer
AirMap is independently maintained. Please use the AirMap project's own issue tracker or support channels for AirMap and its modifications. Do not contact the original [Dynmap Team](https://github.com/webbukkit/dynmap), [JLyne](https://github.com/JLyne/dynmap), or the maintainers of [airgalaxie/dynmap-paper](https://github.com/airgalaxie/dynmap-paper) for AirMap support.
