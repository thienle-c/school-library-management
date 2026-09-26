package thuvien.server.service;

import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;

public interface AuthService {
    UserSessionDTO login(LoginRequestDTO request) throws AuthenticationException;
    void logout(String token);
    UserSessionDTO validateSession(String token) throws AuthenticationException;

    default boolean isAccountLocked(String username) {
        return false;
    }

    default boolean isAccountLocked(Long userId) {
        return false;
    }

    default void unlockAccount(String username) {
    }

    default void unlockAccount(Long userId) {
    }

    default Long getLockoutExpiry(String username) {
        return null;
    }
}
