package thuvien.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated dashboard metrics for an authenticated student user.
 */
public class StudentDashboardDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long studentId;
    private String studentCode;
    private String fullName;
    private String className;
    private int activeBorrowCount;
    private int overdueBorrowCount;
    private int pendingReservationCount;
    private BigDecimal unpaidFineAmount;
    private List<BorrowRecordDTO> recentBorrows = new ArrayList<>();
    private List<ReservationDTO> recentReservations = new ArrayList<>();

    public StudentDashboardDTO() {}

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getActiveBorrowCount() {
        return activeBorrowCount;
    }

    public void setActiveBorrowCount(int activeBorrowCount) {
        this.activeBorrowCount = activeBorrowCount;
    }

    public int getOverdueBorrowCount() {
        return overdueBorrowCount;
    }

    public void setOverdueBorrowCount(int overdueBorrowCount) {
        this.overdueBorrowCount = overdueBorrowCount;
    }

    public int getPendingReservationCount() {
        return pendingReservationCount;
    }

    public void setPendingReservationCount(int pendingReservationCount) {
        this.pendingReservationCount = pendingReservationCount;
    }

    public BigDecimal getUnpaidFineAmount() {
        return unpaidFineAmount;
    }

    public void setUnpaidFineAmount(BigDecimal unpaidFineAmount) {
        this.unpaidFineAmount = unpaidFineAmount;
    }

    public List<BorrowRecordDTO> getRecentBorrows() {
        return recentBorrows;
    }

    public void setRecentBorrows(List<BorrowRecordDTO> recentBorrows) {
        this.recentBorrows = recentBorrows;
    }

    public List<ReservationDTO> getRecentReservations() {
        return recentReservations;
    }

    public void setRecentReservations(List<ReservationDTO> recentReservations) {
        this.recentReservations = recentReservations;
    }
}
