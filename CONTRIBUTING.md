# Contributing to AirMap – based on Dynmap®

## Project Origins and History
AirMap is an independent project based on the [Dynmap® project](https://github.com/webbukkit/dynmap), with a shared Dynmap core and separate Paper and Fabric adapters. Dynmap® is the original project and remains the foundation of AirMap. AirMap is not an official Dynmap release and is not affiliated with, endorsed by, or supported by the Dynmap project. Preserve upstream structure and public integration behavior where practical, while keeping platform-specific code outside the core.

## Target Environments and Java Runtime
The runtime and build strategy is centrally versioned:
- **Authoritative Versions:** Use `gradle/libs.versions.toml`; do not duplicate version literals in platform build files or documentation.
- **Java Target:** Production code must remain compatible with the Java target selected in the version catalog. The development JDK may be newer.
- **Minecraft Baseline:** The current catalog starts at Minecraft 26.2, while the architecture must remain adaptable to later lines such as 27.2.
- **Platform Targets:** Paper, Fabric and their toolchains are selected through the shared catalog.
- **Plugin API Target:** External plugin integrations should continue to use the published Bukkit/Dynmap API where possible.
- **Build Infrastructure:** Keep Gradle and dependency changes focused and documented.

## Contribution and Scope Rules
- **Platform Artifacts:** Paper and Fabric produce separate end-user JARs in `target/`; shared Core/API JARs are not end-user artifacts.
- **Bukkit/Spigot API Compatibility:** Preserve the public Dynmap Bukkit API and common integration behavior for other plugins where practical.
- **Shared Core:** Platform adapters provide Minecraft registry keys; canonical identity, aliases, configuration matching, storage identity and migration belong in the core.
- **Modernized Frontend:** Web assets use modern jQuery and Leaflet versions. Keep frontend changes compatible with the existing Dynmap web UI flow.
- **Internal Webserver:** The fork includes a small internal static webserver and optional storage-backed endpoints. Preserve external web server compatibility and existing URL override behavior.
- **Removed Legacy Storage:** PostgreSQL and S3 storage are not implemented in this fork.
- **Web Chat and Login:** Browser-to-server chat input and login support are disabled. Do not remove the display components used for server-to-web messages, player chat display, join/quit notices, marker labels, or popups.
- **JDBC Drivers:** MariaDB Connector/J is bundled as an extracted resource. Driver loading through `storage/driver-jar` and `storage/driver-class` must remain supported for the bundled MariaDB driver and separately supplied drivers such as MySQL Connector/J.
- **External PHP Endpoints:** Keep the MySQL-compatible standalone PHP endpoints usable for external Apache/Nginx/PHP deployments.
- **Repository Hygiene:** Do not commit generated build output, Gradle caches, reports, `.class` files, or release artifacts unless explicitly required.
- **Pull Requests:** Keep PRs highly focused. Avoid pure style, reformatting, or pretty-printing changes, as they obscure reviews and trigger unnecessary merge conflicts.

## Dynmap compatibility documentation

`/airmap ...` and `/dynmap ...` use the same command implementation, so the existing [upstream Dynmap® documentation](https://github.com/webbukkit/dynmap/wiki) currently applies to both names. Treat that material as Dynmap® compatibility documentation and link to it rather than copying its contents into AirMap documentation.

## Licensing
The project remains licensed under the **Apache License 2.0**. By contributing, you agree that your code falls under this license.

## Support Disclaimer
AirMap is independently maintained. Please use the AirMap project's own issue tracker or support channels for AirMap and its modifications. Do not contact the original Dynmap team or prior maintainers for AirMap support.
