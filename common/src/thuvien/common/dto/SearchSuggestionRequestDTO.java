package thuvien.common.dto;

import java.io.Serializable;

/**
 * Request payload for search autocomplete suggestions.
 */
public class SearchSuggestionRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type; // "BOOK", "STUDENT", "BORROW", "RESERVATION", "FINE"
    private String keyword;

    public SearchSuggestionRequestDTO() {}

    public SearchSuggestionRequestDTO(String type, String keyword) {
        this.type = type;
        this.keyword = keyword;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
