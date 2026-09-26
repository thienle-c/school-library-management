package thuvien.server;

import java.io.InputStream;
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

        int port = 8888;
        int threadPoolSize = 20;
        int socketTimeout = 30000;

        // Load server configuration
        try (InputStream in = ServerMain.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                port = Integer.parseInt(props.getProperty("server.port", "8888"));
                threadPoolSize = Integer.parseInt(props.getProperty("server.threadPoolSize", "20"));
                socketTimeout = Integer.parseInt(props.getProperty("server.socketTimeout", "30000"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load server.properties, using defaults.", e);
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
            LOGGER.info(String.format("Server is online and ready for client connections on port %d.", port));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start library server", e);
            System.exit(1);
        }
    }
}
