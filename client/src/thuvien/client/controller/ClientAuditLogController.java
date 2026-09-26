package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

/**
 * Client controller for system audit log retrieval over TCP.
 * Restricted to administrators by server-side policy.
 */
public class ClientAuditLogController {

    private final NetworkClient networkClient;

    public ClientAuditLogController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response == null) {
            throw new LibraryException("No response received from library server.");
        }
        if (!response.isSuccess()) {
            int code = response.getStatusCode();
            String msg = response.getMessage() != null ? response.getMessage() : "Unknown server error";
            if (code == StatusCode.UNAUTHORIZED) {
                throw new AuthenticationException(msg);
            }
            if (code == StatusCode.FORBIDDEN) {
                throw new AuthorizationException(msg);
            }
            throw new LibraryException(msg);
        }
    }

    @SuppressWarnings("unchecked")
    public List<AuditLogDTO> listRecentLogs(int limit) throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_AUDIT_LOGS, limit > 0 ? limit : 50);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<AuditLogDTO>) response.getData();
        }
        return Collections.emptyList();
    }
}
