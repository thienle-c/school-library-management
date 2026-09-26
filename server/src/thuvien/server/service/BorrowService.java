package thuvien.server.service;

import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.exception.BookUnavailableException;
import thuvien.common.exception.BorrowLimitExceededException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface BorrowService {
    /**
     * Executes atomic 8-step borrow transaction:
     * 1. Validate student
     * 2. Validate book
     * 3. Check book availability (available_copies > 0)
     * 4. Check student borrow limit
     * 5. Start transaction (setAutoCommit false)
     * 6. Create borrow record
     * 7. Atomically decrement available_copies & increment student borrow count
     * 8. Commit transaction (Rollback on failure)
     */
    BorrowRecordDTO borrowBook(BorrowRequestDTO request, Long issuedByUserId)
            throws BookUnavailableException, BorrowLimitExceededException, EntityNotFoundException, LibraryException;

    java.util.List<BorrowRecordDTO> getAllBorrowRecords() throws LibraryException;

    java.util.List<BorrowRecordDTO> getStudentActiveBorrows(Long studentId) throws LibraryException;
}
