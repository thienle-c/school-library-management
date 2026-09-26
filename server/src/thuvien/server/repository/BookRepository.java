package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.PageResponseDTO;

public interface BookRepository {
    BookDTO findById(Long id) throws SQLException;
    BookDTO findByIsbn(String isbn) throws SQLException;
    PageResponseDTO<BookDTO> search(BookSearchCriteriaDTO criteria) throws SQLException;
    List<BookDTO> findAll() throws SQLException;
    Long create(BookDTO book, Connection conn) throws SQLException;
    boolean update(BookDTO book, Connection conn) throws SQLException;
    boolean delete(Long id, Connection conn) throws SQLException;
    
    // Concurrency-safe atomic inventory updates
    boolean decrementAvailableCopies(Long bookId, Connection conn) throws SQLException;
    boolean incrementAvailableCopies(Long bookId, Connection conn) throws SQLException;
}
