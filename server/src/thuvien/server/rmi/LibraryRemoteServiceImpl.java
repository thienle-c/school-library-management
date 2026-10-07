package thuvien.server.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.List;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.dto.DeleteStudentRequestDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.dto.RegisterStudentRequestDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.dto.SearchSuggestionRequestDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.StudentDashboardDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.common.rmi.LibraryRemoteService;
import thuvien.server.router.RequestRouter;

/**
 * Concrete Java RMI Remote Implementation of LibraryRemoteService.
 * Extends UnicastRemoteObject to enable remote method invocation over RMI.
 * Delegates business processing and security to RequestRouter, ensuring 100%
 * consistency with authorization, RBAC, session invalidation, and atomic database transactions.
 */
public class LibraryRemoteServiceImpl extends UnicastRemoteObject implements LibraryRemoteService {
    private static final long serialVersionUID = 1L;

    private final RequestRouter router;

    public LibraryRemoteServiceImpl() throws RemoteException {
        this(0, new RequestRouter());
    }

    public LibraryRemoteServiceImpl(int port) throws RemoteException {
        this(port, new RequestRouter());
    }

    public LibraryRemoteServiceImpl(int port, RequestRouter router) throws RemoteException {
        super(port);
        this.router = (router != null) ? router : new RequestRouter();
    }

    private void unwrapOrThrow(Response response) throws LibraryException {
        if (response == null) {
            throw new LibraryException("No response received from remote server.");
        }
        if (response.isSuccess()) {
            return;
        }
        int code = response.getStatusCode();
        String msg = (response.getMessage() != null && !response.getMessage().isEmpty())
                ? response.getMessage()
                : "RMI operation failed with status code " + code;

        if (code == StatusCode.UNAUTHORIZED) {
            throw new AuthenticationException(msg);
        } else if (code == StatusCode.FORBIDDEN) {
            throw new AuthorizationException(msg);
        } else if (code == StatusCode.NOT_FOUND) {
            throw new EntityNotFoundException(msg);
        } else if (code == StatusCode.BAD_REQUEST) {
            throw new ValidationException(msg);
        } else {
            throw new LibraryException(msg);
        }
    }

    @Override
    public Response execute(Request request) throws RemoteException {
        return router.route(request);
    }

    @Override
    public String ping() throws RemoteException {
        Response resp = router.route(new Request(Action.PING, "PING"));
        return (resp != null && resp.isSuccess() && resp.getData() != null) ? resp.getData().toString() : "PONG";
    }

    @Override
    public UserSessionDTO login(LoginRequestDTO request) throws RemoteException, AuthenticationException, LibraryException {
        Request req = new Request(Action.LOGIN, request);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof UserSessionDTO) {
            return (UserSessionDTO) resp.getData();
        }
        throw new AuthenticationException("Unexpected login result type.");
    }

    @Override
    public void logout(String token) throws RemoteException {
        Request req = new Request(Action.LOGOUT, null);
        req.setToken(token);
        router.route(req);
    }

    @Override
    public UserSessionDTO getCurrentUser(String token) throws RemoteException, AuthenticationException, LibraryException {
        Request req = new Request(Action.GET_CURRENT_USER, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (UserSessionDTO) resp.getData();
    }

    @Override
    public boolean registerStudent(RegisterStudentRequestDTO request) throws RemoteException, ValidationException, LibraryException {
        Request req = new Request(Action.REGISTER_STUDENT, request);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return resp.isSuccess();
    }

    @Override
    public boolean changePassword(String token, ChangePasswordRequestDTO request) throws RemoteException, ValidationException, LibraryException {
        Request req = new Request(Action.CHANGE_PASSWORD, request);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return resp.isSuccess();
    }

    // =========================================================================
    // Books
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public List<BookDTO> getAllBooks(String token) throws RemoteException, LibraryException {
        Request req = new Request(Action.LIST_BOOKS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<BookDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResponseDTO<BookDTO> searchBooks(String token, BookSearchCriteriaDTO criteria) throws RemoteException, LibraryException {
        Request req = new Request(Action.SEARCH_BOOKS, criteria);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof PageResponseDTO) {
            return (PageResponseDTO<BookDTO>) resp.getData();
        }
        return new PageResponseDTO<>(Collections.emptyList(), 1, 20, 0);
    }

    @Override
    public BookDTO getBook(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException {
        Request req = new Request(Action.GET_BOOK, id);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (BookDTO) resp.getData();
    }

    @Override
    public Long createBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        Request req = new Request(Action.CREATE_BOOK, book);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof Number) {
            return ((Number) resp.getData()).longValue();
        }
        return null;
    }

    @Override
    public boolean updateBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        Request req = new Request(Action.UPDATE_BOOK, book);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    @Override
    public boolean deleteBook(String token, Long id) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.DELETE_BOOK, id);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CategoryDTO> getAllCategories(String token) throws RemoteException, LibraryException {
        Request req = new Request(Action.LIST_CATEGORIES, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<CategoryDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AuthorDTO> getAllAuthors(String token) throws RemoteException, LibraryException {
        Request req = new Request(Action.LIST_AUTHORS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<AuthorDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // Students
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public List<StudentDTO> getAllStudents(String token) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.LIST_STUDENTS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<StudentDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    public StudentDTO getStudent(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException {
        Request req = new Request(Action.GET_STUDENT, id);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (StudentDTO) resp.getData();
    }

    @Override
    public StudentDTO getStudentByCode(String token, String studentCode) throws RemoteException, EntityNotFoundException, LibraryException {
        Request req = new Request(Action.GET_STUDENT, studentCode);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (StudentDTO) resp.getData();
    }

    @Override
    public Long createStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        Request req = new Request(Action.CREATE_STUDENT, student);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof Number) {
            return ((Number) resp.getData()).longValue();
        }
        return null;
    }

    @Override
    public boolean updateStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        Request req = new Request(Action.UPDATE_STUDENT, student);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    @Override
    public boolean deleteStudent(String token, DeleteStudentRequestDTO request) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.DELETE_STUDENT, request);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    @Override
    public String generateActivationCode(String token, Long studentId) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.GENERATE_STUDENT_ACTIVATION, studentId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return resp.getData() != null ? resp.getData().toString() : null;
    }

    // =========================================================================
    // Borrow & Return Circulation
    // =========================================================================

    @Override
    public BorrowRecordDTO borrowBook(String token, BorrowRequestDTO request) throws RemoteException, LibraryException {
        Request req = new Request(Action.BORROW_BOOK, request);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (BorrowRecordDTO) resp.getData();
    }

    @Override
    public ReturnResultDTO returnBook(String token, Long studentId, Long bookId) throws RemoteException, LibraryException {
        BorrowRequestDTO payload = new BorrowRequestDTO(studentId, bookId);
        Request req = new Request(Action.RETURN_BOOK, payload);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (ReturnResultDTO) resp.getData();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BorrowRecordDTO> getAllBorrowRecords(String token) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.LIST_BORROW_RECORDS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<BorrowRecordDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BorrowRecordDTO> getStudentActiveBorrows(String token, Long studentId) throws RemoteException, LibraryException {
        Request req = new Request(Action.GET_STUDENT_ACTIVE_BORROWS, studentId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<BorrowRecordDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // Fine Management
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public List<FineDTO> getFines(String token, Long studentId) throws RemoteException, LibraryException {
        Request req = new Request(Action.LIST_FINES, studentId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<FineDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean payFine(String token, Long fineId) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.PAY_FINE, fineId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    // =========================================================================
    // Reservations
    // =========================================================================

    @Override
    public ReservationDTO createReservation(String token, Long studentId, Long bookId) throws RemoteException, LibraryException {
        BorrowRequestDTO payload = new BorrowRequestDTO(studentId, bookId);
        Request req = new Request(Action.CREATE_RESERVATION, payload);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (ReservationDTO) resp.getData();
    }

    @Override
    public boolean cancelReservation(String token, Long reservationId) throws RemoteException, LibraryException {
        Request req = new Request(Action.CANCEL_RESERVATION, reservationId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReservationDTO> getReservations(String token, Long studentId) throws RemoteException, LibraryException {
        Request req = new Request(Action.LIST_RESERVATIONS, studentId);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<ReservationDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // Dashboard & Suggestions & Audit Log
    // =========================================================================

    @Override
    public DashboardMetricsDTO getDashboardMetrics(String token) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.GET_DASHBOARD_METRICS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (DashboardMetricsDTO) resp.getData();
    }

    @Override
    public StudentDashboardDTO getStudentDashboard(String token) throws RemoteException, LibraryException {
        Request req = new Request(Action.GET_STUDENT_DASHBOARD, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return (StudentDashboardDTO) resp.getData();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getSearchSuggestions(String token, SearchSuggestionRequestDTO request) throws RemoteException, LibraryException {
        Request req = new Request(Action.SEARCH_SUGGESTIONS, request);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<String>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AuditLogDTO> getRecentAuditLogs(String token, int limit) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.LIST_AUDIT_LOGS, limit > 0 ? limit : 50);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<AuditLogDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // User Management
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public List<UserDTO> getAllUsers(String token) throws RemoteException, AuthorizationException, LibraryException {
        Request req = new Request(Action.LIST_USERS, null);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof List) {
            return (List<UserDTO>) resp.getData();
        }
        return Collections.emptyList();
    }

    @Override
    public Long createUser(String token, UserDTO user, String initialPassword) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        // Build composite payload if needed or route via UserDTO
        Request req = new Request(Action.CREATE_USER, user);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        if (resp.getData() instanceof Number) {
            return ((Number) resp.getData()).longValue();
        }
        return null;
    }

    @Override
    public boolean updateUser(String token, UserDTO user) throws RemoteException, AuthorizationException, ValidationException, LibraryException {
        Request req = new Request(Action.UPDATE_USER, user);
        req.setToken(token);
        Response resp = router.route(req);
        unwrapOrThrow(resp);
        return Boolean.TRUE.equals(resp.getData()) || resp.isSuccess();
    }
}
