package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.enums.ReservationStatus;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.ReservationRepository;

public class ReservationRepositoryImpl implements ReservationRepository {
    private final DatabaseManager databaseManager;

    private static final String BASE_SELECT =
            "SELECT r.id, r.student_id, s.full_name AS student_name, " +
            "r.book_id, b.title AS book_title, r.reservation_date, r.expiry_date, r.status " +
            "FROM reservations r " +
            "INNER JOIN students s ON r.student_id = s.id " +
            "INNER JOIN books b ON r.book_id = b.id ";

    public ReservationRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public ReservationRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public ReservationDTO findById(Long id) throws SQLException {
        String sql = BASE_SELECT + "WHERE r.id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<ReservationDTO> findByStudentId(Long studentId) throws SQLException {
        String sql = BASE_SELECT + "WHERE r.student_id = ? ORDER BY r.reservation_date DESC";
        List<ReservationDTO> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ReservationDTO> findActiveByBookId(Long bookId) throws SQLException {
        String sql = BASE_SELECT + "WHERE r.book_id = ? AND r.status = 'PENDING' ORDER BY r.reservation_date ASC";
        List<ReservationDTO> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ReservationDTO> findAll() throws SQLException {
        String sql = BASE_SELECT + "ORDER BY r.reservation_date DESC";
        List<ReservationDTO> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public Long create(ReservationDTO reservation, Connection conn) throws SQLException {
        String sql = "INSERT INTO reservations (student_id, book_id, reservation_date, expiry_date, status) " +
                     "VALUES (?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, reservation.getStudentId());
            stmt.setLong(2, reservation.getBookId());

            if (reservation.getReservationDate() != null) {
                stmt.setTimestamp(3, new Timestamp(reservation.getReservationDate().getTime()));
            } else {
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            }

            if (reservation.getExpiryDate() != null) {
                stmt.setDate(4, new Date(reservation.getExpiryDate().getTime()));
            } else {
                // Default expiry date: 7 days from now
                stmt.setDate(4, new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000));
            }

            String statusStr = reservation.getStatus() != null ? reservation.getStatus().name() : ReservationStatus.PENDING.name();
            stmt.setString(5, statusStr);

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating reservation failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    reservation.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating reservation failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean updateStatus(Long reservationId, ReservationStatus status, Connection conn) throws SQLException {
        String sql = "UPDATE reservations SET status = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setLong(2, reservationId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private ReservationDTO mapRow(ResultSet rs) throws SQLException {
        ReservationDTO r = new ReservationDTO();
        r.setId(rs.getLong("id"));
        r.setStudentId(rs.getLong("student_id"));
        r.setStudentName(rs.getString("student_name"));
        r.setBookId(rs.getLong("book_id"));
        r.setBookTitle(rs.getString("book_title"));

        Timestamp resDate = rs.getTimestamp("reservation_date");
        if (resDate != null) {
            r.setReservationDate(new java.util.Date(resDate.getTime()));
        }

        Date expDate = rs.getDate("expiry_date");
        if (expDate != null) {
            r.setExpiryDate(new java.util.Date(expDate.getTime()));
        }

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            try {
                r.setStatus(ReservationStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                r.setStatus(ReservationStatus.PENDING);
            }
        }
        return r;
    }
}
