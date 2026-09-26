package thuvien.server.repository.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import thuvien.common.dto.FineDTO;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.FineRepository;

public class FineRepositoryImpl implements FineRepository {
    private final DatabaseManager databaseManager;

    private static final String BASE_SELECT =
            "SELECT f.id, f.borrow_record_id, f.student_id, s.full_name AS student_name, " +
            "b.title AS book_title, f.overdue_days, f.fine_rate_per_day, f.fine_amount, " +
            "f.is_paid, f.paid_date, f.collected_by_user_id, f.created_at " +
            "FROM fines f " +
            "INNER JOIN students s ON f.student_id = s.id " +
            "INNER JOIN borrow_records br ON f.borrow_record_id = br.id " +
            "INNER JOIN books b ON br.book_id = b.id ";

    public FineRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public FineRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public FineDTO findById(Long id) throws SQLException {
        String sql = BASE_SELECT + "WHERE f.id = ?";
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
    public FineDTO findByBorrowRecordId(Long borrowRecordId) throws SQLException {
        String sql = BASE_SELECT + "WHERE f.borrow_record_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, borrowRecordId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<FineDTO> findByStudentId(Long studentId) throws SQLException {
        String sql = BASE_SELECT + "WHERE f.student_id = ? ORDER BY f.id DESC";
        List<FineDTO> list = new ArrayList<>();
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
    public List<FineDTO> findUnpaidFines() throws SQLException {
        String sql = BASE_SELECT + "WHERE f.is_paid = FALSE ORDER BY f.id DESC";
        List<FineDTO> list = new ArrayList<>();
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
    public List<FineDTO> findAll() throws SQLException {
        String sql = BASE_SELECT + "ORDER BY f.id DESC";
        List<FineDTO> list = new ArrayList<>();
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
    public Long create(FineDTO fine, Connection conn) throws SQLException {
        String sql = "INSERT INTO fines (borrow_record_id, student_id, overdue_days, fine_rate_per_day, fine_amount, is_paid) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, fine.getBorrowRecordId());
            stmt.setLong(2, fine.getStudentId());
            stmt.setInt(3, fine.getOverdueDays());
            stmt.setBigDecimal(4, fine.getFineRatePerDay() != null ? fine.getFineRatePerDay() : new BigDecimal("5000.00"));
            stmt.setBigDecimal(5, fine.getFineAmount() != null ? fine.getFineAmount() : BigDecimal.ZERO);
            stmt.setBoolean(6, fine.isPaid());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating fine failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    fine.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating fine failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean markAsPaid(Long fineId, Long collectedByUserId, Connection conn) throws SQLException {
        String sql = "UPDATE fines SET is_paid = TRUE, paid_date = CURRENT_TIMESTAMP, collected_by_user_id = ? " +
                     "WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            if (collectedByUserId != null) {
                stmt.setLong(1, collectedByUserId);
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setLong(2, fineId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private FineDTO mapRow(ResultSet rs) throws SQLException {
        FineDTO fine = new FineDTO();
        fine.setId(rs.getLong("id"));
        fine.setBorrowRecordId(rs.getLong("borrow_record_id"));
        fine.setStudentId(rs.getLong("student_id"));
        fine.setStudentName(rs.getString("student_name"));
        fine.setBookTitle(rs.getString("book_title"));
        fine.setOverdueDays(rs.getInt("overdue_days"));
        fine.setFineRatePerDay(rs.getBigDecimal("fine_rate_per_day"));
        fine.setFineAmount(rs.getBigDecimal("fine_amount"));
        fine.setPaid(rs.getBoolean("is_paid"));
        Timestamp pt = rs.getTimestamp("paid_date");
        if (pt != null) fine.setPaidDate(new Date(pt.getTime()));
        long cid = rs.getLong("collected_by_user_id");
        if (!rs.wasNull()) fine.setCollectedByUserId(cid);
        Timestamp ct = rs.getTimestamp("created_at");
        if (ct != null) fine.setCreatedAt(new Date(ct.getTime()));
        return fine;
    }
}
