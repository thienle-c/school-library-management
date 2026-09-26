package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.exception.LibraryException;
import thuvien.server.repository.AuditLogRepository;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.service.AuditLogService;

public class AuditLogServiceImpl implements AuditLogService {
    private static final Logger LOGGER = Logger.getLogger(AuditLogServiceImpl.class.getName());

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl() {
        this(new AuditLogRepositoryImpl());
    }

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void log(Long userId, String action, String entityName, Long entityId, String details) {
        try {
            AuditLogDTO dto = new AuditLogDTO();
            dto.setUserId(userId);
            dto.setAction(action);
            dto.setEntityName(entityName);
            dto.setEntityId(entityId);
            dto.setDetails(details);
            dto.setCreatedAt(new Date());

            auditLogRepository.create(dto, null);
        } catch (SQLException e) {
            // Audit log failure must be logged but should not crash primary operations
            LOGGER.log(Level.WARNING, "Failed to record audit log for action: " + action + " on entity: " + entityName, e);
        }
    }

    @Override
    public List<AuditLogDTO> getRecentLogs(int limit) throws LibraryException {
        try {
            return auditLogRepository.findRecent(limit > 0 ? limit : 50);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving recent audit logs", e);
            throw new LibraryException("Database error retrieving audit logs: " + e.getMessage(), e);
        }
    }
}
