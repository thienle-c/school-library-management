package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.FineDTO;

public interface FineRepository {
    FineDTO findById(Long id) throws SQLException;
    FineDTO findByBorrowRecordId(Long borrowRecordId) throws SQLException;
    List<FineDTO> findByStudentId(Long studentId) throws SQLException;
    List<FineDTO> findUnpaidFines() throws SQLException;
    List<FineDTO> findAll() throws SQLException;
    Long create(FineDTO fine, Connection conn) throws SQLException;
    boolean markAsPaid(Long fineId, Long collectedByUserId, Connection conn) throws SQLException;
}
