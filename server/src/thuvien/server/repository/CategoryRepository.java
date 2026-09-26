package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.CategoryDTO;

public interface CategoryRepository {
    CategoryDTO findById(Integer id) throws SQLException;
    List<CategoryDTO> findAll() throws SQLException;
    Integer create(CategoryDTO category, Connection conn) throws SQLException;
    boolean update(CategoryDTO category, Connection conn) throws SQLException;
    boolean delete(Integer id, Connection conn) throws SQLException;
}
