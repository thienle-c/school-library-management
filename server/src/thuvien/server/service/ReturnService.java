package thuvien.server.service;

import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface ReturnService {
    /**
     * Executes atomic 7-step return transaction:
     * 1. Find active borrowing record
     * 2. Validate borrower
     * 3. Calculate overdue duration
     * 4. Calculate fine (if overdue, generate fine record)
     * 5. Update borrowing record (returned_at, status)
     * 6. Atomically increment book available copies & decrement student borrow count
     * 7. Commit transaction (Rollback on failure)
     */
    ReturnResultDTO returnBook(Long studentId, Long bookId, Long collectedByUserId)
            throws EntityNotFoundException, LibraryException;
}
