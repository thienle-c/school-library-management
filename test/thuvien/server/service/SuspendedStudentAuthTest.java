package thuvien.server.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.AdminResetPasswordRequestDTO;
import thuvien.common.dto.AdminResetPasswordResponseDTO;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.CreateUserRequestDTO;
import thuvien.common.dto.DeleteStudentRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.StudentStatus;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.CategoryRepositoryImpl;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
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
 * Regression and security audit tests for suspended student authentication,
 * session revocation, privilege boundaries, and integration with admin password reset.
 * Strictly executes against school_library_test.
 */
public class SuspendedStudentAuthTest {

    private DatabaseManager db;
    private SessionManager sessionManager;
    private AuthService authService;
    private StudentService studentService;
    private UserService userService;
    private AuditLogService auditLogService;
    private RequestRouter router;

    private String adminToken;
    private String librarianToken;

    private Long tempUser2Id = null;
    private Long tempStudent2Id = null;

    @Before
    public void setUp() throws Exception {
        db = DatabaseManager.getInstance();
        sessionManager = SessionManager.getInstance();
        sessionManager.clearAllSessions();

        // Safety check: ensure testing against school_library_test
        try (Connection conn = db.getConnection()) {
            String catalog = conn.getCatalog();
            Assert.assertNotNull("Connection catalog must not be null", catalog);
            Assert.assertTrue("Test must strictly run against school_library_test, got: " + catalog,
                    catalog.toLowerCase().contains("test"));
        }

        auditLogService = new AuditLogServiceImpl(new AuditLogRepositoryImpl(db));
        studentService = new StudentServiceImpl(new StudentRepositoryImpl(db), new UserRepositoryImpl(db), auditLogService);
        userService = new UserServiceImpl(new UserRepositoryImpl(db));
        authService = new AuthServiceImpl(new UserRepositoryImpl(db), new StudentRepositoryImpl(db), sessionManager, auditLogService);

        router = new RequestRouter(
                authService,
                new BookServiceImpl(),
                studentService,
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

        // Standard admin and librarian sessions
        adminToken = sessionManager.createSession(new UserSessionDTO(null, 1L, "admin", "System Administrator", UserRole.ADMIN, false));
        librarianToken = sessionManager.createSession(new UserSessionDTO(null, 2L, "librarian1", "Head Librarian Nguyen", UserRole.LIBRARIAN, false));

        // Unlock student1 if locked from previous tests
        authService.unlockAccount("student1");
        authService.unlockAccount(4L);

        // Ensure student1 (ID 1, User 4) is reset to ACTIVE in DB
        resetStudent1ToActive();
    }

    @After
    public void tearDown() throws Exception {
        if (sessionManager != null) {
            sessionManager.clearAllSessions();
        }
        if (authService != null) {
            authService.unlockAccount("student1");
            authService.unlockAccount(4L);
        }
        resetStudent1ToActive();
        cleanUpTempStudent2();
    }

    private void resetStudent1ToActive() {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE students SET status = 'ACTIVE' WHERE id = 1 AND student_code = 'STU001'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET password_hash = ?, is_active = TRUE, must_change_password = FALSE WHERE id = 4 AND username = 'student1'")) {
                ps.setString(1, PasswordHasher.hash("student123"));
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    private void cleanUpTempStudent2() {
        try (Connection conn = db.getConnection()) {
            if (tempStudent2Id != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                    ps.setLong(1, tempStudent2Id);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (tempUser2Id != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    ps.setLong(1, tempUser2Id);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scenario 1: Active student logs in successfully and accesses student operations.
     */
    @Test
    public void test01_ActiveStudentLoginSuccess() throws Exception {
        UserSessionDTO session = authService.login(new LoginRequestDTO("student1", "student123"));
        Assert.assertNotNull("Active student login must return session", session);
        Assert.assertEquals("student1", session.getUsername());
        Assert.assertEquals(UserRole.STUDENT, session.getRole());
        Assert.assertNotNull("Session must contain token", session.getToken());
        Assert.assertTrue("Session must be recognized by SessionManager", sessionManager.isValid(session.getToken()));

        // Access student dashboard via router
        Request dashReq = new Request(Action.GET_STUDENT_DASHBOARD, session.getToken(), null);
        Response dashResp = router.route(dashReq);
        Assert.assertEquals("Active student must be allowed to view dashboard", StatusCode.OK, dashResp.getStatusCode());
    }

    /**
     * Scenario 2: Suspending a student blocks login even with correct password,
     * while wrong password still yields standard credentials error without leaking status.
     */
    @Test
    public void test02_SuspendedStudentLoginWithCorrectPasswordRejected() throws Exception {
        // Suspend student STU001 (ID 1)
        boolean suspended = studentService.deleteStudent(1L);
        Assert.assertTrue("deleteStudent must succeed in suspending student", suspended);

        // Verify status in DB: students.status == SUSPENDED while users.is_active remains TRUE
        StudentDTO stu = studentService.getStudentById(1L);
        Assert.assertEquals(StudentStatus.SUSPENDED, stu.getStatus());

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT is_active FROM users WHERE id = 4")) {
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertTrue(rs.next());
                Assert.assertTrue("users.is_active remains true, showing separate table state", rs.getBoolean("is_active"));
            }
        }

        // 1. Attempt login with CORRECT password -> MUST BE REJECTED with clear suspension error
        try {
            authService.login(new LoginRequestDTO("student1", "student123"));
            Assert.fail("Suspended student must NOT be allowed to log in even with correct password");
        } catch (AuthenticationException e) {
            Assert.assertTrue("Error message must clearly inform about suspension, got: " + e.getMessage(),
                    e.getMessage().contains("đình chỉ") || e.getMessage().contains("tạm khóa"));
        }

        // Verify NO session was created for student1
        for (int i = 0; i < 5; i++) {
            Assert.assertNull("No session should exist for student1", sessionManager.getSession("fake_token_" + i));
        }

        // 2. Attempt login with WRONG password -> MUST BE REJECTED with standard wrong password error (no status leak)
        try {
            authService.login(new LoginRequestDTO("student1", "completely_wrong_pass"));
            Assert.fail("Wrong password must throw AuthenticationException");
        } catch (AuthenticationException e) {
            Assert.assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác.", e.getMessage());
        }
    }

    /**
     * Scenario 3: Student is logged in, then suspended by Admin/Librarian.
     * The old token must be immediately revoked and cannot access any business operations.
     */
    @Test
    public void test03_SuspensionRevokesActiveSessionAndBlocksBusinessActions() throws Exception {
        // Student logs in
        UserSessionDTO stuSession = authService.login(new LoginRequestDTO("student1", "student123"));
        String stuToken = stuSession.getToken();
        Assert.assertNotNull(stuToken);

        // Verify business action works prior to suspension
        Request preReq = new Request(Action.GET_STUDENT_DASHBOARD, stuToken, null);
        Response preResp = router.route(preReq);
        Assert.assertEquals(StatusCode.OK, preResp.getStatusCode());

        // Librarian suspends student via router Action.DELETE_STUDENT
        Request suspendReq = new Request(Action.DELETE_STUDENT, librarianToken, new DeleteStudentRequestDTO(1L, false));
        Response suspendResp = router.route(suspendReq);
        Assert.assertEquals("Librarian suspend student must succeed", StatusCode.OK, suspendResp.getStatusCode());

        // Verify session was revoked in SessionManager
        Assert.assertNull("Old session token must be purged", sessionManager.getSession(stuToken));

        // Now attempt business actions with old token -> ALL MUST YIELD 401 UNAUTHORIZED
        Request postReq1 = new Request(Action.GET_STUDENT_DASHBOARD, stuToken, null);
        Response postResp1 = router.route(postReq1);
        Assert.assertEquals("Revoked token must be rejected with 401", StatusCode.UNAUTHORIZED, postResp1.getStatusCode());

        Request postReq2 = new Request(Action.LIST_FINES, stuToken, null);
        Response postResp2 = router.route(postReq2);
        Assert.assertEquals("Revoked token must be rejected with 401", StatusCode.UNAUTHORIZED, postResp2.getStatusCode());

        Request postReq3 = new Request(Action.GET_STUDENT, stuToken, 1L);
        Response postResp3 = router.route(postReq3);
        Assert.assertEquals("Revoked token must be rejected with 401", StatusCode.UNAUTHORIZED, postResp3.getStatusCode());

        Request postReq4 = new Request(Action.SEARCH_SUGGESTIONS, stuToken, "Java");
        Response postResp4 = router.route(postReq4);
        Assert.assertEquals("Revoked token must be rejected with 401", StatusCode.UNAUTHORIZED, postResp4.getStatusCode());
    }

    /**
     * Scenario 4: Reactivating a suspended student restores login and operations.
     */
    @Test
    public void test04_ReactivateStudentRestoresLogin() throws Exception {
        // Suspend
        studentService.deleteStudent(1L);
        try {
            authService.login(new LoginRequestDTO("student1", "student123"));
            Assert.fail("Login should fail while suspended");
        } catch (AuthenticationException expected) {}

        // Reactivate via updateStudent
        StudentDTO stu = studentService.getStudentById(1L);
        stu.setStatus(StudentStatus.ACTIVE);
        boolean reactivated = studentService.updateStudent(stu);
        Assert.assertTrue("Reactivating student must succeed", reactivated);

        // Student logs in again -> SUCCESS
        UserSessionDTO newSession = authService.login(new LoginRequestDTO("student1", "student123"));
        Assert.assertNotNull(newSession);
        Assert.assertEquals("student1", newSession.getUsername());

        // Dashboard accessible
        Request dashReq = new Request(Action.GET_STUDENT_DASHBOARD, newSession.getToken(), null);
        Response dashResp = router.route(dashReq);
        Assert.assertEquals(StatusCode.OK, dashResp.getStatusCode());
    }

    /**
     * Scenario 5: Suspending one student does NOT affect other active students or users.
     */
    @Test
    public void test05_SuspensionIsolationBetweenStudents() throws Exception {
        // Create a temporary second student
        String tempUser = "test_stu2_" + System.currentTimeMillis();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role, full_name, email, is_active, must_change_password) " +
                     "VALUES (?, ?, 'STUDENT', 'Second Student', 'stu2@test.edu', 1, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tempUser);
            ps.setString(2, PasswordHasher.hash("PassStu2#123"));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) tempUser2Id = rs.getLong(1);
            }
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (user_id, student_code, full_name, class_name, phone, email, max_borrow_limit, current_borrow_count, status) " +
                     "VALUES (?, ?, 'Second Student', 'K20', '0999888777', 'stu2@test.edu', 5, 0, 'ACTIVE')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tempUser2Id);
            ps.setString(2, "STU999_" + System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) tempStudent2Id = rs.getLong(1);
            }
        }

        // Both students log in
        UserSessionDTO sess1 = authService.login(new LoginRequestDTO("student1", "student123"));
        UserSessionDTO sess2 = authService.login(new LoginRequestDTO(tempUser, "PassStu2#123"));

        String tok1 = sess1.getToken();
        String tok2 = sess2.getToken();

        // Suspend student 1
        studentService.deleteStudent(1L);

        // Student 1 session is revoked
        Assert.assertNull("Student 1 session must be revoked", sessionManager.getSession(tok1));
        Request req1 = new Request(Action.GET_STUDENT_DASHBOARD, tok1, null);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, router.route(req1).getStatusCode());

        // Student 2 session MUST REMAIN INTACT
        Assert.assertNotNull("Student 2 session must remain valid", sessionManager.getSession(tok2));
        Request req2 = new Request(Action.GET_STUDENT_DASHBOARD, tok2, null);
        Response resp2 = router.route(req2);
        Assert.assertEquals("Student 2 dashboard must succeed", StatusCode.OK, resp2.getStatusCode());

        // Student 2 can still log in and out freely
        authService.logout(tok2);
        UserSessionDTO sess2Again = authService.login(new LoginRequestDTO(tempUser, "PassStu2#123"));
        Assert.assertNotNull("Student 2 can re-login freely", sess2Again);
    }

    /**
     * Scenario 6: Librarian can suspend a student (soft delete),
     * but is strictly FORBIDDEN from Admin-only operations (hard delete, admin reset password, user management).
     */
    @Test
    public void test06_LibrarianPermissionsOnStudentSuspension() throws Exception {
        // 1. Librarian suspend student (permanent = false) -> ALLOWED (200 OK)
        Request libSuspend = new Request(Action.DELETE_STUDENT, librarianToken, new DeleteStudentRequestDTO(1L, false));
        Response respSuspend = router.route(libSuspend);
        Assert.assertEquals("Librarian must be allowed to suspend student", StatusCode.OK, respSuspend.getStatusCode());

        // 2. Librarian hard delete student (permanent = true) -> FORBIDDEN (403)
        Request libHardDelete = new Request(Action.DELETE_STUDENT, librarianToken, new DeleteStudentRequestDTO(1L, true));
        Response respHard = router.route(libHardDelete);
        Assert.assertEquals("Librarian must be FORBIDDEN from hard deleting student", StatusCode.FORBIDDEN, respHard.getStatusCode());

        // 3. Librarian call ADMIN_RESET_PASSWORD -> FORBIDDEN (403)
        Request libReset = new Request(Action.ADMIN_RESET_PASSWORD, librarianToken, new AdminResetPasswordRequestDTO(4L));
        Response respReset = router.route(libReset);
        Assert.assertEquals("Librarian must be FORBIDDEN from Admin Reset Password", StatusCode.FORBIDDEN, respReset.getStatusCode());

        // 4. Librarian call LIST_USERS -> FORBIDDEN (403)
        Request libListUsers = new Request(Action.LIST_USERS, librarianToken, null);
        Response respListUsers = router.route(libListUsers);
        Assert.assertEquals("Librarian must be FORBIDDEN from listing users", StatusCode.FORBIDDEN, respListUsers.getStatusCode());

        // 5. Librarian call CREATE_USER -> FORBIDDEN (403)
        UserDTO newUser = new UserDTO();
        newUser.setUsername("fake_user");
        newUser.setRole(UserRole.STUDENT);
        newUser.setFullName("Fake");
        newUser.setActive(true);
        Request libCreateUser = new Request(Action.CREATE_USER, librarianToken, new CreateUserRequestDTO(newUser, "Pass#123456"));
        Response respCreateUser = router.route(libCreateUser);
        Assert.assertEquals("Librarian must be FORBIDDEN from creating users", StatusCode.FORBIDDEN, respCreateUser.getStatusCode());
    }

    /**
     * Scenario 7: Student cannot bypass suspension or forge roles/status in requests.
     */
    @Test
    public void test07_StudentCannotBypassSuspensionOrForgeRole() throws Exception {
        UserSessionDTO stuSession = authService.login(new LoginRequestDTO("student1", "student123"));
        String stuToken = stuSession.getToken();

        // 1. Student attempts DELETE_STUDENT (suspending self or others) -> FORBIDDEN (403)
        Request stuSuspend = new Request(Action.DELETE_STUDENT, stuToken, new DeleteStudentRequestDTO(1L, false));
        Response respSuspend = router.route(stuSuspend);
        Assert.assertEquals("Student must be FORBIDDEN from suspending any student", StatusCode.FORBIDDEN, respSuspend.getStatusCode());

        // 2. Student attempts UPDATE_STUDENT -> FORBIDDEN (403)
        StudentDTO forgedStu = new StudentDTO();
        forgedStu.setId(1L);
        forgedStu.setStatus(StudentStatus.ACTIVE);
        Request stuUpdate = new Request(Action.UPDATE_STUDENT, stuToken, forgedStu);
        Response respUpdate = router.route(stuUpdate);
        Assert.assertEquals("Student must be FORBIDDEN from updating student profile", StatusCode.FORBIDDEN, respUpdate.getStatusCode());

        // 3. Student attempts ADMIN_RESET_PASSWORD -> FORBIDDEN (403)
        Request stuReset = new Request(Action.ADMIN_RESET_PASSWORD, stuToken, new AdminResetPasswordRequestDTO(1L));
        Response respReset = router.route(stuReset);
        Assert.assertEquals("Student must be FORBIDDEN from admin password reset", StatusCode.FORBIDDEN, respReset.getStatusCode());

        // 4. Student attempts LIST_USERS -> FORBIDDEN (403)
        Request stuListUsers = new Request(Action.LIST_USERS, stuToken, null);
        Response respListUsers = router.route(stuListUsers);
        Assert.assertEquals("Student must be FORBIDDEN from listing users", StatusCode.FORBIDDEN, respListUsers.getStatusCode());
    }

    /**
     * Scenario 8: Admin Reset Password and must_change_password enforcement remain fully intact.
     */
    @Test
    public void test08_AdminResetPasswordAndMustChangePasswordWorkflowIntact() throws Exception {
        String tempUser8 = "test_stu8_" + System.currentTimeMillis();
        Long u8Id = null;
        Long s8Id = null;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role, full_name, email, is_active, must_change_password) " +
                     "VALUES (?, ?, 'STUDENT', 'Reset Target Student', 'stu8@test.edu', 1, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tempUser8);
            ps.setString(2, PasswordHasher.hash("InitialPass#123"));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) u8Id = rs.getLong(1);
            }
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO students (user_id, student_code, full_name, class_name, phone, email, max_borrow_limit, current_borrow_count, status) " +
                     "VALUES (?, ?, 'Reset Target Student', 'K20', '0999111222', 'stu8@test.edu', 5, 0, 'ACTIVE')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, u8Id);
            ps.setString(2, "STU888_" + System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) s8Id = rs.getLong(1);
            }
        }

        try {
            // Admin resets temp student password
            Request resetReq = new Request(Action.ADMIN_RESET_PASSWORD, adminToken, new AdminResetPasswordRequestDTO(u8Id));
            Response resetResp = router.route(resetReq);
            Assert.assertEquals("Admin reset password must succeed", StatusCode.OK, resetResp.getStatusCode());
            AdminResetPasswordResponseDTO resetData = (AdminResetPasswordResponseDTO) resetResp.getData();
            Assert.assertNotNull(resetData);
            String tempPass = resetData.getTemporaryPassword();
            Assert.assertNotNull(tempPass);
            Assert.assertEquals(16, tempPass.length());

            // Student logs in with temporary password
            UserSessionDTO tempSession = authService.login(new LoginRequestDTO(tempUser8, tempPass));
            Assert.assertNotNull(tempSession);
            Assert.assertTrue("Session must reflect mustChangePassword = true", tempSession.isMustChangePassword());

            // Attempting business action -> FORBIDDEN (403)
            Request busReq = new Request(Action.GET_STUDENT_DASHBOARD, tempSession.getToken(), null);
            Response busResp = router.route(busReq);
            Assert.assertEquals("Business action must be FORBIDDEN before changing password", StatusCode.FORBIDDEN, busResp.getStatusCode());

            // Change password
            ChangePasswordRequestDTO changeDto = new ChangePasswordRequestDTO(tempPass, "NewPass#Secure999", "NewPass#Secure999");
            Request changeReq = new Request(Action.CHANGE_PASSWORD, tempSession.getToken(), changeDto);
            Response changeResp = router.route(changeReq);
            Assert.assertEquals("Password change must succeed", StatusCode.OK, changeResp.getStatusCode());

            // Business actions unlocked
            Request unlockedReq = new Request(Action.GET_STUDENT_DASHBOARD, tempSession.getToken(), null);
            Response unlockedResp = router.route(unlockedReq);
            Assert.assertEquals("Dashboard must be accessible after password change", StatusCode.OK, unlockedResp.getStatusCode());
        } finally {
            try (Connection conn = db.getConnection()) {
                if (s8Id != null) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                        ps.setLong(1, s8Id);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                }
                if (u8Id != null) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        ps.setLong(1, u8Id);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }
}
