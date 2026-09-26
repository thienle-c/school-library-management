package thuvien.common.dto;

import java.io.Serializable;
import thuvien.common.enums.BookStatus;

public class BookSearchCriteriaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String keyword;
    private Integer categoryId;
    private Integer authorId;
    private BookStatus status;
    private int page = 1;
    private int pageSize = 20;

    public BookSearchCriteriaDTO() {}

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }

    public BookStatus getStatus() {
        return status;
    }

    public void setStatus(BookStatus status) {
        this.status = status;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
