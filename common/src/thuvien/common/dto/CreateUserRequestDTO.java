package thuvien.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for creating a new user with an explicit initial password.
 */
public class CreateUserRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private UserDTO user;
    private String initialPassword;

    public CreateUserRequestDTO() {}

    public CreateUserRequestDTO(UserDTO user, String initialPassword) {
        this.user = user;
        this.initialPassword = initialPassword;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    @Override
    public String toString() {
        return "CreateUserRequestDTO{" +
                "user=" + (user != null ? user.getUsername() : "null") +
                ", initialPassword=[PROTECTED]" +
                '}';
    }
}
