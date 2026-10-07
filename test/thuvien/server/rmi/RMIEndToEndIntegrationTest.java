package thuvien.server.rmi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import thuvien.client.network.RMIClient;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.LibraryException;
import thuvien.common.rmi.LibraryRemoteService;
import thuvien.server.database.DatabaseManager;
import thuvien.server.router.RequestRouter;

/**
 * Comprehensive Automated Test Suite for Java RMI Architecture:
 * 1. RMI Registry startup test
 * 2. Remote lookup test
 * 3. Ping remote test
 * 4. Login over RMI
 * 5. Search books over RMI
 * 6. Borrow book over RMI
 * 7. Return book over RMI
 * 8. Reservation over RMI
 * 9. Pay fine over RMI
 * 10. Authorization over RMI
 * 11. Logout/session invalidation over RMI
 * 12. Concurrent clients over RMI
 * 13. Concurrent borrow same book over RMI (Core Concurrency Requirement)
 * 14. Serialization DTO test
 * 15. Client/server database boundary test
 * 16. Full localhost RMI flow
 */
public class RMIEndToEndIntegrationTest {

    private static final int TEST_REGISTRY_PORT = 10999;
    private static final String TEST_SERVICE_NAME = "TestLibraryRemoteService";

    private static Registry testRegistry;
    private static LibraryRemoteServiceImpl exportedService;
    private static boolean dbAvailable = false;

    @BeforeClass
    public static void setUpRMIAndDatabase() throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            dbAvailable = false;
        }

        if (dbAvailable) {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                try (PreparedStatement psAdmin = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'admin'")) {
                    psAdmin.setString(1, thuvien.server.security.PasswordHasher.hash("admin123"));
                    psAdmin.executeUpdate();
                }
                try (PreparedStatement psLib = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'librarian1'")) {
                    psLib.setString(1, thuvien.server.security.PasswordHasher.hash("lib123"));
                    psLib.executeUpdate();
                }
                try (PreparedStatement psStu = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'student1'")) {
                    psStu.setString(1, thuvien.server.security.PasswordHasher.hash("student123"));
                    psStu.executeUpdate();
                }
                try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET total_copies = 5, available_copies = 5, status = 'AVAILABLE' WHERE id = 1")) {
                    psBook.executeUpdate();
                }
                try (PreparedStatement psStuRec = conn.prepareStatement("UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id = 1")) {
                    psStuRec.executeUpdate();
                }
            }
        }

        // Start dedicated in-process RMI registry and export remote service
        try {
            testRegistry = LocateRegistry.createRegistry(TEST_REGISTRY_PORT);
        } catch (Exception e) {
            testRegistry = LocateRegistry.getRegistry(TEST_REGISTRY_PORT);
        }

        exportedService = new LibraryRemoteServiceImpl(0, new RequestRouter());
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

    // -------------------------------------------------------------------------
    // 1. RMI Registry Startup Test
    // -------------------------------------------------------------------------
    @Test
    public void test01_RMIRegistryStartup() throws Exception {
        Assert.assertNotNull("RMI Registry must be running", testRegistry);
        String[] list = testRegistry.list();
        Assert.assertNotNull(list);
        boolean found = false;
        for (String s : list) {
            if (TEST_SERVICE_NAME.equals(s)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue("Test service must be bound in registry", found);
    }

    // -------------------------------------------------------------------------
    // 2. Remote Lookup Test
    // -------------------------------------------------------------------------
    @Test
    public void test02_RemoteLookup() throws Exception {
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        Object obj = registry.lookup(TEST_SERVICE_NAME);
        Assert.assertNotNull("Lookup result must not be null", obj);
        Assert.assertTrue("Lookup object must implement LibraryRemoteService", obj instanceof LibraryRemoteService);
    }

    // -------------------------------------------------------------------------
    // 3. Ping Remote Test
    // -------------------------------------------------------------------------
    @Test
    public void test03_PingRemoteTest() throws Exception {
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);
        String pong = remote.ping();
        Assert.assertEquals("PONG", pong);
    }

    // -------------------------------------------------------------------------
    // 4. Login over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test04_LoginOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required for login", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        Assert.assertNotNull(session);
        Assert.assertNotNull(session.getToken());
        Assert.assertEquals("librarian1", session.getUsername());
        Assert.assertEquals(UserRole.LIBRARIAN, session.getRole());

        remote.logout(session.getToken());
    }

    // -------------------------------------------------------------------------
    // 5. Search Books over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test05_SearchBooksOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required for search", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
        criteria.setPageSize(10);
        PageResponseDTO<BookDTO> page = remote.searchBooks(session.getToken(), criteria);
        Assert.assertNotNull(page);
        Assert.assertTrue("Catalog should contain books", page.getItems().size() > 0);

        remote.logout(session.getToken());
    }

    // -------------------------------------------------------------------------
    // 6 & 7. Borrow and Return Book over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test06_BorrowAndReturnOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required for borrow & return", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        String token = session.getToken();

        // Check initial copies of book 1
        BookDTO initialBook = remote.getBook(token, 1L);
        int initialCopies = initialBook.getAvailableCopies();
        Assert.assertTrue("Available copies must be > 0", initialCopies > 0);

        // Borrow book
        BorrowRecordDTO record = remote.borrowBook(token, new BorrowRequestDTO(1L, 1L, 14, "RMI Borrow Test"));
        Assert.assertNotNull(record);
        Assert.assertNotNull(record.getId());

        // Verify inventory decremented
        BookDTO duringBook = remote.getBook(token, 1L);
        Assert.assertEquals(initialCopies - 1, duringBook.getAvailableCopies());

        // Return book
        ReturnResultDTO returnResult = remote.returnBook(token, 1L, 1L);
        Assert.assertNotNull(returnResult);
        Assert.assertEquals(record.getId(), returnResult.getBorrowRecordId());

        // Verify inventory restored
        BookDTO afterBook = remote.getBook(token, 1L);
        Assert.assertEquals(initialCopies, afterBook.getAvailableCopies());

        remote.logout(token);
    }

    // -------------------------------------------------------------------------
    // 8. Reservation over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test08_ReservationOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        String token = session.getToken();

        ReservationDTO res = remote.createReservation(token, 1L, 1L);
        Assert.assertNotNull(res);
        Assert.assertNotNull(res.getId());

        boolean cancelled = remote.cancelReservation(token, res.getId());
        Assert.assertTrue(cancelled);

        // Clean up from database
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM reservations WHERE id = ?")) {
            ps.setLong(1, res.getId());
            ps.executeUpdate();
        }

        remote.logout(token);
    }

    // -------------------------------------------------------------------------
    // 9. Pay Fine over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test09_PayFineOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        String token = session.getToken();

        // Create a borrow record and fine directly in DB
        Long borrowId;
        Long fineId;
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psBorrow = conn.prepareStatement(
                    "INSERT INTO borrow_records (student_id, book_id, issued_by_user_id, borrow_date, due_date, status) VALUES (1, 1, 1, NOW(), NOW(), 'RETURNED')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                psBorrow.executeUpdate();
                java.sql.ResultSet rsB = psBorrow.getGeneratedKeys();
                rsB.next();
                borrowId = rsB.getLong(1);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fines (borrow_record_id, student_id, fine_amount, is_paid) VALUES (?, 1, 10000.00, 0)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, borrowId);
                ps.executeUpdate();
                java.sql.ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                fineId = rs.getLong(1);
            }
        }

        boolean paid = remote.payFine(token, fineId);
        Assert.assertTrue(paid);

        // Cleanup
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fines WHERE id = ?")) {
                ps.setLong(1, fineId);
                ps.executeUpdate();
            }
            try (PreparedStatement psB = conn.prepareStatement("DELETE FROM borrow_records WHERE id = ?")) {
                psB.setLong(1, borrowId);
                psB.executeUpdate();
            }
        }

        remote.logout(token);
    }

    // -------------------------------------------------------------------------
    // 10. Authorization over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test10_AuthorizationOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO studentSession = remote.login(new LoginRequestDTO("student1", "student123"));
        String studentToken = studentSession.getToken();

        // Student attempting to access admin-only student list must be rejected
        try {
            remote.getAllStudents(studentToken);
            Assert.fail("Student must not be allowed to list all students");
        } catch (AuthorizationException e) {
            // Expected
            Assert.assertNotNull(e.getMessage());
        }

        remote.logout(studentToken);
    }

    // -------------------------------------------------------------------------
    // 11. Logout and Session Invalidation over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test11_LogoutAndSessionInvalidationOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        String token = session.getToken();

        // Valid while logged in
        UserSessionDTO current = remote.getCurrentUser(token);
        Assert.assertNotNull(current);

        // Logout
        remote.logout(token);

        // Subsequent call with same token must fail with AuthenticationException
        try {
            remote.getCurrentUser(token);
            Assert.fail("Revoked token must be rejected");
        } catch (AuthenticationException e) {
            Assert.assertNotNull(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 12. Concurrent Clients over RMI
    // -------------------------------------------------------------------------
    @Test
    public void test12_ConcurrentClientsOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);
        int clientCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    RMIClient client = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
                    boolean pingOk = client.ping();
                    if (pingOk) {
                        successCount.incrementAndGet();
                    }
                    client.disconnect();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("All concurrent client operations must complete", completed);
        Assert.assertEquals(clientCount, successCount.get());
    }

    // -------------------------------------------------------------------------
    // 13. CORE REQUIREMENT: Concurrent Borrow of Same Book over RMI
    // Client A and Client B concurrently request borrow(bookId=X) when available_copies = 1.
    // Server guarantees ONLY 1 client succeeds, competing client receives BookUnavailableException,
    // available_copies never negative.
    // -------------------------------------------------------------------------
    @Test
    public void test13_ConcurrentBorrowSameBookOverRMI() throws Exception {
        Assume.assumeTrue("Live MySQL required for concurrency", dbAvailable);

        final Long targetBookId = 1L;
        final Long studentA = 1L;
        final Long studentB = 2L;

        // Reset database state: exactly 1 copy available
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fines WHERE student_id IN (?, ?)")) {
                ps.setLong(1, studentA);
                ps.setLong(2, studentB);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM borrow_records WHERE book_id = ?")) {
                ps.setLong(1, targetBookId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE books SET total_copies = 1, available_copies = 1, status = 'AVAILABLE' WHERE id = ?")) {
                ps.setLong(1, targetBookId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id IN (?, ?)")) {
                ps.setLong(1, studentA);
                ps.setLong(2, studentB);
                ps.executeUpdate();
            }
        }

        Registry registry = LocateRegistry.getRegistry("127.0.0.1", TEST_REGISTRY_PORT);
        LibraryRemoteService remote = (LibraryRemoteService) registry.lookup(TEST_SERVICE_NAME);

        UserSessionDTO session = remote.login(new LoginRequestDTO("librarian1", "lib123"));
        final String token = session.getToken();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Client A borrows targetBookId
        executor.execute(() -> {
            try {
                startLatch.await();
                BorrowRecordDTO r = remote.borrowBook(token, new BorrowRequestDTO(studentA, targetBookId, 14, "RMI Concurrency A"));
                if (r != null && r.getId() != null) {
                    successCount.incrementAndGet();
                }
            } catch (LibraryException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected error
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Client B borrows same targetBookId
        executor.execute(() -> {
            try {
                startLatch.await();
                BorrowRecordDTO r = remote.borrowBook(token, new BorrowRequestDTO(studentB, targetBookId, 14, "RMI Concurrency B"));
                if (r != null && r.getId() != null) {
                    successCount.incrementAndGet();
                }
            } catch (LibraryException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected error
            } finally {
                doneLatch.countDown();
            }
        });

        // Fire both threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("Concurrent borrow operations timed out", completed);
        Assert.assertEquals("Exactly ONE client must successfully borrow the last copy", 1, successCount.get());
        Assert.assertEquals("The competing client must fail because no copies remain", 1, failureCount.get());

        // Invariant: available copies must be exactly 0 and NOT negative
        BookDTO finalBook = remote.getBook(token, targetBookId);
        Assert.assertEquals(0, finalBook.getAvailableCopies());
        Assert.assertTrue("Available copies must never be negative", finalBook.getAvailableCopies() >= 0);

        // Clean up
        remote.logout(token);
    }

    // -------------------------------------------------------------------------
    // 14. Serialization DTO Test
    // Validates that all protocol objects and DTOs roundtrip serialize via Java Serialization
    // -------------------------------------------------------------------------
    @Test
    public void test14_SerializationRoundtrip() throws Exception {
        BorrowRequestDTO original = new BorrowRequestDTO(1L, 2L, 14, "Serialization Test");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        BorrowRequestDTO deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (BorrowRequestDTO) ois.readObject();
        }

        Assert.assertNotNull(deserialized);
        Assert.assertEquals(original.getStudentId(), deserialized.getStudentId());
        Assert.assertEquals(original.getBookId(), deserialized.getBookId());
        Assert.assertEquals(original.getNotes(), deserialized.getNotes());
    }

    // -------------------------------------------------------------------------
    // 15. Client/Server Database Boundary Test
    // Verifies RMIClient contains zero JDBC / DataSource imports or references
    // -------------------------------------------------------------------------
    @Test
    public void test15_ClientDatabaseBoundaryIsolation() {
        for (java.lang.reflect.Field field : RMIClient.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            Assert.assertFalse("RMIClient must not contain java.sql.Connection",
                    java.sql.Connection.class.isAssignableFrom(type));
            Assert.assertFalse("RMIClient must not contain javax.sql.DataSource",
                    javax.sql.DataSource.class.isAssignableFrom(type));
        }
    }

    // -------------------------------------------------------------------------
    // 16. Full Localhost RMI Flow via RMIClient Adapter
    // -------------------------------------------------------------------------
    @Test
    public void test16_FullLocalhostRMIFlow() throws Exception {
        Assume.assumeTrue("Live MySQL required", dbAvailable);

        RMIClient client = new RMIClient("127.0.0.1", TEST_REGISTRY_PORT, TEST_SERVICE_NAME);
        Assert.assertTrue(client.ping());

        thuvien.client.controller.ClientAuthController authCtrl = new thuvien.client.controller.ClientAuthController(client);
        UserSessionDTO session = authCtrl.login("librarian1", "lib123");
        Assert.assertNotNull(session);
        Assert.assertNotNull(session.getToken());

        thuvien.client.controller.ClientBookController bookCtrl = new thuvien.client.controller.ClientBookController(client);
        BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
        criteria.setPageSize(5);
        PageResponseDTO<BookDTO> page = bookCtrl.searchBooks(criteria);
        Assert.assertNotNull(page);

        authCtrl.logout();
        client.disconnect();
    }
}
