package thuvien.common.dto;

import java.io.Serializable;

/**
 * Request payload for student deletion, supporting soft suspension or permanent purge.
 */
public class DeleteStudentRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long studentId;
    private boolean permanent; // true = hard delete (admin only), false = suspend (soft delete)

    public DeleteStudentRequestDTO() {}

    public DeleteStudentRequestDTO(Long studentId, boolean permanent) {
        this.studentId = studentId;
        this.permanent = permanent;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }
}
