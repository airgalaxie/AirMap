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

    // MariaDB specific driver check
    @Override
    protected boolean checkDriver() {
        connectionString = "jdbc:mariadb://" + hostname + ":" + port + "/" + database + flags;
        org.dynmap.Log.info("Opening MariaDB database " + hostname + ":" + port + "/" + database + " as map store");
        return loadJdbcDriver("MariaDB-JDBC", "org.mariadb.jdbc.Driver");
    }
}
