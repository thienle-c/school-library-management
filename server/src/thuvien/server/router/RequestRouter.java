package thuvien.server.router;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.dto.DeleteStudentRequestDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.ChangePasswordRequestDTO;
import thuvien.common.dto.RegisterStudentRequestDTO;
import thuvien.common.dto.SearchSuggestionRequestDTO;
import thuvien.common.dto.StudentDashboardDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.BookUnavailableException;
import thuvien.common.exception.BorrowLimitExceededException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.ValidationException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.security.SessionManager;
import thuvien.server.service.AuditLogService;
import thuvien.server.service.AuthorService;
import thuvien.server.service.AuthService;
import thuvien.server.service.BookService;
import thuvien.server.service.BorrowService;
import thuvien.server.service.CategoryService;
import thuvien.server.service.DashboardService;
import thuvien.server.service.FineService;
import thuvien.server.service.ReservationService;
import thuvien.server.service.ReturnService;
import thuvien.server.service.SearchSuggestionService;
import thuvien.server.service.StudentService;
import thuvien.server.service.UserService;
import thuvien.server.service.impl.AuditLogServiceImpl;
import thuvien.server.service.impl.AuthorServiceImpl;
import thuvien.server.service.impl.AuthServiceImpl;
import thuvien.server.service.impl.BookServiceImpl;
import thuvien.server.service.impl.BorrowServiceImpl;
import thuvien.server.service.impl.CategoryServiceImpl;
import thuvien.server.service.impl.DashboardServiceImpl;
import thuvien.server.service.impl.FineServiceImpl;
import thuvien.server.service.impl.ReservationServiceImpl;
import thuvien.server.service.impl.ReturnServiceImpl;
import thuvien.server.service.impl.StudentServiceImpl;
import thuvien.server.service.impl.UserServiceImpl;

/**
 * Server RequestRouter: Validates sessions, enforces role authorization,
 * performs request payload validation, and routes actions to appropriate services.
 */
public class RequestRouter {
    private static final Logger LOGGER = Logger.getLogger(RequestRouter.class.getName());

    private final AuthService authService;
    private final BookService bookService;
    private final StudentService studentService;
    private final BorrowService borrowService;
    private final ReturnService returnService;
    private final FineService fineService;
    private final DashboardService dashboardService;
    private final ReservationService reservationService;
    private final AuditLogService auditLogService;
    private final CategoryService categoryService;
    private final AuthorService authorService;
    private final UserService userService;
    private final SearchSuggestionService searchSuggestionService;
    private final SessionManager sessionManager;

    public RequestRouter() {
        this(new AuthServiceImpl(),
             new BookServiceImpl(),
             new StudentServiceImpl(),
             new BorrowServiceImpl(),
             new ReturnServiceImpl(),
             new FineServiceImpl(),
             new DashboardServiceImpl(),
             new ReservationServiceImpl(),
             new AuditLogServiceImpl(),
             new CategoryServiceImpl(),
             new AuthorServiceImpl(),
             new UserServiceImpl());
    }

    public RequestRouter(AuthService authService,
                         BookService bookService,
                         StudentService studentService,
                         BorrowService borrowService,
                         ReturnService returnService,
                         FineService fineService,
                         DashboardService dashboardService) {
        this(authService, bookService, studentService, borrowService, returnService, fineService, dashboardService,
             new ReservationServiceImpl(), new AuditLogServiceImpl(), new CategoryServiceImpl(), new AuthorServiceImpl(), new UserServiceImpl());
    }

    public RequestRouter(AuthService authService,
                         BookService bookService,
                         StudentService studentService,
                         BorrowService borrowService,
                         ReturnService returnService,
                         FineService fineService,
                         DashboardService dashboardService,
                         ReservationService reservationService,
                         AuditLogService auditLogService,
                         CategoryService categoryService,
                         AuthorService authorService,
                         UserService userService) {
        this(authService, bookService, studentService, borrowService, returnService, fineService,
             dashboardService, reservationService, auditLogService, categoryService, authorService, userService,
             new thuvien.server.service.impl.SearchSuggestionServiceImpl());
    }

    public RequestRouter(AuthService authService,
                         BookService bookService,
                         StudentService studentService,
                         BorrowService borrowService,
                         ReturnService returnService,
                         FineService fineService,
                         DashboardService dashboardService,
                         ReservationService reservationService,
                         AuditLogService auditLogService,
                         CategoryService categoryService,
                         AuthorService authorService,
                         UserService userService,
                         SearchSuggestionService searchSuggestionService) {
        this.authService = authService;
        this.bookService = bookService;
        this.studentService = studentService;
        this.borrowService = borrowService;
        this.returnService = returnService;
        this.fineService = fineService;
        this.dashboardService = dashboardService;
        this.reservationService = reservationService;
        this.auditLogService = auditLogService;
        this.categoryService = categoryService;
        this.authorService = authorService;
        this.userService = userService;
        this.searchSuggestionService = searchSuggestionService != null ? searchSuggestionService : new thuvien.server.service.impl.SearchSuggestionServiceImpl();
        this.sessionManager = SessionManager.getInstance();
    }

    public Response route(Request request) {
        if (request == null || request.getAction() == null) {
            return Response.error("UNKNOWN", StatusCode.BAD_REQUEST, "Missing request or action.");
        }

        String reqId = request.getRequestId();
        Action action = request.getAction();

        try {
            // 1. Authentication Check
            UserSessionDTO session = null;
            if (action != Action.LOGIN && action != Action.REGISTER_STUDENT) {
                String token = request.getToken();
                if (token == null || token.trim().isEmpty()) {
                    return Response.error(reqId, StatusCode.UNAUTHORIZED, "Unauthorized: Session token is missing.");
                }
                session = sessionManager.getSession(token);
                if (session == null) {
                    return Response.error(reqId, StatusCode.UNAUTHORIZED, "Unauthorized: Invalid or expired session token.");
                }

                // Global Account Lockout Enforcement
                if (authService != null && (authService.isAccountLocked(session.getUsername()) || authService.isAccountLocked(session.getUserId()))) {
                    sessionManager.removeSession(token);
                    return Response.error(reqId, StatusCode.UNAUTHORIZED, "Tài khoản đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.");
                }
            }

            // 2. Action Routing
            switch (action) {
                // --- Authentication & User Self-Service ---
                case LOGIN:
                    return handleLogin(reqId, request);
                case LOGOUT:
                    return handleLogout(reqId, request);
                case GET_CURRENT_USER:
                    return Response.ok(reqId, session);
                case REGISTER_STUDENT:
                    return handleRegisterStudent(reqId, request);
                case CHANGE_PASSWORD:
                    return handleChangePassword(reqId, request, session);

                // --- Book Management & Catalog Search ---
                case LIST_BOOKS:
                    return Response.ok(reqId, bookService.getAllBooks());
                case GET_BOOK:
                    return handleGetBook(reqId, request);
                case SEARCH_BOOKS:
                    return handleSearchBooks(reqId, request);
                case CREATE_BOOK:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleCreateBook(reqId, request);
                case UPDATE_BOOK:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleUpdateBook(reqId, request);
                case DELETE_BOOK:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleDeleteBook(reqId, request);

                // --- Student Management ---
                case LIST_STUDENTS:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return Response.ok(reqId, studentService.getAllStudents());
                case GET_STUDENT:
                    return handleGetStudent(reqId, request, session);
                case CREATE_STUDENT:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleCreateStudent(reqId, request);
                case UPDATE_STUDENT:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleUpdateStudent(reqId, request);
                case DELETE_STUDENT:
                    requireRole(session, UserRole.ADMIN);
                    return handleDeleteStudent(reqId, request);
                case GENERATE_STUDENT_ACTIVATION:
                    requireRole(session, UserRole.ADMIN);
                    return handleGenerateStudentActivation(reqId, request, session);

                // --- Borrow & Return Transactions ---
                case BORROW_BOOK:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleBorrowBook(reqId, request, session);
                case RETURN_BOOK:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleReturnBook(reqId, request, session);
                case LIST_BORROW_RECORDS:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return Response.ok(reqId, borrowService.getAllBorrowRecords());
                case GET_STUDENT_ACTIVE_BORROWS:
                    return handleGetStudentActiveBorrows(reqId, request, session);

                // --- Fine Management ---
                case LIST_FINES:
                    return handleListFines(reqId, request, session);
                case CALCULATE_FINE:
                    return handleCalculateFine(reqId, request);
                case PAY_FINE:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handlePayFine(reqId, request, session);

                // --- Reservations ---
                case CREATE_RESERVATION:
                    return handleCreateReservation(reqId, request, session);
                case CANCEL_RESERVATION:
                    return handleCancelReservation(reqId, request, session);
                case LIST_RESERVATIONS:
                    return handleListReservations(reqId, request, session);

                // --- Category Management ---
                case LIST_CATEGORIES:
                    return Response.ok(reqId, categoryService.getAllCategories());
                case GET_CATEGORY:
                    return handleGetCategory(reqId, request);
                case CREATE_CATEGORY:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleCreateCategory(reqId, request);
                case UPDATE_CATEGORY:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleUpdateCategory(reqId, request);
                case DELETE_CATEGORY:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleDeleteCategory(reqId, request);

                // --- Author Management ---
                case LIST_AUTHORS:
                    return Response.ok(reqId, authorService.getAllAuthors());
                case GET_AUTHOR:
                    return handleGetAuthor(reqId, request);
                case CREATE_AUTHOR:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleCreateAuthor(reqId, request);
                case UPDATE_AUTHOR:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleUpdateAuthor(reqId, request);
                case DELETE_AUTHOR:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return handleDeleteAuthor(reqId, request);

                // --- User Management ---
                case LIST_USERS:
                    requireRole(session, UserRole.ADMIN);
                    return Response.ok(reqId, userService.getAllUsers());
                case GET_USER:
                    requireRole(session, UserRole.ADMIN);
                    return handleGetUser(reqId, request);
                case CREATE_USER:
                    requireRole(session, UserRole.ADMIN);
                    return handleCreateUser(reqId, request);
                case UPDATE_USER:
                    requireRole(session, UserRole.ADMIN);
                    return handleUpdateUser(reqId, request);
                case DELETE_USER:
                    requireRole(session, UserRole.ADMIN);
                    return handleDeleteUser(reqId, request);

                // --- Dashboard & Reports ---
                case GET_DASHBOARD_METRICS:
                    requireRole(session, UserRole.ADMIN, UserRole.LIBRARIAN);
                    return Response.ok(reqId, dashboardService.getMetrics());
                case GET_STUDENT_DASHBOARD:
                    requireRole(session, UserRole.STUDENT);
                    return handleGetStudentDashboard(reqId, session);

                // --- Audit Logs ---
                case LIST_AUDIT_LOGS:
                    requireRole(session, UserRole.ADMIN);
                    return handleListAuditLogs(reqId, request);

                // --- Search Autocomplete Suggestions ---
                case SEARCH_SUGGESTIONS:
                    return handleSearchSuggestions(reqId, request, session);

                default:
                    return Response.error(reqId, StatusCode.BAD_REQUEST, "Unsupported action: " + action);
            }

        } catch (AuthenticationException e) {
            return Response.error(reqId, StatusCode.UNAUTHORIZED, e.getMessage());
        } catch (AuthorizationException e) {
            return Response.error(reqId, StatusCode.FORBIDDEN, e.getMessage());
        } catch (EntityNotFoundException e) {
            return Response.error(reqId, StatusCode.NOT_FOUND, e.getMessage());
        } catch (BookUnavailableException e) {
            return Response.error(reqId, StatusCode.CONFLICT, e.getMessage());
        } catch (BorrowLimitExceededException e) {
            return Response.error(reqId, StatusCode.BAD_REQUEST, e.getMessage());
        } catch (ValidationException | IllegalArgumentException e) {
            return Response.error(reqId, StatusCode.BAD_REQUEST, e.getMessage());
        } catch (LibraryException e) {
            return Response.error(reqId, StatusCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Internal server error while routing action: " + action, e);
            return Response.error(reqId, StatusCode.SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    private Response handleLogin(String reqId, Request request) throws AuthenticationException, ValidationException {
        if (!(request.getPayload() instanceof LoginRequestDTO)) {
            throw new ValidationException("Invalid login payload: must be LoginRequestDTO.");
        }
        UserSessionDTO loggedIn = authService.login((LoginRequestDTO) request.getPayload());
        return Response.ok(reqId, "Login successful", loggedIn);
    }

    private Response handleLogout(String reqId, Request request) {
        authService.logout(request.getToken());
        return Response.ok(reqId, "Logout successful", null);
    }

    private Response handleGetBook(String reqId, Request request) throws EntityNotFoundException, LibraryException, ValidationException {
        Object payload = request.getPayload();
        if (payload == null) {
            throw new ValidationException("Book ID or ISBN is required.");
        }
        if (payload instanceof Number) {
            long id = ((Number) payload).longValue();
            if (id <= 0) {
                throw new ValidationException("Book ID must be a positive number.");
            }
            return Response.ok(reqId, bookService.getBookById(id));
        } else if (payload instanceof String) {
            String str = ((String) payload).trim();
            if (str.isEmpty()) {
                throw new ValidationException("Book ID or ISBN cannot be empty.");
            }
            try {
                long id = Long.parseLong(str);
                if (id <= 0) {
                    throw new ValidationException("Book ID must be a positive number.");
                }
                return Response.ok(reqId, bookService.getBookById(id));
            } catch (NumberFormatException e) {
                return Response.ok(reqId, bookService.getBookByIsbn(str));
            }
        }
        throw new ValidationException("Invalid payload for GET_BOOK: expected ID (Number) or ISBN (String).");
    }

    private Response handleSearchBooks(String reqId, Request request) throws LibraryException, ValidationException {
        Object payload = request.getPayload();
        BookSearchCriteriaDTO criteria;
        if (payload == null) {
            criteria = new BookSearchCriteriaDTO();
        } else if (payload instanceof BookSearchCriteriaDTO) {
            criteria = (BookSearchCriteriaDTO) payload;
        } else {
            throw new ValidationException("Invalid payload for SEARCH_BOOKS: expected BookSearchCriteriaDTO.");
        }
        return Response.ok(reqId, bookService.searchBooks(criteria));
    }

    private Response handleCreateBook(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof BookDTO)) {
            throw new ValidationException("Invalid payload for CREATE_BOOK: must be BookDTO.");
        }
        Long id = bookService.createBook((BookDTO) request.getPayload());
        return new Response(reqId, StatusCode.CREATED, "Book created successfully", id);
    }

    private Response handleUpdateBook(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof BookDTO)) {
            throw new ValidationException("Invalid payload for UPDATE_BOOK: must be BookDTO.");
        }
        boolean ok = bookService.updateBook((BookDTO) request.getPayload());
        return Response.ok(reqId, "Book updated successfully", ok);
    }

    private Response handleDeleteBook(String reqId, Request request) throws LibraryException, ValidationException {
        Long id = extractLong(request.getPayload(), "Book ID");
        boolean ok = bookService.deleteBook(id);
        return Response.ok(reqId, "Book deleted successfully", ok);
    }

    private Response handleGetStudent(String reqId, Request request, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException, AuthorizationException, ValidationException {
        Object payload = request.getPayload();
        if (payload == null) {
            if (session.getRole() == UserRole.STUDENT) {
                StudentDTO student = studentService.getStudentByUserId(session.getUserId());
                return Response.ok(reqId, student);
            }
            throw new ValidationException("Student ID or code is required.");
        }

        StudentDTO student;
        if (payload instanceof Number) {
            long id = ((Number) payload).longValue();
            if (id <= 0) {
                throw new ValidationException("Student ID must be a positive number.");
            }
            student = studentService.getStudentById(id);
        } else if (payload instanceof String) {
            String str = ((String) payload).trim();
            if (str.isEmpty()) {
                throw new ValidationException("Student ID or code cannot be empty.");
            }
            try {
                long id = Long.parseLong(str);
                if (id <= 0) {
                    throw new ValidationException("Student ID must be a positive number.");
                }
                student = studentService.getStudentById(id);
            } catch (NumberFormatException e) {
                student = studentService.getStudentByCode(str);
            }
        } else {
            throw new ValidationException("Invalid payload for GET_STUDENT.");
        }

        // Student role can only inspect their own record
        if (session.getRole() == UserRole.STUDENT) {
            if (student.getUserId() == null || !student.getUserId().equals(session.getUserId())) {
                throw new AuthorizationException("Access denied: students may only view their own student profile.");
            }
        }
        return Response.ok(reqId, student);
    }

    private Response handleCreateStudent(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof StudentDTO)) {
            throw new ValidationException("Invalid payload for CREATE_STUDENT: must be StudentDTO.");
        }
        Long id = studentService.createStudent((StudentDTO) request.getPayload());
        return new Response(reqId, StatusCode.CREATED, "Student created successfully", id);
    }

    private Response handleUpdateStudent(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof StudentDTO)) {
            throw new ValidationException("Invalid payload for UPDATE_STUDENT: must be StudentDTO.");
        }
        boolean ok = studentService.updateStudent((StudentDTO) request.getPayload());
        return Response.ok(reqId, "Student updated successfully", ok);
    }

    private Response handleDeleteStudent(String reqId, Request request) throws LibraryException, ValidationException {
        boolean permanent = false;
        Long id;
        Object payload = request.getPayload();
        if (payload instanceof DeleteStudentRequestDTO) {
            DeleteStudentRequestDTO dto = (DeleteStudentRequestDTO) payload;
            id = dto.getStudentId();
            permanent = dto.isPermanent();
        } else {
            id = extractLong(payload, "Student ID");
        }

        if (id == null || id <= 0) {
            throw new ValidationException("Mã ID sinh viên không hợp lệ.");
        }

        if (permanent) {
            boolean ok = studentService.hardDeleteStudent(id);
            return Response.ok(reqId, "Đã xóa vĩnh viễn sinh viên thành công.", ok);
        } else {
            boolean ok = studentService.deleteStudent(id);
            return Response.ok(reqId, "Đã tạm khóa sinh viên thành công.", ok);
        }
    }

    private Response handleBorrowBook(String reqId, Request request, UserSessionDTO session)
            throws BookUnavailableException, BorrowLimitExceededException, EntityNotFoundException, LibraryException, ValidationException {
        if (!(request.getPayload() instanceof BorrowRequestDTO)) {
            throw new ValidationException("Invalid payload for BORROW_BOOK: must be BorrowRequestDTO.");
        }
        BorrowRequestDTO borrowReq = (BorrowRequestDTO) request.getPayload();
        if (borrowReq.getBookId() == null || borrowReq.getBookId() <= 0) {
            throw new ValidationException("Book ID must be a positive number.");
        }
        if (borrowReq.getStudentId() == null || borrowReq.getStudentId() <= 0) {
            throw new ValidationException("Valid Student ID is required.");
        }
        BorrowRecordDTO record = borrowService.borrowBook(borrowReq, session.getUserId());
        return Response.ok(reqId, "Book borrowed successfully", record);
    }

    private Response handleReturnBook(String reqId, Request request, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException, ValidationException {
        Long studentId;
        Long bookId;

        Object payload = request.getPayload();
        if (payload instanceof BorrowRequestDTO) {
            BorrowRequestDTO r = (BorrowRequestDTO) payload;
            studentId = r.getStudentId();
            bookId = r.getBookId();
        } else if (payload instanceof BorrowRecordDTO) {
            BorrowRecordDTO r = (BorrowRecordDTO) payload;
            studentId = r.getStudentId();
            bookId = r.getBookId();
        } else if (payload instanceof Long[]) {
            Long[] arr = (Long[]) payload;
            if (arr.length < 2) throw new ValidationException("Array must contain studentId and bookId.");
            studentId = arr[0];
            bookId = arr[1];
        } else {
            throw new ValidationException("Invalid payload for RETURN_BOOK: expected BorrowRequestDTO or BorrowRecordDTO.");
        }

        if (studentId == null || studentId <= 0 || bookId == null || bookId <= 0) {
            throw new ValidationException("Valid positive Student ID and Book ID are required for return.");
        }

        ReturnResultDTO result = returnService.returnBook(studentId, bookId, session.getUserId());
        return Response.ok(reqId, result.getMessage(), result);
    }

    private Response handleGetStudentActiveBorrows(String reqId, Request request, UserSessionDTO session)
            throws LibraryException, AuthorizationException, ValidationException {
        Long studentId;
        if (session.getRole() == UserRole.STUDENT) {
            // Student role always views own borrows
            StudentDTO student = studentService.getStudentByUserId(session.getUserId());
            studentId = student.getId();
        } else {
            studentId = extractLong(request.getPayload(), "Student ID");
        }
        return Response.ok(reqId, borrowService.getStudentActiveBorrows(studentId));
    }

    private Response handleListFines(String reqId, Request request, UserSessionDTO session)
            throws LibraryException, AuthorizationException, ValidationException {
        if (session.getRole() == UserRole.STUDENT) {
            StudentDTO student = studentService.getStudentByUserId(session.getUserId());
            return Response.ok(reqId, fineService.getFinesByStudentId(student.getId()));
        }
        // Staff role
        if (request.getPayload() != null) {
            if (request.getPayload() instanceof Number || (request.getPayload() instanceof String && ((String) request.getPayload()).trim().matches("\\d+"))) {
                Long studentId = extractLong(request.getPayload(), "Student ID");
                return Response.ok(reqId, fineService.getFinesByStudentId(studentId));
            }
        }
        return Response.ok(reqId, fineService.getAllFines());
    }

    private Response handleCalculateFine(String reqId, Request request) throws ValidationException {
        Object payload = request.getPayload();
        if (payload == null) {
            throw new ValidationException("Overdue days cannot be null.");
        }
        int days;
        if (payload instanceof Number) {
            days = ((Number) payload).intValue();
        } else if (payload instanceof String) {
            try {
                days = Integer.parseInt(((String) payload).trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid overdue days: " + payload);
            }
        } else {
            throw new ValidationException("Expected integer for overdue days.");
        }
        if (days < 0) {
            throw new ValidationException("Overdue days cannot be negative.");
        }
        BigDecimal fine = fineService.calculateOverdueFine(days);
        return Response.ok(reqId, fine);
    }

    private Response handlePayFine(String reqId, Request request, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException, ValidationException {
        Long fineId = extractLong(request.getPayload(), "Fine ID");
        boolean ok = fineService.payFine(fineId, session.getUserId());
        return Response.ok(reqId, "Fine paid successfully", ok);
    }

    private Response handleCreateReservation(String reqId, Request request, UserSessionDTO session)
            throws LibraryException, ValidationException {
        Long studentId;
        Long bookId;

        Object payload = request.getPayload();
        if (payload instanceof ReservationDTO) {
            ReservationDTO r = (ReservationDTO) payload;
            studentId = r.getStudentId();
            bookId = r.getBookId();
        } else if (payload instanceof BorrowRequestDTO) {
            BorrowRequestDTO r = (BorrowRequestDTO) payload;
            studentId = r.getStudentId();
            bookId = r.getBookId();
        } else {
            throw new ValidationException("Invalid payload for CREATE_RESERVATION.");
        }

        if (session.getRole() == UserRole.STUDENT) {
            StudentDTO s = studentService.getStudentByUserId(session.getUserId());
            studentId = s.getId();
        } else {
            if (studentId == null || studentId <= 0) {
                throw new ValidationException("Valid Student ID is required for reservation.");
            }
        }

        if (bookId == null || bookId <= 0) {
            throw new ValidationException("Valid Book ID is required for reservation.");
        }

        ReservationDTO reservation = reservationService.createReservation(studentId, bookId);
        return new Response(reqId, StatusCode.CREATED, "Reservation created successfully", reservation);
    }

    private Response handleCancelReservation(String reqId, Request request, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException, ValidationException, AuthorizationException {
        Long resId = extractLong(request.getPayload(), "Reservation ID");
        if (session.getRole() == UserRole.STUDENT) {
            StudentDTO currentStudent = studentService.getStudentByUserId(session.getUserId());
            ReservationDTO reservation = reservationService.getReservationById(resId);
            if (!currentStudent.getId().equals(reservation.getStudentId())) {
                throw new AuthorizationException("Access denied: students may only cancel their own reservations.");
            }
        }
        boolean ok = reservationService.cancelReservation(resId);
        return Response.ok(reqId, "Reservation cancelled successfully", ok);
    }

    private Response handleListReservations(String reqId, Request request, UserSessionDTO session)
            throws LibraryException, ValidationException {
        if (session.getRole() == UserRole.STUDENT) {
            StudentDTO s = studentService.getStudentByUserId(session.getUserId());
            return Response.ok(reqId, reservationService.getReservationsByStudent(s.getId()));
        }
        if (request.getPayload() != null) {
            Long studentId = extractLong(request.getPayload(), "Student ID");
            return Response.ok(reqId, reservationService.getReservationsByStudent(studentId));
        }
        return Response.ok(reqId, reservationService.getAllReservations());
    }

    private Response handleGetCategory(String reqId, Request request) throws EntityNotFoundException, LibraryException, ValidationException {
        Integer id = extractInteger(request.getPayload(), "Category ID");
        return Response.ok(reqId, categoryService.getCategoryById(id));
    }

    private Response handleCreateCategory(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof CategoryDTO)) {
            throw new ValidationException("Invalid payload for CREATE_CATEGORY: must be CategoryDTO.");
        }
        Integer id = categoryService.createCategory((CategoryDTO) request.getPayload());
        return new Response(reqId, StatusCode.CREATED, "Category created successfully", id);
    }

    private Response handleUpdateCategory(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof CategoryDTO)) {
            throw new ValidationException("Invalid payload for UPDATE_CATEGORY: must be CategoryDTO.");
        }
        boolean ok = categoryService.updateCategory((CategoryDTO) request.getPayload());
        return Response.ok(reqId, "Category updated successfully", ok);
    }

    private Response handleDeleteCategory(String reqId, Request request) throws LibraryException, ValidationException {
        Integer id = extractInteger(request.getPayload(), "Category ID");
        boolean ok = categoryService.deleteCategory(id);
        return Response.ok(reqId, "Category deleted successfully", ok);
    }

    private Response handleGetAuthor(String reqId, Request request) throws EntityNotFoundException, LibraryException, ValidationException {
        Integer id = extractInteger(request.getPayload(), "Author ID");
        return Response.ok(reqId, authorService.getAuthorById(id));
    }

    private Response handleCreateAuthor(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof AuthorDTO)) {
            throw new ValidationException("Invalid payload for CREATE_AUTHOR: must be AuthorDTO.");
        }
        Integer id = authorService.createAuthor((AuthorDTO) request.getPayload());
        return new Response(reqId, StatusCode.CREATED, "Author created successfully", id);
    }

    private Response handleUpdateAuthor(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof AuthorDTO)) {
            throw new ValidationException("Invalid payload for UPDATE_AUTHOR: must be AuthorDTO.");
        }
        boolean ok = authorService.updateAuthor((AuthorDTO) request.getPayload());
        return Response.ok(reqId, "Author updated successfully", ok);
    }

    private Response handleDeleteAuthor(String reqId, Request request) throws LibraryException, ValidationException {
        Integer id = extractInteger(request.getPayload(), "Author ID");
        boolean ok = authorService.deleteAuthor(id);
        return Response.ok(reqId, "Author deleted successfully", ok);
    }

    private Response handleGetUser(String reqId, Request request) throws EntityNotFoundException, LibraryException, ValidationException {
        Object payload = request.getPayload();
        if (payload == null) {
            throw new ValidationException("User ID or username is required.");
        }
        if (payload instanceof Number) {
            long id = ((Number) payload).longValue();
            if (id <= 0) {
                throw new ValidationException("User ID must be a positive number.");
            }
            return Response.ok(reqId, userService.getUserById(id));
        } else if (payload instanceof String) {
            String str = ((String) payload).trim();
            if (str.isEmpty()) {
                throw new ValidationException("User ID or username cannot be empty.");
            }
            try {
                long id = Long.parseLong(str);
                if (id <= 0) {
                    throw new ValidationException("User ID must be a positive number.");
                }
                return Response.ok(reqId, userService.getUserById(id));
            } catch (NumberFormatException e) {
                return Response.ok(reqId, userService.getUserByUsername(str));
            }
        }
        throw new ValidationException("Invalid payload for GET_USER: expected User ID or username.");
    }

    private Response handleCreateUser(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof UserDTO)) {
            throw new ValidationException("Invalid payload for CREATE_USER: must be UserDTO.");
        }
        Long id = userService.createUser((UserDTO) request.getPayload(), "password123");
        return new Response(reqId, StatusCode.CREATED, "User created successfully", id);
    }

    private Response handleUpdateUser(String reqId, Request request) throws LibraryException, ValidationException {
        if (!(request.getPayload() instanceof UserDTO)) {
            throw new ValidationException("Invalid payload for UPDATE_USER: must be UserDTO.");
        }
        boolean ok = userService.updateUser((UserDTO) request.getPayload());
        return Response.ok(reqId, "User updated successfully", ok);
    }

    private Response handleDeleteUser(String reqId, Request request) throws LibraryException, ValidationException {
        Long id = extractLong(request.getPayload(), "User ID");
        boolean ok = userService.deleteUser(id);
        return Response.ok(reqId, "User deleted successfully", ok);
    }

    private Response handleListAuditLogs(String reqId, Request request) throws LibraryException {
        int limit = 50;
        if (request.getPayload() instanceof Number) {
            limit = ((Number) request.getPayload()).intValue();
        }
        List<AuditLogDTO> logs = auditLogService.getRecentLogs(limit);
        return Response.ok(reqId, logs);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void requireRole(UserSessionDTO session, UserRole... allowedRoles) throws AuthorizationException {
        if (session == null) {
            throw new AuthorizationException("Authentication required.");
        }
        for (UserRole role : allowedRoles) {
            if (session.getRole() == role) {
                return;
            }
        }
        throw new AuthorizationException("Access denied: insufficient permissions for role " + session.getRole());
    }

    private Long extractLong(Object payload, String fieldName) throws ValidationException {
        if (payload == null) {
            throw new ValidationException(fieldName + " cannot be null.");
        }
        long val;
        if (payload instanceof Number) {
            val = ((Number) payload).longValue();
        } else if (payload instanceof String) {
            String str = ((String) payload).trim();
            if (str.isEmpty()) {
                throw new ValidationException(fieldName + " cannot be empty.");
            }
            try {
                val = Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid numeric format for " + fieldName + ": " + payload);
            }
        } else {
            throw new ValidationException("Expected numeric value for " + fieldName + " but received: " + payload.getClass().getSimpleName());
        }
        if (val <= 0) {
            throw new ValidationException(fieldName + " must be a positive number.");
        }
        return val;
    }

    private Integer extractInteger(Object payload, String fieldName) throws ValidationException {
        if (payload == null) {
            throw new ValidationException(fieldName + " cannot be null.");
        }
        int val;
        if (payload instanceof Number) {
            val = ((Number) payload).intValue();
        } else if (payload instanceof String) {
            String str = ((String) payload).trim();
            if (str.isEmpty()) {
                throw new ValidationException(fieldName + " cannot be empty.");
            }
            try {
                val = Integer.parseInt(str);
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid numeric format for " + fieldName + ": " + payload);
            }
        } else {
            throw new ValidationException("Expected integer value for " + fieldName + " but received: " + payload.getClass().getSimpleName());
        }
        if (val <= 0) {
            throw new ValidationException(fieldName + " must be a positive number.");
        }
        return val;
    }

    private Response handleRegisterStudent(String reqId, Request request)
            throws ValidationException, LibraryException {
        if (!(request.getPayload() instanceof RegisterStudentRequestDTO)) {
            throw new ValidationException("Dữ liệu đăng ký không hợp lệ.");
        }
        RegisterStudentRequestDTO reg = (RegisterStudentRequestDTO) request.getPayload();
        boolean ok = studentService.registerStudentAccount(reg);
        return Response.ok(reqId, "Đăng ký tài khoản sinh viên thành công! Bạn có thể đăng nhập ngay.", ok);
    }

    private Response handleChangePassword(String reqId, Request request, UserSessionDTO session)
            throws ValidationException, EntityNotFoundException, LibraryException {
        if (!(request.getPayload() instanceof ChangePasswordRequestDTO)) {
            throw new ValidationException("Dữ liệu đổi mật khẩu không hợp lệ.");
        }
        ChangePasswordRequestDTO dto = (ChangePasswordRequestDTO) request.getPayload();
        if (dto.getNewPassword() == null || !dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ValidationException("Mật khẩu xác nhận không trùng khớp.");
        }
        boolean ok = userService.changePassword(session.getUserId(), dto.getCurrentPassword(), dto.getNewPassword());
        if (auditLogService != null) {
            auditLogService.log(session.getUserId(), "PASSWORD_CHANGED", "User", session.getUserId(), "Đổi mật khẩu thành công");
        }
        return Response.ok(reqId, "Đổi mật khẩu thành công.", ok);
    }

    private Response handleGenerateStudentActivation(String reqId, Request request, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException, ValidationException {
        Long studentId = extractLong(request.getPayload(), "Mã ID sinh viên");
        String code = studentService.generateActivationCode(studentId);
        if (auditLogService != null) {
            auditLogService.log(session.getUserId(), "ACTIVATION_CODE_GENERATED", "Student", studentId, "Cấp mã kích hoạt mới");
        }
        return Response.ok(reqId, "Cấp mã kích hoạt thành công", code);
    }

    private Response handleGetStudentDashboard(String reqId, UserSessionDTO session)
            throws EntityNotFoundException, LibraryException {
        StudentDashboardDTO dto = dashboardService.getStudentDashboard(session.getUserId());
        return Response.ok(reqId, dto);
    }

    private Response handleSearchSuggestions(String reqId, Request request, UserSessionDTO session)
            throws AuthorizationException, LibraryException, ValidationException {
        Object payload = request.getPayload();
        String type = "BOOK";
        String kw = "";
        if (payload instanceof SearchSuggestionRequestDTO) {
            SearchSuggestionRequestDTO dto = (SearchSuggestionRequestDTO) payload;
            type = dto.getType();
            kw = dto.getKeyword();
        } else if (payload instanceof String) {
            kw = (String) payload;
        }
        List<String> list = searchSuggestionService.getSuggestions(type, kw, session);
        return Response.ok(reqId, list);
    }
}
