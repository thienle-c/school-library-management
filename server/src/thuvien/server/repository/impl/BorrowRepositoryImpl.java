package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.BorrowRepository;

public class BorrowRepositoryImpl implements BorrowRepository {
    private final DatabaseManager databaseManager;

    private static final String BASE_SELECT =
            "SELECT br.id, br.student_id, s.student_code, s.full_name AS student_name, " +
            "br.book_id, b.title AS book_title, b.isbn AS book_isbn, " +
            "br.issued_by_user_id, u.full_name AS issued_by_user_name, " +
            "br.borrow_date, br.due_date, br.return_date, br.status, br.notes " +
            "FROM borrow_records br " +
            "INNER JOIN students s ON br.student_id = s.id " +
            "INNER JOIN books b ON br.book_id = b.id " +
            "INNER JOIN users u ON br.issued_by_user_id = u.id ";

    public BorrowRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public BorrowRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public BorrowRecordDTO findById(Long id) throws SQLException {
        String sql = BASE_SELECT + "WHERE br.id = ?";
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
    public BorrowRecordDTO findActiveBorrow(Long studentId, Long bookId) throws SQLException {
        String sql = BASE_SELECT + "WHERE br.student_id = ? AND br.book_id = ? AND br.status = 'ACTIVE' LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            stmt.setLong(2, bookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<BorrowRecordDTO> findByStudentId(Long studentId) throws SQLException {
        String sql = BASE_SELECT + "WHERE br.student_id = ? ORDER BY br.id DESC";
        List<BorrowRecordDTO> list = new ArrayList<>();
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
    public List<BorrowRecordDTO> findOverdueBorrows() throws SQLException {
        String sql = BASE_SELECT + "WHERE br.status = 'ACTIVE' AND br.due_date < CURRENT_DATE() ORDER BY br.due_date ASC";
        List<BorrowRecordDTO> list = new ArrayList<>();
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
    public List<BorrowRecordDTO> findAll() throws SQLException {
        String sql = BASE_SELECT + "ORDER BY br.id DESC";
        List<BorrowRecordDTO> list = new ArrayList<>();
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
    public Long create(BorrowRecordDTO record, Connection conn) throws SQLException {
        String sql = "INSERT INTO borrow_records (student_id, book_id, issued_by_user_id, borrow_date, due_date, return_date, status, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, record.getStudentId());
            stmt.setLong(2, record.getBookId());
            stmt.setLong(3, record.getIssuedByUserId());
            stmt.setDate(4, new java.sql.Date(record.getBorrowDate() != null ? record.getBorrowDate().getTime() : System.currentTimeMillis()));
            stmt.setDate(5, new java.sql.Date(record.getDueDate() != null ? record.getDueDate().getTime() : System.currentTimeMillis() + 14L * 86400000L));
            if (record.getReturnDate() != null) {
                stmt.setDate(6, new java.sql.Date(record.getReturnDate().getTime()));
            } else {
                stmt.setNull(6, Types.DATE);
            }
            stmt.setString(7, record.getStatus() != null ? record.getStatus().name() : BorrowStatus.ACTIVE.name());
            stmt.setString(8, record.getNotes());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating borrow record failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    record.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating borrow record failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean updateStatus(Long recordId, BorrowStatus status, Date returnDate, Connection conn) throws SQLException {
        String sql = "UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            if (returnDate != null) {
                stmt.setDate(2, new java.sql.Date(returnDate.getTime()));
            } else {
                stmt.setNull(2, Types.DATE);
            }
            stmt.setLong(3, recordId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private BorrowRecordDTO mapRow(ResultSet rs) throws SQLException {
        BorrowRecordDTO record = new BorrowRecordDTO();
        record.setId(rs.getLong("id"));
        record.setStudentId(rs.getLong("student_id"));
        record.setStudentCode(rs.getString("student_code"));
        record.setStudentName(rs.getString("student_name"));
        record.setBookId(rs.getLong("book_id"));
        record.setBookTitle(rs.getString("book_title"));
        record.setBookIsbn(rs.getString("book_isbn"));
        record.setIssuedByUserId(rs.getLong("issued_by_user_id"));
        record.setIssuedByUserName(rs.getString("issued_by_user_name"));
        java.sql.Date bd = rs.getDate("borrow_date");
        if (bd != null) record.setBorrowDate(new Date(bd.getTime()));
        java.sql.Date dd = rs.getDate("due_date");
        if (dd != null) record.setDueDate(new Date(dd.getTime()));
        java.sql.Date rd = rs.getDate("return_date");
        if (rd != null) record.setReturnDate(new Date(rd.getTime()));
        record.setStatus(BorrowStatus.valueOf(rs.getString("status")));
        record.setNotes(rs.getString("notes"));
        return record;
    }
}
