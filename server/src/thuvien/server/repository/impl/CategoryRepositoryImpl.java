package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.CategoryDTO;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.CategoryRepository;

public class CategoryRepositoryImpl implements CategoryRepository {
    private final DatabaseManager databaseManager;

    public CategoryRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public CategoryRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public CategoryDTO findById(Integer id) throws SQLException {
        String sql = "SELECT id, name, description FROM categories WHERE id = ?";
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
    public List<CategoryDTO> findAll() throws SQLException {
        String sql = "SELECT id, name, description FROM categories ORDER BY name ASC";
        List<CategoryDTO> list = new ArrayList<>();
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
    public Integer create(CategoryDTO category, Connection conn) throws SQLException {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, category.getName());
            stmt.setString(2, category.getDescription());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating category failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    category.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating category failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean update(CategoryDTO category, Connection conn) throws SQLException {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, category.getName());
            stmt.setString(2, category.getDescription());
            stmt.setInt(3, category.getId());
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean delete(Integer id, Connection conn) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ?";
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

    private CategoryDTO mapRow(ResultSet rs) throws SQLException {
        return new CategoryDTO(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description")
        );
    }
}
