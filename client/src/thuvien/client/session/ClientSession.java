package thuvien.client.session;

import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;

/**
 * Client session state store (current logged-in user, token, role).
 */
public class ClientSession {
    private static ClientSession instance;
    private UserSessionDTO currentUser;

    private ClientSession() {}

    public static synchronized ClientSession getInstance() {
        if (instance == null) {
            instance = new ClientSession();
        }
        return instance;
    }

    public synchronized void setSession(UserSessionDTO session) {
        this.currentUser = session;
    }

    public synchronized UserSessionDTO getCurrentUser() {
        return currentUser;
    }

    public synchronized String getToken() {
        return currentUser != null ? currentUser.getToken() : null;
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null && currentUser.getToken() != null;
    }

    public synchronized UserRole getRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    public synchronized String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }

    public synchronized String getFullName() {
        return currentUser != null ? currentUser.getFullName() : null;
    }

    public synchronized Long getUserId() {
        return currentUser != null ? currentUser.getUserId() : null;
    }

    public synchronized boolean hasRole(UserRole role) {
        return currentUser != null && currentUser.getRole() == role;
    }

    public synchronized boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    public synchronized boolean isLibrarian() {
        return hasRole(UserRole.LIBRARIAN);
    }

    public synchronized boolean isStudent() {
        return hasRole(UserRole.STUDENT);
    }

    public synchronized void clear() {
        this.currentUser = null;
    }
}
