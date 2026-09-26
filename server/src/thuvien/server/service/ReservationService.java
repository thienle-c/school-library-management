package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface ReservationService {
    ReservationDTO createReservation(Long studentId, Long bookId) throws LibraryException;
    ReservationDTO getReservationById(Long reservationId) throws EntityNotFoundException, LibraryException;
    boolean cancelReservation(Long reservationId) throws EntityNotFoundException, LibraryException;
    List<ReservationDTO> getReservationsByStudent(Long studentId) throws LibraryException;
    List<ReservationDTO> getAllReservations() throws LibraryException;
}
