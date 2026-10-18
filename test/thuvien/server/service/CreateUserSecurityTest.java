package thuvien.server.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.CreateUserRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.repository.UserRepository;
import thuvien.server.router.RequestRouter;
import thuvien.server.security.PasswordHasher;
import thuvien.server.security.SessionManager;
import thuvien.server.service.impl.UserServiceImpl;

/**
 * Unit test suite verifying LOGIC-01 remediation:
 * 1. Initial password is required, validated, and hashed with PBKDF2.
 * 2. Hardcoded default 'password123' is completely eliminated.
 * 3. Only ADMIN can create users (RBAC validation).
 * 4. Passwords are never stored as plaintext.
 */
public class CreateUserSecurityTest {

    private SessionManager sessionManager;
    private String adminToken;
    private String librarianToken;
    private String studentToken;

    private AtomicReference<String> capturedHash;
    private AtomicReference<UserDTO> capturedUser;
    private UserService userService;
    private RequestRouter router;

    @Before
    public void setUp() {
        sessionManager = SessionManager.getInstance();
        sessionManager.clearAllSessions();

        adminToken = sessionManager.createSession(new UserSessionDTO(null, 1L, "admin", "Admin User", UserRole.ADMIN));
        librarianToken = sessionManager.createSession(new UserSessionDTO(null, 2L, "librarian1", "Librarian User", UserRole.LIBRARIAN));
        studentToken = sessionManager.createSession(new UserSessionDTO(null, 3L, "student1", "Student User", UserRole.STUDENT));

        capturedHash = new AtomicReference<>();
        capturedUser = new AtomicReference<>();

        UserRepository mockRepo = new UserRepository() {
            @Override
            public UserDTO findById(Long id) throws SQLException { return null; }
            @Override
            public UserDTO findByUsername(String username) throws SQLException { return null; }
            @Override
            public String getPasswordHash(String username) throws SQLException { return capturedHash.get(); }
            @Override
            public List<UserDTO> findAll() throws SQLException { return Collections.emptyList(); }
            @Override
            public Long create(UserDTO user, String passwordHash, Connection conn) throws SQLException {
                capturedUser.set(user);
                capturedHash.set(passwordHash);
                return 999L;
            }
            @Override
            public boolean update(UserDTO user, Connection conn) throws SQLException { return true; }
            @Override
            public boolean delete(Long id, Connection conn) throws SQLException { return true; }
            @Override
            public boolean updatePassword(Long userId, String passwordHash, Connection conn) throws SQLException { return true; }
        };

        userService = new UserServiceImpl(mockRepo);

        // Instantiate RequestRouter with mocked user service
        router = new RequestRouter(
                null, // authService
                null, // bookService
                null, // studentService
                null, // borrowService
                null, // returnService
                null, // fineService
                null, // dashboardService
                null, // reservationService
                null, // auditLogService
                null, // categoryService
                null, // authorService
                userService
        );
    }

    @Test
    public void testCreateUserWithValidCustomPasswordHashesWithPbkdf2() throws Exception {
        UserDTO user = new UserDTO();
        user.setUsername("new_librarian");
        user.setFullName("Nguyen Van A");
        user.setRole(UserRole.LIBRARIAN);

        String customPassword = "CustomLibrarianSecretPass#2026";
        Long id = userService.createUser(user, customPassword);

        Assert.assertNotNull(id);
        Assert.assertEquals(Long.valueOf(999L), id);
        Assert.assertNotNull(capturedHash.get());

        String hash = capturedHash.get();
        // 1. Must use PBKDF2 format
        Assert.assertTrue("Password hash must start with PBKDF2$10000$", hash.startsWith("PBKDF2$10000$"));

        // 2. Must verify against the supplied custom password
        Assert.assertTrue("Supplied custom password must verify against stored hash",
                PasswordHasher.verify(customPassword, hash));

        // 3. Must NOT verify against 'password123'
        Assert.assertFalse("Old hardcoded password 'password123' must NOT match",
                PasswordHasher.verify("password123", hash));

        // 4. Stored hash must never be plaintext
        Assert.assertFalse("Password in database must never be plaintext", hash.equals(customPassword));
    }

    @Test
    public void testCreateUserRejectsEmptyOrShortPasswords() {
        UserDTO user = new UserDTO();
        user.setUsername("test_user");
        user.setFullName("Test User");

        // Null password
        try {
            userService.createUser(user, null);
            Assert.fail("Null password must be rejected");
        } catch (ValidationException e) {
            Assert.assertTrue(e.getMessage().contains("cannot be empty"));
        } catch (Exception e) {
            Assert.fail("Expected ValidationException but got " + e.getClass().getName());
        }

        // Empty password
        try {
            userService.createUser(user, "   ");
            Assert.fail("Blank password must be rejected");
        } catch (ValidationException e) {
            Assert.assertTrue(e.getMessage().contains("cannot be empty"));
        } catch (Exception e) {
            Assert.fail("Expected ValidationException but got " + e.getClass().getName());
        }

        // Short password (< 6 chars)
        try {
            userService.createUser(user, "12345");
            Assert.fail("Short password (< 6 chars) must be rejected");
        } catch (ValidationException e) {
            Assert.assertTrue(e.getMessage().contains("at least 6 characters"));
        } catch (Exception e) {
            Assert.fail("Expected ValidationException but got " + e.getClass().getName());
        }
    }

    @Test
    public void testRouterEnforcesAdminRoleForCreateUser() {
        UserDTO user = new UserDTO();
        user.setUsername("staff1");
        user.setFullName("Staff One");
        user.setRole(UserRole.LIBRARIAN);

        CreateUserRequestDTO payload = new CreateUserRequestDTO(user, "StaffValidPass#1");

        // 1. Admin creates user -> Success (201 CREATED)
        Request adminReq = new Request(Action.CREATE_USER, payload);
        adminReq.setToken(adminToken);
        Response adminResp = router.route(adminReq);
        Assert.assertEquals(StatusCode.CREATED, adminResp.getStatusCode());

        // 2. Librarian attempts to create user -> 403 FORBIDDEN
        Request libReq = new Request(Action.CREATE_USER, payload);
        libReq.setToken(librarianToken);
        Response libResp = router.route(libReq);
        Assert.assertEquals(StatusCode.FORBIDDEN, libResp.getStatusCode());

        // 3. Student attempts to create user -> 403 FORBIDDEN
        Request stuReq = new Request(Action.CREATE_USER, payload);
        stuReq.setToken(studentToken);
        Response stuResp = router.route(stuReq);
        Assert.assertEquals(StatusCode.FORBIDDEN, stuResp.getStatusCode());

        // 4. Unauthenticated guest attempts to create user -> 401 UNAUTHORIZED
        Request guestReq = new Request(Action.CREATE_USER, payload);
        Response guestResp = router.route(guestReq);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, guestResp.getStatusCode());
    }

    @Test
    public void testRouterRejectsCreateUserWithShortPassword() {
        UserDTO user = new UserDTO();
        user.setUsername("staff2");
        user.setFullName("Staff Two");

        CreateUserRequestDTO payload = new CreateUserRequestDTO(user, "abc"); // only 3 chars
        Request adminReq = new Request(Action.CREATE_USER, payload);
        adminReq.setToken(adminToken);

        Response resp = router.route(adminReq);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp.getStatusCode());
        Assert.assertTrue(resp.getMessage().contains("at least 6 characters"));
    }
}
