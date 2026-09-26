package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.AuthorDTO;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.AuthorRepository;

public class AuthorRepositoryImpl implements AuthorRepository {
    private final DatabaseManager databaseManager;

    public AuthorRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public AuthorRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public AuthorDTO findById(Integer id) throws SQLException {
        String sql = "SELECT id, name, bio, nationality FROM authors WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<AuthorDTO> findByBookId(Long bookId) throws SQLException {
        String sql = "SELECT a.id, a.name, a.bio, a.nationality " +
                     "FROM authors a " +
                     "INNER JOIN book_authors ba ON a.id = ba.author_id " +
                     "WHERE ba.book_id = ? " +
                     "ORDER BY a.name ASC";
        List<AuthorDTO> list = new ArrayList<>();
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
    public List<AuthorDTO> findAll() throws SQLException {
        String sql = "SELECT id, name, bio, nationality FROM authors ORDER BY name ASC";
        List<AuthorDTO> list = new ArrayList<>();
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
    public Integer create(AuthorDTO author, Connection conn) throws SQLException {
        String sql = "INSERT INTO authors (name, bio, nationality) VALUES (?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, author.getName());
            stmt.setString(2, author.getBio());
            stmt.setString(3, author.getNationality());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating author failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    author.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating author failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean update(AuthorDTO author, Connection conn) throws SQLException {
        String sql = "UPDATE authors SET name = ?, bio = ?, nationality = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, author.getName());
            stmt.setString(2, author.getBio());
            stmt.setString(3, author.getNationality());
            stmt.setInt(4, author.getId());
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean delete(Integer id, Connection conn) throws SQLException {
        String sql = "DELETE FROM authors WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private AuthorDTO mapRow(ResultSet rs) throws SQLException {
        AuthorDTO author = new AuthorDTO(rs.getInt("id"), rs.getString("name"));
        author.setBio(rs.getString("bio"));
        author.setNationality(rs.getString("nationality"));
        return author;
    }
}
