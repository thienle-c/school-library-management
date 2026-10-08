package thuvien.server.rmi;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
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

    private final int registryPort;
    private final int servicePort;
    private final String serviceName;
    private final String configuredHost;

    private Registry registry;
    private LibraryRemoteServiceImpl remoteService;
    private String activeHost;
    private boolean running = false;

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

        // Initialize database pool early
        try {
            DatabaseManager.getInstance().getConnection().close();
            LOGGER.info("Database connection pool verified.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database not available immediately during RMI server startup: " + e.getMessage());
        }

        // 1. Resolve actual LAN IPv4 and configure java.rmi.server.hostname BEFORE exporting remote object
        activeHost = resolveServerHost(configuredHost);
        System.setProperty("java.rmi.server.hostname", activeHost);
        LOGGER.info("Configured java.rmi.server.hostname = " + activeHost);

        // 2. Locate or create RMI registry on designated port (default 1099)
        try {
            registry = LocateRegistry.createRegistry(registryPort);
            LOGGER.info("Created new RMI Registry on port " + registryPort);
        } catch (Exception e) {
            registry = LocateRegistry.getRegistry(registryPort);
            LOGGER.info("Using existing RMI Registry on port " + registryPort);
        }

        // 3. Export remote object on fixed port (default 1100, NEVER dynamic) and bind to registry
        remoteService = new LibraryRemoteServiceImpl(servicePort, new RequestRouter());
        registry.rebind(serviceName, remoteService);
        running = true;

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
        if (!running) {
            return;
        }
        try {
            if (registry != null) {
                registry.unbind(serviceName);
            }
        } catch (Exception ignored) {}
        try {
            if (remoteService != null) {
                java.rmi.server.UnicastRemoteObject.unexportObject(remoteService, true);
            }
        } catch (Exception ignored) {}
        running = false;
        LOGGER.info("LibraryRMIServer stopped.");
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

                String s = props.getProperty("rmi.service.name");
                if (s != null && !s.trim().isEmpty()) serviceName = s.trim();

                String h = props.getProperty("rmi.server.host");
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
        String sysService = System.getProperty("rmi.service.name");
        if (sysService != null && !sysService.trim().isEmpty()) {
            serviceName = sysService.trim();
        }
        String sysHost = System.getProperty("rmi.server.host");
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
