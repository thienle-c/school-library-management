package thuvien.client.controller;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.FineDTO;
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
 * Client controller for overdue fines and fee payment operations over TCP.
 */
public class ClientFineController {
    private final NetworkClient networkClient;

    public ClientFineController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = response.getMessage() != null && !response.getMessage().isEmpty()
                ? response.getMessage()
                : "Fine operation failed with status " + code;

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
    public List<FineDTO> listAllFines() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_FINES, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<FineDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<FineDTO> listUnpaidFines() throws LibraryException, NetworkException {
        return listAllFines();
    }

    @SuppressWarnings("unchecked")
    public List<FineDTO> listStudentFines(Long studentId) throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_FINES, studentId);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<FineDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    public BigDecimal calculateFine(int overdueDays) throws LibraryException, NetworkException {
        Request request = new Request(Action.CALCULATE_FINE, overdueDays);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof BigDecimal) {
            return (BigDecimal) response.getData();
        }
        return BigDecimal.ZERO;
    }

    public boolean payFine(Long fineId) throws LibraryException, NetworkException {
        if (fineId == null || fineId <= 0) {
            throw new ValidationException("Valid Fine ID is required for payment.");
        }
        Request request = new Request(Action.PAY_FINE, fineId);
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }
}
