package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.ReservationStatus;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.ReservationRepository;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.service.ReservationService;

public class ReservationServiceImpl implements ReservationService {
    private static final Logger LOGGER = Logger.getLogger(ReservationServiceImpl.class.getName());

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;

    public ReservationServiceImpl() {
        this(new ReservationRepositoryImpl(), new BookRepositoryImpl(), new StudentRepositoryImpl());
    }

    public ReservationServiceImpl(ReservationRepository reservationRepository,
                                  BookRepository bookRepository,
                                  StudentRepository studentRepository) {
        this.reservationRepository = reservationRepository;
        this.bookRepository = bookRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public ReservationDTO createReservation(Long studentId, Long bookId) throws LibraryException {
        if (studentId == null || bookId == null) {
            throw new LibraryException("Student ID and Book ID are required to reserve a book.");
        }

        try {
            StudentDTO student = studentRepository.findById(studentId);
            if (student == null) {
                throw new EntityNotFoundException("Student not found with ID: " + studentId);
            }

            BookDTO book = bookRepository.findById(bookId);
            if (book == null) {
                throw new EntityNotFoundException("Book not found with ID: " + bookId);
            }

            ReservationDTO reservation = new ReservationDTO();
            reservation.setStudentId(studentId);
            reservation.setStudentName(student.getFullName());
            reservation.setBookId(bookId);
            reservation.setBookTitle(book.getTitle());
            reservation.setReservationDate(new Date());
            // Default 7 days expiry
            reservation.setExpiryDate(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000L));
            reservation.setStatus(ReservationStatus.PENDING);

            Long id = reservationRepository.create(reservation, null);
            reservation.setId(id);
            return reservation;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating reservation for student " + studentId + " on book " + bookId, e);
            throw new LibraryException("Database error creating reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public ReservationDTO getReservationById(Long reservationId) throws EntityNotFoundException, LibraryException {
        if (reservationId == null) {
            throw new EntityNotFoundException("Reservation ID cannot be null.");
        }
        try {
            ReservationDTO existing = reservationRepository.findById(reservationId);
            if (existing == null) {
                throw new EntityNotFoundException("Reservation not found with ID: " + reservationId);
            }
            return existing;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving reservation ID: " + reservationId, e);
            throw new LibraryException("Database error retrieving reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelReservation(Long reservationId) throws EntityNotFoundException, LibraryException {
        if (reservationId == null) {
            throw new EntityNotFoundException("Reservation ID cannot be null.");
        }

        try {
            ReservationDTO existing = reservationRepository.findById(reservationId);
            if (existing == null) {
                throw new EntityNotFoundException("Reservation not found with ID: " + reservationId);
            }
            return reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error cancelling reservation ID: " + reservationId, e);
            throw new LibraryException("Database error cancelling reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ReservationDTO> getReservationsByStudent(Long studentId) throws LibraryException {
        if (studentId == null) {
            throw new LibraryException("Student ID cannot be null.");
        }
        try {
            return reservationRepository.findByStudentId(studentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving reservations for student: " + studentId, e);
            throw new LibraryException("Database error retrieving reservations: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ReservationDTO> getAllReservations() throws LibraryException {
        try {
            return reservationRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving all reservations", e);
            throw new LibraryException("Database error retrieving all reservations: " + e.getMessage(), e);
        }
    }
}
