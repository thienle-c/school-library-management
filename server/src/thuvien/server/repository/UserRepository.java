package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.UserDTO;

public interface UserRepository {
    UserDTO findById(Long id) throws SQLException;
    UserDTO findByUsername(String username) throws SQLException;
    String getPasswordHash(String username) throws SQLException;
    List<UserDTO> findAll() throws SQLException;
    Long create(UserDTO user, String passwordHash, Connection conn) throws SQLException;
    boolean update(UserDTO user, Connection conn) throws SQLException;
    boolean updatePassword(Long userId, String newPasswordHash, Connection conn) throws SQLException;
    boolean delete(Long id, Connection conn) throws SQLException;
}
