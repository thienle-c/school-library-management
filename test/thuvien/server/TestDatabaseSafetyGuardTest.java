package thuvien.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests verifying TestDatabaseSafetyGuard behavior (Phase 1.5 - Work Item B.6):
 * 1. Database đúng: school_library_test -> allowed
 * 2. Database nghiệp vụ: school_library -> strictly blocked
 * 3. Thiếu cấu hình kiểm thử (null connection, closed connection, null catalog, empty catalog) -> safely blocked
 * 4. Cấu hình URL và catalog không khớp -> blocked
 * 5. System property boolean set to true with school_library catalog -> strictly blocked (boolean cannot bypass catalog)
 */
public class TestDatabaseSafetyGuardTest {

    private Connection createMockConnection(final String catalog, final String url, final boolean closed) {
        final DatabaseMetaData meta = (DatabaseMetaData) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("getURL".equals(method.getName())) {
                            return url;
                        }
                        return null;
                    }
                }
        );

        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("isClosed".equals(method.getName())) {
                            return closed;
                        }
                        if ("getCatalog".equals(method.getName())) {
                            return catalog;
                        }
                        if ("getMetaData".equals(method.getName())) {
                            return meta;
                        }
                        return null;
                    }
                }
        );
    }

    @Test
    public void testCorrectTestDatabaseAllowed() {
        Connection conn = createMockConnection(
                "school_library_test",
                "jdbc:mysql://localhost:3306/school_library_test?useSSL=false",
                false
        );
        boolean safe = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
        Assert.assertTrue("school_library_test with matching URL must be recognized as safe", safe);
    }

    @Test
    public void testProductionDatabaseStrictlyBlocked() {
        Connection conn = createMockConnection(
                "school_library",
                "jdbc:mysql://localhost:3306/school_library",
                false
        );
        boolean safe = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
        Assert.assertFalse("school_library MUST BE STRICTLY BLOCKED from write operations", safe);
    }

    @Test
    public void testSystemPropertyCannotBypassProductionBlock() {
        System.setProperty("test.isolated.db", "true");
        System.setProperty("test.database.enabled", "true");
        try {
            Connection conn = createMockConnection(
                    "school_library",
                    "jdbc:mysql://localhost:3306/school_library",
                    false
            );
            boolean safe = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
            Assert.assertFalse("System property boolean MUST NEVER bypass production database protection", safe);
        } finally {
            System.clearProperty("test.isolated.db");
            System.clearProperty("test.database.enabled");
        }
    }

    @Test
    public void testMissingOrInvalidConnectionMetadataBlocked() {
        // Null connection
        Assert.assertFalse("Null connection must fail safe", TestDatabaseSafetyGuard.isSafeTestDatabase(null));

        // Closed connection
        Connection closedConn = createMockConnection("school_library_test", "jdbc:mysql://localhost:3306/school_library_test", true);
        Assert.assertFalse("Closed connection must fail safe", TestDatabaseSafetyGuard.isSafeTestDatabase(closedConn));

        // Null catalog
        Connection nullCatalogConn = createMockConnection(null, "jdbc:mysql://localhost:3306/school_library_test", false);
        Assert.assertFalse("Null catalog must fail safe", TestDatabaseSafetyGuard.isSafeTestDatabase(nullCatalogConn));

        // Empty catalog
        Connection emptyCatalogConn = createMockConnection("   ", "jdbc:mysql://localhost:3306/school_library_test", false);
        Assert.assertFalse("Empty catalog must fail safe", TestDatabaseSafetyGuard.isSafeTestDatabase(emptyCatalogConn));
    }

    @Test
    public void testUrlAndCatalogMismatchBlocked() {
        // Catalog says test, but URL points to production school_library
        Connection conn = createMockConnection(
                "school_library_test",
                "jdbc:mysql://localhost:3306/school_library",
                false
        );
        boolean safe = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
        Assert.assertFalse("Mismatch where URL points to production DB must be rejected", safe);
    }

    @Test
    public void testUrlDifferentDbNameMismatchBlocked() {
        // Catalog says school_library_test, but URL points to other_db
        Connection conn = createMockConnection(
                "school_library_test",
                "jdbc:mysql://localhost:3306/other_test_db",
                false
        );
        boolean safe = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
        Assert.assertFalse("Mismatch between URL database and catalog must be rejected", safe);
    }
}
