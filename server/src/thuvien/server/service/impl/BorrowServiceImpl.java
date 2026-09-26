package thuvien.server.service.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.BookStatus;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.enums.ReservationStatus;
import thuvien.common.enums.StudentStatus;
import thuvien.common.exception.BookUnavailableException;
import thuvien.common.exception.BorrowLimitExceededException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.AuditLogRepository;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.BorrowRepository;
import thuvien.server.repository.ReservationRepository;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.service.BorrowService;

public class BorrowServiceImpl implements BorrowService {
    private static final Logger LOGGER = Logger.getLogger(BorrowServiceImpl.class.getName());

    private final DatabaseManager databaseManager;
    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;
    private final BorrowRepository borrowRepository;
    private final ReservationRepository reservationRepository;
    private final AuditLogRepository auditLogRepository;

    public BorrowServiceImpl() {
        this(DatabaseManager.getInstance(),
             new BookRepositoryImpl(),
             new StudentRepositoryImpl(),
             new BorrowRepositoryImpl(),
             new ReservationRepositoryImpl(),
             new AuditLogRepositoryImpl());
    }

    public BorrowServiceImpl(DatabaseManager databaseManager,
                             BookRepository bookRepository,
                             StudentRepository studentRepository,
                             BorrowRepository borrowRepository) {
        this(databaseManager, bookRepository, studentRepository, borrowRepository, null, null);
    }

    public BorrowServiceImpl(DatabaseManager databaseManager,
                             BookRepository bookRepository,
                             StudentRepository studentRepository,
                             BorrowRepository borrowRepository,
                             ReservationRepository reservationRepository,
                             AuditLogRepository auditLogRepository) {
        this.databaseManager = databaseManager;
        this.bookRepository = bookRepository;
        this.studentRepository = studentRepository;
        this.borrowRepository = borrowRepository;
        this.reservationRepository = reservationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public BorrowRecordDTO borrowBook(BorrowRequestDTO request, Long issuedByUserId)
            throws BookUnavailableException, BorrowLimitExceededException, EntityNotFoundException, LibraryException {

        // 1. Parameter Validation
        if (request == null || request.getStudentId() == null || request.getBookId() == null) {
            throw new EntityNotFoundException("Student ID and Book ID are required to borrow a book.");
        }

        Connection conn = null;
        boolean originalAutoCommit = true;

        try {
            conn = databaseManager.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Step 1: Validate Student
            StudentDTO student = studentRepository.findById(request.getStudentId());
            if (student == null) {
                throw new EntityNotFoundException("Student not found with ID: " + request.getStudentId());
            }
            if (student.getStatus() != StudentStatus.ACTIVE) {
                throw new LibraryException("Student account is not active (Status: " + student.getStatus() + "). Borrowing denied.");
            }

            // Step 2: Validate Book
            BookDTO book = bookRepository.findById(request.getBookId());
            if (book == null) {
                throw new EntityNotFoundException("Book not found with ID: " + request.getBookId());
            }
            if (book.getStatus() == BookStatus.LOST) {
                throw new BookUnavailableException("Book is unavailable for borrowing (Status: " + book.getStatus() + ").");
            }

            // Step 3: Validate Borrow Limit
            if (student.getCurrentBorrowCount() >= student.getMaxBorrowLimit()) {
                throw new BorrowLimitExceededException(
                        String.format("Student %s has reached the maximum borrowing limit (%d/%d).",
                                student.getStudentCode(), student.getCurrentBorrowCount(), student.getMaxBorrowLimit())
                );
            }

            // Step 4: Atomic Inventory Decrement
            // Direct atomic conditional UPDATE: WHERE id = ? AND available_copies > 0
            boolean decremented = bookRepository.decrementAvailableCopies(request.getBookId(), conn);
            if (!decremented) {
                throw new BookUnavailableException("No copies available for book '" + book.getTitle() + "' (ID: " + book.getId() + ").");
            }

            // Step 5: Increment Student Borrow Count
            boolean incremented = studentRepository.incrementBorrowCount(request.getStudentId(), conn);
            if (!incremented) {
                throw new LibraryException("Failed to update student borrowing counter.");
            }

            // Step 6: Create Borrow Record on the SAME Connection
            int durationDays = request.getDurationDays() > 0 ? request.getDurationDays() : 14;
            Date now = new Date();
            Date dueDate = new Date(now.getTime() + (long) durationDays * 24 * 3600 * 1000L);

            BorrowRecordDTO record = new BorrowRecordDTO();
            record.setStudentId(student.getId());
            record.setStudentCode(student.getStudentCode());
            record.setStudentName(student.getFullName());
            record.setBookId(book.getId());
            record.setBookTitle(book.getTitle());
            record.setBookIsbn(book.getIsbn());
            record.setIssuedByUserId(issuedByUserId);
            record.setBorrowDate(now);
            record.setDueDate(dueDate);
            record.setStatus(BorrowStatus.ACTIVE);
            record.setNotes(request.getNotes());

            Long recordId = borrowRepository.create(record, conn);
            record.setId(recordId);

            // Step 7: Check and Fulfill Pending Reservation for this student and book
            if (reservationRepository != null) {
                List<ReservationDTO> activeReservations = reservationRepository.findActiveByBookId(book.getId());
                for (ReservationDTO res : activeReservations) {
                    if (res.getStudentId().equals(student.getId())) {
                        reservationRepository.updateStatus(res.getId(), ReservationStatus.FULFILLED, conn);
                        break;
                    }
                }
            }

            // Step 8: Audit Logging on SAME Connection
            if (auditLogRepository != null) {
                AuditLogDTO audit = new AuditLogDTO();
                audit.setUserId(issuedByUserId);
                audit.setAction("BORROW_BOOK");
                audit.setEntityName("borrow_records");
                audit.setEntityId(recordId);
                audit.setDetails("Student " + student.getStudentCode() + " borrowed book ID " + book.getId() + " (" + book.getTitle() + ")");
                audit.setCreatedAt(now);
                auditLogRepository.create(audit, conn);
            }

            // Step 9: Commit Transaction
            conn.commit();
            LOGGER.info(String.format("Successfully committed borrow transaction: Record #%d (Student: %s, Book: %s)",
                    recordId, student.getStudentCode(), book.getTitle()));

            return record;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    LOGGER.info("Rolled back borrow transaction due to: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Failed to rollback borrow transaction", rollbackEx);
                }
            }

            if (e instanceof BookUnavailableException) {
                throw (BookUnavailableException) e;
            } else if (e instanceof BorrowLimitExceededException) {
                throw (BorrowLimitExceededException) e;
            } else if (e instanceof EntityNotFoundException) {
                throw (EntityNotFoundException) e;
            } else if (e instanceof LibraryException) {
                throw (LibraryException) e;
            } else {
                throw new LibraryException("Borrow transaction failed: " + e.getMessage(), e);
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

    @Override
    public List<BorrowRecordDTO> getAllBorrowRecords() throws LibraryException {
        try {
            return borrowRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error listing borrow records", e);
            throw new LibraryException("Database error listing borrow records: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BorrowRecordDTO> getStudentActiveBorrows(Long studentId) throws LibraryException {
        if (studentId == null) {
            throw new LibraryException("Student ID cannot be null.");
        }
        try {
            List<BorrowRecordDTO> all = borrowRepository.findByStudentId(studentId);
            List<BorrowRecordDTO> active = new java.util.ArrayList<>();
            for (BorrowRecordDTO b : all) {
                if (b.getStatus() == BorrowStatus.ACTIVE) {
                    active.add(b);
                }
            }
            return active;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving active borrows for student: " + studentId, e);
            throw new LibraryException("Database error retrieving active borrows: " + e.getMessage(), e);
        }
    }
}

