package thuvien.server.service.impl;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.FineDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.FineRepository;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.service.FineService;

public class FineServiceImpl implements FineService {
    private static final Logger LOGGER = Logger.getLogger(FineServiceImpl.class.getName());
    private static final BigDecimal DEFAULT_DAILY_RATE = new BigDecimal("5000.00");

    private final FineRepository fineRepository;

    public FineServiceImpl() {
        this(new FineRepositoryImpl());
    }

    public FineServiceImpl(FineRepository fineRepository) {
        this.fineRepository = fineRepository;
    }

    @Override
    public FineDTO getFineById(Long id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Fine ID cannot be null.");
        }
        try {
            FineDTO fine = fineRepository.findById(id);
            if (fine == null) {
                throw new EntityNotFoundException("Fine not found with ID: " + id);
            }
            return fine;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving fine by ID: " + id, e);
            throw new LibraryException("Database error retrieving fine: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FineDTO> getFinesByStudentId(Long studentId) throws LibraryException {
        if (studentId == null) {
            throw new LibraryException("Student ID cannot be null.");
        }
        try {
            return fineRepository.findByStudentId(studentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving fines for student: " + studentId, e);
            throw new LibraryException("Database error retrieving fines: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FineDTO> getUnpaidFines() throws LibraryException {
        try {
            return fineRepository.findUnpaidFines();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving unpaid fines", e);
            throw new LibraryException("Database error retrieving unpaid fines: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FineDTO> getAllFines() throws LibraryException {
        try {
            return fineRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving all fines", e);
            throw new LibraryException("Database error retrieving all fines: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean payFine(Long fineId, Long collectedByUserId) throws EntityNotFoundException, LibraryException {
        if (fineId == null) {
            throw new EntityNotFoundException("Fine ID cannot be null.");
        }

        // Verify fine exists
        FineDTO fine = getFineById(fineId);
        if (fine.isPaid()) {
            LOGGER.info(String.format("Fine #%d is already marked as paid.", fineId));
            return true;
        }

        try {
            return fineRepository.markAsPaid(fineId, collectedByUserId, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error marking fine as paid: " + fineId, e);
            throw new LibraryException("Database error marking fine as paid: " + e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal calculateOverdueFine(int overdueDays) {
        if (overdueDays <= 0) {
            return BigDecimal.ZERO;
        }
        return DEFAULT_DAILY_RATE.multiply(new BigDecimal(overdueDays));
    }
}
