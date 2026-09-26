package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.StudentStatus;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.StudentRepository;

public class StudentRepositoryImpl implements StudentRepository {
    private final DatabaseManager databaseManager;

    public StudentRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public StudentRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public StudentDTO findById(Long id) throws SQLException {
        String sql = "SELECT id, user_id, student_code, full_name, class_name, phone, email, " +
                     "max_borrow_limit, current_borrow_count, status " +
                     "FROM students WHERE id = ?";
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
    public StudentDTO findByStudentCode(String code) throws SQLException {
        String sql = "SELECT id, user_id, student_code, full_name, class_name, phone, email, " +
                     "max_borrow_limit, current_borrow_count, status " +
                     "FROM students WHERE student_code = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public StudentDTO findByUserId(Long userId) throws SQLException {
        String sql = "SELECT id, user_id, student_code, full_name, class_name, phone, email, " +
                     "max_borrow_limit, current_borrow_count, status " +
                     "FROM students WHERE user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<StudentDTO> findAll() throws SQLException {
        String sql = "SELECT id, user_id, student_code, full_name, class_name, phone, email, " +
                     "max_borrow_limit, current_borrow_count, status " +
                     "FROM students ORDER BY id ASC";
        List<StudentDTO> list = new ArrayList<>();
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
    public Long create(StudentDTO student, Connection conn) throws SQLException {
        String sql = "INSERT INTO students (user_id, student_code, full_name, class_name, phone, email, max_borrow_limit, current_borrow_count, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (student.getUserId() != null) {
                stmt.setLong(1, student.getUserId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setString(2, student.getStudentCode());
            stmt.setString(3, student.getFullName());
            stmt.setString(4, student.getClassName());
            stmt.setString(5, student.getPhone());
            stmt.setString(6, student.getEmail());
            stmt.setInt(7, student.getMaxBorrowLimit() > 0 ? student.getMaxBorrowLimit() : 5);
            stmt.setInt(8, student.getCurrentBorrowCount());
            stmt.setString(9, student.getStatus() != null ? student.getStatus().name() : StudentStatus.ACTIVE.name());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating student failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    student.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating student failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean update(StudentDTO student, Connection conn) throws SQLException {
        String sql = "UPDATE students SET user_id = ?, full_name = ?, class_name = ?, phone = ?, email = ?, " +
                     "max_borrow_limit = ?, current_borrow_count = ?, status = ? " +
                     "WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            if (student.getUserId() != null) {
                stmt.setLong(1, student.getUserId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }
            stmt.setString(2, student.getFullName());
            stmt.setString(3, student.getClassName());
            stmt.setString(4, student.getPhone());
            stmt.setString(5, student.getEmail());
            stmt.setInt(6, student.getMaxBorrowLimit());
            stmt.setInt(7, student.getCurrentBorrowCount());
            stmt.setString(8, student.getStatus().name());
            stmt.setLong(9, student.getId());
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean incrementBorrowCount(Long studentId, Connection conn) throws SQLException {
        String sql = "UPDATE students SET current_borrow_count = current_borrow_count + 1 " +
                     "WHERE id = ? AND current_borrow_count < max_borrow_limit";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean decrementBorrowCount(Long studentId, Connection conn) throws SQLException {
        String sql = "UPDATE students SET current_borrow_count = current_borrow_count - 1 " +
                     "WHERE id = ? AND current_borrow_count > 0";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setLong(1, studentId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean delete(Long id, Connection conn) throws SQLException {
        String sql = "DELETE FROM students WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private void ensureActivationTable(Connection conn) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS student_activations (" +
                     "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                     "student_id BIGINT NOT NULL UNIQUE, " +
                     "code_hash VARCHAR(255) NOT NULL, " +
                     "is_used BOOLEAN NOT NULL DEFAULT FALSE, " +
                     "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                     "used_at TIMESTAMP NULL, " +
                     "CONSTRAINT fk_sa_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE" +
                     ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Statement s = conn.createStatement()) {
            s.execute(ddl);
        }
    }

    @Override
    public void saveActivationCode(Long studentId, String codeHash, Connection conn) throws SQLException {
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try {
            ensureActivationTable(activeConn);
            String sql = "INSERT INTO student_activations (student_id, code_hash, is_used) VALUES (?, ?, FALSE) " +
                         "ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), is_used = FALSE, created_at = CURRENT_TIMESTAMP, used_at = NULL";
            try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
                stmt.setLong(1, studentId);
                stmt.setString(2, codeHash);
                stmt.executeUpdate();
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public String getActivationCodeHash(Long studentId, Connection conn) throws SQLException {
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try {
            ensureActivationTable(activeConn);
            String sql = "SELECT code_hash FROM student_activations WHERE student_id = ?";
            try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
                stmt.setLong(1, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("code_hash");
                    }
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
        return null;
    }

    @Override
    public boolean isActivationCodeUsed(Long studentId, Connection conn) throws SQLException {
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try {
            ensureActivationTable(activeConn);
            String sql = "SELECT is_used FROM student_activations WHERE student_id = ?";
            try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
                stmt.setLong(1, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("is_used");
                    }
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
        return false;
    }

    @Override
    public boolean markActivationCodeUsed(Long studentId, Connection conn) throws SQLException {
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try {
            ensureActivationTable(activeConn);
            String sql = "UPDATE student_activations SET is_used = TRUE, used_at = CURRENT_TIMESTAMP WHERE student_id = ?";
            try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
                stmt.setLong(1, studentId);
                return stmt.executeUpdate() > 0;
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private StudentDTO mapRow(ResultSet rs) throws SQLException {
        StudentDTO student = new StudentDTO();
        student.setId(rs.getLong("id"));
        long userId = rs.getLong("user_id");
        if (!rs.wasNull()) {
            student.setUserId(userId);
        }
        student.setStudentCode(rs.getString("student_code"));
        student.setFullName(rs.getString("full_name"));
        student.setClassName(rs.getString("class_name"));
        student.setPhone(rs.getString("phone"));
        student.setEmail(rs.getString("email"));
        student.setMaxBorrowLimit(rs.getInt("max_borrow_limit"));
        student.setCurrentBorrowCount(rs.getInt("current_borrow_count"));
        student.setStatus(StudentStatus.valueOf(rs.getString("status")));
        return student;
    }
}
