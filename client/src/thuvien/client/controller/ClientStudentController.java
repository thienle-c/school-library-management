package thuvien.client.controller;

import java.util.Collections;
import java.util.List;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.common.dto.StudentDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.NetworkException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

/**
 * Client controller for Student records and profile operations over TCP.
 */
public class ClientStudentController {
    private final NetworkClient networkClient;

    public ClientStudentController(NetworkClient networkClient) {
        this.networkClient = networkClient;
    }

    private void checkError(Response response) throws LibraryException {
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = response.getMessage() != null && !response.getMessage().isEmpty()
                ? response.getMessage()
                : "Student operation failed with status " + code;

        if (code == StatusCode.UNAUTHORIZED) {
            ClientSession.getInstance().clear();
            throw new AuthenticationException("Session expired: " + msg);
        } else if (code == StatusCode.FORBIDDEN) {
            throw new AuthorizationException("Permission denied: " + msg);
        } else if (code == StatusCode.NOT_FOUND) {
            throw new EntityNotFoundException(msg);
        } else if (code == StatusCode.BAD_REQUEST) {
            throw new ValidationException(msg);
        } else {
            throw new LibraryException(msg);
        }
    }

    @SuppressWarnings("unchecked")
    public List<StudentDTO> listStudents() throws LibraryException, NetworkException {
        Request request = new Request(Action.LIST_STUDENTS, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof List) {
            return (List<StudentDTO>) response.getData();
        }
        return Collections.emptyList();
    }

    public StudentDTO getStudent(Long id) throws LibraryException, NetworkException {
        if (id == null || id <= 0) {
            throw new ValidationException("Valid Student ID is required.");
        }
        Request request = new Request(Action.GET_STUDENT, id);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof StudentDTO) {
            return (StudentDTO) response.getData();
        }
        throw new EntityNotFoundException("Student ID " + id + " not found.");
    }

    public StudentDTO getStudentByCode(String code) throws LibraryException, NetworkException {
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationException("Student code cannot be empty.");
        }
        Request request = new Request(Action.GET_STUDENT, code.trim());
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof StudentDTO) {
            return (StudentDTO) response.getData();
        }
        throw new EntityNotFoundException("Student with code '" + code + "' not found.");
    }

    public StudentDTO getMyProfile() throws LibraryException, NetworkException {
        Request request = new Request(Action.GET_STUDENT, null);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof StudentDTO) {
            return (StudentDTO) response.getData();
        }
        throw new EntityNotFoundException("Authenticated student profile not found.");
    }

    public Long createStudent(StudentDTO student) throws LibraryException, NetworkException {
        if (student == null) {
            throw new ValidationException("Student data cannot be null.");
        }
        Request request = new Request(Action.CREATE_STUDENT, student);
        Response response = networkClient.send(request);
        checkError(response);
        if (response.getData() instanceof Number) {
            return ((Number) response.getData()).longValue();
        }
        return null;
    }

    public boolean updateStudent(StudentDTO student) throws LibraryException, NetworkException {
        if (student == null || student.getId() == null) {
            throw new ValidationException("Student ID is required for update.");
        }
        Request request = new Request(Action.UPDATE_STUDENT, student);
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }

    public boolean deleteStudent(Long id) throws LibraryException, NetworkException {
        if (id == null || id <= 0) {
            throw new ValidationException("Mã sinh viên không hợp lệ.");
        }
        Request request = new Request(Action.DELETE_STUDENT, new thuvien.common.dto.DeleteStudentRequestDTO(id, false));
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }

    public boolean hardDeleteStudent(Long id) throws LibraryException, NetworkException {
        if (id == null || id <= 0) {
            throw new ValidationException("Mã sinh viên không hợp lệ.");
        }
        Request request = new Request(Action.DELETE_STUDENT, new thuvien.common.dto.DeleteStudentRequestDTO(id, true));
        Response response = networkClient.send(request);
        checkError(response);
        return Boolean.TRUE.equals(response.getData()) || response.isSuccess();
    }

    public String generateActivationCode(Long studentId) throws LibraryException, NetworkException {
        if (studentId == null || studentId <= 0) {
            throw new ValidationException("Mã sinh viên không hợp lệ.");
        }
        Request request = new Request(Action.GENERATE_STUDENT_ACTIVATION, studentId);
        Response response = networkClient.send(request);
        checkError(response);
        return response.getData() != null ? response.getData().toString() : null;
    }
}

