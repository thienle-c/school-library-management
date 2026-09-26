package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.ReturnResultDTO;
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
 * Client controller for Borrow and Return operations over TCP.
 */
public class ClientBorrowController {
    private final NetworkClient networkClient;

    public ClientBorrowController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = response.getMessage() != null && !response.getMessage().isEmpty()
                ? response.getMessage()
                : "Borrow/Return operation failed with status " + code;

        if (code == StatusCode.UNAUTHORIZED) {
            ClientSession.getInstance().clear();
            throw new AuthenticationException("Session expired: " + msg);
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
    public List<BorrowRecordDTO> listAllBorrowRecords() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_BORROW_RECORDS, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<BorrowRecordDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<BorrowRecordDTO> getStudentActiveBorrows(Long studentId) throws LibraryException, NetworkException {
        Request request = new Request(Action.GET_STUDENT_ACTIVE_BORROWS, studentId);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<BorrowRecordDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    public List<BorrowRecordDTO> getMyActiveLoans() throws LibraryException, NetworkException {
        return getStudentActiveBorrows(null);
    }

    public BorrowRecordDTO borrowBook(BorrowRequestDTO borrowReq) throws LibraryException, NetworkException {
        if (borrowReq == null) {
            throw new ValidationException("Borrow request cannot be null.");
        }
        Request request = new Request(Action.BORROW_BOOK, borrowReq);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof BorrowRecordDTO) {
            return (BorrowRecordDTO) response.getData();
        }
        return null;
    }

    public ReturnResultDTO returnBook(Long studentId, Long bookId) throws LibraryException, NetworkException {
        if (studentId == null || bookId == null) {
            throw new ValidationException("Student ID and Book ID are required for return.");
        }
        BorrowRequestDTO payload = new BorrowRequestDTO(studentId, bookId);
        Request request = new Request(Action.RETURN_BOOK, payload);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof ReturnResultDTO) {
            return (ReturnResultDTO) response.getData();
        }
        return null;
    }
}
