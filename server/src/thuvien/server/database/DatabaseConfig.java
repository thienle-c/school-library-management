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
        boolean loadedFromClasspath = false;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                props.load(in);
                this.driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
                this.url = props.getProperty("db.url");
                this.username = props.getProperty("db.username");
                this.password = props.getProperty("db.password");
                this.maxPoolSize = Integer.parseInt(props.getProperty("db.pool.maxSize", "10"));
                this.minIdle = Integer.parseInt(props.getProperty("db.pool.minIdle", "2"));
                this.idleTimeout = Long.parseLong(props.getProperty("db.pool.idleTimeout", "30000"));
                this.connectionTimeout = Long.parseLong(props.getProperty("db.pool.connectionTimeout", "10000"));
                this.maxLifetime = Long.parseLong(props.getProperty("db.pool.maxLifetime", "1800000"));
                loadedFromClasspath = true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load db.properties", e);
        }

        boolean loadedFromTestFile = false;
        // 1. Check for custom configuration file specified via System property -Ddb.config.file=...
        // Note: 'test.local.properties' is only permitted if explicitly specified via -Ddb.config.file
        // or when explicitly running in a designated test/development environment via -Dapp.env=test.
        // This strictly prevents a test.local.properties file from silently overriding production configurations.
        String customConfigFile = System.getProperty("db.config.file");
        if (customConfigFile == null && "test".equalsIgnoreCase(System.getProperty("app.env"))) {
            if (new java.io.File("test.local.properties").exists()) {
                customConfigFile = "test.local.properties";
            }
        }
        if (customConfigFile != null) {
            java.io.File testConfigFile = new java.io.File(customConfigFile);
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
                    loadedFromTestFile = true;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to load " + testConfigFile.getName(), e);
                }
            }
        }

        // 2. Check for environment variable overrides
        boolean hasEnvOverride = false;
        String envUrl = System.getenv("DB_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            this.url = envUrl.trim();
            hasEnvOverride = true;
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
        boolean hasSysOverride = false;
        String sysUrl = System.getProperty("db.url");
        if (sysUrl != null && !sysUrl.trim().isEmpty()) {
            this.url = sysUrl.trim();
            hasSysOverride = true;
        }
        String sysUser = System.getProperty("db.username");
        if (sysUser != null && !sysUser.trim().isEmpty()) {
            this.username = sysUser.trim();
        }
        String sysPass = System.getProperty("db.password");
        if (sysPass != null) {
            this.password = sysPass;
        }

        // 4. Strict Validation: Fail-Fast if no valid configuration source was found
        if (!loadedFromClasspath && !loadedFromTestFile && !hasEnvOverride && !hasSysOverride) {
            String missingMsg = "[DATABASE CONFIG ERROR] Không tìm thấy tệp cấu hình 'db.properties' trên Classpath hoặc thư mục 'resources/'. "
                    + "Vui lòng sao chép 'resources/db.properties.example' thành 'resources/db.properties' và cấu hình tham số kết nối chính xác trước khi khởi động server.";
            LOGGER.severe(missingMsg);
            throw new IllegalStateException(missingMsg);
        }

        if (this.url == null || this.url.trim().isEmpty()) {
            String emptyUrlMsg = "[DATABASE CONFIG ERROR] Tham số 'db.url' không được để trống trong cấu hình database.";
            LOGGER.severe(emptyUrlMsg);
            throw new IllegalStateException(emptyUrlMsg);
        }

        // 5. Detect placeholder credentials in production releases
        if (this.username != null && this.username.trim().equals("your_db_username")) {
            String placeholderUserMsg = "[DATABASE CONFIG ERROR] Cấu hình database vẫn đang sử dụng giá trị mẫu placeholder: 'your_db_username'. "
                    + "Vui lòng cập nhật thông tin tài khoản cơ sở dữ liệu thực tế trong 'resources/db.properties' trước khi vận hành.";
            LOGGER.severe(placeholderUserMsg);
            throw new IllegalStateException(placeholderUserMsg);
        }
        if (this.password != null && this.password.trim().equals("your_db_password")) {
            String placeholderPassMsg = "[DATABASE CONFIG ERROR] Cấu hình database vẫn đang sử dụng giá trị mẫu placeholder: 'your_db_password'. "
                    + "Vui lòng cập nhật mật khẩu cơ sở dữ liệu thực tế trong 'resources/db.properties' trước khi vận hành.";
            LOGGER.severe(placeholderPassMsg);
            throw new IllegalStateException(placeholderPassMsg);
        }
        if (this.driver == null || this.driver.trim().isEmpty()) {
            this.driver = "com.mysql.cj.jdbc.Driver";
        }
        if (this.username == null) {
            this.username = "root";
        }
        if (this.password == null) {
            this.password = "";
        }
    }

    public String getDatabaseName() {
        if (url == null) return "UNKNOWN";
        int qIdx = url.indexOf('?');
        String cleanUrl = (qIdx != -1) ? url.substring(0, qIdx) : url;
        int slashIdx = cleanUrl.lastIndexOf('/');
        if (slashIdx != -1 && slashIdx < cleanUrl.length() - 1) {
            return cleanUrl.substring(slashIdx + 1);
        }
        return "UNKNOWN";
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
