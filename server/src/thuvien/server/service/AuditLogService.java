package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.exception.LibraryException;

public interface AuditLogService {
    void log(Long userId, String action, String entityName, Long entityId, String details);
    List<AuditLogDTO> getRecentLogs(int limit) throws LibraryException;
}
