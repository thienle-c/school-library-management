package thuvien.client.controller;

import thuvien.client.network.NetworkClient;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.dto.StudentDashboardDTO;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

public class ClientDashboardController {
    private final NetworkClient networkClient;

    public ClientDashboardController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    public DashboardMetricsDTO getMetrics() throws LibraryException, NetworkException {
        Request request = new Request(Action.GET_DASHBOARD_METRICS, null);
        Response response = networkClient.send(request);

        if (response.getStatusCode() == thuvien.common.protocol.StatusCode.UNAUTHORIZED) {
            thuvien.client.session.ClientSession.getInstance().clear();
            throw new thuvien.common.exception.AuthenticationException("Session expired: " + response.getMessage());
        }

        if (response.isSuccess() && response.getData() instanceof DashboardMetricsDTO) {
            return (DashboardMetricsDTO) response.getData();
        } else {
            throw new LibraryException(response.getMessage());
        }
    }

    public StudentDashboardDTO getStudentDashboard() throws LibraryException, NetworkException {
        Request request = new Request(Action.GET_STUDENT_DASHBOARD, null);
        Response response = networkClient.send(request);

        if (response.getStatusCode() == thuvien.common.protocol.StatusCode.UNAUTHORIZED) {
            thuvien.client.session.ClientSession.getInstance().clear();
            throw new thuvien.common.exception.AuthenticationException("Session expired: " + response.getMessage());
        }

        if (response.isSuccess() && response.getData() instanceof StudentDashboardDTO) {
            return (StudentDashboardDTO) response.getData();
        } else {
            throw new LibraryException(response.getMessage());
        }
    }
}

