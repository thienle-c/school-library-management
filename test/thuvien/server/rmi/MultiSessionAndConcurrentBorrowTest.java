package thuvien.server.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.RMIClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.database.DatabaseManager;
import thuvien.server.router.RequestRouter;

/**
 * Phase 1.12 Automated Verification Test Suite:
 * - Part C1: Multi-session support per account, independent session tokens,
 *   role-based permission enforcement, and selective logout.
 * - Part C2: Concurrent borrow competition between two independent clients over RMI,
 *   atomic inventory decrement, negative inventory prevention, and duplicate borrow rejection.
 *
 * SAFETY GUARANTEES:
 * - Mutating tests execute ONLY against isolated test database ('school_library_test').
 * - Protected by TestDatabaseSafetyGuard.
 * - Test entities are dynamically created and cleaned up by explicit primary keys.
 */
public class MultiSessionAndConcurrentBorrowTest {

    private static final int TEST_REGISTRY_PORT = 10998;
    private static final int TEST_SERVICE_PORT = 11002;
    private static final String TEST_SERVICE_NAME = "MultiSessionTestRemoteService";

    private static Registry testRegistry;
    private static LibraryRemoteServiceImpl exportedService;
    private static boolean dbAvailable = false;
    private static boolean isIsolatedTestDb = false;

    // Track test-created IDs for cleanup
    private final List<Long> createdBookIds = new ArrayList<>();
    private final List<Long> createdStudentIds = new ArrayList<>();
    private final List<Long> createdBorrowRecordIds = new ArrayList<>();

    @BeforeClass
    public static void setUpRMIAndDatabase() throws Exception {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
            if (dbAvailable) {
                isIsolatedTestDb = thuvien.server.TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
            }
        } catch (Exception e) {
            dbAvailable = false;
        }

        // Initialize user passwords in test database if running against school_library_test
        if (dbAvailable && isIsolatedTestDb) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                try (PreparedStatement psAdmin = conn.prepareStatement(
                        "UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'admin'")) {
                    psAdmin.setString(1, thuvien.server.security.PasswordHasher.hash("admin123"));
                    psAdmin.executeUpdate();
                }
                try (PreparedStatement psLib = conn.prepareStatement(
                        "UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'librarian1'")) {
                    psLib.setString(1, thuvien.server.security.PasswordHasher.hash("lib123"));
                    psLib.executeUpdate();
                }
                try (PreparedStatement psStu = conn.prepareStatement(
                        "UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'student1'")) {
                    psStu.setString(1, thuvien.server.security.PasswordHasher.hash("student123"));
                    psStu.executeUpdate();
                }
            }
        }

        // Start dedicated RMI registry on isolated port 10998
        try {
            testRegistry = LocateRegistry.createRegistry(TEST_REGISTRY_PORT);
        } catch (Exception e) {
            testRegistry = LocateRegistry.getRegistry(TEST_REGISTRY_PORT);
        }

        exportedService = new LibraryRemoteServiceImpl(TEST_SERVICE_PORT, new RequestRouter());
        testRegistry.rebind(TEST_SERVICE_NAME, exportedService);
    }

    @AfterClass
    public static void tearDownRMI() {
        try {
            if (testRegistry != null) {
                testRegistry.unbind(TEST_SERVICE_NAME);
            }
        } catch (Exception ignored) {}
        try {
            if (exportedService != null) {
                java.rmi.server.UnicastRemoteObject.unexportObject(exportedService, true);
            }
        } catch (Exception ignored) {}
    }

    @Before
    public void checkIsolation() {
        createdBookIds.clear();
        createdStudentIds.clear();
        createdBorrowRecordIds.clear();
    }

    @After
    public void tearDown() {
        if (!dbAvailable || !isIsolatedTestDb) {
            return;
        }
        cleanupTestCreatedRecords();
    }

    private void cleanupTestCreatedRecords() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            for (Long borrowId : createdBorrowRecordIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fines WHERE borrow_record_id = ?")) {
                    ps.setLong(1, borrowId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM borrow_records WHERE id = ?")) {
                    ps.setLong(1, borrowId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (Long bookId : createdBookIds) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM fines WHERE borrow_record_id IN (SELECT id FROM borrow_records WHERE book_id = ?)")) {
                    ps.setLong(1, bookId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM borrow_records WHERE book_id = ?")) {
                    ps.setLong(1, bookId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE id = ?")) {
                    ps.setLong(1, bookId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (Long studentId : createdStudentIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                    ps.setLong(1, studentId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        createdBookIds.clear();
        createdStudentIds.clear();
        createdBorrowRecordIds.clear();
    }

    private Long createTestBook(Connection conn, String title, int totalCopies, int availableCopies) throws Exception {
        String isbn = "T12" + (System.currentTimeMillis() % 100000000L) + (int)(Math.random() * 900 + 100);
        String sql = "INSERT INTO books (isbn, title, category_id, total_copies, available_copies, status) " +
                     "VALUES (?, ?, 1, ?, ?, 'AVAILABLE')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, isbn);
            ps.setString(2, title);
            ps.setInt(3, totalCopies);
            ps.setInt(4, availableCopies);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    createdBookIds.add(id);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Failed to create test book");
    }

    private Long createTestStudent(Connection conn, String name, int maxLimit) throws Exception {
        String code = "ST12" + (System.currentTimeMillis() % 100000000L) + (int)(Math.random() * 900 + 100);
        String sql = "INSERT INTO students (student_code, full_name, class_name, max_borrow_limit, current_borrow_count, status) " +
                     "VALUES (?, ?, 'P112_CLASS', ?, 0, 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setInt(3, maxLimit);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    createdStudentIds.add(id);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Failed to create test student");
    }

    // =========================================================================
    // PART C1: MULTI-SESSION PER ACCOUNT AND AUTHORIZATION TESTS
    // =========================================================================

    /**
     * C1.1 - C1.7:
     * - Two independent client instances log in with the same account 'librarian1'.
     * - Each receives a unique, valid session token.
     * - Both clients can independently execute authorized operations.
     * - Logging out Client A revokes session A without affecting session B.
     * - Client B continues to operate successfully.
     */
    @Test
    public void testC1_MultiSessionLoginAndIndependentLogout() throws Exception {
        Assume.assumeTrue("Test database required", dbAvailable && isIsolatedTestDb);

        // 1. Client A logs in
        RMIClient clientA = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession sessionA = new ClientSession();
        clientA.setSession(sessionA);
        ClientAuthController authA = new ClientAuthController(clientA, sessionA);

        UserSessionDTO userSessionA = authA.login("librarian1", "lib123");
        Assert.assertNotNull("Client A login must succeed", userSessionA);
        Assert.assertNotNull("Session A token must not be null", userSessionA.getToken());
        Assert.assertEquals("librarian1", userSessionA.getUsername());

        // 2. Client B logs in with the SAME account 'librarian1'
        RMIClient clientB = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession sessionB = new ClientSession();
        clientB.setSession(sessionB);
        ClientAuthController authB = new ClientAuthController(clientB, sessionB);

        UserSessionDTO userSessionB = authB.login("librarian1", "lib123");
        Assert.assertNotNull("Client B login must succeed", userSessionB);
        Assert.assertNotNull("Session B token must not be null", userSessionB.getToken());
        Assert.assertEquals("librarian1", userSessionB.getUsername());

        // 3. Confirm both sessions are distinct and non-empty
        Assert.assertNotEquals("Each login must receive a distinct session token",
                userSessionA.getToken(), userSessionB.getToken());

        // 4. Send an authorized request from each client; both must succeed
        Request reqA = new Request(Action.SEARCH_BOOKS, new BookSearchCriteriaDTO());
        Response respA = clientA.send(reqA);
        Assert.assertEquals("Client A request must succeed", StatusCode.OK, respA.getStatusCode());

        Request reqB = new Request(Action.SEARCH_BOOKS, new BookSearchCriteriaDTO());
        Response respB = clientB.send(reqB);
        Assert.assertEquals("Client B request must succeed", StatusCode.OK, respB.getStatusCode());

        // 5. Logout Client A
        authA.logout();
        Assert.assertFalse("Client A session must be cleared locally", sessionA.isLoggedIn());

        // 6. Verify Session A is rejected on server
        Request revokedReqA = new Request(Action.SEARCH_BOOKS, new BookSearchCriteriaDTO());
        revokedReqA.setToken(userSessionA.getToken());
        Response revokedRespA = clientA.send(revokedReqA);
        Assert.assertEquals("Revoked Session A must be rejected with 401 UNAUTHORIZED",
                StatusCode.UNAUTHORIZED, revokedRespA.getStatusCode());

        // 7. Verify Session B remains fully valid and operational on server
        Request reqBAfterA = new Request(Action.SEARCH_BOOKS, new BookSearchCriteriaDTO());
        Response respBAfterA = clientB.send(reqBAfterA);
        Assert.assertEquals("Session B must remain valid after Session A logs out",
                StatusCode.OK, respBAfterA.getStatusCode());

        // Clean up Client B
        authB.logout();
    }

    /**
     * C1.8: STUDENT role cannot call BORROW_BOOK directly (Server-enforced RBAC).
     */
    @Test
    public void testC1_StudentDirectBorrowActionForbidden() throws Exception {
        Assume.assumeTrue("Test database required", dbAvailable && isIsolatedTestDb);

        RMIClient studentClient = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession studentSession = new ClientSession();
        studentClient.setSession(studentSession);
        ClientAuthController studentAuth = new ClientAuthController(studentClient, studentSession);

        UserSessionDTO sessionDto = studentAuth.login("student1", "student123");
        Assert.assertNotNull(sessionDto);

        // Attempt to call staff-only Action.BORROW_BOOK directly
        Request borrowReq = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(1L, 1L));
        Response borrowResp = studentClient.send(borrowReq);

        Assert.assertEquals("STUDENT role must be denied access to BORROW_BOOK with 403 FORBIDDEN",
                StatusCode.FORBIDDEN, borrowResp.getStatusCode());

        studentAuth.logout();
    }

    /**
     * C1.9: Forged or invalid session token cannot be used to perform authorized operations.
     */
    @Test
    public void testC1_InvalidOrForgedSessionTokenRejected() throws Exception {
        RMIClient client = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);

        Request forgedReq = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(1L, 1L));
        forgedReq.setToken("forged-unregistered-token-99999");
        Response resp = client.send(forgedReq);

        Assert.assertEquals("Forged session token must be rejected with 401 UNAUTHORIZED",
                StatusCode.UNAUTHORIZED, resp.getStatusCode());
    }

    // =========================================================================
    // PART C2: CONCURRENT BORROW COMPETITION OVER RMI
    // =========================================================================

    /**
     * C2.1 - C2.5:
     * - Two independent clients with LIBRARIAN permission.
     * - Compete simultaneously for the single available copy of a book.
     * - At most ONE request succeeds (200/201).
     * - The competing request fails with CONFLICT (409) / BookUnavailableException.
     * - Final available_copies is exactly 0 and never negative.
     * - Exactly 1 borrow record is created.
     */
    @Test
    public void testC2_ConcurrentBorrowSingleCopyCompetition() throws Exception {
        Assume.assumeTrue("Test database required", dbAvailable && isIsolatedTestDb);

        final Long targetBookId;
        final Long studentA;
        final Long studentB;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            targetBookId = createTestBook(conn, "P112 Concurrent Single Copy Book", 1, 1);
            studentA = createTestStudent(conn, "P112 Student Alpha", 5);
            studentB = createTestStudent(conn, "P112 Student Beta", 5);
        }

        // Two independent clients logged in with librarian1
        RMIClient clientA = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession csA = new ClientSession();
        clientA.setSession(csA);
        ClientAuthController authA = new ClientAuthController(clientA, csA);
        authA.login("librarian1", "lib123");

        RMIClient clientB = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession csB = new ClientSession();
        clientB.setSession(csB);
        ClientAuthController authB = new ClientAuthController(clientB, csB);
        authB.login("librarian1", "lib123");

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger conflictCount = new AtomicInteger(0);
        final AtomicInteger otherErrorCount = new AtomicInteger(0);
        final AtomicReference<Response> respARef = new AtomicReference<>();
        final AtomicReference<Response> respBRef = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Client A borrows targetBookId for Student Alpha
        executor.execute(() -> {
            try {
                startLatch.await();
                Request req = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentA, targetBookId, 14, "Client A Loan"));
                Response resp = clientA.send(req);
                respARef.set(resp);
                if (resp.getStatusCode() == StatusCode.CREATED || resp.getStatusCode() == StatusCode.OK) {
                    successCount.incrementAndGet();
                    if (resp.getData() instanceof BorrowRecordDTO) {
                        synchronized (createdBorrowRecordIds) {
                            createdBorrowRecordIds.add(((BorrowRecordDTO) resp.getData()).getId());
                        }
                    }
                } else if (resp.getStatusCode() == StatusCode.CONFLICT) {
                    conflictCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Client B borrows the exact same targetBookId for Student Beta
        executor.execute(() -> {
            try {
                startLatch.await();
                Request req = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentB, targetBookId, 14, "Client B Loan"));
                Response resp = clientB.send(req);
                respBRef.set(resp);
                if (resp.getStatusCode() == StatusCode.CREATED || resp.getStatusCode() == StatusCode.OK) {
                    successCount.incrementAndGet();
                    if (resp.getData() instanceof BorrowRecordDTO) {
                        synchronized (createdBorrowRecordIds) {
                            createdBorrowRecordIds.add(((BorrowRecordDTO) resp.getData()).getId());
                        }
                    }
                } else if (resp.getStatusCode() == StatusCode.CONFLICT) {
                    conflictCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Fire both simultaneous requests
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        authA.logout();
        authB.logout();

        Assert.assertTrue("Concurrent borrow operations timed out", completed);
        Assert.assertEquals("Exactly ONE client must succeed in borrowing the single available copy", 1, successCount.get());
        Assert.assertEquals("The competing client must receive 409 CONFLICT", 1, conflictCount.get());
        Assert.assertEquals("No unexpected errors should occur", 0, otherErrorCount.get());

        // Invariant verification on MySQL: available_copies must be exactly 0, never negative
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
                ps.setLong(1, targetBookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    int finalCopies = rs.getInt("available_copies");
                    Assert.assertEquals("Final available copies must be exactly 0", 0, finalCopies);
                    Assert.assertTrue("Available copies must never be negative", finalCopies >= 0);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM borrow_records WHERE book_id = ?")) {
                ps.setLong(1, targetBookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    int recordCount = rs.getInt(1);
                    Assert.assertEquals("Exactly 1 borrow record must be created", 1, recordCount);
                }
            }
        }
    }

    /**
     * C2.6: When 2 copies are available, both concurrent clients can succeed.
     */
    @Test
    public void testC2_ConcurrentBorrowTwoCopiesBothSucceed() throws Exception {
        Assume.assumeTrue("Test database required", dbAvailable && isIsolatedTestDb);

        final Long targetBookId;
        final Long studentA;
        final Long studentB;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            targetBookId = createTestBook(conn, "P112 Concurrent Dual Copy Book", 2, 2);
            studentA = createTestStudent(conn, "P112 Student Dual A", 5);
            studentB = createTestStudent(conn, "P112 Student Dual B", 5);
        }

        RMIClient clientA = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession csA = new ClientSession();
        clientA.setSession(csA);
        ClientAuthController authA = new ClientAuthController(clientA, csA);
        authA.login("librarian1", "lib123");

        RMIClient clientB = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession csB = new ClientSession();
        clientB.setSession(csB);
        ClientAuthController authB = new ClientAuthController(clientB, csB);
        authB.login("librarian1", "lib123");

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);
        final AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(() -> {
            try {
                startLatch.await();
                Request req = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentA, targetBookId, 14, "Dual A"));
                Response resp = clientA.send(req);
                if (resp.isSuccess()) {
                    successCount.incrementAndGet();
                    if (resp.getData() instanceof BorrowRecordDTO) {
                        synchronized (createdBorrowRecordIds) {
                            createdBorrowRecordIds.add(((BorrowRecordDTO) resp.getData()).getId());
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.execute(() -> {
            try {
                startLatch.await();
                Request req = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentB, targetBookId, 14, "Dual B"));
                Response resp = clientB.send(req);
                if (resp.isSuccess()) {
                    successCount.incrementAndGet();
                    if (resp.getData() instanceof BorrowRecordDTO) {
                        synchronized (createdBorrowRecordIds) {
                            createdBorrowRecordIds.add(((BorrowRecordDTO) resp.getData()).getId());
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        authA.logout();
        authB.logout();

        Assert.assertTrue("Concurrent dual borrow operations timed out", completed);
        Assert.assertEquals("Both clients must succeed when 2 copies are available", 2, successCount.get());

        // Invariant: available_copies must be 0, borrow_records must be 2
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
                ps.setLong(1, targetBookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(0, rs.getInt("available_copies"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM borrow_records WHERE book_id = ?")) {
                ps.setLong(1, targetBookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(2, rs.getInt(1));
                }
            }
        }
    }

    /**
     * Duplicate active borrow rejection:
     * A student cannot borrow the same book twice concurrently while the first borrow is ACTIVE.
     */
    @Test
    public void testC2_DuplicateActiveBorrowRejected() throws Exception {
        Assume.assumeTrue("Test database required", dbAvailable && isIsolatedTestDb);

        final Long targetBookId;
        final Long studentId;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            targetBookId = createTestBook(conn, "P112 Duplicate Prevention Book", 5, 5);
            studentId = createTestStudent(conn, "P112 Duplicate Student", 5);
        }

        RMIClient client = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        ClientSession cs = new ClientSession();
        client.setSession(cs);
        ClientAuthController auth = new ClientAuthController(client, cs);
        auth.login("librarian1", "lib123");

        // First borrow: must succeed
        Request firstReq = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentId, targetBookId, 14, "First Loan"));
        Response firstResp = client.send(firstReq);
        Assert.assertTrue("First borrow must succeed: " + firstResp.getMessage(), firstResp.isSuccess());
        if (firstResp.getData() instanceof BorrowRecordDTO) {
            createdBorrowRecordIds.add(((BorrowRecordDTO) firstResp.getData()).getId());
        }

        // Duplicate borrow for SAME student and SAME book while first is ACTIVE: must fail
        Request secondReq = new Request(Action.BORROW_BOOK, new BorrowRequestDTO(studentId, targetBookId, 14, "Duplicate Loan"));
        Response secondResp = client.send(secondReq);
        Assert.assertTrue("Duplicate active loan must be rejected with 400 BAD_REQUEST or 409 CONFLICT: " + secondResp.getStatusCode(),
                secondResp.getStatusCode() == StatusCode.BAD_REQUEST || secondResp.getStatusCode() == StatusCode.CONFLICT);
        Assert.assertTrue("Message must indicate duplicate borrow",
                secondResp.getMessage() != null && secondResp.getMessage().toLowerCase().contains("đang mượn"));

        auth.logout();
    }
}
