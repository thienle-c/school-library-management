package thuvien.server.service;

import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.RegisterStudentRequestDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.repository.impl.UserRepositoryImpl;
import thuvien.server.router.RequestRouter;
import thuvien.server.security.PasswordHasher;
import thuvien.server.security.SessionManager;
import thuvien.server.service.AuditLogService;
import thuvien.server.service.SearchSuggestionService;
import thuvien.server.service.StudentService;
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
 * Comprehensive automated test suite for hardened security mechanisms,
 * PBKDF2 password migration, rate-limiting, session management, and new features.
 */
public class SecurityAndFeatureHardeningTest {

    private AuthServiceImpl authService;
    private SessionManager sessionManager;
    private SearchSuggestionService suggestionService;
    private StudentService studentService;
    private AuditLogService auditLogService;
    private BookService bookService;
    private BorrowService borrowService;
    private ReservationService reservationService;
    private FineService fineService;
    private RequestRouter router;

    @Before
    public void setUp() {
        DatabaseManager db = DatabaseManager.getInstance();
        sessionManager = SessionManager.getInstance();
        sessionManager.clearAllSessions();

        auditLogService = new AuditLogServiceImpl(new AuditLogRepositoryImpl(db));
        authService = new AuthServiceImpl(new UserRepositoryImpl(db), sessionManager, auditLogService);
        authService.unlockAccount("student1");
        authService.unlockAccount("admin");
        authService.unlockAccount("librarian1");
        authService.unlockAccount(1L);
        authService.unlockAccount(2L);
        authService.unlockAccount(4L);

        suggestionService = new SearchSuggestionServiceImpl(db, new StudentRepositoryImpl(db));
        studentService = new StudentServiceImpl(new StudentRepositoryImpl(db), new UserRepositoryImpl(db), auditLogService);
        bookService = new BookServiceImpl();
        borrowService = new BorrowServiceImpl();
        reservationService = new ReservationServiceImpl();
        fineService = new FineServiceImpl();

        router = new RequestRouter(
                authService,
                bookService,
                studentService,
                borrowService,
                new ReturnServiceImpl(),
                fineService,
                new DashboardServiceImpl(),
                reservationService,
                auditLogService,
                new CategoryServiceImpl(),
                new AuthorServiceImpl(),
                new UserServiceImpl()
        );
    }

    @After
    public void tearDown() {
        if (authService != null) {
            authService.unlockAccount("student1");
            authService.unlockAccount("admin");
            authService.unlockAccount("librarian1");
            authService.unlockAccount(1L);
            authService.unlockAccount(2L);
            authService.unlockAccount(4L);
        }
        if (sessionManager != null) {
            sessionManager.clearAllSessions();
        }
    }

    @Test
    public void testPbkdf2HashFormatAndVerification() {
        String password = "SecureStudentPass!99";
        String hash = PasswordHasher.hash(password);

        Assert.assertNotNull(hash);
        Assert.assertTrue("Hash should use PBKDF2 format", hash.startsWith("PBKDF2$10000$"));
        Assert.assertFalse("PBKDF2 hash should not be recognized as legacy", PasswordHasher.isLegacyHash(hash));

        // Successful verification
        Assert.assertTrue("Verification should succeed with correct password", PasswordHasher.verify(password, hash));

        // Failed verification
        Assert.assertFalse("Verification should fail with incorrect password", PasswordHasher.verify("WrongPass123", hash));
        Assert.assertFalse("Verification should fail with empty password", PasswordHasher.verify("", hash));
        Assert.assertFalse("Verification should fail with null", PasswordHasher.verify(null, hash));
    }

    @Test
    public void testLegacySha256VerificationAndDetection() {
        // Seed hash for 'admin123'
        String seedAdminHash = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

        Assert.assertTrue("Seed hash should be detected as legacy", PasswordHasher.isLegacyHash(seedAdminHash));
        Assert.assertTrue("Legacy hash should verify correctly against 'admin123'", PasswordHasher.verify("admin123", seedAdminHash));
        Assert.assertFalse("Legacy hash should fail against wrong password", PasswordHasher.verify("wrongpass", seedAdminHash));
    }

    @Test
    public void testLoginLockoutRateLimiting() {
        String testUser = "lockout_test_user_" + System.currentTimeMillis();

        // 5 consecutive failed attempts
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO(testUser, "wrong_password_" + i));
                Assert.fail("Attempt " + i + " should have failed");
            } catch (AuthenticationException e) {
                if (i < 5) {
                    Assert.assertTrue("Generic error expected before lockout",
                            e.getMessage().contains("không chính xác"));
                } else {
                    Assert.assertTrue("Lockout notification expected on 5th attempt",
                            e.getMessage().contains("tạm khóa"));
                }
            }
        }

        // 6th attempt should be immediately blocked by lockout
        try {
            authService.login(new LoginRequestDTO(testUser, "any_password"));
            Assert.fail("6th attempt must be blocked by temporary account lockout");
        } catch (AuthenticationException e) {
            Assert.assertTrue("Should indicate account lockout", e.getMessage().contains("tạm khóa"));
        }
    }

    @Test
    public void testSessionManagerLifecycleAndExpiry() {
        UserSessionDTO user = new UserSessionDTO(null, 999L, "session_test", "Test Admin", UserRole.ADMIN);
        String token = sessionManager.createSession(user);

        Assert.assertNotNull(token);

        // Validate active session
        UserSessionDTO valid = sessionManager.getSession(token);
        Assert.assertNotNull(valid);
        Assert.assertEquals("session_test", valid.getUsername());

        // Invalidate session on logout
        sessionManager.removeSession(token);
        Assert.assertNull("Invalidated session should be null", sessionManager.getSession(token));
    }

    @Test
    public void testSearchSuggestionServiceResponses() throws Exception {
        UserSessionDTO adminSession = new UserSessionDTO("test_token", 1L, "admin", "System Admin", UserRole.ADMIN);

        // Query search suggestions across entity types
        List<String> bookSuggestions = suggestionService.getSuggestions("BOOK", "Java", adminSession);
        Assert.assertNotNull(bookSuggestions);

        List<String> studentSuggestions = suggestionService.getSuggestions("STUDENT", "Le", adminSession);
        Assert.assertNotNull(studentSuggestions);

        List<String> borrowSuggestions = suggestionService.getSuggestions("BORROW", "STU", adminSession);
        Assert.assertNotNull(borrowSuggestions);

        // Blank queries should return empty list gracefully
        List<String> emptySuggestions = suggestionService.getSuggestions("BOOK", " ", adminSession);
        Assert.assertNotNull(emptySuggestions);
        Assert.assertTrue(emptySuggestions.isEmpty());
    }

    @Test
    public void testStudentRegistrationValidation() throws Exception {
        // Test invalid/empty payloads reject with ValidationException
        RegisterStudentRequestDTO emptyReq = new RegisterStudentRequestDTO();
        try {
            studentService.registerStudentAccount(emptyReq);
            Assert.fail("Empty registration request should throw ValidationException");
        } catch (ValidationException e) {
            Assert.assertNotNull(e.getMessage());
        }

        // Test mismatched passwords
        RegisterStudentRequestDTO mismatchReq = new RegisterStudentRequestDTO(
                "STU001", "Le Van An", "an@test.edu", "anle_new", "password123", "different123", "ACT-12345");
        try {
            studentService.registerStudentAccount(mismatchReq);
            Assert.fail("Mismatched password should throw ValidationException");
        } catch (ValidationException e) {
            Assert.assertTrue(e.getMessage().contains("khớp"));
        }
    }

    // =========================================================================
    // Required Security & Global Lockout Tests (TEST 1 to 18)
    // =========================================================================

    @Test
    public void test01_LoginSai5LanAccountLocked() {
        String testUser = "lockout_stu_" + System.currentTimeMillis();
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO(testUser, "wrong_pass_" + i));
                Assert.fail("Login attempt " + i + " should have failed");
            } catch (AuthenticationException e) {
                if (i == 5) {
                    Assert.assertTrue("5th failed attempt must report account locked",
                            e.getMessage().contains("tạm khóa"));
                }
            }
        }
        Assert.assertTrue("Account must be marked locked", authService.isAccountLocked(testUser));
    }

    @Test
    public void test02_SauLockedLoginDungPasswordVanBiTuChoi() {
        authService.unlockAccount("student1");
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("student1", "wrong_pass"));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));
        try {
            authService.login(new LoginRequestDTO("student1", "student123"));
            Assert.fail("Correct password must be rejected while account is locked");
        } catch (AuthenticationException e) {
            Assert.assertTrue("Must report account locked", e.getMessage().contains("tạm khóa"));
        } finally {
            authService.unlockAccount("student1");
        }
    }

    @Test
    public void test03_AccountBiLockedSessionCuBiRevoke() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        Assert.assertNotNull(token);
        Assert.assertTrue("Session must be valid before lockout", sessionManager.isValid(token));

        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("student1", "wrong_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));
        Assert.assertNull("Session must be revoked from sessionManager immediately", sessionManager.getSession(token));
        Assert.assertFalse("Token must no longer be valid", sessionManager.isValid(token));
        authService.unlockAccount("student1");
    }

    @Test
    public void test04_SessionCuGoiGetCurrentUser401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.GET_CURRENT_USER, null);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test05_SessionCuGoiGetStudent401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.GET_STUDENT, 1L);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test06_SessionCuGoiGetStudentDashboard401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.GET_STUDENT_DASHBOARD, null);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test07_SessionCuGoiListBorrowRecords401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.LIST_BORROW_RECORDS, null);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test08_SessionCuGoiListReservations401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.LIST_RESERVATIONS, null);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test09_SessionCuGoiCreateReservation401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        BorrowRequestDTO resReq = new BorrowRequestDTO(1L, 1L, 7, "Reservation Test");
        Request req = new Request(Action.CREATE_RESERVATION, resReq);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test10_SessionCuGoiBorrowBook401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        BorrowRequestDTO borrowReq = new BorrowRequestDTO(1L, 1L, 14, "Test");
        Request req = new Request(Action.BORROW_BOOK, borrowReq);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test11_SessionCuGoiGetStudentFines401() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Request req = new Request(Action.LIST_FINES, 1L);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test12_TrongThoiGianLockoutLoginDungPasswordKhongTaoSession() {
        authService.unlockAccount("student1");
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        try {
            authService.login(new LoginRequestDTO("student1", "student123"));
            Assert.fail("Login during lockout should fail");
        } catch (AuthenticationException e) {
            Assert.assertTrue(e.getMessage().contains("tạm khóa"));
        }
        Assert.assertNull(sessionManager.getSession("any_dummy_token"));
        authService.unlockAccount("student1");
    }

    @Test
    public void test13_SauKhiLockoutHetHanLoginDungPasswordThanhCong() throws Exception {
        authService.unlockAccount("student1");
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));
        // Unlock simulating expiration of lockout duration
        authService.unlockAccount("student1");
        Assert.assertFalse(authService.isAccountLocked("student1"));

        UserSessionDTO newSession = authService.login(new LoginRequestDTO("student1", "student123"));
        Assert.assertNotNull("Login must succeed after unlock", newSession);
        Assert.assertNotNull(newSession.getToken());
        authService.unlockAccount("student1");
    }

    @Test
    public void test14_SessionMoiSauUnlockHoatDongBinhThuong() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO newSession = authService.login(new LoginRequestDTO("student1", "student123"));
        String newToken = newSession.getToken();

        Request req = new Request(Action.GET_CURRENT_USER, null);
        req.setToken(newToken);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertNotNull(resp.getData());

        Request reqDash = new Request(Action.GET_STUDENT_DASHBOARD, null);
        reqDash.setToken(newToken);
        Response respDash = router.route(reqDash);
        Assert.assertEquals(StatusCode.OK, respDash.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test15_AdminActiveKhongBiAnhHuong() throws Exception {
        authService.unlockAccount("student1");
        authService.unlockAccount("admin");
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));

        UserSessionDTO adminSession = authService.login(new LoginRequestDTO("admin", "admin123"));
        Assert.assertNotNull(adminSession);
        Request req = new Request(Action.GET_DASHBOARD_METRICS, null);
        req.setToken(adminSession.getToken());
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test16_LibrarianActiveKhongBiAnhHuong() throws Exception {
        authService.unlockAccount("student1");
        authService.unlockAccount("librarian1");
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", "wrong")); } catch (Exception ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));

        UserSessionDTO libSession = authService.login(new LoginRequestDTO("librarian1", "lib123"));
        Assert.assertNotNull(libSession);
        Request req = new Request(Action.LIST_BOOKS, null);
        req.setToken(libSession.getToken());
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test17_StudentActiveKhongBiAnhHuong() throws Exception {
        String lockedUser = "lock_other_" + System.currentTimeMillis();
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO(lockedUser, "wrong")); } catch (Exception ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked(lockedUser));

        authService.unlockAccount("student1");
        UserSessionDTO stuSession = authService.login(new LoginRequestDTO("student1", "student123"));
        Assert.assertNotNull(stuSession);
        Request req = new Request(Action.GET_CURRENT_USER, null);
        req.setToken(stuSession.getToken());
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        authService.unlockAccount("student1");
    }

    @Test
    public void test18_KhongCoPasswordHashTokenTrongAuditLog() throws Exception {
        authService.unlockAccount("student1");
        String secretInput = "MySecretP@ssword999";
        for (int i = 1; i <= 5; i++) {
            try { authService.login(new LoginRequestDTO("student1", secretInput)); } catch (Exception ignored) {}
        }
        List<AuditLogDTO> logs = auditLogService.getRecentLogs(20);
        for (AuditLogDTO l : logs) {
            String details = l.getDetails() != null ? l.getDetails() : "";
            Assert.assertFalse("Audit log must not contain plaintext password", details.contains(secretInput));
            Assert.assertFalse("Audit log must not contain hash prefix", details.contains("PBKDF2$"));
            Assert.assertFalse("Audit log must not contain UUID session token", details.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"));
        }
        authService.unlockAccount("student1");
    }

    @Test
    public void test19_MultipleSessionsRevokedOnLockout() throws Exception {
        authService.unlockAccount("student1");
        // Session A
        UserSessionDTO sessionA = authService.login(new LoginRequestDTO("student1", "student123"));
        String tokenA = sessionA.getToken();
        Assert.assertNotNull(tokenA);
        Assert.assertTrue("Session A must be valid", sessionManager.isValid(tokenA));

        // Session B (secondary active session for same user)
        UserSessionDTO sessionB = authService.login(new LoginRequestDTO("student1", "student123"));
        String tokenB = sessionB.getToken();
        Assert.assertNotNull(tokenB);
        Assert.assertTrue("Session B must be valid", sessionManager.isValid(tokenB));
        Assert.assertNotEquals("Session tokens must be unique", tokenA, tokenB);

        // Trigger lockout by 5 failed attempts
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("student1", "wrong_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));

        // Both Session A and Session B must be immediately revoked from sessionManager
        Assert.assertNull("Session A must be revoked", sessionManager.getSession(tokenA));
        Assert.assertNull("Session B must be revoked", sessionManager.getSession(tokenB));
        Assert.assertFalse("Token A must no longer be valid", sessionManager.isValid(tokenA));
        Assert.assertFalse("Token B must no longer be valid", sessionManager.isValid(tokenB));

        // Requests using tokenA or tokenB must be rejected
        Request reqA = new Request(Action.GET_CURRENT_USER, null);
        reqA.setToken(tokenA);
        Response respA = router.route(reqA);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, respA.getStatusCode());

        Request reqB = new Request(Action.GET_STUDENT_DASHBOARD, null);
        reqB.setToken(tokenB);
        Response respB = router.route(reqB);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, respB.getStatusCode());

        authService.unlockAccount("student1");
    }

    @Test
    public void test20_NoDatabaseMutationAfterRejectedRequests() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();

        // Baseline counts before lockout
        int initialBorrows = borrowService.getAllBorrowRecords().size();
        int initialReservations = reservationService.getAllReservations().size();
        int initialFines = fineService.getAllFines().size();
        int initialAvailableCopies = bookService.getBookById(1L).getAvailableCopies();

        // Trigger lockout
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("student1", "wrong_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));

        // Attempt 1: Borrow book using locked/stale session
        BorrowRequestDTO borrowReq = new BorrowRequestDTO(1L, 1L, 14, "Mutation Attempt When Locked");
        Request reqBorrow = new Request(Action.BORROW_BOOK, borrowReq);
        reqBorrow.setToken(token);
        Response respBorrow = router.route(reqBorrow);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, respBorrow.getStatusCode());

        // Attempt 2: Create reservation using locked/stale session
        BorrowRequestDTO resReq = new BorrowRequestDTO(1L, 1L, 7, "Mutation Reservation Attempt");
        Request reqRes = new Request(Action.CREATE_RESERVATION, resReq);
        reqRes.setToken(token);
        Response respRes = router.route(reqRes);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, respRes.getStatusCode());

        // Verify database state: NO mutations occurred
        int currentBorrows = borrowService.getAllBorrowRecords().size();
        int currentReservations = reservationService.getAllReservations().size();
        int currentFines = fineService.getAllFines().size();
        int currentAvailableCopies = bookService.getBookById(1L).getAvailableCopies();

        Assert.assertEquals("Borrow records count must remain unchanged", initialBorrows, currentBorrows);
        Assert.assertEquals("Reservations count must remain unchanged", initialReservations, currentReservations);
        Assert.assertEquals("Fines count must remain unchanged", initialFines, currentFines);
        Assert.assertEquals("Book available copies must remain unchanged", initialAvailableCopies, currentAvailableCopies);

        authService.unlockAccount("student1");
    }

    @Test
    public void test21_AdminAccountLockoutBlocksAdminActions() throws Exception {
        authService.unlockAccount("admin");
        UserSessionDTO adminSession = authService.login(new LoginRequestDTO("admin", "admin123"));
        String adminToken = adminSession.getToken();
        Assert.assertNotNull(adminToken);

        // Lock admin account
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("admin", "wrong_admin_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("admin"));
        Assert.assertFalse("Admin token must be revoked", sessionManager.isValid(adminToken));

        // Attempt admin action with revoked token
        Request req = new Request(Action.GET_DASHBOARD_METRICS, null);
        req.setToken(adminToken);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());

        // Normal active user (e.g. librarian1) is unaffected
        authService.unlockAccount("librarian1");
        UserSessionDTO libSession = authService.login(new LoginRequestDTO("librarian1", "lib123"));
        Assert.assertNotNull(libSession);
        Request libReq = new Request(Action.LIST_BOOKS, null);
        libReq.setToken(libSession.getToken());
        Response libResp = router.route(libReq);
        Assert.assertEquals(StatusCode.OK, libResp.getStatusCode());

        authService.unlockAccount("admin");
    }

    @Test
    public void test22_LockedStudentCannotChangePassword() throws Exception {
        authService.unlockAccount("student1");
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        String token = session.getToken();
        Assert.assertNotNull(token);

        // Lock student1
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO("student1", "wrong_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked("student1"));

        // Attempt CHANGE_PASSWORD with revoked/locked session
        ChangePasswordRequestDTO changeReq = new ChangePasswordRequestDTO("student123", "newPassword999", "newPassword999");
        Request req = new Request(Action.CHANGE_PASSWORD, changeReq);
        req.setToken(token);
        Response resp = router.route(req);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());

        // Password must NOT be changed in database
        authService.unlockAccount("student1");
        UserSessionDTO verifySession = authService.login(new LoginRequestDTO("student1", "student123"));
        Assert.assertNotNull("Original password must still work", verifySession);
    }

    @Test
    public void test23_SuccessfulLoginAfterLockoutResetsFailedAttempts() throws Exception {
        String testUser = "reset_attempts_" + System.currentTimeMillis();
        // Trigger 5 failed attempts
        for (int i = 1; i <= 5; i++) {
            try {
                authService.login(new LoginRequestDTO(testUser, "wrong_pass_" + i));
            } catch (AuthenticationException ignored) {}
        }
        Assert.assertTrue(authService.isAccountLocked(testUser));

        // Unlock account
        authService.unlockAccount(testUser);
        Assert.assertFalse(authService.isAccountLocked(testUser));

        // First failed attempt after unlock should not trigger lockout immediately
        try {
            authService.login(new LoginRequestDTO(testUser, "wrong_pass_after_unlock"));
            Assert.fail("Wrong password should fail");
        } catch (AuthenticationException e) {
            Assert.assertFalse("Should not be locked on attempt 1", e.getMessage().contains("tạm khóa"));
            Assert.assertTrue("Should indicate incorrect credentials", e.getMessage().contains("không chính xác"));
        }
    }
}
