package thuvien.server.rmi;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.Connection;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.rmi.LibraryRemoteService;
import thuvien.server.database.DatabaseManager;
import thuvien.server.router.RequestRouter;

/**
 * Bootstrap entry point for the Remote School Library Java RMI Server.
 * Configures java.rmi.server.hostname to the actual LAN IPv4 address,
 * creates/locates the RMI Registry on port 1099, instantiates LibraryRemoteServiceImpl
 * on fixed port 1100 (non-dynamic), and binds it as "LibraryRemoteService".
 */
public class LibraryRMIServer {
    private static final Logger LOGGER = Logger.getLogger(LibraryRMIServer.class.getName());

    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final int DEFAULT_SERVICE_PORT = 1100;
    public static final String DEFAULT_SERVICE_NAME = "LibraryRemoteService";

    public static final String DEFAULT_SERIAL_FILTER =
            "maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;"
            + "thuvien.common.**;"
            + "java.lang.**;"
            + "java.util.**;"
            + "java.math.**;"
            + "java.sql.**;"
            + "java.rmi.**;"
            + "sun.rmi.**;"
            + "com.sun.proxy.**;"
            + "!*";

    static {
        configureSerializationFilter();
    }

    /**
     * Configures process-wide JEP 290 object input filter for RMI serialization security.
     */
    public static void configureSerializationFilter() {
        String filter = System.getProperty("jdk.serialFilter");
        if (filter == null || filter.trim().isEmpty()) {
            filter = DEFAULT_SERIAL_FILTER;
            System.setProperty("jdk.serialFilter", filter);
        }
        java.security.Security.setProperty("jdk.serialFilter", filter);
        LOGGER.info("Configured JEP 290 Serialization Filter: " + filter);
    }

    private final int registryPort;
    private final int servicePort;
    private final String serviceName;
    private final String configuredHost;

    private Registry registry;
    private LibraryRemoteServiceImpl remoteService;
    private String activeHost;
    private boolean running = false;
    private boolean createdRegistry = false;

    public LibraryRMIServer() {
        this(DEFAULT_REGISTRY_PORT, DEFAULT_SERVICE_PORT, DEFAULT_SERVICE_NAME, null);
    }

    public LibraryRMIServer(int registryPort, String serviceName) {
        this(registryPort, DEFAULT_SERVICE_PORT, serviceName, null);
    }

    public LibraryRMIServer(int registryPort, int servicePort, String serviceName) {
        this(registryPort, servicePort, serviceName, null);
    }

    public LibraryRMIServer(int registryPort, int servicePort, String serviceName, String configuredHost) {
        this.registryPort = registryPort > 0 ? registryPort : DEFAULT_REGISTRY_PORT;
        this.servicePort = servicePort > 0 ? servicePort : DEFAULT_SERVICE_PORT;
        this.serviceName = (serviceName != null && !serviceName.trim().isEmpty()) ? serviceName.trim() : DEFAULT_SERVICE_NAME;
        this.configuredHost = configuredHost;
    }

    public synchronized void start() throws Exception {
        if (running) {
            return;
        }

        // Configure serialization filter before RMI initialization
        configureSerializationFilter();

        // Initialize database pool early and verify schema integrity
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String catalog = conn.getCatalog();
            LOGGER.info("Connected to Target Database Catalog: '" + catalog + "'");

            // Verify expected catalog / environment if specified
            String expectedCatalog = System.getProperty("db.expected.catalog");
            if (expectedCatalog == null || expectedCatalog.trim().isEmpty()) {
                expectedCatalog = System.getenv("DB_EXPECTED_CATALOG");
            }
            if (expectedCatalog != null && !expectedCatalog.trim().isEmpty()) {
                if (catalog == null || !expectedCatalog.trim().equalsIgnoreCase(catalog.trim())) {
                    String envMismatchMsg = String.format(
                            "[DATABASE CONFIG ERROR] Mục tiêu cơ sở dữ liệu không khớp! Yêu cầu: '%s', nhưng kết nối thực tế tới: '%s'. "
                            + "Máy chủ dừng khởi động để bảo vệ an toàn dữ liệu.",
                            expectedCatalog.trim(), catalog);
                    LOGGER.severe(envMismatchMsg);
                    System.err.println(envMismatchMsg);
                    throw new IllegalStateException(envMismatchMsg);
                }
                LOGGER.info("Verified expected database catalog match: '" + catalog + "'");
            } else {
                LOGGER.warning("[WARN] Chưa thiết lập tham số bảo vệ catalog ('db.expected.catalog' hoặc 'DB_EXPECTED_CATALOG'). "
                        + "Khuyến nghị thiết lập DB_EXPECTED_CATALOG=school_library trên Production để ngăn chặn kết nối sai môi trường.");
            }

            if (!thuvien.server.repository.impl.UserRepositoryImpl.isSchemaMigrated(conn)) {
                String fatalMsg = "[SECURITY FATAL] Cơ sở dữ liệu '" + catalog
                        + "' chưa được nâng cấp schema: bảng 'users' thiếu cột 'must_change_password'. "
                        + "Máy chủ từ chối khởi động để ngăn chặn rủi ro bỏ qua kiểm soát mật khẩu tạm thời. "
                        + "Vui lòng chạy migration: ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active;";
                LOGGER.severe(fatalMsg);
                System.err.println(fatalMsg);
                throw new IllegalStateException(fatalMsg);
            }
            LOGGER.info("Database connection pool and schema integrity verified (must_change_password column present in '" + catalog + "').");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            String dbFailMsg = "[DATABASE CONNECTION ERROR] Không thể kết nối cơ sở dữ liệu khi khởi động máy chủ: " + e.getMessage();
            LOGGER.severe(dbFailMsg);
            System.err.println(dbFailMsg);
            throw new IllegalStateException(dbFailMsg, e);
        }

        // 1. Resolve actual LAN IPv4 and configure java.rmi.server.hostname BEFORE exporting remote object
        activeHost = resolveServerHost(configuredHost);
        System.setProperty("java.rmi.server.hostname", activeHost);
        LOGGER.info("Configured java.rmi.server.hostname = " + activeHost);

        try {
            // 2. Locate or create RMI registry on designated port (default 1099)
            try {
                registry = LocateRegistry.createRegistry(registryPort);
                createdRegistry = true;
                LOGGER.info("Created new RMI Registry on port " + registryPort);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(registryPort);
                createdRegistry = false;
                LOGGER.info("Using existing RMI Registry on port " + registryPort);
            }

            // 3. Export remote object on fixed port (default 1100, NEVER dynamic) and bind to registry
            remoteService = new LibraryRemoteServiceImpl(servicePort, new RequestRouter());
            registry.rebind(serviceName, remoteService);
            running = true;
        } catch (Exception ex) {
            cleanupRmiResources();
            throw ex;
        }

        LOGGER.info("==================================================");
        LOGGER.info("LibraryRMIServer started successfully");
        LOGGER.info("RMI Registry port: " + registryPort);
        LOGGER.info("RMI Remote Object port: " + servicePort);
        LOGGER.info("RMI hostname: " + activeHost);
        LOGGER.info("Service: " + serviceName);
        LOGGER.info("Available IPv4 addresses for LAN clients:");
        printLocalIPv4Addresses();
        LOGGER.info("==================================================");

        // Standard user-visible startup banner matching coursework and multi-machine requirements
        System.out.println("RMI Server started");
        System.out.println("RMI Registry port: " + registryPort);
        System.out.println("RMI Remote Object port: " + servicePort);
        System.out.println("RMI hostname: " + activeHost);
        System.out.println("Service: " + serviceName);
        System.out.println("Available IPv4 addresses:");
        printLocalIPv4AddressesStdout();
    }

    public synchronized void stop() {
        if (!running && registry == null && remoteService == null) {
            return;
        }
        cleanupRmiResources();
        running = false;
        LOGGER.info("LibraryRMIServer stopped.");
    }

    private void cleanupRmiResources() {
        if (registry != null) {
            try {
                registry.unbind(serviceName);
            } catch (Exception ignored) {}
            if (createdRegistry) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                } catch (Exception ignored) {}
            }
            registry = null;
        }
        if (remoteService != null) {
            try {
                java.rmi.server.UnicastRemoteObject.unexportObject(remoteService, true);
            } catch (Exception ignored) {}
            remoteService = null;
        }
        createdRegistry = false;
    }

    public boolean isRunning() {
        return running;
    }

    public LibraryRemoteService getRemoteService() {
        return remoteService;
    }

    public int getRegistryPort() {
        return registryPort;
    }

    public int getServicePort() {
        return servicePort;
    }

    public String getActiveHost() {
        return activeHost;
    }

    /**
     * Resolves the best non-loopback site-local IPv4 address for multi-machine LAN connectivity.
     */
    public static String resolveServerHost(String explicitHost) {
        if (explicitHost != null && !explicitHost.trim().isEmpty()
                && !"127.0.0.1".equals(explicitHost.trim())
                && !"localhost".equalsIgnoreCase(explicitHost.trim())
                && !"0.0.0.0".equals(explicitHost.trim())) {
            return explicitHost.trim();
        }
        String sysProp = System.getProperty("java.rmi.server.hostname");
        if (sysProp != null && !sysProp.trim().isEmpty()
                && !"127.0.0.1".equals(sysProp.trim())
                && !"localhost".equalsIgnoreCase(sysProp.trim())
                && !"0.0.0.0".equals(sysProp.trim())) {
            return sysProp.trim();
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress fallback = null;
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                String dName = iface.getDisplayName().toLowerCase();
                String name = iface.getName().toLowerCase();
                // Filter out common virtual or VPN adapters
                if (dName.contains("virtual") || dName.contains("vmware") || dName.contains("vbox")
                        || dName.contains("hyper-v") || dName.contains("wsl") || name.startsWith("veth")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (addr.isSiteLocalAddress()) {
                            return addr.getHostAddress();
                        }
                        if (fallback == null) {
                            fallback = addr;
                        }
                    }
                }
            }
            if (fallback != null) {
                return fallback.getHostAddress();
            }
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error resolving LAN IPv4 address: " + e.getMessage(), e);
        }
        return "127.0.0.1";
    }

    private static void printLocalIPv4Addresses() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        LOGGER.info("  -> " + addr.getHostAddress() + " (" + iface.getDisplayName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not enumerate network interfaces", e);
        }
    }

    private static void printLocalIPv4AddressesStdout() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("- " + addr.getHostAddress() + " (" + iface.getDisplayName() + ")");
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        configureSerializationFilter();

        int registryPort = DEFAULT_REGISTRY_PORT;
        int servicePort = DEFAULT_SERVICE_PORT;
        String serviceName = DEFAULT_SERVICE_NAME;
        String host = null;

        // Load configuration from server.properties
        try (InputStream in = LibraryRMIServer.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String p = props.getProperty("rmi.registry.port");
                if (p == null) p = props.getProperty("server.port");
                if (p != null) registryPort = Integer.parseInt(p.trim());

                String sp = props.getProperty("rmi.service.port");
                if (sp != null) servicePort = Integer.parseInt(sp.trim());

                String s = props.getProperty("rmi.service.name", props.getProperty("server.service"));
                if (s != null && !s.trim().isEmpty()) serviceName = s.trim();

                String h = props.getProperty("rmi.server.host", props.getProperty("server.host"));
                if (h != null && !h.trim().isEmpty()) host = h.trim();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load server.properties: " + e.getMessage());
        }

        // System property overrides
        String sysRegPort = System.getProperty("rmi.registry.port", System.getProperty("server.port"));
        if (sysRegPort != null && !sysRegPort.trim().isEmpty()) {
            try { registryPort = Integer.parseInt(sysRegPort.trim()); } catch (NumberFormatException ignored) {}
        }
        String sysSvcPort = System.getProperty("rmi.service.port");
        if (sysSvcPort != null && !sysSvcPort.trim().isEmpty()) {
            try { servicePort = Integer.parseInt(sysSvcPort.trim()); } catch (NumberFormatException ignored) {}
        }
        String sysService = System.getProperty("rmi.service.name", System.getProperty("server.service"));
        if (sysService != null && !sysService.trim().isEmpty()) {
            serviceName = sysService.trim();
        }
        String sysHost = System.getProperty("rmi.server.host", System.getProperty("server.host"));
        if (sysHost != null && !sysHost.trim().isEmpty()) {
            host = sysHost.trim();
        }

        // CLI args: [registryPort] [servicePort|serviceName] [serviceName] [host]
        if (args != null && args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            try { registryPort = Integer.parseInt(args[0].trim()); } catch (NumberFormatException ignored) {}
        }
        if (args != null && args.length >= 2 && args[1] != null && !args[1].trim().isEmpty()) {
            try {
                servicePort = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                // If second argument is not numeric, treat it as serviceName
                serviceName = args[1].trim();
            }
        }
        if (args != null && args.length >= 3 && args[2] != null && !args[2].trim().isEmpty()) {
            serviceName = args[2].trim();
        }
        if (args != null && args.length >= 4 && args[3] != null && !args[3].trim().isEmpty()) {
            host = args[3].trim();
        }

        LibraryRMIServer server = new LibraryRMIServer(registryPort, servicePort, serviceName, host);
        try {
            server.start();

            // Keep alive
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down RMI Server...");
                server.stop();
                DatabaseManager.getInstance().shutdown();
            }));

            // Sleep main thread
            while (server.isRunning()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start LibraryRMIServer", e);
            System.err.println("[FATAL] Server startup failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
