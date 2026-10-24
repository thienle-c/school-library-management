package thuvien.common.dto;

import java.io.Serializable;

/**
 * DTO for Admin Reset Password request envelope.
 */
public class AdminResetPasswordRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long targetUserId;

    public AdminResetPasswordRequestDTO() {}

    public AdminResetPasswordRequestDTO(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
