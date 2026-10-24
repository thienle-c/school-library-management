package thuvien.common.dto;

import java.io.Serializable;

/**
 * DTO for Admin Reset Password response envelope.
 * Returns the one-time temporary password exclusively to the authorized Admin.
 */
public class AdminResetPasswordResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String temporaryPassword;
    private boolean mustChangePassword;

    public AdminResetPasswordResponseDTO() {}

    public AdminResetPasswordResponseDTO(Long userId, String username, String temporaryPassword, boolean mustChangePassword) {
        this.userId = userId;
        this.username = username;
        this.temporaryPassword = temporaryPassword;
        this.mustChangePassword = mustChangePassword;
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

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }
}
