package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.ReservationDTO;
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
 * Client controller for book reservation operations over TCP.
 */
public class ClientReservationController {
    private final NetworkClient networkClient;

    public ClientReservationController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = response.getMessage() != null && !response.getMessage().isEmpty()
                ? response.getMessage()
                : "Reservation operation failed with status " + code;

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
    public List<ReservationDTO> listAllReservations() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_RESERVATIONS, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<ReservationDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<ReservationDTO> listStudentReservations(Long studentId) throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_RESERVATIONS, studentId);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<ReservationDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    public ReservationDTO createReservation(Long bookId, Long studentId) throws LibraryException, NetworkException {
        if (bookId == null || bookId <= 0) {
            throw new ValidationException("Valid Book ID is required for reservation.");
        }
        ReservationDTO payload = new ReservationDTO();
        payload.setBookId(bookId);
        payload.setStudentId(studentId);

        Request request = new Request(Action.CREATE_RESERVATION, payload);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof ReservationDTO) {
            return (ReservationDTO) response.getData();
        }
        return null;
    }

    public boolean cancelReservation(Long reservationId) throws LibraryException, NetworkException {
        if (reservationId == null || reservationId <= 0) {
            throw new ValidationException("Valid Reservation ID is required for cancellation.");
        }
        Request request = new Request(Action.CANCEL_RESERVATION, reservationId);
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }
}
