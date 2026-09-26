package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface AuthorService {
    AuthorDTO getAuthorById(Integer id) throws EntityNotFoundException, LibraryException;
    List<AuthorDTO> getAuthorsByBookId(Long bookId) throws LibraryException;
    List<AuthorDTO> getAllAuthors() throws LibraryException;
    Integer createAuthor(AuthorDTO author) throws LibraryException;
    boolean updateAuthor(AuthorDTO author) throws LibraryException;
    boolean deleteAuthor(Integer id) throws LibraryException;
}
