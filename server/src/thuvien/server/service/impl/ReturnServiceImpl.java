package thuvien.server.service.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.AuditLogRepository;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.BorrowRepository;
import thuvien.server.repository.FineRepository;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.service.ReturnService;

public class ReturnServiceImpl implements ReturnService {
    private static final Logger LOGGER = Logger.getLogger(ReturnServiceImpl.class.getName());
    private static final BigDecimal DEFAULT_DAILY_RATE = new BigDecimal("5000.00");

    private final DatabaseManager databaseManager;
    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;
    private final BorrowRepository borrowRepository;
    private final FineRepository fineRepository;
    private final AuditLogRepository auditLogRepository;

    public ReturnServiceImpl() {
        this(DatabaseManager.getInstance(),
             new BookRepositoryImpl(),
             new StudentRepositoryImpl(),
             new BorrowRepositoryImpl(),
             new FineRepositoryImpl(),
             new AuditLogRepositoryImpl());
    }

    public ReturnServiceImpl(DatabaseManager databaseManager,
                             BookRepository bookRepository,
                             StudentRepository studentRepository,
                             BorrowRepository borrowRepository,
                             FineRepository fineRepository) {
        this(databaseManager, bookRepository, studentRepository, borrowRepository, fineRepository, null);
    }

    public ReturnServiceImpl(DatabaseManager databaseManager,
                             BookRepository bookRepository,
                             StudentRepository studentRepository,
                             BorrowRepository borrowRepository,
                             FineRepository fineRepository,
                             AuditLogRepository auditLogRepository) {
        this.databaseManager = databaseManager;
        this.bookRepository = bookRepository;
        this.studentRepository = studentRepository;
        this.borrowRepository = borrowRepository;
        this.fineRepository = fineRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public ReturnResultDTO returnBook(Long studentId, Long bookId, Long collectedByUserId)
            throws EntityNotFoundException, LibraryException {

        // 1. Parameter Validation
        if (studentId == null || bookId == null) {
            throw new EntityNotFoundException("Student ID and Book ID are required to process a book return.");
        }

        Connection conn = null;
        boolean originalAutoCommit = true;

        try {
            conn = databaseManager.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Step 1: Find active borrowing record
            BorrowRecordDTO activeBorrow = borrowRepository.findActiveBorrow(studentId, bookId);
            if (activeBorrow == null) {
                throw new EntityNotFoundException(
                        String.format("No active borrowing record found for Student ID %d and Book ID %d.", studentId, bookId)
                );
            }
            if (activeBorrow.getStatus() != BorrowStatus.ACTIVE) {
                throw new LibraryException("Borrowing record is not in ACTIVE state. Current status: " + activeBorrow.getStatus());
            }

            // Step 2: Determine return date & calculate overdue duration
            Date returnDate = new Date();
            Date dueDate = activeBorrow.getDueDate();
            int overdueDays = 0;
            BigDecimal fineAmount = BigDecimal.ZERO;

            if (dueDate != null && returnDate.after(dueDate)) {
                long diffMs = returnDate.getTime() - dueDate.getTime();
                overdueDays = (int) (diffMs / (24L * 3600 * 1000L));
                if (overdueDays == 0 && diffMs > 0) {
                    overdueDays = 1;
                }
                fineAmount = DEFAULT_DAILY_RATE.multiply(new BigDecimal(overdueDays));
            }

            // Step 3: If overdue, generate Fine record on the SAME Connection
            if (overdueDays > 0 && fineRepository != null) {
                FineDTO fine = new FineDTO();
                fine.setBorrowRecordId(activeBorrow.getId());
                fine.setStudentId(studentId);
                fine.setOverdueDays(overdueDays);
                fine.setFineRatePerDay(DEFAULT_DAILY_RATE);
                fine.setFineAmount(fineAmount);
                fine.setPaid(false);
                fineRepository.create(fine, conn);
            }

            // Step 4: Update Borrowing Record (returned_at, status=RETURNED) on the SAME Connection
            boolean recordUpdated = borrowRepository.updateStatus(activeBorrow.getId(), BorrowStatus.RETURNED, returnDate, conn);
            if (!recordUpdated) {
                throw new LibraryException("Failed to update borrowing record status to RETURNED.");
            }

            // Step 5: Atomically increment book available copies on the SAME Connection
            boolean bookIncremented = bookRepository.incrementAvailableCopies(bookId, conn);
            if (!bookIncremented) {
                throw new LibraryException("Failed to increment book available copies.");
            }

            // Step 6: Atomically decrement student borrow count on the SAME Connection
            boolean studentDecremented = studentRepository.decrementBorrowCount(studentId, conn);
            if (!studentDecremented) {
                throw new LibraryException("Failed to decrement student borrow count.");
            }

            // Step 7: Audit Logging on SAME Connection
            if (auditLogRepository != null) {
                AuditLogDTO audit = new AuditLogDTO();
                audit.setUserId(collectedByUserId);
                audit.setAction("RETURN_BOOK");
                audit.setEntityName("borrow_records");
                audit.setEntityId(activeBorrow.getId());
                audit.setDetails(String.format("Student ID %d returned Book ID %d%s",
                        studentId, bookId, overdueDays > 0 ? " (Overdue: " + overdueDays + " days, Fine: " + fineAmount + " VND)" : " on time"));
                audit.setCreatedAt(returnDate);
                auditLogRepository.create(audit, conn);
            }

            // Step 8: Commit Transaction
            conn.commit();
            LOGGER.info(String.format("Successfully committed return transaction: Record #%d (Student: %d, Book: %d, Overdue: %d days)",
                    activeBorrow.getId(), studentId, bookId, overdueDays));

            // Build Return Result DTO
            ReturnResultDTO result = new ReturnResultDTO();
            result.setBorrowRecordId(activeBorrow.getId());
            result.setBookId(bookId);
            result.setBookTitle(activeBorrow.getBookTitle());
            result.setStudentId(studentId);
            result.setStudentName(activeBorrow.getStudentName());
            result.setOverdue(overdueDays > 0);
            result.setOverdueDays(overdueDays);
            result.setFineAmount(fineAmount);
            result.setMessage(overdueDays > 0
                    ? String.format("Book returned with %d day(s) overdue. Fine assessed: %s VND.", overdueDays, fineAmount.toPlainString())
                    : "Book returned successfully on time.");

            return result;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    LOGGER.info("Rolled back return transaction due to: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Failed to rollback return transaction", rollbackEx);
                }
            }

            if (e instanceof EntityNotFoundException) {
                throw (EntityNotFoundException) e;
            } else if (e instanceof LibraryException) {
                throw (LibraryException) e;
            } else {
                throw new LibraryException("Return transaction failed: " + e.getMessage(), e);
            }

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                    conn.close();
                } catch (SQLException closeEx) {
                    LOGGER.log(Level.WARNING, "Failed to restore auto-commit or close connection", closeEx);
                }
            }
        }
    }
}
