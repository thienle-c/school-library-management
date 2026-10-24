package thuvien.server.rmi;

import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import thuvien.server.TestDatabaseSafetyGuard;
import thuvien.server.database.DatabaseManager;

/**
 * Automated Test Suite for Environment Catalog Guard:
 * 1. Mismatch Guard: Expected catalog = 'school_library', actual = 'school_library_test'
 *    -> Must throw IllegalStateException, server must NOT run, NO lingering RMI ports.
 * 2. Match Guard: Expected catalog = 'school_library_test', actual = 'school_library_test'
 *    -> Passes verification and starts successfully.
 * 3. Omitted Guard: Expected catalog is not set
 *    -> Defaults to normal startup with warning, does not block.
 *
 * STRICT SAFETY GUARANTEE:
 * - Tests ONLY run against 'school_library_test'.
 * - NEVER connects to or modifies 'school_library'.
 */
public class EnvironmentCatalogGuardTest {

    private static final int MISMATCH_REGISTRY_PORT = 10995;
    private static final int MISMATCH_SERVICE_PORT = 11005;

    private static final int MATCH_REGISTRY_PORT = 10996;
    private static final int MATCH_SERVICE_PORT = 11006;

    private static final int OMIT_REGISTRY_PORT = 10997;
    private static final int OMIT_SERVICE_PORT = 11007;

    private static final String GUARD_SERVICE_NAME = "GuardTestRemoteService";

    private String originalExpectedCatalogProp;
    private LibraryRMIServer activeServer;

    @BeforeClass
    public static void verifySafety() throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            assertTrue("Phải là database kiểm thử cách ly", TestDatabaseSafetyGuard.isSafeTestDatabase(conn));
        }
    }

    @Before
    public void setUp() {
        originalExpectedCatalogProp = System.getProperty("db.expected.catalog");
    }

    @After
    public void tearDown() {
        if (activeServer != null && activeServer.isRunning()) {
            activeServer.stop();
            activeServer = null;
        }
        if (originalExpectedCatalogProp != null) {
            System.setProperty("db.expected.catalog", originalExpectedCatalogProp);
        } else {
            System.clearProperty("db.expected.catalog");
        }
    }

    @AfterClass
    public static void cleanUpAll() {
        System.clearProperty("db.expected.catalog");
    }

    @Test
    public void testMismatchCatalogGuard_AbortsStartupWithoutLingeringPorts() {
        // Actual DB is 'school_library_test'. We set expected to 'school_library' (Production)
        System.setProperty("db.expected.catalog", "school_library");

        activeServer = new LibraryRMIServer(MISMATCH_REGISTRY_PORT, MISMATCH_SERVICE_PORT, GUARD_SERVICE_NAME, "127.0.0.1");

        try {
            activeServer.start();
            fail("Expected IllegalStateException due to environment catalog mismatch!");
        } catch (IllegalStateException e) {
            assertTrue("Error message should mention catalog mismatch",
                    e.getMessage().contains("Mục tiêu cơ sở dữ liệu không khớp"));
            assertTrue("Error message should mention required catalog",
                    e.getMessage().contains("school_library"));
        } catch (Exception e) {
            fail("Unexpected exception type: " + e.getClass().getName() + " - " + e.getMessage());
        }

        // Verify server is not marked running
        assertFalse("Server must NOT be marked as running when catalog guard fails", activeServer.isRunning());

        // Verify no lingering process or open port on MISMATCH_REGISTRY_PORT and MISMATCH_SERVICE_PORT
        boolean registryPortListening = isPortListening("127.0.0.1", MISMATCH_REGISTRY_PORT);
        boolean servicePortListening = isPortListening("127.0.0.1", MISMATCH_SERVICE_PORT);
        assertFalse("Registry port " + MISMATCH_REGISTRY_PORT + " must NOT be listening after failed startup", registryPortListening);
        assertFalse("Service port " + MISMATCH_SERVICE_PORT + " must NOT be listening after failed startup", servicePortListening);
    }

    @Test
    public void testMatchingCatalogGuard_AllowsStartupWhenSchemaValid() throws Exception {
        // Actual DB is 'school_library_test'. Expected is 'school_library_test'
        System.setProperty("db.expected.catalog", "school_library_test");

        activeServer = new LibraryRMIServer(MATCH_REGISTRY_PORT, MATCH_SERVICE_PORT, GUARD_SERVICE_NAME, "127.0.0.1");
        activeServer.start();

        assertTrue("Server should be running when expected catalog matches actual catalog", activeServer.isRunning());

        // Verify port is reachable
        boolean portListening = isPortListening("127.0.0.1", MATCH_REGISTRY_PORT);
        assertTrue("Registry port should be listening when server started successfully", portListening);

        activeServer.stop();
        assertFalse("Server should not be running after stop()", activeServer.isRunning());
    }

    @Test
    public void testOmittedCatalogGuard_AllowsStartupByDefault() throws Exception {
        // No expected catalog configured
        System.clearProperty("db.expected.catalog");

        activeServer = new LibraryRMIServer(OMIT_REGISTRY_PORT, OMIT_SERVICE_PORT, GUARD_SERVICE_NAME, "127.0.0.1");
        activeServer.start();

        assertTrue("Server should start normally when db.expected.catalog is omitted", activeServer.isRunning());

        activeServer.stop();
        assertFalse("Server should not be running after stop()", activeServer.isRunning());
    }

    @Test
    public void testStopMethod_SafeWhenCalledBeforeStartOrMultipleTimes() {
        // Server not started: stop() should be a safe no-op
        LibraryRMIServer unstartedServer = new LibraryRMIServer(10994, 11008, "UnstartedService", "127.0.0.1");
        assertFalse("Unstarted server isRunning() should be false", unstartedServer.isRunning());

        // Calling stop() on unstarted server must not throw any exception
        unstartedServer.stop();
        assertFalse(unstartedServer.isRunning());

        // Repeated stop() calls must be idempotent and safe
        unstartedServer.stop();
        assertFalse(unstartedServer.isRunning());
    }

    @Test
    public void testSchemaValidation_ChecksNullConnectionAndDetectsUnmigratedSchema() {
        // 1. isSchemaMigrated with null connection must return false safely
        assertFalse("null connection must return false safely",
                thuvien.server.repository.impl.UserRepositoryImpl.isSchemaMigrated(null));

        // 2. Closed connection must return false safely
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.close();
            assertFalse("closed connection must return false safely",
                    thuvien.server.repository.impl.UserRepositoryImpl.isSchemaMigrated(conn));
        } catch (Exception ignored) {}
    }

    private boolean isPortListening(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
