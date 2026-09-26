package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.enums.BorrowStatus;

public interface BorrowRepository {
    BorrowRecordDTO findById(Long id) throws SQLException;
    BorrowRecordDTO findActiveBorrow(Long studentId, Long bookId) throws SQLException;
    List<BorrowRecordDTO> findByStudentId(Long studentId) throws SQLException;
    List<BorrowRecordDTO> findOverdueBorrows() throws SQLException;
    List<BorrowRecordDTO> findAll() throws SQLException;
    Long create(BorrowRecordDTO record, Connection conn) throws SQLException;
    boolean updateStatus(Long recordId, BorrowStatus status, Date returnDate, Connection conn) throws SQLException;
}
