package thuvien.common.dto;

import java.io.Serializable;
import thuvien.common.enums.UserRole;

public class UserSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private Long userId;
    private String username;
    private String fullName;
    private UserRole role;

    public UserSessionDTO() {}

    public UserSessionDTO(String token, Long userId, String username, String fullName, UserRole role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "UserSessionDTO{" +
                "username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role=" + role +
                '}';
    }
}
