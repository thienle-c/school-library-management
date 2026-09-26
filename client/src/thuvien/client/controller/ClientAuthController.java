package thuvien.client.controller;

import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.RegisterStudentRequestDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

public class ClientAuthController {
    private final NetworkClient networkClient;

    public ClientAuthController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    public UserSessionDTO login(String username, String password) throws AuthenticationException, NetworkException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationException("Tên đăng nhập không được để trống.");
        }
        if (password == null || password.isEmpty()) {
            throw new AuthenticationException("Mật khẩu không được để trống.");
        }

        LoginRequestDTO payload = new LoginRequestDTO(username.trim(), password);
        Request request = new Request(Action.LOGIN, payload);

        Response response = networkClient.send(request);
        if (response.isSuccess() && response.getData() instanceof UserSessionDTO) {
            UserSessionDTO session = (UserSessionDTO) response.getData();
            ClientSession.getInstance().setSession(session);
            return session;
        } else if (response.getStatusCode() == StatusCode.UNAUTHORIZED) {
            throw new AuthenticationException(response.getMessage() != null ? response.getMessage() : "Tên đăng nhập hoặc mật khẩu không chính xác.");
        } else {
            String msg = response.getMessage();
            throw new AuthenticationException(msg != null && !msg.isEmpty() ? msg : "Đăng nhập thất bại.");
        }
    }

    public boolean registerStudent(RegisterStudentRequestDTO requestDto) throws LibraryException, NetworkException {
        if (requestDto == null) {
            throw new ValidationException("Thông tin đăng ký không hợp lệ.");
        }
        Request request = new Request(Action.REGISTER_STUDENT, requestDto);
        Response response = networkClient.send(request);
        if (response.isSuccess()) {
            return true;
        } else if (response.getStatusCode() == StatusCode.BAD_REQUEST) {
            throw new ValidationException(response.getMessage());
        } else {
            throw new LibraryException(response.getMessage());
        }
    }

    public boolean changePassword(String currentPassword, String newPassword) throws LibraryException, NetworkException {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new ValidationException("Mật khẩu hiện tại không được để trống.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("Mật khẩu mới phải có ít nhất 6 ký tự.");
        }
        ChangePasswordRequestDTO payload = new ChangePasswordRequestDTO(currentPassword, newPassword);
        Request request = new Request(Action.CHANGE_PASSWORD, payload);
        Response response = networkClient.send(request);
        if (response.isSuccess()) {
            return true;
        } else if (response.getStatusCode() == StatusCode.BAD_REQUEST) {
            throw new ValidationException(response.getMessage());
        } else {
            throw new LibraryException(response.getMessage());
        }
    }

    public void logout() {
        try {
            Request request = new Request(Action.LOGOUT, null);
            networkClient.send(request);
        } catch (Exception ignored) {
        } finally {
            ClientSession.getInstance().clear();
            networkClient.disconnect();
        }
    }
}

