package thuvien.common.dto;

import java.io.Serializable;

/**
 * Request payload for changing user password.
 */
public class ChangePasswordRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    public ChangePasswordRequestDTO() {}

    public ChangePasswordRequestDTO(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, newPassword);
    }

    public ChangePasswordRequestDTO(String currentPassword, String newPassword, String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
