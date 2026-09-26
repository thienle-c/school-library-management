package thuvien.common.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class PageResponseDTO<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<T> items;
    private int page;
    private int pageSize;
    private int totalItems;
    private int totalPages;

    public PageResponseDTO() {
        this.items = Collections.emptyList();
    }

    public PageResponseDTO(List<T> items, int page, int pageSize, int totalItems) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        this.totalPages = (int) Math.ceil((double) totalItems / pageSize);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
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

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
