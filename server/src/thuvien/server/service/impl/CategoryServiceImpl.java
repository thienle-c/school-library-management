package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.CategoryRepository;
import thuvien.server.repository.impl.CategoryRepositoryImpl;
import thuvien.server.service.CategoryService;

public class CategoryServiceImpl implements CategoryService {
    private static final Logger LOGGER = Logger.getLogger(CategoryServiceImpl.class.getName());

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl() {
        this(new CategoryRepositoryImpl());
    }

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryDTO getCategoryById(Integer id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Category ID cannot be null.");
        }
        try {
            CategoryDTO category = categoryRepository.findById(id);
            if (category == null) {
                throw new EntityNotFoundException("Category not found with ID: " + id);
            }
            return category;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving category ID: " + id, e);
            throw new LibraryException("Database error retrieving category: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CategoryDTO> getAllCategories() throws LibraryException {
        try {
            return categoryRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error listing categories", e);
            throw new LibraryException("Database error listing categories: " + e.getMessage(), e);
        }
    }

    @Override
    public Integer createCategory(CategoryDTO category) throws LibraryException {
        if (category == null || category.getName() == null || category.getName().trim().isEmpty()) {
            throw new LibraryException("Category name is required.");
        }
        try {
            return categoryRepository.create(category, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating category: " + category.getName(), e);
            throw new LibraryException("Database error creating category: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateCategory(CategoryDTO category) throws LibraryException {
        if (category == null || category.getId() == null) {
            throw new LibraryException("Category and Category ID are required for update.");
        }
        getCategoryById(category.getId());
        try {
            return categoryRepository.update(category, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating category ID: " + category.getId(), e);
            throw new LibraryException("Database error updating category: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteCategory(Integer id) throws LibraryException {
        if (id == null) {
            throw new LibraryException("Category ID is required for deletion.");
        }
        getCategoryById(id);
        try {
            return categoryRepository.delete(id, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting category ID: " + id, e);
            throw new LibraryException("Database error deleting category: " + e.getMessage(), e);
        }
    }
}
