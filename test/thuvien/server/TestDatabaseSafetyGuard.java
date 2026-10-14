package thuvien.server;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Strict Safety Guard for Integration Tests (Phase 1.5).
 * Ensures tests never perform write operations on the business database 'school_library'.
 *
 * Rules enforced:
 * 1. Inspects the actual JDBC catalog from a live connection.
 * 2. Prohibits any system property boolean alone from proving database safety.
 * 3. Enforces catalog and URL consistency.
 * 4. Fails safe (rejects) if database identity cannot be positively verified.
 */
public final class TestDatabaseSafetyGuard {

    private static final Logger LOGGER = Logger.getLogger(TestDatabaseSafetyGuard.class.getName());
    public static final String PRODUCTION_DB_NAME = "school_library";
    public static final String TEST_DB_NAME = "school_library_test";

    private TestDatabaseSafetyGuard() {
    }

    /**
     * Inspects a connection and verifies that it is safely targeting an isolated test database.
     *
     * @param conn live JDBC connection
     * @return true if and only if the connection is safely connected to a test database and not the production database.
     */
    public static boolean isSafeTestDatabase(Connection conn) {
        if (conn == null) {
            LOGGER.warning("[SAFETY GUARD] Connection is null. Rejecting write operations.");
            return false;
        }
        try {
            if (conn.isClosed()) {
                LOGGER.warning("[SAFETY GUARD] Connection is closed. Rejecting write operations.");
                return false;
            }

            String catalog = conn.getCatalog();
            if (catalog == null || catalog.trim().isEmpty()) {
                LOGGER.warning("[SAFETY GUARD] Connection catalog is null or empty. Rejecting write operations.");
                return false;
            }
            catalog = catalog.trim().toLowerCase();

            // 1. Strict guard: If catalog is the coursework/production DB, REJECT unconditionally.
            // No system property boolean is allowed to override or bypass this check!
            if (PRODUCTION_DB_NAME.equalsIgnoreCase(catalog)) {
                LOGGER.severe("[SAFETY GUARD] ABORT: Target database is the production/coursework database '"
                        + catalog + "'! Write operations strictly forbidden.");
                return false;
            }

            // 2. Catalog must be a recognized test database
            boolean catalogIsTest = TEST_DB_NAME.equalsIgnoreCase(catalog)
                    || (catalog.endsWith("_test") && !catalog.contains(PRODUCTION_DB_NAME + "_production"));
            if (!catalogIsTest) {
                LOGGER.warning("[SAFETY GUARD] Catalog '" + catalog + "' is not recognized as an isolated test database.");
                return false;
            }

            // 3. Verify URL does not point to production database or conflict with catalog
            DatabaseMetaData meta = conn.getMetaData();
            String url = (meta != null) ? meta.getURL() : null;
            if (url != null && !url.trim().isEmpty()) {
                String cleanUrl = url.trim().toLowerCase();
                int qIdx = cleanUrl.indexOf('?');
                String path = (qIdx > 0) ? cleanUrl.substring(0, qIdx) : cleanUrl;

                // If URL path explicitly points to school_library, reject!
                if (path.endsWith("/" + PRODUCTION_DB_NAME)) {
                    LOGGER.severe("[SAFETY GUARD] URL path ends with production DB '" + PRODUCTION_DB_NAME
                            + "' while catalog is '" + catalog + "'. Mismatch detected! Rejecting.");
                    return false;
                }

                // If URL path specifies a different database name than catalog, reject mismatch!
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    String urlDb = path.substring(lastSlash + 1);
                    if (!urlDb.isEmpty() && !urlDb.equalsIgnoreCase(catalog)) {
                        LOGGER.severe("[SAFETY GUARD] URL database '" + urlDb + "' does not match catalog '"
                                + catalog + "'. Rejecting mismatch.");
                        return false;
                    }
                }
            } else {
                // If URL metadata cannot be determined, fail-safe
                LOGGER.warning("[SAFETY GUARD] Connection URL metadata is null or empty. Rejecting.");
                return false;
            }

            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "[SAFETY GUARD] Failed to inspect connection metadata: " + e.getMessage(), e);
            return false;
        }
    }
}
