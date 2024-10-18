package org.openmrs.standalone;

import java.nio.file.Paths;
import java.util.Properties;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.apache.commons.lang3.RandomStringUtils;

public class MariaDbController {

    public static final String DATABASE_NAME = "openmrs";
    private static final String MARIA_DB_BASE_DIR = "database";
    private static final String MARIA_DB_DATA_DIR = Paths.get(MARIA_DB_BASE_DIR, "data").toString();

    private static DB mariaDB;
    private static DBConfigurationBuilder mariaDBConfig;
    private static String rootPassword;

    public static String KEY_MARIADB_BASE_DIR = "mariadb.basedir";
    public static String KEY_MARIADB_DATA_DIR = "mariadb.datadir";

    public static void startMariaDB(String port) throws Exception {
        startMariaDB(Integer.parseInt(port));
    }

    public static void startMariaDB(int port) throws Exception {
        mariaDBConfig = DBConfigurationBuilder.newBuilder();
        mariaDBConfig.setPort(port);
        mariaDBConfig.setSecurityDisabled(false);

        Properties properties = OpenmrsUtil.getRuntimeProperties(StandaloneUtil.getContextName());

        String baseDir = safeResolveProperty(properties, KEY_MARIADB_BASE_DIR, MARIA_DB_BASE_DIR);
        String dataDir = safeResolveProperty(properties, KEY_MARIADB_DATA_DIR, MARIA_DB_DATA_DIR);

        mariaDBConfig.setBaseDir(Paths.get(baseDir).toAbsolutePath().toString());
        mariaDBConfig.setDataDir(Paths.get(dataDir).toAbsolutePath().toString());

        mariaDBConfig.addArg("--max_allowed_packet=96M");
        mariaDBConfig.addArg("--collation-server=utf8_general_ci");
        mariaDBConfig.addArg("--character-set-server=utf8");

        mariaDB = DB.newEmbeddedDB(mariaDBConfig.build());

        mariaDB.start();

        // Starting with MariaDB 10.4, the root user has an invalid password.
        // We will therefore modify the root user password to a secure random string (a security best practice).
        // Using the UID of the user that owns the data directory, we can execute this initial bootstrapping command:
        // Note that on Windows MariaDB apparently does not implement this, still uses empty string password for the
        // root user, so we can just use the root user.
        if (rootPassword != null) {
            rootPassword = RandomStringUtils.random(69, 97, 122, true, true);
            mariaDB.run("SET PASSWORD FOR 'root'@'localhost' = PASSWORD('" + rootPassword + "');",
                    mariaDBConfig.isWindows() ? "root" : System.getProperty("user.name"), "");
        }

        mariaDB.createDB(DATABASE_NAME, "root", MariaDbController.getRootPassword());
    }

    private static String safeResolveProperty(Properties properties, String key, String defaultValue) {
        if (properties == null || !properties.containsKey(key)) {
            return defaultValue;
        }
        return properties.getProperty(key, defaultValue);
    }

    public static void stopMariaDB() throws ManagedProcessException {
        if (mariaDB != null) {
            mariaDB.stop();
        } else {
            System.out.println("MariaDB has already been stopped");
        }
    }

    public static String getRootPassword() {
        return rootPassword;
    }
}
