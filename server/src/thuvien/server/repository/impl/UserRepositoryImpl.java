package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.UserRepository;

public class UserRepositoryImpl implements UserRepository {
    private final DatabaseManager databaseManager;

    public UserRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public UserRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public UserDTO findById(Long id) throws SQLException {
        String sql = "SELECT id, username, role, full_name, email, phone, is_active, created_at " +
                     "FROM users WHERE id = ?";
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
    public UserDTO findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, role, full_name, email, phone, is_active, created_at " +
                     "FROM users WHERE username = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public String getPasswordHash(String username) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ? AND is_active = TRUE";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            }
        }
        return null;
    }

    @Override
    public List<UserDTO> findAll() throws SQLException {
        String sql = "SELECT id, username, role, full_name, email, phone, is_active, created_at " +
                     "FROM users ORDER BY id ASC";
        List<UserDTO> users = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    @Override
    public Long create(UserDTO user, String passwordHash, Connection conn) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role, full_name, email, phone, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, passwordHash);
            stmt.setString(3, user.getRole().name());
            stmt.setString(4, user.getFullName());
            stmt.setString(5, user.getEmail());
            stmt.setString(6, user.getPhone());
            stmt.setBoolean(7, user.isActive());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    user.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean update(UserDTO user, Connection conn) throws SQLException {
        String sql = "UPDATE users SET role = ?, full_name = ?, email = ?, phone = ?, is_active = ? " +
                     "WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, user.getRole().name());
            stmt.setString(2, user.getFullName());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPhone());
            stmt.setBoolean(5, user.isActive());
            stmt.setLong(6, user.getId());
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean updatePassword(Long userId, String newPasswordHash, Connection conn) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean delete(Long id, Connection conn) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
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

    private UserDTO mapRow(ResultSet rs) throws SQLException {
        UserDTO user = new UserDTO();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setActive(rs.getBoolean("is_active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(new java.util.Date(ts.getTime()));
        }
        return user;
    }
}
