package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.enums.ReservationStatus;

public interface ReservationRepository {
    ReservationDTO findById(Long id) throws SQLException;
    List<ReservationDTO> findByStudentId(Long studentId) throws SQLException;
    List<ReservationDTO> findActiveByBookId(Long bookId) throws SQLException;
    List<ReservationDTO> findAll() throws SQLException;
    Long create(ReservationDTO reservation, Connection conn) throws SQLException;
    boolean updateStatus(Long reservationId, ReservationStatus status, Connection conn) throws SQLException;
}
