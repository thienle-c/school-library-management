package thuvien.server.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import thuvien.common.dto.AuditLogDTO;

public interface AuditLogRepository {
    List<AuditLogDTO> findRecent(int limit) throws SQLException;
    Long create(AuditLogDTO log, Connection conn) throws SQLException;
}
