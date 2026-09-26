package thuvien.server.service.impl;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.enums.ReservationStatus;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.BorrowRepository;
import thuvien.server.repository.FineRepository;
import thuvien.server.repository.ReservationRepository;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.service.DashboardService;

public class DashboardServiceImpl implements DashboardService {
    private static final Logger LOGGER = Logger.getLogger(DashboardServiceImpl.class.getName());

    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;
    private final BorrowRepository borrowRepository;
    private final FineRepository fineRepository;
    private final ReservationRepository reservationRepository;

    public DashboardServiceImpl() {
        this(new BookRepositoryImpl(),
             new StudentRepositoryImpl(),
             new BorrowRepositoryImpl(),
             new FineRepositoryImpl(),
             new ReservationRepositoryImpl());
    }

    public DashboardServiceImpl(BookRepository bookRepository,
                                StudentRepository studentRepository,
                                BorrowRepository borrowRepository,
                                FineRepository fineRepository,
                                ReservationRepository reservationRepository) {
        this.bookRepository = bookRepository;
        this.studentRepository = studentRepository;
        this.borrowRepository = borrowRepository;
        this.fineRepository = fineRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public DashboardMetricsDTO getMetrics() throws LibraryException {
        try {
            DashboardMetricsDTO metrics = new DashboardMetricsDTO();

            // 1. Books Metrics
            List<BookDTO> books = bookRepository.findAll();
            int totalBooks = 0;
            int availableBooks = 0;
            for (BookDTO b : books) {
                totalBooks += b.getTotalCopies();
                availableBooks += b.getAvailableCopies();
            }
            int borrowedBooks = Math.max(0, totalBooks - availableBooks);
            metrics.setTotalBooks(totalBooks);
            metrics.setAvailableBooks(availableBooks);
            metrics.setBorrowedBooks(borrowedBooks);

            // 2. Students Count
            List<StudentDTO> students = studentRepository.findAll();
            metrics.setTotalStudents(students.size());

            // 3. Borrows Metrics
            List<BorrowRecordDTO> borrows = borrowRepository.findAll();
            int activeBorrows = 0;
            for (BorrowRecordDTO br : borrows) {
                if (br.getStatus() == BorrowStatus.ACTIVE) {
                    activeBorrows++;
                }
            }
            metrics.setActiveBorrowCount(activeBorrows);

            List<BorrowRecordDTO> overdueBorrows = borrowRepository.findOverdueBorrows();
            metrics.setOverdueBorrowCount(overdueBorrows.size());

            // 4. Pending Reservations Count
            int pendingReservations = 0;
            if (reservationRepository != null) {
                List<ReservationDTO> reservations = reservationRepository.findAll();
                for (ReservationDTO r : reservations) {
                    if (r.getStatus() == ReservationStatus.PENDING) {
                        pendingReservations++;
                    }
                }
            }
            metrics.setPendingReservations(pendingReservations);

            // 5. Total Unpaid Fines
            BigDecimal totalUnpaidFines = BigDecimal.ZERO;
            if (fineRepository != null) {
                List<FineDTO> unpaidFines = fineRepository.findUnpaidFines();
                for (FineDTO f : unpaidFines) {
                    if (f.getFineAmount() != null) {
                        totalUnpaidFines = totalUnpaidFines.add(f.getFineAmount());
                    }
                }
            }
            metrics.setTotalUnpaidFines(totalUnpaidFines);

            return metrics;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving dashboard metrics", e);
            throw new LibraryException("Database error computing dashboard metrics: " + e.getMessage(), e);
        }
    }

    @Override
    public thuvien.common.dto.StudentDashboardDTO getStudentDashboard(Long userId)
            throws LibraryException, thuvien.common.exception.EntityNotFoundException {
        if (userId == null) {
            throw new thuvien.common.exception.EntityNotFoundException("User ID is required.");
        }
        try {
            StudentDTO student = studentRepository.findByUserId(userId);
            if (student == null) {
                throw new thuvien.common.exception.EntityNotFoundException("Không tìm thấy hồ sơ sinh viên tương ứng với tài khoản.");
            }

            thuvien.common.dto.StudentDashboardDTO dto = new thuvien.common.dto.StudentDashboardDTO();
            dto.setStudentId(student.getId());
            dto.setStudentCode(student.getStudentCode());
            dto.setFullName(student.getFullName());
            dto.setClassName(student.getClassName());

            // 1. Borrows
            List<BorrowRecordDTO> borrows = borrowRepository.findByStudentId(student.getId());
            int active = 0;
            int overdue = 0;
            long now = System.currentTimeMillis();
            for (BorrowRecordDTO b : borrows) {
                if (b.getStatus() == BorrowStatus.ACTIVE) {
                    active++;
                    if (b.getDueDate() != null && b.getDueDate().getTime() < now) {
                        overdue++;
                    }
                } else if (b.getStatus() == BorrowStatus.OVERDUE) {
                    active++;
                    overdue++;
                }
            }
            dto.setActiveBorrowCount(active);
            dto.setOverdueBorrowCount(overdue);
            dto.setRecentBorrows(borrows.size() > 5 ? borrows.subList(0, 5) : borrows);

            // 2. Reservations
            List<ReservationDTO> reservations = reservationRepository.findByStudentId(student.getId());
            int pending = 0;
            for (ReservationDTO r : reservations) {
                if (r.getStatus() == ReservationStatus.PENDING) {
                    pending++;
                }
            }
            dto.setPendingReservationCount(pending);
            dto.setRecentReservations(reservations.size() > 5 ? reservations.subList(0, 5) : reservations);

            // 3. Fines
            List<FineDTO> fines = fineRepository.findByStudentId(student.getId());
            BigDecimal unpaidAmt = BigDecimal.ZERO;
            for (FineDTO f : fines) {
                if (!f.isPaid() && f.getFineAmount() != null) {
                    unpaidAmt = unpaidAmt.add(f.getFineAmount());
                }
            }
            dto.setUnpaidFineAmount(unpaidAmt);

            return dto;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving student dashboard for user: " + userId, e);
            throw new LibraryException("Lỗi cơ sở dữ liệu khi tải bảng điều khiển sinh viên: " + e.getMessage(), e);
        }
    }
}
