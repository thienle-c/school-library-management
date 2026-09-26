package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.common.dto.SearchSuggestionRequestDTO;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

/**
 * Client controller for fetching search autocomplete suggestions over TCP.
 */
public class ClientSearchSuggestionController {
    private final NetworkClient networkClient;

    public ClientSearchSuggestionController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    @SuppressWarnings("unchecked")
    public List<String> getSuggestions(String type, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            SearchSuggestionRequestDTO payload = new SearchSuggestionRequestDTO(type, keyword.trim());
            Request request = new Request(Action.SEARCH_SUGGESTIONS, payload);
            Response response = networkClient.send(request);
            if (response.isSuccess() && response.getData() instanceof List) {
                return (List<String>) response.getData();
            }
        } catch (Exception e) {
            // Suggestion failure should fail silently and gracefully without breaking UI
        }
        return Collections.emptyList();
    }
}
