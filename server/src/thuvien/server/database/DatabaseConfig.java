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
