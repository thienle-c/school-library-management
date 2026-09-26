package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.enums.BookStatus;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.BookRepository;

public class BookRepositoryImpl implements BookRepository {
    private final DatabaseManager databaseManager;

    public BookRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public BookRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public BookDTO findById(Long id) throws SQLException {
        String sql = "SELECT b.id, b.isbn, b.title, b.category_id, c.name AS category_name, " +
                     "b.publisher, b.publish_year, b.edition, b.total_copies, b.available_copies, " +
                     "b.shelf_location, b.status " +
                     "FROM books b " +
                     "LEFT JOIN categories c ON b.category_id = c.id " +
                     "WHERE b.id = ?";
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
    public BookDTO findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT b.id, b.isbn, b.title, b.category_id, c.name AS category_name, " +
                     "b.publisher, b.publish_year, b.edition, b.total_copies, b.available_copies, " +
                     "b.shelf_location, b.status " +
                     "FROM books b " +
                     "LEFT JOIN categories c ON b.category_id = c.id " +
                     "WHERE b.isbn = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, isbn);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public PageResponseDTO<BookDTO> search(BookSearchCriteriaDTO criteria) throws SQLException {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            String kw = criteria.getKeyword().trim();
            String wildcard = "%" + kw + "%";
            whereClause.append(" AND (b.title LIKE ? OR b.isbn LIKE ? OR b.publisher LIKE ? OR b.shelf_location LIKE ? OR c.name LIKE ? ");
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
            whereClause.append(" OR EXISTS (SELECT 1 FROM book_authors ba2 JOIN authors a2 ON ba2.author_id = a2.id WHERE ba2.book_id = b.id AND a2.name LIKE ?) ");
            params.add(wildcard);
            try {
                long numId = Long.parseLong(kw);
                whereClause.append(" OR b.id = ? ");
                params.add(numId);
            } catch (NumberFormatException ignored) {}
            whereClause.append(") ");
        }
        if (criteria.getCategoryId() != null) {
            whereClause.append(" AND b.category_id = ? ");
            params.add(criteria.getCategoryId());
        }
        if (criteria.getStatus() != null) {
            whereClause.append(" AND b.status = ? ");
            params.add(criteria.getStatus().name());
        }

        String authorJoin = "";
        if (criteria.getAuthorId() != null) {
            authorJoin = " INNER JOIN book_authors ba ON b.id = ba.book_id AND ba.author_id = ? ";
        }

        // 1. Count query
        String countSql = "SELECT COUNT(DISTINCT b.id) FROM books b " +
                          "LEFT JOIN categories c ON b.category_id = c.id " +
                          authorJoin +
                          whereClause.toString();

        int totalItems = 0;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement countStmt = conn.prepareStatement(countSql)) {
            int paramIndex = 1;
            if (criteria.getAuthorId() != null) {
                countStmt.setInt(paramIndex++, criteria.getAuthorId());
            }
            for (Object param : params) {
                countStmt.setObject(paramIndex++, param);
            }
            try (ResultSet rs = countStmt.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getInt(1);
                }
            }
        }

        // 2. Select paginated items
        int page = criteria.getPage() > 0 ? criteria.getPage() : 1;
        int pageSize = criteria.getPageSize() > 0 ? criteria.getPageSize() : 20;
        int offset = (page - 1) * pageSize;

        String selectSql = "SELECT b.id, b.isbn, b.title, b.category_id, c.name AS category_name, " +
                           "b.publisher, b.publish_year, b.edition, b.total_copies, b.available_copies, " +
                           "b.shelf_location, b.status " +
                           "FROM books b " +
                           authorJoin +
                           "LEFT JOIN categories c ON b.category_id = c.id " +
                           whereClause.toString() +
                           "ORDER BY b.id DESC LIMIT ? OFFSET ?";

        List<BookDTO> items = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            int paramIndex = 1;
            if (criteria.getAuthorId() != null) {
                selectStmt.setInt(paramIndex++, criteria.getAuthorId());
            }
            for (Object param : params) {
                selectStmt.setObject(paramIndex++, param);
            }
            selectStmt.setInt(paramIndex++, pageSize);
            selectStmt.setInt(paramIndex, offset);

            try (ResultSet rs = selectStmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
            }
        }

        return new PageResponseDTO<>(items, page, pageSize, totalItems);
    }

    @Override
    public List<BookDTO> findAll() throws SQLException {
        String sql = "SELECT b.id, b.isbn, b.title, b.category_id, c.name AS category_name, " +
                     "b.publisher, b.publish_year, b.edition, b.total_copies, b.available_copies, " +
                     "b.shelf_location, b.status " +
                     "FROM books b " +
                     "LEFT JOIN categories c ON b.category_id = c.id " +
                     "ORDER BY b.id ASC";
        List<BookDTO> list = new ArrayList<>();
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
    public Long create(BookDTO book, Connection conn) throws SQLException {
        String sql = "INSERT INTO books (isbn, title, category_id, publisher, publish_year, edition, " +
                     "total_copies, available_copies, shelf_location, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, book.getIsbn());
            stmt.setString(2, book.getTitle());
            stmt.setInt(3, book.getCategoryId());
            stmt.setString(4, book.getPublisher());
            if (book.getPublishYear() != null) {
                stmt.setInt(5, book.getPublishYear());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, book.getEdition());
            stmt.setInt(7, book.getTotalCopies() > 0 ? book.getTotalCopies() : 1);
            stmt.setInt(8, book.getAvailableCopies() >= 0 ? book.getAvailableCopies() : book.getTotalCopies());
            stmt.setString(9, book.getShelfLocation());
            stmt.setString(10, book.getStatus() != null ? book.getStatus().name() : BookStatus.AVAILABLE.name());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating book failed, no rows affected.");
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    book.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating book failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean update(BookDTO book, Connection conn) throws SQLException {
        String sql = "UPDATE books SET isbn = ?, title = ?, category_id = ?, publisher = ?, " +
                     "publish_year = ?, edition = ?, total_copies = ?, available_copies = ?, " +
                     "shelf_location = ?, status = ? WHERE id = ?";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setString(1, book.getIsbn());
            stmt.setString(2, book.getTitle());
            stmt.setInt(3, book.getCategoryId());
            stmt.setString(4, book.getPublisher());
            if (book.getPublishYear() != null) {
                stmt.setInt(5, book.getPublishYear());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, book.getEdition());
            stmt.setInt(7, book.getTotalCopies());
            stmt.setInt(8, book.getAvailableCopies());
            stmt.setString(9, book.getShelfLocation());
            stmt.setString(10, book.getStatus().name());
            stmt.setLong(11, book.getId());
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean delete(Long id, Connection conn) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";
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

    @Override
    public boolean decrementAvailableCopies(Long bookId, Connection conn) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies - 1, " +
                     "status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END " +
                     "WHERE id = ? AND available_copies > 0";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    @Override
    public boolean incrementAvailableCopies(Long bookId, Connection conn) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies + 1, " +
                     "status = 'AVAILABLE' " +
                     "WHERE id = ? AND available_copies < total_copies";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql)) {
            stmt.setLong(1, bookId);
            return stmt.executeUpdate() > 0;
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private BookDTO mapRow(ResultSet rs) throws SQLException {
        BookDTO book = new BookDTO();
        book.setId(rs.getLong("id"));
        book.setIsbn(rs.getString("isbn"));
        book.setTitle(rs.getString("title"));
        book.setCategoryId(rs.getInt("category_id"));
        book.setCategoryName(rs.getString("category_name"));
        book.setPublisher(rs.getString("publisher"));
        int py = rs.getInt("publish_year");
        if (!rs.wasNull()) {
            book.setPublishYear(py);
        }
        book.setEdition(rs.getString("edition"));
        book.setTotalCopies(rs.getInt("total_copies"));
        book.setAvailableCopies(rs.getInt("available_copies"));
        book.setShelfLocation(rs.getString("shelf_location"));
        book.setStatus(BookStatus.valueOf(rs.getString("status")));
        return book;
    }
}
