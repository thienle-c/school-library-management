package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.enums.BookStatus;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.service.BookService;

public class BookServiceImpl implements BookService {
    private static final Logger LOGGER = Logger.getLogger(BookServiceImpl.class.getName());

    private final BookRepository bookRepository;

    public BookServiceImpl() {
        this(new BookRepositoryImpl());
    }

    public BookServiceImpl(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public BookDTO getBookById(Long id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Book ID cannot be null.");
        }
        try {
            BookDTO book = bookRepository.findById(id);
            if (book == null) {
                throw new EntityNotFoundException("Book not found with ID: " + id);
            }
            return book;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving book with ID: " + id, e);
            throw new LibraryException("Database error retrieving book: " + e.getMessage(), e);
        }
    }

    @Override
    public BookDTO getBookByIsbn(String isbn) throws EntityNotFoundException, LibraryException {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new EntityNotFoundException("ISBN cannot be null or empty.");
        }
        try {
            BookDTO book = bookRepository.findByIsbn(isbn.trim());
            if (book == null) {
                throw new EntityNotFoundException("Book not found with ISBN: " + isbn);
            }
            return book;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving book with ISBN: " + isbn, e);
            throw new LibraryException("Database error retrieving book: " + e.getMessage(), e);
        }
    }

    @Override
    public PageResponseDTO<BookDTO> searchBooks(BookSearchCriteriaDTO criteria) throws LibraryException {
        if (criteria == null) {
            criteria = new BookSearchCriteriaDTO();
        }
        try {
            return bookRepository.search(criteria);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error searching books", e);
            throw new LibraryException("Database error searching books: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BookDTO> getAllBooks() throws LibraryException {
        try {
            return bookRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error listing all books", e);
            throw new LibraryException("Database error listing all books: " + e.getMessage(), e);
        }
    }

    @Override
    public Long createBook(BookDTO book) throws LibraryException {
        if (book == null) {
            throw new LibraryException("Book payload cannot be null.");
        }
        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            throw new LibraryException("Book title is required.");
        }
        if (book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            throw new LibraryException("Book ISBN is required.");
        }
        if (book.getTotalCopies() < 0) {
            throw new LibraryException("Total copies cannot be negative.");
        }

        if (book.getAvailableCopies() <= 0) {
            book.setAvailableCopies(book.getTotalCopies());
        }
        if (book.getStatus() == null) {
            book.setStatus(BookStatus.AVAILABLE);
        }

        try {
            return bookRepository.create(book, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating book: " + book.getTitle(), e);
            throw new LibraryException("Database error creating book: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateBook(BookDTO book) throws LibraryException {
        if (book == null || book.getId() == null) {
            throw new LibraryException("Book and Book ID are required for update.");
        }
        // Verify existence
        getBookById(book.getId());

        try {
            return bookRepository.update(book, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating book ID: " + book.getId(), e);
            throw new LibraryException("Database error updating book: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteBook(Long id) throws LibraryException {
        if (id == null) {
            throw new LibraryException("Book ID is required for deletion.");
        }
        // Verify existence
        getBookById(id);

        try {
            return bookRepository.delete(id, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting book ID: " + id, e);
            throw new LibraryException("Database error deleting book: " + e.getMessage(), e);
        }
    }
}
