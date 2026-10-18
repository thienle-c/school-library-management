package thuvien.server.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.exception.BookUnavailableException;
import thuvien.common.exception.LibraryException;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.service.impl.BorrowServiceImpl;
import thuvien.server.service.impl.ReturnServiceImpl;

/**
 * Isolated MySQL integration tests for Borrow & Return workflows and concurrency safety.
 *
 * DATA-01 & CONCUR-01 SAFETY RULES:
 * 1. Checks for dedicated test database or explicit property (-Dtest.database.enabled=true).
 * 2. If running on default 'school_library' without explicit permission, skips automatically.
 * 3. Dynamically generates isolated test entities (never modifies/deletes existing books/students).
 * 4. Cleans up ONLY test-created entities identified by primary keys in finally/tearDown.
 */
public class BorrowConcurrencyIntegrationTest {

    private boolean dbAvailable = false;
    private boolean isIsolatedTestDb = false;
    private String skipReason = "";
    private BorrowService borrowService;
    private ReturnService returnService;

    // Track test-created IDs for guaranteed cleanup
    private final List<Long> createdBookIds = new ArrayList<>();
    private final List<Long> createdStudentIds = new ArrayList<>();
    private final List<Long> createdBorrowRecordIds = new ArrayList<>();

    @Before
    public void checkDatabaseAvailabilityAndIsolation() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            if (conn != null && !conn.isClosed()) {
                dbAvailable = true;
                this.isIsolatedTestDb = thuvien.server.TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
                if (!this.isIsolatedTestDb) {
                    this.skipReason = "[SAFETY — DATA-01] BorrowConcurrencyIntegrationTest is disabled on database '"
                            + conn.getCatalog() + "' to protect application data. "
                            + "Test requires an isolated test database ('school_library_test').";
                }
            }
        } catch (Exception e) {
            dbAvailable = false;
            skipReason = "Database connection is not available: " + e.getMessage();
        }

        if (dbAvailable && isIsolatedTestDb) {
            borrowService = new BorrowServiceImpl();
            returnService = new ReturnServiceImpl();
        }
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
            // 1. Delete fines for created borrow records
            for (Long borrowId : createdBorrowRecordIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM fines WHERE borrow_record_id = ?")) {
                    ps.setLong(1, borrowId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            // 2. Delete created borrow records
            for (Long borrowId : createdBorrowRecordIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM borrow_records WHERE id = ?")) {
                    ps.setLong(1, borrowId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            // Also clean up by book ID for any borrow record created during concurrent tests
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
            }
            // 3. Delete created books
            for (Long bookId : createdBookIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE id = ?")) {
                    ps.setLong(1, bookId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            // 4. Delete created students
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
        // schema: isbn VARCHAR(20) NOT NULL UNIQUE
        String isbn = "T" + (System.currentTimeMillis() % 10000000000L) + (int)(Math.random() * 900 + 100);
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
        // schema: student_code VARCHAR(30) NOT NULL UNIQUE
        String code = "STU" + (System.currentTimeMillis() % 1000000000L) + (int)(Math.random() * 900 + 100);
        String sql = "INSERT INTO students (student_code, full_name, class_name, max_borrow_limit, current_borrow_count, status) " +
                     "VALUES (?, ?, 'TEST_CLASS', ?, 0, 'ACTIVE')";
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

    @Test
    public void testConcurrentBorrowLastAvailableCopy() throws Exception {
        Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb);

        final Long targetBookId;
        final Long studentA;
        final Long studentB;
        final Long adminUserId = 1L;

        // Dynamically create isolated test book (total=1, available=1) and test students
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            targetBookId = createTestBook(conn, "Concurrency Isolation Test Book", 1, 1);
            studentA = createTestStudent(conn, "Concurrent Test Student A", 5);
            studentB = createTestStudent(conn, "Concurrent Test Student B", 5);
        }

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger bookUnavailableCount = new AtomicInteger(0);
        final AtomicInteger otherErrorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Student A attempts to borrow
        executor.execute(() -> {
            try {
                startLatch.await();
                BorrowRecordDTO r = borrowService.borrowBook(new BorrowRequestDTO(studentA, targetBookId), adminUserId);
                if (r != null && r.getId() != null) {
                    successCount.incrementAndGet();
                    synchronized (createdBorrowRecordIds) {
                        createdBorrowRecordIds.add(r.getId());
                    }
                }
            } catch (BookUnavailableException e) {
                bookUnavailableCount.incrementAndGet();
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Student B attempts to borrow the exact same copy
        executor.execute(() -> {
            try {
                startLatch.await();
                BorrowRecordDTO r = borrowService.borrowBook(new BorrowRequestDTO(studentB, targetBookId), adminUserId);
                if (r != null && r.getId() != null) {
                    successCount.incrementAndGet();
                    synchronized (createdBorrowRecordIds) {
                        createdBorrowRecordIds.add(r.getId());
                    }
                }
            } catch (BookUnavailableException e) {
                bookUnavailableCount.incrementAndGet();
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Fire both threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("Concurrent operations timed out", completed);
        Assert.assertEquals("Exactly one borrow operation must succeed", 1, successCount.get());
        Assert.assertEquals("The competing operation must fail with BookUnavailableException", 1, bookUnavailableCount.get());
        Assert.assertEquals("No unexpected errors should occur", 0, otherErrorCount.get());

        // Invariant check: available_copies must be exactly 0 and never negative
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
            ps.setLong(1, targetBookId);
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertTrue(rs.next());
                int finalCopies = rs.getInt("available_copies");
                Assert.assertEquals("Final available copies must be exactly 0", 0, finalCopies);
                Assert.assertTrue("Available copies must never be negative", finalCopies >= 0);
            }
        }
    }

    @Test
    public void testNormalBorrowFollowedByReturn() throws Exception {
        Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb);

        final Long studentId;
        final Long bookId;
        final Long adminUserId = 1L;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            bookId = createTestBook(conn, "Normal Borrow Return Test Book", 4, 4);
            studentId = createTestStudent(conn, "Normal Borrow Return Student", 5);
        }

        // 1. Execute Borrow
        BorrowRecordDTO borrowRecord = borrowService.borrowBook(
                new BorrowRequestDTO(studentId, bookId, 14, "Isolated Test Borrow"), adminUserId);
        Assert.assertNotNull(borrowRecord);
        Assert.assertNotNull(borrowRecord.getId());
        createdBorrowRecordIds.add(borrowRecord.getId());
        Assert.assertEquals(BorrowStatus.ACTIVE, borrowRecord.getStatus());

        // Verify state after borrow
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
                ps.setLong(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(3, rs.getInt("available_copies"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_borrow_count FROM students WHERE id = ?")) {
                ps.setLong(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(1, rs.getInt("current_borrow_count"));
                }
            }
        }

        // 2. Execute Return
        ReturnResultDTO returnResult = returnService.returnBook(studentId, bookId, adminUserId);
        Assert.assertNotNull(returnResult);
        Assert.assertEquals(borrowRecord.getId(), returnResult.getBorrowRecordId());
        Assert.assertFalse("On-time return should not be overdue", returnResult.isOverdue());
        Assert.assertEquals(BigDecimal.ZERO, returnResult.getFineAmount());

        // Verify state after return
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
                ps.setLong(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(4, rs.getInt("available_copies"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_borrow_count FROM students WHERE id = ?")) {
                ps.setLong(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals(0, rs.getInt("current_borrow_count"));
                }
            }
        }
    }

    @Test
    public void testOverdueReturnGeneratesFine() throws Exception {
        Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb);

        final Long studentId;
        final Long bookId;
        final Long adminUserId = 1L;
        final Long borrowId;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            bookId = createTestBook(conn, "Overdue Return Test Book", 2, 1);
            studentId = createTestStudent(conn, "Overdue Return Student", 5);

            // Update student count to 1 for this borrow
            try (PreparedStatement ps = conn.prepareStatement("UPDATE students SET current_borrow_count = 1 WHERE id = ?")) {
                ps.setLong(1, studentId);
                ps.executeUpdate();
            }

            long now = System.currentTimeMillis();
            Date borrowDate = new Date(now - 19L * 24 * 3600 * 1000L); // 19 days ago
            Date dueDate = new Date(now - 5L * 24 * 3600 * 1000L); // 5 days ago

            String sql = "INSERT INTO borrow_records (student_id, book_id, issued_by_user_id, borrow_date, due_date, status) " +
                         "VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
            try (PreparedStatement psIns = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                psIns.setLong(1, studentId);
                psIns.setLong(2, bookId);
                psIns.setLong(3, adminUserId);
                psIns.setDate(4, borrowDate);
                psIns.setDate(5, dueDate);
                psIns.executeUpdate();
                try (ResultSet keys = psIns.getGeneratedKeys()) {
                    Assert.assertTrue(keys.next());
                    borrowId = keys.getLong(1);
                    createdBorrowRecordIds.add(borrowId);
                }
            }
        }

        // Execute Return on overdue book
        ReturnResultDTO result = returnService.returnBook(studentId, bookId, adminUserId);
        Assert.assertNotNull(result);
        Assert.assertEquals(borrowId, result.getBorrowRecordId());
        Assert.assertTrue("Return should be marked overdue", result.isOverdue());
        Assert.assertEquals("Should be 5 days overdue", 5, result.getOverdueDays());
        Assert.assertEquals(new BigDecimal("25000.00"), result.getFineAmount());

        // Verify fine was recorded
        FineRepositoryImpl fineRepo = new FineRepositoryImpl(DatabaseManager.getInstance());
        FineDTO fine = fineRepo.findByBorrowRecordId(borrowId);
        Assert.assertNotNull("Fine record must be created in database", fine);
        Assert.assertEquals(new BigDecimal("25000.00"), fine.getFineAmount());
        Assert.assertFalse("Fine must not be paid yet", fine.isPaid());
    }

    /**
     * CONCUR-01: Validates that concurrent return requests on the exact same borrow record
     * cannot double-increment inventory or double-decrement student borrow count.
     */
    @Test
    public void testConcurrentReturnSameBorrowRecord() throws Exception {
        Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb);

        final Long studentId;
        final Long bookId;
        final Long adminUserId = 1L;
        final Long borrowId;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            bookId = createTestBook(conn, "Concurrent Return Test Book", 1, 0);
            studentId = createTestStudent(conn, "Concurrent Return Student", 5);

            try (PreparedStatement ps = conn.prepareStatement("UPDATE students SET current_borrow_count = 1 WHERE id = ?")) {
                ps.setLong(1, studentId);
                ps.executeUpdate();
            }

            long now = System.currentTimeMillis();
            Date borrowDate = new Date(now - 2L * 24 * 3600 * 1000L);
            Date dueDate = new Date(now + 12L * 24 * 3600 * 1000L);

            String sql = "INSERT INTO borrow_records (student_id, book_id, issued_by_user_id, borrow_date, due_date, status) " +
                         "VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
            try (PreparedStatement psIns = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                psIns.setLong(1, studentId);
                psIns.setLong(2, bookId);
                psIns.setLong(3, adminUserId);
                psIns.setDate(4, borrowDate);
                psIns.setDate(5, dueDate);
                psIns.executeUpdate();
                try (ResultSet keys = psIns.getGeneratedKeys()) {
                    Assert.assertTrue(keys.next());
                    borrowId = keys.getLong(1);
                    createdBorrowRecordIds.add(borrowId);
                }
            }
        }

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        final AtomicInteger returnSuccessCount = new AtomicInteger(0);
        final AtomicInteger returnRejectedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Return attempt 1
        executor.execute(() -> {
            try {
                startLatch.await();
                ReturnResultDTO r = returnService.returnBook(studentId, bookId, adminUserId);
                if (r != null) {
                    returnSuccessCount.incrementAndGet();
                }
            } catch (LibraryException e) {
                returnRejectedCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected error
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Return attempt 2 (competing for the same borrow record)
        executor.execute(() -> {
            try {
                startLatch.await();
                ReturnResultDTO r = returnService.returnBook(studentId, bookId, adminUserId);
                if (r != null) {
                    returnSuccessCount.incrementAndGet();
                }
            } catch (LibraryException e) {
                returnRejectedCount.incrementAndGet();
            } catch (Exception e) {
                // Unexpected error
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("Concurrent return operations timed out", completed);
        Assert.assertEquals("Exactly one return operation must succeed", 1, returnSuccessCount.get());
        Assert.assertEquals("Competing return operation must be rejected", 1, returnRejectedCount.get());

        // Invariant check: inventory must be exactly 1 (NOT 2), student borrow count must be 0 (NOT -1)
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT available_copies FROM books WHERE id = ?")) {
                ps.setLong(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals("Available copies must be exactly 1 (never double incremented)", 1, rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_borrow_count FROM students WHERE id = ?")) {
                ps.setLong(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals("Current borrow count must be 0 (never double decremented)", 0, rs.getInt(1));
                }
            }
        }
    }
}
