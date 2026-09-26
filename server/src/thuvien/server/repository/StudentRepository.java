package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.StudentDTO;

public interface StudentRepository {
    StudentDTO findById(Long id) throws SQLException;
    StudentDTO findByStudentCode(String code) throws SQLException;
    StudentDTO findByUserId(Long userId) throws SQLException;
    List<StudentDTO> findAll() throws SQLException;
    Long create(StudentDTO student, Connection conn) throws SQLException;
    boolean update(StudentDTO student, Connection conn) throws SQLException;
    boolean incrementBorrowCount(Long studentId, Connection conn) throws SQLException;
    boolean decrementBorrowCount(Long studentId, Connection conn) throws SQLException;
    boolean delete(Long id, Connection conn) throws SQLException;
    void saveActivationCode(Long studentId, String codeHash, Connection conn) throws SQLException;
    String getActivationCodeHash(Long studentId, Connection conn) throws SQLException;
    boolean isActivationCodeUsed(Long studentId, Connection conn) throws SQLException;
    boolean markActivationCodeUsed(Long studentId, Connection conn) throws SQLException;
}
