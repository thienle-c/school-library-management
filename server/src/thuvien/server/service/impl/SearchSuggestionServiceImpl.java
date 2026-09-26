package thuvien.server.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.LibraryException;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.service.SearchSuggestionService;

/**
 * Autocomplete search suggestion service providing multi-entity keyword completions.
 */
public class SearchSuggestionServiceImpl implements SearchSuggestionService {
    private static final Logger LOGGER = Logger.getLogger(SearchSuggestionServiceImpl.class.getName());

    private final DatabaseManager databaseManager;
    private final StudentRepository studentRepository;

    public SearchSuggestionServiceImpl() {
        this(DatabaseManager.getInstance(), new StudentRepositoryImpl());
    }

    public SearchSuggestionServiceImpl(DatabaseManager databaseManager, StudentRepository studentRepository) {
        this.databaseManager = databaseManager;
        this.studentRepository = studentRepository;
    }

    @Override
    public List<String> getSuggestions(String type, String keyword, UserSessionDTO session)
            throws AuthorizationException, LibraryException {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = "%" + keyword.trim() + "%";
        String normalizedType = type != null ? type.trim().toUpperCase() : "BOOK";

        Set<String> results = new LinkedHashSet<>();

        try (Connection conn = databaseManager.getConnection()) {
            switch (normalizedType) {
                case "BOOK":
                    // Suggestions for book titles, ISBNs, authors, publishers
                    String sqlBook = "SELECT DISTINCT title FROM books WHERE title LIKE ? LIMIT 5";
                    try (PreparedStatement ps = conn.prepareStatement(sqlBook)) {
                        ps.setString(1, kw);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) results.add(rs.getString("title"));
                        }
                    }
                    if (results.size() < 8) {
                        String sqlAuthor = "SELECT DISTINCT name FROM authors WHERE name LIKE ? LIMIT 3";
                        try (PreparedStatement ps = conn.prepareStatement(sqlAuthor)) {
                            ps.setString(1, kw);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) results.add(rs.getString("name"));
                            }
                        }
                    }
                    break;

                case "STUDENT":
                    // Staff only! Students must NOT search all students
                    if (session != null && session.getRole() == UserRole.STUDENT) {
                        throw new AuthorizationException("Sinh viên không có quyền tra cứu gợi ý sinh viên khác.");
                    }
                    String sqlStu = "SELECT student_code, full_name FROM students " +
                                    "WHERE student_code LIKE ? OR full_name LIKE ? LIMIT 6";
                    try (PreparedStatement ps = conn.prepareStatement(sqlStu)) {
                        ps.setString(1, kw);
                        ps.setString(2, kw);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                results.add(rs.getString("student_code") + " - " + rs.getString("full_name"));
                            }
                        }
                    }
                    break;

                case "BORROW":
                    Long studentId = null;
                    if (session != null && session.getRole() == UserRole.STUDENT) {
                        StudentDTO s = studentRepository.findByUserId(session.getUserId());
                        if (s != null) studentId = s.getId();
                    }

                    String sqlBorrow;
                    if (studentId != null) {
                        sqlBorrow = "SELECT DISTINCT b.title FROM borrow_records br " +
                                    "JOIN books b ON br.book_id = b.id " +
                                    "WHERE br.student_id = ? AND b.title LIKE ? LIMIT 6";
                        try (PreparedStatement ps = conn.prepareStatement(sqlBorrow)) {
                            ps.setLong(1, studentId);
                            ps.setString(2, kw);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) results.add(rs.getString("title"));
                            }
                        }
                    } else {
                        sqlBorrow = "SELECT DISTINCT b.title, s.full_name, s.student_code FROM borrow_records br " +
                                    "JOIN books b ON br.book_id = b.id " +
                                    "JOIN students s ON br.student_id = s.id " +
                                    "WHERE b.title LIKE ? OR s.full_name LIKE ? OR s.student_code LIKE ? LIMIT 6";
                        try (PreparedStatement ps = conn.prepareStatement(sqlBorrow)) {
                            ps.setString(1, kw);
                            ps.setString(2, kw);
                            ps.setString(3, kw);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    results.add(rs.getString("title") + " (" + rs.getString("student_code") + ")");
                                }
                            }
                        }
                    }
                    break;

                case "RESERVATION":
                    Long resStudentId = null;
                    if (session != null && session.getRole() == UserRole.STUDENT) {
                        StudentDTO s = studentRepository.findByUserId(session.getUserId());
                        if (s != null) resStudentId = s.getId();
                    }

                    String sqlRes;
                    if (resStudentId != null) {
                        sqlRes = "SELECT DISTINCT b.title FROM reservations r " +
                                 "JOIN books b ON r.book_id = b.id " +
                                 "WHERE r.student_id = ? AND b.title LIKE ? LIMIT 6";
                        try (PreparedStatement ps = conn.prepareStatement(sqlRes)) {
                            ps.setLong(1, resStudentId);
                            ps.setString(2, kw);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) results.add(rs.getString("title"));
                            }
                        }
                    } else {
                        sqlRes = "SELECT DISTINCT b.title, s.student_code FROM reservations r " +
                                 "JOIN books b ON r.book_id = b.id " +
                                 "JOIN students s ON r.student_id = s.id " +
                                 "WHERE b.title LIKE ? OR s.full_name LIKE ? OR s.student_code LIKE ? LIMIT 6";
                        try (PreparedStatement ps = conn.prepareStatement(sqlRes)) {
                            ps.setString(1, kw);
                            ps.setString(2, kw);
                            ps.setString(3, kw);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    results.add(rs.getString("title") + " (" + rs.getString("student_code") + ")");
                                }
                            }
                        }
                    }
                    break;

                case "FINE":
                    String sqlFine = "SELECT DISTINCT b.title, s.student_code FROM fines f " +
                                     "JOIN borrow_records br ON f.borrow_record_id = br.id " +
                                     "JOIN books b ON br.book_id = b.id " +
                                     "JOIN students s ON f.student_id = s.id " +
                                     "WHERE b.title LIKE ? OR s.full_name LIKE ? OR s.student_code LIKE ? LIMIT 6";
                    try (PreparedStatement ps = conn.prepareStatement(sqlFine)) {
                        ps.setString(1, kw);
                        ps.setString(2, kw);
                        ps.setString(3, kw);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                results.add(rs.getString("title") + " (" + rs.getString("student_code") + ")");
                            }
                        }
                    }
                    break;

                default:
                    break;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving suggestions for " + normalizedType, e);
            throw new LibraryException("Lỗi cơ sở dữ liệu khi tìm kiếm gợi ý: " + e.getMessage(), e);
        }

        return new ArrayList<>(results);
    }
}
