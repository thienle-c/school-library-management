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
 * Creates/locates the RMI Registry on port 1099, instantiates LibraryRemoteServiceImpl,
 * and binds it as "LibraryRemoteService".
 */
public class LibraryRMIServer {
    private static final Logger LOGGER = Logger.getLogger(LibraryRMIServer.class.getName());

    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final String DEFAULT_SERVICE_NAME = "LibraryRemoteService";

    private final int port;
    private final String serviceName;
    private Registry registry;
    private LibraryRemoteServiceImpl remoteService;
    private boolean running = false;

    public LibraryRMIServer() {
        this(DEFAULT_REGISTRY_PORT, DEFAULT_SERVICE_NAME);
    }

    public LibraryRMIServer(int port, String serviceName) {
        this.port = port;
        this.serviceName = serviceName;
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

        // Locate or create RMI registry on designated port
        try {
            registry = LocateRegistry.createRegistry(port);
            LOGGER.info("Created new RMI Registry on port " + port);
        } catch (Exception e) {
            registry = LocateRegistry.getRegistry(port);
            LOGGER.info("Using existing RMI Registry on port " + port);
        }

        // Export and bind remote object
        remoteService = new LibraryRemoteServiceImpl(0, new RequestRouter());
        registry.rebind(serviceName, remoteService);
        running = true;

        LOGGER.info("==================================================");
        LOGGER.info("LibraryRMIServer started successfully");
        LOGGER.info("Bound service: " + serviceName);
        LOGGER.info("RMI Registry Port: " + port);
        LOGGER.info("Available IPv4 addresses for LAN clients:");
        printLocalIPv4Addresses();
        LOGGER.info("==================================================");

        // Standard user-visible startup banner matching course expectations
        System.out.println("RMI Server started");
        System.out.println("Port: " + port);
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
                        System.out.println("- " + addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        int port = DEFAULT_REGISTRY_PORT;
        String serviceName = DEFAULT_SERVICE_NAME;

        // Load configuration from server.properties
        try (InputStream in = LibraryRMIServer.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String p = props.getProperty("rmi.registry.port");
                if (p == null) p = props.getProperty("server.port");
                if (p != null) port = Integer.parseInt(p.trim());

                String s = props.getProperty("rmi.service.name");
                if (s != null && !s.trim().isEmpty()) serviceName = s.trim();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load server.properties: " + e.getMessage());
        }

        // System property overrides
        String sysPort = System.getProperty("rmi.registry.port", System.getProperty("server.port"));
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try { port = Integer.parseInt(sysPort.trim()); } catch (NumberFormatException ignored) {}
        }
        String sysService = System.getProperty("rmi.service.name");
        if (sysService != null && !sysService.trim().isEmpty()) {
            serviceName = sysService.trim();
        }

        // CLI args: [port] [serviceName]
        if (args != null && args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            try { port = Integer.parseInt(args[0].trim()); } catch (NumberFormatException ignored) {}
        }
        if (args != null && args.length >= 2 && args[1] != null && !args[1].trim().isEmpty()) {
            serviceName = args[1].trim();
        }

        LibraryRMIServer server = new LibraryRMIServer(port, serviceName);
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
