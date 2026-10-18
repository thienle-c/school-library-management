package thuvien.server.database;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConfig {
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String CONFIG_FILE = "db.properties";

    private String driver;
    private String url;
    private String username;
    private String password;
    private int maxPoolSize = 10;
    private int minIdle = 2;
    private long idleTimeout = 30000;
    private long connectionTimeout = 10000;
    private long maxLifetime = 1800000;

    public DatabaseConfig() {
        loadConfig();
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
                this.driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
                this.url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/school_library");
                this.username = props.getProperty("db.username", "root");
                this.password = props.getProperty("db.password", "");
                this.maxPoolSize = Integer.parseInt(props.getProperty("db.pool.maxSize", "10"));
                this.minIdle = Integer.parseInt(props.getProperty("db.pool.minIdle", "2"));
                this.idleTimeout = Long.parseLong(props.getProperty("db.pool.idleTimeout", "30000"));
                this.connectionTimeout = Long.parseLong(props.getProperty("db.pool.connectionTimeout", "10000"));
                this.maxLifetime = Long.parseLong(props.getProperty("db.pool.maxLifetime", "1800000"));
            } else {
                LOGGER.log(Level.WARNING, "db.properties not found on classpath, using defaults.");
                this.driver = "com.mysql.cj.jdbc.Driver";
                this.url = "jdbc:mysql://localhost:3306/school_library";
                this.username = "root";
                this.password = "root";
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load db.properties", e);
        }

        // 1. Check for uncommitted test configuration file: test.local.properties (ignored by git via *.local.properties)
        // or custom file specified via System property -Ddb.config.file=...
        String customConfigFile = System.getProperty("db.config.file");
        java.io.File testConfigFile = customConfigFile != null
                ? new java.io.File(customConfigFile)
                : new java.io.File("test.local.properties");
        if (testConfigFile.exists() && testConfigFile.isFile()) {
            try (InputStream testIn = new java.io.FileInputStream(testConfigFile)) {
                Properties testProps = new Properties();
                testProps.load(testIn);
                if (testProps.getProperty("db.driver") != null) {
                    this.driver = testProps.getProperty("db.driver");
                }
                if (testProps.getProperty("db.url") != null) {
                    this.url = testProps.getProperty("db.url");
                }
                if (testProps.getProperty("db.username") != null) {
                    this.username = testProps.getProperty("db.username");
                }
                if (testProps.getProperty("db.password") != null) {
                    this.password = testProps.getProperty("db.password");
                }
                if (testProps.getProperty("db.pool.maxSize") != null) {
                    this.maxPoolSize = Integer.parseInt(testProps.getProperty("db.pool.maxSize"));
                }
                LOGGER.info("Loaded configuration override from: " + testConfigFile.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load " + testConfigFile.getName(), e);
            }
        }

        // 2. Check for environment variable overrides
        String envUrl = System.getenv("DB_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            this.url = envUrl.trim();
        }
        String envUser = System.getenv("DB_USERNAME");
        if (envUser != null && !envUser.trim().isEmpty()) {
            this.username = envUser.trim();
        }
        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null) {
            this.password = envPass;
        }

        // 3. Check for System property overrides (-Ddb.url=... or mapped from -Dtest-sys-prop.db.url=...)
        String sysUrl = System.getProperty("db.url");
        if (sysUrl != null && !sysUrl.trim().isEmpty()) {
            this.url = sysUrl.trim();
        }
        String sysUser = System.getProperty("db.username");
        if (sysUser != null && !sysUser.trim().isEmpty()) {
            this.username = sysUser.trim();
        }
        String sysPass = System.getProperty("db.password");
        if (sysPass != null) {
            this.password = sysPass;
        }
    }

    public String getDriver() { return driver; }
    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getMinIdle() { return minIdle; }
    public long getIdleTimeout() { return idleTimeout; }
    public long getConnectionTimeout() { return connectionTimeout; }
    public long getMaxLifetime() { return maxLifetime; }
}
