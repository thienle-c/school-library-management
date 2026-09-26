package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.server.repository.UserRepository;
import thuvien.server.repository.impl.UserRepositoryImpl;
import thuvien.server.security.PasswordHasher;
import thuvien.server.security.SessionManager;
import thuvien.server.service.AuditLogService;
import thuvien.server.service.AuthService;

/**
 * Hardened Authentication Service with PBKDF2 verification, automatic legacy password
 * migration, rate-limiting lockout protection (5 failed attempts -> 15 min lock),
 * and security event audit logging.
 */
public class AuthServiceImpl implements AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthServiceImpl.class.getName());

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final AuditLogService auditLogService;

    // Track failed attempts and lockouts per username and userId
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutUntil = new ConcurrentHashMap<>();
    private final Map<Long, Long> userIdLockoutUntil = new ConcurrentHashMap<>();

    public AuthServiceImpl() {
        this(new UserRepositoryImpl(), SessionManager.getInstance(), new AuditLogServiceImpl());
    }

    public AuthServiceImpl(UserRepository userRepository) {
        this(userRepository, SessionManager.getInstance(), null);
    }

    public AuthServiceImpl(UserRepository userRepository, SessionManager sessionManager) {
        this(userRepository, sessionManager, null);
    }

    public AuthServiceImpl(UserRepository userRepository, SessionManager sessionManager, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.sessionManager = sessionManager != null ? sessionManager : SessionManager.getInstance();
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean isAccountLocked(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String clean = username.trim();
        String lower = clean.toLowerCase();
        Long lockedUntil = lockoutUntil.get(lower);
        if (lockedUntil == null) {
            lockedUntil = lockoutUntil.get(clean);
        }
        if (lockedUntil != null) {
            long now = System.currentTimeMillis();
            if (now < lockedUntil) {
                return true;
            } else {
                // Lockout period expired lazily
                lockoutUntil.remove(lower);
                lockoutUntil.remove(clean);
                failedAttempts.remove(lower);
                failedAttempts.remove(clean);
                try {
                    UserDTO u = userRepository.findByUsername(clean);
                    if (u != null) {
                        userIdLockoutUntil.remove(u.getId());
                    }
                } catch (Exception ignored) {}
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isAccountLocked(Long userId) {
        if (userId == null) {
            return false;
        }
        Long lockedUntil = userIdLockoutUntil.get(userId);
        if (lockedUntil != null) {
            long now = System.currentTimeMillis();
            if (now < lockedUntil) {
                return true;
            } else {
                userIdLockoutUntil.remove(userId);
                return false;
            }
        }
        return false;
    }

    @Override
    public void unlockAccount(String username) {
        if (username == null) return;
        String clean = username.trim();
        String lower = clean.toLowerCase();
        lockoutUntil.remove(lower);
        lockoutUntil.remove(clean);
        failedAttempts.remove(lower);
        failedAttempts.remove(clean);
        try {
            UserDTO u = userRepository.findByUsername(clean);
            if (u != null) {
                userIdLockoutUntil.remove(u.getId());
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void unlockAccount(Long userId) {
        if (userId == null) return;
        userIdLockoutUntil.remove(userId);
    }

    @Override
    public Long getLockoutExpiry(String username) {
        if (username == null) return null;
        String clean = username.trim();
        Long exp = lockoutUntil.get(clean.toLowerCase());
        if (exp == null) exp = lockoutUntil.get(clean);
        return exp;
    }

    @Override
    public UserSessionDTO login(LoginRequestDTO request) throws AuthenticationException {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new AuthenticationException("Tên đăng nhập và mật khẩu không được để trống.");
        }

        String username = request.getUsername().trim();
        String plainPassword = request.getPassword();

        if (username.isEmpty() || plainPassword.isEmpty()) {
            throw new AuthenticationException("Tên đăng nhập và mật khẩu không được để trống.");
        }

        long now = System.currentTimeMillis();

        // 1. Check temporary rate-limiting lock BEFORE password check
        if (isAccountLocked(username)) {
            Long lockedUntil = getLockoutExpiry(username);
            long remainingMinutes = lockedUntil != null ? Math.max(1, (long) Math.ceil((lockedUntil - now) / 60000.0)) : 15;
            if (auditLogService != null) {
                Long uid = null;
                try {
                    UserDTO u = userRepository.findByUsername(username);
                    if (u != null) uid = u.getId();
                } catch (Exception ignored) {}
                auditLogService.log(uid, "LOGIN_BLOCKED_LOCKED", "User", uid != null ? uid : 0L,
                        "Đăng nhập bị từ chối do tài khoản đang bị tạm khóa");
            }
            throw new AuthenticationException(String.format(
                    "Tài khoản đã bị tạm khóa do nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau %d phút.",
                    remainingMinutes));
        }

        try {
            UserDTO user = userRepository.findByUsername(username);
            if (user != null && isAccountLocked(user.getId())) {
                if (auditLogService != null) {
                    auditLogService.log(user.getId(), "LOGIN_BLOCKED_LOCKED", "User", user.getId(),
                            "Đăng nhập bị từ chối do tài khoản đang bị tạm khóa");
                }
                Long exp = userIdLockoutUntil.get(user.getId());
                long remainingMinutes = exp != null ? Math.max(1, (long) Math.ceil((exp - now) / 60000.0)) : 15;
                throw new AuthenticationException(String.format(
                        "Tài khoản đã bị tạm khóa do nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau %d phút.",
                        remainingMinutes));
            }

            if (user == null) {
                recordFailedAttempt(username, null, "Người dùng không tồn tại");
                throw new AuthenticationException("Tên đăng nhập hoặc mật khẩu không chính xác.");
            }

            if (!user.isActive()) {
                recordFailedAttempt(username, user.getId(), "Tài khoản đang bị vô hiệu hóa");
                throw new AuthenticationException("Tài khoản đang bị vô hiệu hóa hoặc chưa kích hoạt.");
            }

            String storedHash = userRepository.getPasswordHash(username);
            if (storedHash == null || !PasswordHasher.verify(plainPassword, storedHash)) {
                recordFailedAttempt(username, user.getId(), "Mật khẩu không khớp");
                throw new AuthenticationException("Tên đăng nhập hoặc mật khẩu không chính xác.");
            }

            // 2. Successful Login: Clear failed attempts and lockouts
            failedAttempts.remove(username);
            failedAttempts.remove(username.toLowerCase());
            lockoutUntil.remove(username);
            lockoutUntil.remove(username.toLowerCase());
            userIdLockoutUntil.remove(user.getId());

            // 3. Automatic legacy hash migration to PBKDF2 if needed
            if (PasswordHasher.isLegacyHash(storedHash)) {
                try {
                    String newPbkdf2Hash = PasswordHasher.hash(plainPassword);
                    userRepository.updatePassword(user.getId(), newPbkdf2Hash, null);
                    LOGGER.info("Successfully upgraded password to PBKDF2 for user: " + username);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to upgrade password hash for: " + username, e);
                }
            }

            UserSessionDTO session = new UserSessionDTO(
                    null,
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getRole()
            );
            sessionManager.createSession(session);

            if (auditLogService != null) {
                auditLogService.log(user.getId(), "LOGIN_SUCCESS", "User", user.getId(),
                        "Đăng nhập thành công với vai trò " + user.getRole());
            }

            return session;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during user login authentication for: " + username, e);
            throw new AuthenticationException("Dịch vụ xác thực tạm thời không khả dụng: " + e.getMessage());
        }
    }

    private void recordFailedAttempt(String username, Long userId, String reason) throws AuthenticationException {
        String lower = username.toLowerCase();
        int attempts = failedAttempts.getOrDefault(lower, 0) + 1;
        failedAttempts.put(lower, attempts);
        failedAttempts.put(username, attempts);

        if (auditLogService != null) {
            auditLogService.log(userId, "LOGIN_FAILED", "User", userId != null ? userId : 0L,
                    "Đăng nhập thất bại (lần " + attempts + "/" + MAX_FAILED_ATTEMPTS + ") - " + reason);
        }

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            long lockTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            lockoutUntil.put(lower, lockTime);
            lockoutUntil.put(username, lockTime);

            if (userId == null) {
                try {
                    UserDTO u = userRepository.findByUsername(username);
                    if (u != null) {
                        userId = u.getId();
                    }
                } catch (Exception ignored) {}
            }

            if (userId != null) {
                userIdLockoutUntil.put(userId, lockTime);
                sessionManager.revokeAllSessionsForUser(userId);
            }
            sessionManager.revokeAllSessionsForUsername(username);
            sessionManager.revokeAllSessionsForUsername(lower);

            failedAttempts.remove(lower);
            failedAttempts.remove(username);

            if (auditLogService != null) {
                auditLogService.log(userId, "ACCOUNT_LOCKED", "User", userId != null ? userId : 0L,
                        "Tài khoản bị tạm khóa 15 phút do 5 lần đăng nhập thất bại");
            }
            LOGGER.warning("Account temporarily locked for 15 minutes due to 5 failed attempts: " + username);
            throw new AuthenticationException("Tài khoản đã bị tạm khóa 15 phút do nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau.");
        }
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            UserSessionDTO session = sessionManager.getSession(token);
            if (session != null && auditLogService != null) {
                auditLogService.log(session.getUserId(), "LOGOUT", "User", session.getUserId(), "Đăng xuất tài khoản");
            }
            sessionManager.removeSession(token);
        }
    }

    @Override
    public UserSessionDTO validateSession(String token) throws AuthenticationException {
        if (token == null || token.trim().isEmpty()) {
            throw new AuthenticationException("Session token is required.");
        }
        UserSessionDTO session = sessionManager.getSession(token);
        if (session == null) {
            throw new AuthenticationException("Session is invalid or has expired.");
        }
        return session;
    }
}
