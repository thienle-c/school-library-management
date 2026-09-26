package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface BookService {
    BookDTO getBookById(Long id) throws EntityNotFoundException, LibraryException;
    BookDTO getBookByIsbn(String isbn) throws EntityNotFoundException, LibraryException;
    PageResponseDTO<BookDTO> searchBooks(BookSearchCriteriaDTO criteria) throws LibraryException;
    List<BookDTO> getAllBooks() throws LibraryException;
    Long createBook(BookDTO book) throws LibraryException;
    boolean updateBook(BookDTO book) throws LibraryException;
    boolean deleteBook(Long id) throws LibraryException;
}
