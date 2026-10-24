package thuvien.server.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.AdminResetPasswordRequestDTO;
import thuvien.common.dto.AdminResetPasswordResponseDTO;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.TestDatabaseSafetyGuard;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.repository.impl.UserRepositoryImpl;
import thuvien.server.router.RequestRouter;
import thuvien.server.security.PasswordHasher;
import thuvien.server.security.SessionManager;
import thuvien.server.service.impl.AuditLogServiceImpl;
import thuvien.server.service.impl.AuthorServiceImpl;
import thuvien.server.service.impl.AuthServiceImpl;
import thuvien.server.service.impl.BookServiceImpl;
import thuvien.server.service.impl.BorrowServiceImpl;
import thuvien.server.service.impl.CategoryServiceImpl;
import thuvien.server.service.impl.DashboardServiceImpl;
import thuvien.server.service.impl.FineServiceImpl;
import thuvien.server.service.impl.ReservationServiceImpl;
import thuvien.server.service.impl.ReturnServiceImpl;
import thuvien.server.service.impl.SearchSuggestionServiceImpl;
import thuvien.server.service.impl.StudentServiceImpl;
import thuvien.server.service.impl.UserServiceImpl;

/**
 * Comprehensive Automated Test Suite for Admin Reset Password functionality
 * and librarian2 legacy account recovery flow on school_library_test.
 */
public class AdminResetPasswordTest {

    private DatabaseManager db;
    private SessionManager sessionManager;
    private AuthServiceImpl authService;
    private AuditLogServiceImpl auditLogService;
    private UserServiceImpl userService;
    private RequestRouter router;

    private boolean isIsolatedTestDb = false;
    private String adminToken;
    private String librarianToken;
    private String studentToken;

    private Long testTargetUserId;
    private Long testInactiveUserId;

    private void requireIsolatedTestDb() {
        org.junit.Assume.assumeTrue("[SAFETY] Test disabled on non-test database.", isIsolatedTestDb);
    }

    @Before
    public void setUp() throws Exception {
        db = DatabaseManager.getInstance();
        sessionManager = SessionManager.getInstance();
        sessionManager.clearAllSessions();

        try (Connection conn = db.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                isIsolatedTestDb = TestDatabaseSafetyGuard.isSafeTestDatabase(conn);
            }
        } catch (Exception e) {
            isIsolatedTestDb = false;
        }

        requireIsolatedTestDb();

        auditLogService = new AuditLogServiceImpl(new AuditLogRepositoryImpl(db));
        authService = new AuthServiceImpl(new UserRepositoryImpl(db), sessionManager, auditLogService);
        userService = new UserServiceImpl(new UserRepositoryImpl(db));

        router = new RequestRouter(
                authService,
                new BookServiceImpl(),
                new StudentServiceImpl(new StudentRepositoryImpl(db), new UserRepositoryImpl(db), auditLogService),
                new BorrowServiceImpl(),
                new ReturnServiceImpl(),
                new FineServiceImpl(),
                new DashboardServiceImpl(),
                new ReservationServiceImpl(),
                auditLogService,
                new CategoryServiceImpl(),
                new AuthorServiceImpl(),
                userService,
                new SearchSuggestionServiceImpl(db, new StudentRepositoryImpl(db))
        );

        // Create standard sessions
        adminToken = sessionManager.createSession(new UserSessionDTO(null, 1L, "admin", "Admin System", UserRole.ADMIN, false));
        librarianToken = sessionManager.createSession(new UserSessionDTO(null, 2L, "librarian1", "Librarian One", UserRole.LIBRARIAN, false));
        studentToken = sessionManager.createSession(new UserSessionDTO(null, 4L, "student1", "Student One", UserRole.STUDENT, false));

        // Create a temporary active test user
        cleanUpTestUsers();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role, full_name, email, is_active, must_change_password) " +
                     "VALUES (?, ?, 'STUDENT', 'Test Target User', 'target@test.edu', 1, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "test_target_reset_" + System.currentTimeMillis());
            stmt.setString(2, PasswordHasher.hash("InitialPassword#123"));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    testTargetUserId = rs.getLong(1);
                }
            }
        }

        // Create a temporary inactive test user
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role, full_name, email, is_active, must_change_password) " +
                     "VALUES (?, ?, 'STUDENT', 'Test Inactive User', 'inactive@test.edu', 0, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "test_inactive_reset_" + System.currentTimeMillis());
            stmt.setString(2, PasswordHasher.hash("InitialPassword#123"));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    testInactiveUserId = rs.getLong(1);
                }
            }
        }
    }

    @After
    public void tearDown() {
        if (isIsolatedTestDb) {
            cleanUpTestUsers();
        }
        if (sessionManager != null) {
            sessionManager.clearAllSessions();
        }
    }

    private void cleanUpTestUsers() {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username LIKE 'test_%_reset_%'")) {
                stmt.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    @Test
    public void testAdminResetSuccess() {
        requireIsolatedTestDb();
        Request req = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response resp = router.route(req);

        Assert.assertNotNull(resp);
        Assert.assertEquals("Admin reset password must return 200 OK", StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof AdminResetPasswordResponseDTO);

        AdminResetPasswordResponseDTO dto = (AdminResetPasswordResponseDTO) resp.getData();
        Assert.assertEquals(testTargetUserId, dto.getUserId());
        Assert.assertTrue("must_change_password flag must be true", dto.isMustChangePassword());

        String tempPass = dto.getTemporaryPassword();
        Assert.assertNotNull("Temporary password must not be null", tempPass);
        Assert.assertTrue("Temporary password length must be >= 16", tempPass.length() >= 16);

        // Verify character categories
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : tempPass.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        Assert.assertTrue("Must contain uppercase", hasUpper);
        Assert.assertTrue("Must contain lowercase", hasLower);
        Assert.assertTrue("Must contain digit", hasDigit);
        Assert.assertTrue("Must contain special char", hasSpecial);
    }

    @Test
    public void testLibrarianAndStudentRejectedAtServer() {
        requireIsolatedTestDb();

        // 1. Librarian attempting to reset password
        Request reqLib = new Request(Action.ADMIN_RESET_PASSWORD, librarianToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response respLib = router.route(reqLib);
        Assert.assertEquals("Librarian must be rejected with 403 FORBIDDEN",
                StatusCode.FORBIDDEN, respLib.getStatusCode());

        // 2. Student attempting to reset password
        Request reqStu = new Request(Action.ADMIN_RESET_PASSWORD, studentToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response respStu = router.route(reqStu);
        Assert.assertEquals("Student must be rejected with 403 FORBIDDEN",
                StatusCode.FORBIDDEN, respStu.getStatusCode());
    }

    @Test
    public void testAdminCannotSelfReset() {
        requireIsolatedTestDb();
        // Admin user ID is 1L
        Request reqSelf = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(1L));
        Response respSelf = router.route(reqSelf);

        Assert.assertEquals("Admin self-reset must be rejected with 400 BAD_REQUEST",
                StatusCode.BAD_REQUEST, respSelf.getStatusCode());
        Assert.assertTrue("Message must explain self-reset restriction",
                respSelf.getMessage().contains("chính mình"));
    }

    @Test
    public void testTargetUserNotFoundOrInactive() {
        requireIsolatedTestDb();

        // 1. Target ID does not exist
        Request reqNotFound = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(99999999L));
        Response respNotFound = router.route(reqNotFound);
        Assert.assertEquals("Non-existent user must return 404 NOT_FOUND",
                StatusCode.NOT_FOUND, respNotFound.getStatusCode());

        // 2. Target user is inactive/disabled
        Request reqInactive = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testInactiveUserId));
        Response respInactive = router.route(reqInactive);
        Assert.assertEquals("Inactive user must return 400 BAD_REQUEST",
                StatusCode.BAD_REQUEST, respInactive.getStatusCode());
        Assert.assertTrue("Message must indicate user is disabled",
                respInactive.getMessage().contains("vô hiệu hóa"));
    }

    @Test
    public void testHashStoredInPbkdf2Format() throws Exception {
        requireIsolatedTestDb();

        Request req = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        AdminResetPasswordResponseDTO dto = (AdminResetPasswordResponseDTO) resp.getData();

        // Query database directly
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT password_hash, must_change_password FROM users WHERE id = ?")) {
            stmt.setLong(1, testTargetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                Assert.assertTrue("User record must exist", rs.next());
                String hash = rs.getString("password_hash");
                boolean mustChange = rs.getBoolean("must_change_password");

                Assert.assertTrue("must_change_password column must be TRUE", mustChange);
                Assert.assertNotNull("Hash must not be null", hash);
                Assert.assertTrue("Hash must be in PBKDF2 format", hash.startsWith("PBKDF2$10000$"));
                Assert.assertTrue("PasswordHasher must successfully verify temporary password against hash",
                        PasswordHasher.verify(dto.getTemporaryPassword(), hash));
            }
        }
    }

    @Test
    public void testTemporaryPasswordNotInAuditLog() throws Exception {
        requireIsolatedTestDb();

        Request req = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        AdminResetPasswordResponseDTO dto = (AdminResetPasswordResponseDTO) resp.getData();
        String tempPass = dto.getTemporaryPassword();

        // Query audit_logs
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT action, details FROM audit_logs WHERE action = 'ADMIN_PASSWORD_RESET' AND entity_id = ?")) {
            stmt.setLong(1, testTargetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                Assert.assertTrue("Audit log entry must be recorded", rs.next());
                String details = rs.getString("details");
                Assert.assertNotNull("Audit details must not be null", details);

                Assert.assertFalse("Temporary password MUST NEVER appear in audit log details",
                        details.contains(tempPass));
                Assert.assertFalse("Password hash MUST NEVER appear in audit log details",
                        details.contains("PBKDF2$"));
            }
        }
    }

    @Test
    public void testTargetOldSessionRevoked() {
        requireIsolatedTestDb();

        // Target user creates an active session
        UserSessionDTO targetSession = new UserSessionDTO(null, testTargetUserId, "test_target", "Target", UserRole.STUDENT, false);
        String oldToken = sessionManager.createSession(targetSession);
        Assert.assertNotNull("Old session token must exist", sessionManager.getSession(oldToken));

        // Admin resets target user's password
        Request req = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());

        // Verify old session token was revoked immediately
        Assert.assertNull("Old session token must be revoked and removed from SessionManager",
                sessionManager.getSession(oldToken));

        // Attempting to send request with old token yields 401 UNAUTHORIZED
        Request reqOld = new Request(Action.LIST_BOOKS, oldToken, null);
        Response respOld = router.route(reqOld);
        Assert.assertEquals("Request using revoked session must be 401 UNAUTHORIZED",
                StatusCode.UNAUTHORIZED, respOld.getStatusCode());
    }

    @Test
    public void testUserBlockedFromBusinessActionsBeforeChangingPassword() throws Exception {
        requireIsolatedTestDb();

        // 1. Admin resets password
        Request reqReset = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response respReset = router.route(reqReset);
        Assert.assertEquals(StatusCode.OK, respReset.getStatusCode());
        AdminResetPasswordResponseDTO resetDto = (AdminResetPasswordResponseDTO) respReset.getData();

        // 2. User logs in with temporary password
        Request reqLogin = new Request(Action.LOGIN,
                new LoginRequestDTO(resetDto.getUsername(), resetDto.getTemporaryPassword()));
        Response respLogin = router.route(reqLogin);
        Assert.assertEquals("Login with temporary password must succeed", StatusCode.OK, respLogin.getStatusCode());
        Assert.assertTrue(respLogin.getData() instanceof UserSessionDTO);

        UserSessionDTO userSession = (UserSessionDTO) respLogin.getData();
        Assert.assertTrue("Session must have must_change_password=true", userSession.isMustChangePassword());
        String userToken = userSession.getToken();

        // 3. User attempts business actions -> MUST BE REJECTED with 403 FORBIDDEN
        Request reqBooks = new Request(Action.LIST_BOOKS, userToken, null);
        Response respBooks = router.route(reqBooks);
        Assert.assertEquals("LIST_BOOKS must be blocked by mandatory password change",
                StatusCode.FORBIDDEN, respBooks.getStatusCode());
        Assert.assertTrue(respBooks.getMessage().contains("bắt buộc phải đổi mật khẩu"));

        Request reqSearch = new Request(Action.SEARCH_BOOKS, userToken, new thuvien.common.dto.BookSearchCriteriaDTO());
        Response respSearch = router.route(reqSearch);
        Assert.assertEquals("SEARCH_BOOKS must be blocked by mandatory password change",
                StatusCode.FORBIDDEN, respSearch.getStatusCode());

        // 4. Allowed actions: GET_CURRENT_USER and LOGOUT
        Request reqCurrent = new Request(Action.GET_CURRENT_USER, userToken, null);
        Response respCurrent = router.route(reqCurrent);
        Assert.assertEquals("GET_CURRENT_USER must be allowed", StatusCode.OK, respCurrent.getStatusCode());
    }

    @Test
    public void testChangePasswordRestoresFullAccess() throws Exception {
        requireIsolatedTestDb();

        // 1. Admin resets password
        Request reqReset = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(testTargetUserId));
        Response respReset = router.route(reqReset);
        AdminResetPasswordResponseDTO resetDto = (AdminResetPasswordResponseDTO) respReset.getData();

        // 2. User logs in with temporary password
        Request reqLogin = new Request(Action.LOGIN,
                new LoginRequestDTO(resetDto.getUsername(), resetDto.getTemporaryPassword()));
        Response respLogin = router.route(reqLogin);
        UserSessionDTO userSession = (UserSessionDTO) respLogin.getData();
        String userToken = userSession.getToken();

        // 3. User performs CHANGE_PASSWORD
        String newPassword = "NewPermanentPassword#2026!";
        ChangePasswordRequestDTO changeDto = new ChangePasswordRequestDTO(resetDto.getTemporaryPassword(), newPassword);
        Request reqChange = new Request(Action.CHANGE_PASSWORD, userToken, changeDto);
        Response respChange = router.route(reqChange);
        Assert.assertEquals("Change password must return 200 OK", StatusCode.OK, respChange.getStatusCode());

        // 4. Verify in database that must_change_password is now FALSE
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT must_change_password FROM users WHERE id = ?")) {
            stmt.setLong(1, testTargetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                Assert.assertTrue(rs.next());
                Assert.assertFalse("must_change_password in DB must be FALSE after password change",
                        rs.getBoolean("must_change_password"));
            }
        }

        // 5. Existing active session is now unlocked and can access business actions
        Assert.assertFalse("Active session flag must be cleared", userSession.isMustChangePassword());
        Request reqBooks = new Request(Action.LIST_BOOKS, userToken, null);
        Response respBooks = router.route(reqBooks);
        Assert.assertEquals("LIST_BOOKS must now succeed", StatusCode.OK, respBooks.getStatusCode());

        // 6. User can log in with new password
        Request reqNewLogin = new Request(Action.LOGIN,
                new LoginRequestDTO(resetDto.getUsername(), newPassword));
        Response respNewLogin = router.route(reqNewLogin);
        Assert.assertEquals("Login with new permanent password must succeed", StatusCode.OK, respNewLogin.getStatusCode());
        UserSessionDTO newSession = (UserSessionDTO) respNewLogin.getData();
        Assert.assertFalse("New session must not require password change", newSession.isMustChangePassword());
    }

    @Test
    public void testRollbackOnFailure() throws Exception {
        requireIsolatedTestDb();

        // Save original hash of target user
        String originalHash;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
            stmt.setLong(1, testTargetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                originalHash = rs.getString("password_hash");
            }
        }

        // Create a custom UserService with failing repo on resetPassword to trigger transaction rollback
        final thuvien.server.repository.UserRepository realRepo = new UserRepositoryImpl(db);
        thuvien.server.repository.UserRepository failingRepo = new thuvien.server.repository.UserRepository() {
            @Override
            public UserDTO findById(Long id) throws java.sql.SQLException { return realRepo.findById(id); }
            @Override
            public UserDTO findByUsername(String username) throws java.sql.SQLException { return realRepo.findByUsername(username); }
            @Override
            public List<UserDTO> findAll() throws java.sql.SQLException { return realRepo.findAll(); }
            @Override
            public Long create(UserDTO user, String passwordHash, Connection conn) throws java.sql.SQLException { return realRepo.create(user, passwordHash, conn); }
            @Override
            public boolean update(UserDTO user, Connection conn) throws java.sql.SQLException { return realRepo.update(user, conn); }
            @Override
            public boolean delete(Long id, Connection conn) throws java.sql.SQLException { return realRepo.delete(id, conn); }
            @Override
            public String getPasswordHash(String username) throws java.sql.SQLException { return realRepo.getPasswordHash(username); }
            @Override
            public boolean updatePassword(Long userId, String newPasswordHash, Connection conn) throws java.sql.SQLException { return realRepo.updatePassword(userId, newPasswordHash, conn); }
            @Override
            public boolean resetPassword(Long userId, String newPasswordHash, boolean mustChangePassword, Connection conn) throws java.sql.SQLException {
                throw new java.sql.SQLException("Simulated Disk Failure during transaction");
            }
        };

        UserService failingUserService = new UserServiceImpl(failingRepo);
        try {
            failingUserService.adminResetPassword(1L, testTargetUserId);
            Assert.fail("Expected LibraryException when DB transaction fails");
        } catch (thuvien.common.exception.LibraryException expected) {
            Assert.assertTrue("Exception message should indicate transaction failure",
                    expected.getMessage().contains("Lỗi giao dịch") || expected.getMessage().contains("Simulated Disk Failure"));
        }

        // Verify target user's hash remains unchanged in database due to rollback
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
            stmt.setLong(1, testTargetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                Assert.assertEquals("Original hash must remain unchanged due to rollback", originalHash, rs.getString("password_hash"));
            }
        }
    }

    @Test
    public void testExistingAccountsMigrationIntegrity() throws Exception {
        requireIsolatedTestDb();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, username, role, is_active, must_change_password FROM users WHERE id IN (1, 2, 4, 5)")) {
            int count = 0;
            while (rs.next()) {
                count++;
                String username = rs.getString("username");
                boolean mustChange = rs.getBoolean("must_change_password");
                boolean isActive = rs.getBoolean("is_active");

                Assert.assertTrue("Existing accounts should be active: " + username, isActive);
                Assert.assertFalse("Existing accounts must have must_change_password=FALSE by default: " + username, mustChange);
            }
            Assert.assertTrue("Should have inspected existing seed accounts", count >= 3);
        }
    }

    @Test
    public void testLibrarian2LegacyAccountRecoveryFlow() throws Exception {
        requireIsolatedTestDb();

        // 1. Locate librarian2 (ID 3) in school_library_test
        UserDTO lib2 = null;
        try {
            lib2 = userService.getUserById(3L);
        } catch (Exception ignored) {}

        if (lib2 == null || !"librarian2".equals(lib2.getUsername())) {
            // If ID 3 is not librarian2, query by username
            lib2 = userService.getUserByUsername("librarian2");
        }

        Assert.assertNotNull("librarian2 must exist in school_library_test", lib2);
        Long lib2Id = lib2.getId();

        // 2. Inspect initial hash format (legacy SHA-256 or unreset state)
        String initialHash;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
            stmt.setLong(1, lib2Id);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                initialHash = rs.getString("password_hash");
            }
        }
        Assert.assertNotNull("Initial hash must exist", initialHash);

        // 3. Admin performs ADMIN_RESET_PASSWORD on librarian2
        Request reqReset = new Request(Action.ADMIN_RESET_PASSWORD, adminToken,
                new AdminResetPasswordRequestDTO(lib2Id));
        Response respReset = router.route(reqReset);
        Assert.assertEquals("Admin reset for librarian2 must succeed", StatusCode.OK, respReset.getStatusCode());
        AdminResetPasswordResponseDTO resetDto = (AdminResetPasswordResponseDTO) respReset.getData();

        String tempPassword = resetDto.getTemporaryPassword();
        Assert.assertNotNull("Temporary password must be returned", tempPassword);

        // 4. Verify password_hash upgraded to PBKDF2 in database
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT password_hash, must_change_password FROM users WHERE id = ?")) {
            stmt.setLong(1, lib2Id);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                String newHash = rs.getString("password_hash");
                boolean mustChange = rs.getBoolean("must_change_password");

                Assert.assertTrue("New hash must be upgraded to PBKDF2 format", newHash.startsWith("PBKDF2$10000$"));
                Assert.assertTrue("must_change_password must be TRUE", mustChange);
                Assert.assertTrue("PasswordHasher verifies temporary password",
                        PasswordHasher.verify(tempPassword, newHash));
            }
        }

        // 5. librarian2 logs in using the temporary password
        Request reqLogin = new Request(Action.LOGIN, new LoginRequestDTO("librarian2", tempPassword));
        Response respLogin = router.route(reqLogin);
        Assert.assertEquals("librarian2 login with temporary password must succeed",
                StatusCode.OK, respLogin.getStatusCode());
        UserSessionDTO lib2Session = (UserSessionDTO) respLogin.getData();
        Assert.assertEquals(UserRole.LIBRARIAN, lib2Session.getRole());
        Assert.assertTrue("Session must require password change", lib2Session.isMustChangePassword());

        // 6. Verify librarian2 is blocked from circulation actions prior to changing password
        Request reqCirculation = new Request(Action.LIST_BORROW_RECORDS, lib2Session.getToken(), null);
        Response respCirculation = router.route(reqCirculation);
        Assert.assertEquals("Circulation must be blocked until password is changed",
                StatusCode.FORBIDDEN, respCirculation.getStatusCode());

        // 7. librarian2 changes password to new secure password
        String newLibrarianPassword = "SecureLibPass#2026";
        ChangePasswordRequestDTO changeDto = new ChangePasswordRequestDTO(tempPassword, newLibrarianPassword);
        Request reqChange = new Request(Action.CHANGE_PASSWORD, lib2Session.getToken(), changeDto);
        Response respChange = router.route(reqChange);
        Assert.assertEquals("Change password for librarian2 must return 200 OK",
                StatusCode.OK, respChange.getStatusCode());

        // 8. Circulation action is now fully unlocked and succeeds!
        Response respCirculationUnlocked = router.route(reqCirculation);
        Assert.assertEquals("Circulation action must now succeed for librarian2",
                StatusCode.OK, respCirculationUnlocked.getStatusCode());

        // 9. librarian2 can log in cleanly with new password
        Request reqFinalLogin = new Request(Action.LOGIN,
                new LoginRequestDTO("librarian2", newLibrarianPassword));
        Response respFinalLogin = router.route(reqFinalLogin);
        Assert.assertEquals("Login with new permanent password must succeed",
                StatusCode.OK, respFinalLogin.getStatusCode());
        UserSessionDTO finalSession = (UserSessionDTO) respFinalLogin.getData();
        Assert.assertFalse("Final session must have mustChangePassword=false",
                finalSession.isMustChangePassword());
    }

    @Test
    public void testUnmigratedSchemaRefusesResetAndRefusesBypass() throws Exception {
        requireIsolatedTestDb();

        // 1. Verify schema migration helper on actual test DB
        try (Connection conn = db.getConnection()) {
            boolean migrated = UserRepositoryImpl.isSchemaMigrated(conn);
            Assert.assertTrue("school_library_test must be detected as migrated", migrated);
        }

        // 2. Simulate unmigrated database throwing MySQL Error 1054 on must_change_password
        final java.sql.SQLException err1054 = new java.sql.SQLException("Unknown column 'must_change_password' in 'field list'", "42S22", 1054);
        UserRepositoryImpl unmigratedRepo = new UserRepositoryImpl(db) {
            @Override
            public boolean resetPassword(Long userId, String newPasswordHash, boolean mustChangePassword, Connection conn) throws java.sql.SQLException {
                throw err1054;
            }
        };

        UserService unmigratedUserService = new UserServiceImpl(unmigratedRepo);
        try {
            unmigratedUserService.adminResetPassword(1L, testTargetUserId);
            Assert.fail("Expected LibraryException refusing reset on unmigrated database");
        } catch (thuvien.common.exception.LibraryException expected) {
            // Verify message explicitly demands migration
            Assert.assertTrue("Error message must clearly inform about missing must_change_password column",
                    expected.getMessage().contains("must_change_password") || expected.getMessage().contains("chưa được nâng cấp"));
        }
    }
}
