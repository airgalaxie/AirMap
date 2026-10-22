package org.dynmap.storage.mariadb;

import org.dynmap.DynmapCore;
import org.dynmap.storage.mysql.MySQLMapStorage;

public class MariaDBMapStorage extends MySQLMapStorage {

    public MariaDBMapStorage() {
    }

    @Override
    protected String getDefaultDriverJar(DynmapCore core) {
        return "drivers/mariadb-java-client-" + core.getMariaDbDriverVersion() + ".jar";
    }

    @Override
    public boolean init(DynmapCore core) {
        String bundledDriverJar = getDefaultDriverJar(core);
        String configuredDriverJar = core.configuration.getString("storage/driver-jar", "").trim();
        if (!configuredDriverJar.isEmpty()
                && !core.getFile(configuredDriverJar).toPath().toAbsolutePath().normalize()
                        .equals(core.getFile(bundledDriverJar).toPath().toAbsolutePath().normalize())
                && core.getFile(bundledDriverJar).isFile()) {
            org.dynmap.Log.warning("\u001B[33mBundled MariaDB JDBC driver " + core.getMariaDbDriverVersion()
                    + " is available at " + bundledDriverJar + "; configuration still uses " + configuredDriverJar
                    + "\u001B[0m");
        }
        return super.init(core);
    }

    // MariaDB specific driver check
    @Override
    protected boolean checkDriver() {
        connectionString = "jdbc:mariadb://" + hostname + ":" + port + "/" + database + flags;
        org.dynmap.Log.info("Opening MariaDB database " + hostname + ":" + port + "/" + database + " as map store");
        return loadJdbcDriver("MariaDB-JDBC", "org.mariadb.jdbc.Driver");
    }
}
