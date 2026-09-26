package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.AuthorDTO;

public interface AuthorRepository {
    AuthorDTO findById(Integer id) throws SQLException;
    List<AuthorDTO> findByBookId(Long bookId) throws SQLException;
    List<AuthorDTO> findAll() throws SQLException;
    Integer create(AuthorDTO author, Connection conn) throws SQLException;
    boolean update(AuthorDTO author, Connection conn) throws SQLException;
    boolean delete(Integer id, Connection conn) throws SQLException;
}
