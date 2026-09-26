package thuvien.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class DashboardMetricsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalBooks;
    private int availableBooks;
    private int borrowedBooks;
    private int totalStudents;
    private int activeBorrowCount;
    private int overdueBorrowCount;
    private int pendingReservations;
    private BigDecimal totalUnpaidFines = BigDecimal.ZERO;

    public DashboardMetricsDTO() {}

    public int getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(int totalBooks) {
        this.totalBooks = totalBooks;
    }

    public int getAvailableBooks() {
        return availableBooks;
    }

    public void setAvailableBooks(int availableBooks) {
        this.availableBooks = availableBooks;
    }

    public int getBorrowedBooks() {
        return borrowedBooks;
    }

    public void setBorrowedBooks(int borrowedBooks) {
        this.borrowedBooks = borrowedBooks;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
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

    public int getPendingReservations() {
        return pendingReservations;
    }

    public void setPendingReservations(int pendingReservations) {
        this.pendingReservations = pendingReservations;
    }

    public BigDecimal getTotalUnpaidFines() {
        return totalUnpaidFines;
    }

    public void setTotalUnpaidFines(BigDecimal totalUnpaidFines) {
        this.totalUnpaidFines = totalUnpaidFines;
    }
}
