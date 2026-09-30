package thuvien.server;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.server.database.DatabaseManager;
import thuvien.server.network.LibraryServer;
import thuvien.server.router.RequestRouter;
import thuvien.server.service.impl.AuthServiceImpl;
import thuvien.server.service.impl.BookServiceImpl;
import thuvien.server.service.impl.BorrowServiceImpl;
import thuvien.server.service.impl.DashboardServiceImpl;
import thuvien.server.service.impl.FineServiceImpl;
import thuvien.server.service.impl.ReturnServiceImpl;
import thuvien.server.service.impl.StudentServiceImpl;

/**
 * Bootstrap entry point for the Remote School Library TCP Server.
 */
public class ServerMain {
    private static final Logger LOGGER = Logger.getLogger(ServerMain.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Starting Remote School Library Server...");

        int port = 9999;
        int threadPoolSize = 20;
        int socketTimeout = 30000;

        // Load server configuration
        try (InputStream in = ServerMain.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                port = Integer.parseInt(props.getProperty("server.port", "9999"));
                threadPoolSize = Integer.parseInt(props.getProperty("server.threadPoolSize", "20"));
                socketTimeout = Integer.parseInt(props.getProperty("server.socketTimeout", "30000"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load server.properties, using defaults.", e);
        }

        // Allow system property override if provided
        String sysPort = System.getProperty("server.port");
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(sysPort.trim());
            } catch (NumberFormatException ignored) {}
        }

        // Initialize Services
        AuthServiceImpl authService = new AuthServiceImpl();
        BookServiceImpl bookService = new BookServiceImpl();
        StudentServiceImpl studentService = new StudentServiceImpl();
        BorrowServiceImpl borrowService = new BorrowServiceImpl();
        ReturnServiceImpl returnService = new ReturnServiceImpl();
        FineServiceImpl fineService = new FineServiceImpl();
        DashboardServiceImpl dashboardService = new DashboardServiceImpl();
        thuvien.server.service.impl.ReservationServiceImpl reservationService = new thuvien.server.service.impl.ReservationServiceImpl();
        thuvien.server.service.impl.AuditLogServiceImpl auditLogService = new thuvien.server.service.impl.AuditLogServiceImpl();
        thuvien.server.service.impl.CategoryServiceImpl categoryService = new thuvien.server.service.impl.CategoryServiceImpl();
        thuvien.server.service.impl.AuthorServiceImpl authorService = new thuvien.server.service.impl.AuthorServiceImpl();
        thuvien.server.service.impl.UserServiceImpl userService = new thuvien.server.service.impl.UserServiceImpl();

        // Initialize RequestRouter
        RequestRouter router = new RequestRouter(
                authService,
                bookService,
                studentService,
                borrowService,
                returnService,
                fineService,
                dashboardService,
                reservationService,
                auditLogService,
                categoryService,
                authorService,
                userService
        );

        // Initialize and start TCP Server
        LibraryServer server = new LibraryServer(port, threadPoolSize, socketTimeout, router);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown hook triggered. Stopping server and closing pools...");
            server.stop();
            DatabaseManager.getInstance().shutdown();
        }));

        try {
            server.start();
            printStartupBanner(port);
            LOGGER.info(String.format("Server is online and ready for client connections on 0.0.0.0:%d.", port));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start library server", e);
            System.exit(1);
        }
    }

    private static void printStartupBanner(int port) {
        List<String> ipv4List = getAvailableLanIPv4Addresses();
        System.out.println("Server started");
        System.out.println("Port: " + port);
        System.out.println("Available IPv4 addresses:");
        if (ipv4List.isEmpty()) {
            System.out.println("- 127.0.0.1");
        } else {
            for (String ip : ipv4List) {
                System.out.println("- " + ip);
            }
        }
        System.out.flush();
    }

    private static List<String> getAvailableLanIPv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress addr = inetAddresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            addresses.add(addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not enumerate network interfaces", e);
        }
        return addresses;
    }
}
