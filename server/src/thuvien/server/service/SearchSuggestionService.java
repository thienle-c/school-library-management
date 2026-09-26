package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.LibraryException;

public interface SearchSuggestionService {
    List<String> getSuggestions(String type, String keyword, UserSessionDTO session)
            throws AuthorizationException, LibraryException;
}
