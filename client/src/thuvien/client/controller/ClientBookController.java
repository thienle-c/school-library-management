package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

/**
 * Client controller for Book Management operations.
 * Communicates with LibraryServer over TCP via Request/Response protocol.
 */
public class ClientBookController {
    private final NetworkClient networkClient;

    public ClientBookController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = response.getMessage() != null && !response.getMessage().isEmpty()
                ? response.getMessage()
                : "Server operation failed with status code " + code;

        if (code == StatusCode.UNAUTHORIZED) {
            ClientSession.getInstance().clear();
            throw new AuthenticationException("Session expired or unauthorized: " + msg);
        } else if (code == StatusCode.FORBIDDEN) {
            throw new AuthorizationException("Permission denied: " + msg);
        } else if (code == StatusCode.NOT_FOUND) {
            throw new EntityNotFoundException(msg);
        } else if (code == StatusCode.BAD_REQUEST) {
            throw new ValidationException(msg);
        } else {
            throw new LibraryException(msg);
        }
    }

    @SuppressWarnings("unchecked")
    public List<BookDTO> listBooks() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_BOOKS, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<BookDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public PageResponseDTO<BookDTO> searchBooks(BookSearchCriteriaDTO criteria) throws LibraryException, NetworkException {
        Request request = new Request(Action.SEARCH_BOOKS, criteria);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof PageResponseDTO) {
            return (PageResponseDTO<BookDTO>) response.getData();
        }
        return new PageResponseDTO<>(Collections.emptyList(), 1, 20, 0);
    }

    public BookDTO getBook(Long id) throws LibraryException, NetworkException {
        if (id == null) {
            throw new ValidationException("Book ID cannot be null.");
        }
        Request request = new Request(Action.GET_BOOK, id);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof BookDTO) {
            return (BookDTO) response.getData();
        }
        throw new EntityNotFoundException("Book with ID " + id + " not found.");
    }

    public Long createBook(BookDTO book) throws LibraryException, NetworkException {
        if (book == null) {
            throw new ValidationException("Book data cannot be null.");
        }
        Request request = new Request(Action.CREATE_BOOK, book);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof Number) {
            return ((Number) response.getData()).longValue();
        }
        return null;
    }

    public boolean updateBook(BookDTO book) throws LibraryException, NetworkException {
        if (book == null || book.getId() == null) {
            throw new ValidationException("Book ID is required for update.");
        }
        Request request = new Request(Action.UPDATE_BOOK, book);
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }

    public boolean deleteBook(Long id) throws LibraryException, NetworkException {
        if (id == null) {
            throw new ValidationException("Book ID is required for deletion.");
        }
        Request request = new Request(Action.DELETE_BOOK, id);
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }

    @SuppressWarnings("unchecked")
    public List<CategoryDTO> listCategories() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_CATEGORIES, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<CategoryDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<AuthorDTO> listAuthors() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_AUTHORS, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<AuthorDTO>) response.getData();
        }
        return Collections.emptyList();
    }
}
