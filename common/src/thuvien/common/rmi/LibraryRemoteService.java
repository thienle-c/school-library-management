package thuvien.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
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
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

/**
 * Standard Java RMI Remote Interface for the Remote School Library Management System.
 * Defines remote procedures executed across Java RMI by the Swing desktop client.
 * All remote methods declare 'throws RemoteException' per Java RMI specification.
 */
public interface LibraryRemoteService extends Remote {

    /**
     * Diagnostic connectivity test over Java RMI.
     * @return "PONG" when server is active.
     */
    String ping() throws RemoteException;

    /**
     * Authenticates user credentials and returns active session DTO with token.
     */
    UserSessionDTO login(LoginRequestDTO request) throws RemoteException, AuthenticationException, LibraryException;

    /**
     * Terminates session and invalidates token server-side.
     */
    void logout(String token) throws RemoteException;

    /**
     * Retrieves current user session info by token.
     */
    UserSessionDTO getCurrentUser(String token) throws RemoteException, AuthenticationException, LibraryException;

    /**
     * Self-service student registration.
     */
    boolean registerStudent(RegisterStudentRequestDTO request) throws RemoteException, ValidationException, LibraryException;

    /**
     * Self-service user password change.
     */
    boolean changePassword(String token, ChangePasswordRequestDTO request) throws RemoteException, ValidationException, LibraryException;

    // =========================================================================
    // Book Management & Catalog Search
    // =========================================================================

    List<BookDTO> getAllBooks(String token) throws RemoteException, LibraryException;

    PageResponseDTO<BookDTO> searchBooks(String token, BookSearchCriteriaDTO criteria) throws RemoteException, LibraryException;

    BookDTO getBook(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException;

    Long createBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    boolean updateBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    boolean deleteBook(String token, Long id) throws RemoteException, AuthorizationException, LibraryException;

    List<CategoryDTO> getAllCategories(String token) throws RemoteException, LibraryException;

    List<AuthorDTO> getAllAuthors(String token) throws RemoteException, LibraryException;

    // =========================================================================
    // Student Management
    // =========================================================================

    List<StudentDTO> getAllStudents(String token) throws RemoteException, AuthorizationException, LibraryException;

    StudentDTO getStudent(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException;

    StudentDTO getStudentByCode(String token, String studentCode) throws RemoteException, EntityNotFoundException, LibraryException;

    Long createStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    boolean updateStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    boolean deleteStudent(String token, DeleteStudentRequestDTO request) throws RemoteException, AuthorizationException, LibraryException;

    String generateActivationCode(String token, Long studentId) throws RemoteException, AuthorizationException, LibraryException;

    // =========================================================================
    // Borrow & Return Circulation Transactions
    // =========================================================================

    /**
     * Executes atomic borrow transaction with database concurrency lock.
     * Prevents concurrent double-borrowing of the same book copy.
     */
    BorrowRecordDTO borrowBook(String token, BorrowRequestDTO request) throws RemoteException, LibraryException;

    /**
     * Executes atomic return transaction, restores inventory, and calculates overdue fines.
     */
    ReturnResultDTO returnBook(String token, Long studentId, Long bookId) throws RemoteException, LibraryException;

    List<BorrowRecordDTO> getAllBorrowRecords(String token) throws RemoteException, AuthorizationException, LibraryException;

    List<BorrowRecordDTO> getStudentActiveBorrows(String token, Long studentId) throws RemoteException, LibraryException;

    // =========================================================================
    // Fine Management
    // =========================================================================

    List<FineDTO> getFines(String token, Long studentId) throws RemoteException, LibraryException;

    boolean payFine(String token, Long fineId) throws RemoteException, AuthorizationException, LibraryException;

    // =========================================================================
    // Reservation Management
    // =========================================================================

    ReservationDTO createReservation(String token, Long studentId, Long bookId) throws RemoteException, LibraryException;

    boolean cancelReservation(String token, Long reservationId) throws RemoteException, LibraryException;

    List<ReservationDTO> getReservations(String token, Long studentId) throws RemoteException, LibraryException;

    // =========================================================================
    // Dashboard & Suggestions & Audit Log
    // =========================================================================

    DashboardMetricsDTO getDashboardMetrics(String token) throws RemoteException, AuthorizationException, LibraryException;

    StudentDashboardDTO getStudentDashboard(String token) throws RemoteException, LibraryException;

    List<String> getSearchSuggestions(String token, SearchSuggestionRequestDTO request) throws RemoteException, LibraryException;

    List<AuditLogDTO> getRecentAuditLogs(String token, int limit) throws RemoteException, AuthorizationException, LibraryException;

    // =========================================================================
    // User Management (Admin)
    // =========================================================================

    List<UserDTO> getAllUsers(String token) throws RemoteException, AuthorizationException, LibraryException;

    Long createUser(String token, UserDTO user, String initialPassword) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    boolean updateUser(String token, UserDTO user) throws RemoteException, AuthorizationException, ValidationException, LibraryException;

    // =========================================================================
    // Generic Envelope Execution (Transparent Migration & Testing)
    // =========================================================================

    /**
     * Executes Request envelope over RMI and returns Response envelope.
     */
    Response execute(Request request) throws RemoteException;
}
