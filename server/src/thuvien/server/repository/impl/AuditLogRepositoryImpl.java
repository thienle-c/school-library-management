package thuvien.server.repository.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import thuvien.common.dto.AuditLogDTO;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.AuditLogRepository;

public class AuditLogRepositoryImpl implements AuditLogRepository {
    private final DatabaseManager databaseManager;

    private static final String BASE_SELECT =
            "SELECT a.id, a.user_id, u.username, a.action, a.entity_name, " +
            "a.entity_id, a.details, a.created_at " +
            "FROM audit_logs a " +
            "LEFT JOIN users u ON a.user_id = u.id ";

    public AuditLogRepositoryImpl() {
        this(DatabaseManager.getInstance());
    }

    public AuditLogRepositoryImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<AuditLogDTO> findRecent(int limit) throws SQLException {
        String sql = BASE_SELECT + "ORDER BY a.created_at DESC, a.id DESC LIMIT ?";
        List<AuditLogDTO> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit > 0 ? limit : 50);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public Long create(AuditLogDTO log, Connection conn) throws SQLException {
        String sql = "INSERT INTO audit_logs (user_id, action, entity_name, entity_id, details, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        boolean localConn = (conn == null);
        Connection activeConn = localConn ? databaseManager.getConnection() : conn;
        try (PreparedStatement stmt = activeConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getUserId() != null) {
                stmt.setLong(1, log.getUserId());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }

            stmt.setString(2, log.getAction());
            stmt.setString(3, log.getEntityName());

            if (log.getEntityId() != null) {
                stmt.setLong(4, log.getEntityId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }

            stmt.setString(5, log.getDetails());

            if (log.getCreatedAt() != null) {
                stmt.setTimestamp(6, new Timestamp(log.getCreatedAt().getTime()));
            } else {
                stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            }

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating audit log failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    log.setId(id);
                    return id;
                } else {
                    throw new SQLException("Creating audit log failed, no ID obtained.");
                }
            }
        } finally {
            if (localConn && activeConn != null) {
                activeConn.close();
            }
        }
    }

    private AuditLogDTO mapRow(ResultSet rs) throws SQLException {
        AuditLogDTO log = new AuditLogDTO();
        log.setId(rs.getLong("id"));

        long userId = rs.getLong("user_id");
        if (!rs.wasNull()) {
            log.setUserId(userId);
        }

        log.setUsername(rs.getString("username"));
        log.setAction(rs.getString("action"));
        log.setEntityName(rs.getString("entity_name"));

        long entityId = rs.getLong("entity_id");
        if (!rs.wasNull()) {
            log.setEntityId(entityId);
        }

        log.setDetails(rs.getString("details"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            log.setCreatedAt(new java.util.Date(createdAt.getTime()));
        }

        return log;
    }
}
