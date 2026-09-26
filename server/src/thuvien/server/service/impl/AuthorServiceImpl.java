package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.AuthorRepository;
import thuvien.server.repository.impl.AuthorRepositoryImpl;
import thuvien.server.service.AuthorService;

public class AuthorServiceImpl implements AuthorService {
    private static final Logger LOGGER = Logger.getLogger(AuthorServiceImpl.class.getName());

    private final AuthorRepository authorRepository;

    public AuthorServiceImpl() {
        this(new AuthorRepositoryImpl());
    }

    public AuthorServiceImpl(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Override
    public AuthorDTO getAuthorById(Integer id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Author ID cannot be null.");
        }
        try {
            AuthorDTO author = authorRepository.findById(id);
            if (author == null) {
                throw new EntityNotFoundException("Author not found with ID: " + id);
            }
            return author;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving author ID: " + id, e);
            throw new LibraryException("Database error retrieving author: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AuthorDTO> getAuthorsByBookId(Long bookId) throws LibraryException {
        if (bookId == null) {
            throw new LibraryException("Book ID cannot be null.");
        }
        try {
            return authorRepository.findByBookId(bookId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving authors for book ID: " + bookId, e);
            throw new LibraryException("Database error retrieving authors for book: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AuthorDTO> getAllAuthors() throws LibraryException {
        try {
            return authorRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error listing authors", e);
            throw new LibraryException("Database error listing authors: " + e.getMessage(), e);
        }
    }

    @Override
    public Integer createAuthor(AuthorDTO author) throws LibraryException {
        if (author == null || author.getName() == null || author.getName().trim().isEmpty()) {
            throw new LibraryException("Author name is required.");
        }
        try {
            return authorRepository.create(author, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating author: " + author.getName(), e);
            throw new LibraryException("Database error creating author: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateAuthor(AuthorDTO author) throws LibraryException {
        if (author == null || author.getId() == null) {
            throw new LibraryException("Author and Author ID are required for update.");
        }
        getAuthorById(author.getId());
        try {
            return authorRepository.update(author, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating author ID: " + author.getId(), e);
            throw new LibraryException("Database error updating author: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteAuthor(Integer id) throws LibraryException {
        if (id == null) {
            throw new LibraryException("Author ID is required for deletion.");
        }
        getAuthorById(id);
        try {
            return authorRepository.delete(id, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting author ID: " + id, e);
            throw new LibraryException("Database error deleting author: " + e.getMessage(), e);
        }
    }
}
