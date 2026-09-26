package thuvien.server.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import thuvien.common.dto.UserSessionDTO;

/**
 * In-memory Session Manager with idle session expiration and revocation tracking.
 */
public class SessionManager {
    private static SessionManager instance;

    // 30 minutes idle session expiration
    public static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000L;

    private static class SessionEntry {
        final UserSessionDTO session;
        final long createdAt;
        volatile long lastAccessedAt;

        SessionEntry(UserSessionDTO session) {
            this.session = session;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessedAt = this.createdAt;
        }

        boolean isExpired(long now) {
            return (now - lastAccessedAt) > IDLE_TIMEOUT_MS;
        }

        void touch(long now) {
            this.lastAccessedAt = now;
        }
    }

    private final Map<String, SessionEntry> activeSessions = new ConcurrentHashMap<>();

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public String createSession(UserSessionDTO user) {
        String token = UUID.randomUUID().toString();
        user.setToken(token);
        activeSessions.put(token, new SessionEntry(user));
        return token;
    }

    public UserSessionDTO getSession(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        SessionEntry entry = activeSessions.get(token);
        if (entry == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (entry.isExpired(now)) {
            activeSessions.remove(token);
            return null;
        }

        entry.touch(now);
        return entry.session;
    }

    public void removeSession(String token) {
        if (token != null) {
            activeSessions.remove(token);
        }
    }

    public void invalidateUserSessions(Long userId) {
        if (userId == null) return;
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().session.getUserId() != null &&
                entry.getValue().session.getUserId().equals(userId)
        );
    }

    public void revokeAllSessionsForUser(Long userId) {
        invalidateUserSessions(userId);
    }

    public void revokeAllSessionsForUsername(String username) {
        if (username == null) return;
        String cleanUser = username.trim();
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().session.getUsername() != null &&
                entry.getValue().session.getUsername().equalsIgnoreCase(cleanUser)
        );
    }

    public void revokeSession(String token) {
        removeSession(token);
    }

    public void clearAllSessions() {
        activeSessions.clear();
    }

    public boolean isValid(String token) {
        return getSession(token) != null;
    }
}
