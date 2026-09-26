package thuvien.server.service;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.server.repository.UserRepository;
import thuvien.server.security.PasswordHasher;
import thuvien.server.security.SessionManager;
import thuvien.server.service.impl.AuthServiceImpl;

public class AuthServiceTest {

    private AuthService authService;

    @Before
    public void setUp() {
        UserRepository mockUserRepo = new UserRepository() {
            @Override
            public UserDTO findById(Long id) {
                return null;
            }

            @Override
            public UserDTO findByUsername(String username) {
                if ("librarian1".equalsIgnoreCase(username)) {
                    UserDTO u = new UserDTO();
                    u.setId(2L);
                    u.setUsername("librarian1");
                    u.setFullName("Librarian One");
                    u.setRole(UserRole.LIBRARIAN);
                    u.setActive(true);
                    return u;
                } else if ("inactive_user".equalsIgnoreCase(username)) {
                    UserDTO u = new UserDTO();
                    u.setId(3L);
                    u.setUsername("inactive_user");
                    u.setFullName("Inactive User");
                    u.setRole(UserRole.STUDENT);
                    u.setActive(false);
                    return u;
                }
                return null;
            }

            @Override
            public String getPasswordHash(String username) {
                if ("librarian1".equalsIgnoreCase(username)) {
                    return PasswordHasher.hash("lib123");
                }
                return null;
            }

            @Override
            public List<UserDTO> findAll() {
                return Collections.emptyList();
            }

            @Override
            public Long create(UserDTO user, String passwordHash, Connection conn) {
                return 1L;
            }

            @Override
            public boolean update(UserDTO user, Connection conn) {
                return true;
            }

            @Override
            public boolean updatePassword(Long userId, String newPasswordHash, Connection conn) {
                return true;
            }

            @Override
            public boolean delete(Long id, Connection conn) {
                return true;
            }
        };

        authService = new AuthServiceImpl(mockUserRepo, SessionManager.getInstance());
    }

    @Test
    public void testLoginSuccess() throws AuthenticationException {
        UserSessionDTO session = authService.login(new LoginRequestDTO("librarian1", "lib123"));
        Assert.assertNotNull(session);
        Assert.assertEquals("librarian1", session.getUsername());
        Assert.assertEquals(UserRole.LIBRARIAN, session.getRole());
        Assert.assertNotNull(session.getToken());

        UserSessionDTO validated = authService.validateSession(session.getToken());
        Assert.assertEquals(session.getUsername(), validated.getUsername());
    }

    @Test(expected = AuthenticationException.class)
    public void testLoginWrongPassword() throws AuthenticationException {
        authService.login(new LoginRequestDTO("librarian1", "wrongpwd"));
    }

    @Test(expected = AuthenticationException.class)
    public void testLoginInactiveAccount() throws AuthenticationException {
        authService.login(new LoginRequestDTO("inactive_user", "any"));
    }

    @Test(expected = AuthenticationException.class)
    public void testLoginNonExistentUser() throws AuthenticationException {
        authService.login(new LoginRequestDTO("ghost", "pass"));
    }

    @Test
    public void testLogout() throws AuthenticationException {
        UserSessionDTO session = authService.login(new LoginRequestDTO("librarian1", "lib123"));
        String token = session.getToken();
        authService.logout(token);

        try {
            authService.validateSession(token);
            Assert.fail("Expected AuthenticationException after logout");
        } catch (AuthenticationException expected) {
            Assert.assertTrue(expected.getMessage().contains("invalid or has expired"));
        }
    }
}
