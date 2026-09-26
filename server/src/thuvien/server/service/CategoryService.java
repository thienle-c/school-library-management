package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface CategoryService {
    CategoryDTO getCategoryById(Integer id) throws EntityNotFoundException, LibraryException;
    List<CategoryDTO> getAllCategories() throws LibraryException;
    Integer createCategory(CategoryDTO category) throws LibraryException;
    boolean updateCategory(CategoryDTO category) throws LibraryException;
    boolean deleteCategory(Integer id) throws LibraryException;
}
