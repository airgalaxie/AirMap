# JDBC Drivers

Place optional JDBC driver JARs in this directory when using SQL storage.

Examples:
- MySQL: `mysql-connector-j.jar`
- MariaDB: `mariadb-java-client-<version>.jar`

Then set storage/driver-jar in configuration.yaml, for example:
```yaml
driver-jar: "drivers/mariadb-java-client-<version>.jar"
```

The configured filename must match the JAR placed in the data folder.
Dependency versions used by the build are defined in
`gradle/libs.versions.toml` at the repository root.
