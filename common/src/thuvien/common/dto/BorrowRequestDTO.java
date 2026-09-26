package thuvien.common.dto;

import java.io.Serializable;

public class BorrowRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long studentId;
    private Long bookId;
    private int durationDays = 14;
    private String notes;

    public BorrowRequestDTO() {}

    public BorrowRequestDTO(Long studentId, Long bookId) {
        this.studentId = studentId;
        this.bookId = bookId;
    }

    public BorrowRequestDTO(Long studentId, Long bookId, int durationDays, String notes) {
        this.studentId = studentId;
        this.bookId = bookId;
        this.durationDays = durationDays;
        this.notes = notes;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(int durationDays) {
        this.durationDays = durationDays;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
