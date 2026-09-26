package thuvien.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class FineDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long borrowRecordId;
    private Long studentId;
    private String studentName;
    private String bookTitle;
    private int overdueDays;
    private BigDecimal fineRatePerDay;
    private BigDecimal fineAmount;
    private boolean paid;
    private Date paidDate;
    private Long collectedByUserId;
    private Date createdAt;

    public FineDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBorrowRecordId() {
        return borrowRecordId;
    }

    public void setBorrowRecordId(Long borrowRecordId) {
        this.borrowRecordId = borrowRecordId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(int overdueDays) {
        this.overdueDays = overdueDays;
    }

    public BigDecimal getFineRatePerDay() {
        return fineRatePerDay;
    }

    public void setFineRatePerDay(BigDecimal fineRatePerDay) {
        this.fineRatePerDay = fineRatePerDay;
    }

    public BigDecimal getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(BigDecimal fineAmount) {
        this.fineAmount = fineAmount;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public Date getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(Date paidDate) {
        this.paidDate = paidDate;
    }

    public Long getCollectedByUserId() {
        return collectedByUserId;
    }

    public void setCollectedByUserId(Long collectedByUserId) {
        this.collectedByUserId = collectedByUserId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
