package thuvien.server.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.service.impl.BorrowServiceImpl;
import thuvien.server.service.impl.ReturnServiceImpl;

/**
 * Real MySQL integration tests for Borrow & Return workflows:
 * 1. Concurrency test: Two concurrent borrow requests on last available copy.
 * 2. Normal borrow followed by return: Validates atomic inventory and student counter balance.
 * 3. Overdue return: Validates automatic fine generation.
 */
public class BorrowConcurrencyIntegrationTest {

    private boolean dbAvailable = false;
    private BorrowService borrowService;
    private ReturnService returnService;

    @Before
    public void checkDatabaseAvailability() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            dbAvailable = false;
        }

        if (dbAvailable) {
            borrowService = new BorrowServiceImpl();
            returnService = new ReturnServiceImpl();
        }
    }

    @Test
    public void testConcurrentBorrowLastAvailableCopy() throws Exception {
        Assume.assumeTrue(
                "Live MySQL database is not reachable on localhost:3306. Concurrency test marked as NOT EXECUTED.",
                dbAvailable
        );

        final Long targetBookId = 1L; // Test book
        final Long studentA = 1L;
        final Long studentB = 2L;
        final Long adminUserId = 1L;

        // Reset database state for book and students
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDeleteFines = conn.prepareStatement(
                    "DELETE FROM fines WHERE borrow_record_id IN (SELECT id FROM borrow_records WHERE book_id = ?)")) {
                psDeleteFines.setLong(1, targetBookId);
                psDeleteFines.executeUpdate();
            }
            try (PreparedStatement psDeleteBorrows = conn.prepareStatement(
                    "DELETE FROM borrow_records WHERE book_id = ?")) {
                psDeleteBorrows.setLong(1, targetBookId);
                psDeleteBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement(
                    "UPDATE books SET total_copies = 1, available_copies = 1 WHERE id = ?")) {
                psBook.setLong(1, targetBookId);
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement(
                    "UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id IN (?, ?)")) {
                psStudent.setLong(1, studentA);
                psStudent.setLong(2, studentB);
                psStudent.executeUpdate();
            }
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
                }
            } catch (BookUnavailableException e) {
                bookUnavailableCount.incrementAndGet();
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Student B attempts to borrow the exact same book
        executor.execute(() -> {
            try {
                startLatch.await();
                BorrowRecordDTO r = borrowService.borrowBook(new BorrowRequestDTO(studentB, targetBookId), adminUserId);
                if (r != null && r.getId() != null) {
                    successCount.incrementAndGet();
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
        Assert.assertEquals("Exactly one borrow operation should succeed", 1, successCount.get());
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
        Assume.assumeTrue(
                "Live MySQL database is not reachable on localhost:3306. Integration test marked as NOT EXECUTED.",
                dbAvailable
        );

        final Long studentId = 3L;
        final Long bookId = 2L; // Clean Code
        final Long adminUserId = 1L;

        // Reset state for student 3 and book 2
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement(
                    "DELETE FROM fines WHERE student_id = ?")) {
                ps1.setLong(1, studentId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "DELETE FROM borrow_records WHERE student_id = ?")) {
                ps2.setLong(1, studentId);
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = conn.prepareStatement(
                    "UPDATE books SET total_copies = 4, available_copies = 4 WHERE id = ?")) {
                ps3.setLong(1, bookId);
                ps3.executeUpdate();
            }
            try (PreparedStatement ps4 = conn.prepareStatement(
                    "UPDATE students SET current_borrow_count = 0 WHERE id = ?")) {
                ps4.setLong(1, studentId);
                ps4.executeUpdate();
            }
        }

        // 1. Execute Borrow
        BorrowRecordDTO borrowRecord = borrowService.borrowBook(new BorrowRequestDTO(studentId, bookId, 14, "Integration Test Borrow"), adminUserId);
        Assert.assertNotNull(borrowRecord);
        Assert.assertNotNull(borrowRecord.getId());
        Assert.assertEquals(BorrowStatus.ACTIVE, borrowRecord.getStatus());

        // Verify state after borrow: available_copies decremented, current_borrow_count incremented
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

        // Verify state after return: available_copies incremented, current_borrow_count decremented, status RETURNED
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
            try (PreparedStatement ps = conn.prepareStatement("SELECT status, return_date FROM borrow_records WHERE id = ?")) {
                ps.setLong(1, borrowRecord.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    Assert.assertTrue(rs.next());
                    Assert.assertEquals("RETURNED", rs.getString("status"));
                    Assert.assertNotNull(rs.getDate("return_date"));
                }
            }
        }
    }

    @Test
    public void testOverdueReturnGeneratesFine() throws Exception {
        Assume.assumeTrue(
                "Live MySQL database is not reachable on localhost:3306. Integration test marked as NOT EXECUTED.",
                dbAvailable
        );

        final Long studentId = 3L;
        final Long bookId = 3L; // Effective Java
        final Long adminUserId = 1L;

        Long borrowId;
        // Seed an overdue borrow record (due 5 days ago)
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE student_id = ?")) {
                psDelFines.setLong(1, studentId);
                psDelFines.executeUpdate();
            }
            try (PreparedStatement psDelBorrows = conn.prepareStatement("DELETE FROM borrow_records WHERE student_id = ?")) {
                psDelBorrows.setLong(1, studentId);
                psDelBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET available_copies = 2 WHERE id = ?")) {
                psBook.setLong(1, bookId);
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement("UPDATE students SET current_borrow_count = 1 WHERE id = ?")) {
                psStudent.setLong(1, studentId);
                psStudent.executeUpdate();
            }

            long now = System.currentTimeMillis();
            Date borrowDate = new Date(now - 19L * 24 * 3600 * 1000L); // 19 days ago
            Date dueDate = new Date(now - 5L * 24 * 3600 * 1000L); // 5 days ago

            try (PreparedStatement psIns = conn.prepareStatement(
                    "INSERT INTO borrow_records (student_id, book_id, issued_by_user_id, borrow_date, due_date, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                psIns.setLong(1, studentId);
                psIns.setLong(2, bookId);
                psIns.setLong(3, adminUserId);
                psIns.setDate(4, borrowDate);
                psIns.setDate(5, dueDate);
                psIns.executeUpdate();
                try (ResultSet keys = psIns.getGeneratedKeys()) {
                    Assert.assertTrue(keys.next());
                    borrowId = keys.getLong(1);
                }
            }
        }

        // Execute Return on overdue book
        ReturnResultDTO result = returnService.returnBook(studentId, bookId, adminUserId);
        Assert.assertNotNull(result);
        Assert.assertEquals(borrowId, result.getBorrowRecordId());
        Assert.assertTrue("Return should be marked overdue", result.isOverdue());
        Assert.assertEquals("Should be 5 days overdue", 5, result.getOverdueDays());
        // 5 days * 5000.00 = 25000.00
        Assert.assertEquals(new BigDecimal("25000.00"), result.getFineAmount());

        // Verify fine was recorded in database
        FineRepositoryImpl fineRepo = new FineRepositoryImpl(DatabaseManager.getInstance());
        FineDTO fine = fineRepo.findByBorrowRecordId(borrowId);
        Assert.assertNotNull("Fine record must be created in database", fine);
        Assert.assertEquals(new BigDecimal("25000.00"), fine.getFineAmount());
        Assert.assertFalse("Fine must not be paid yet", fine.isPaid());
    }
}
