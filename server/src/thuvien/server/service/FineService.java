package thuvien.server.service;

import java.math.BigDecimal;
import java.util.List;
import thuvien.common.dto.FineDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface FineService {
    FineDTO getFineById(Long id) throws EntityNotFoundException, LibraryException;
    List<FineDTO> getFinesByStudentId(Long studentId) throws LibraryException;
    List<FineDTO> getUnpaidFines() throws LibraryException;
    List<FineDTO> getAllFines() throws LibraryException;
    boolean payFine(Long fineId, Long collectedByUserId) throws EntityNotFoundException, LibraryException;
    BigDecimal calculateOverdueFine(int overdueDays);
}
