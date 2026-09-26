package thuvien.common.dto;

import java.io.Serializable;

/**
 * Request payload for self-registration of a student account.
 */
public class RegisterStudentRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String studentCode;
    private String fullName;
    private String email;
    private String username;
    private String password;
    private String confirmPassword;
    private String activationCode;

    public RegisterStudentRequestDTO() {}

    public RegisterStudentRequestDTO(String studentCode, String fullName, String email,
                                   String username, String password, String confirmPassword,
                                   String activationCode) {
        this.studentCode = studentCode;
        this.fullName = fullName;
        this.email = email;
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.activationCode = activationCode;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
}
