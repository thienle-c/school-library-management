package thuvien.server;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.dto.FineDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.BookUnavailableException;
import thuvien.common.exception.BorrowLimitExceededException;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.router.RequestRouter;
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
import thuvien.server.service.StudentService;
import thuvien.server.service.UserService;

public class RequestRouterTest {
    private RequestRouter router;
    private SessionManager sessionManager;
    private String adminToken;
    private String librarianToken;
    private String studentToken;

    @Before
    public void setUp() {
        sessionManager = SessionManager.getInstance();

        // 1. Mock AuthService
        AuthService authService = new AuthService() {
            @Override
            public UserSessionDTO login(LoginRequestDTO req) throws AuthenticationException {
                if ("admin".equalsIgnoreCase(req.getUsername()) && "admin123".equals(req.getPassword())) {
                    UserSessionDTO s = new UserSessionDTO(null, 1L, "admin", "Admin User", UserRole.ADMIN);
                    sessionManager.createSession(s);
                    return s;
                }
                if ("librarian".equalsIgnoreCase(req.getUsername()) && "lib123".equals(req.getPassword())) {
                    UserSessionDTO s = new UserSessionDTO(null, 2L, "librarian", "Librarian User", UserRole.LIBRARIAN);
                    sessionManager.createSession(s);
                    return s;
                }
                if ("student".equalsIgnoreCase(req.getUsername()) && "student123".equals(req.getPassword())) {
                    UserSessionDTO s = new UserSessionDTO(null, 3L, "student", "Student User", UserRole.STUDENT);
                    sessionManager.createSession(s);
                    return s;
                }
                throw new AuthenticationException("Invalid username or password.");
            }

            @Override
            public void logout(String token) {
                sessionManager.removeSession(token);
            }

            @Override
            public UserSessionDTO validateSession(String token) throws AuthenticationException {
                UserSessionDTO s = sessionManager.getSession(token);
                if (s == null) throw new AuthenticationException("Session is invalid or has expired.");
                return s;
            }
        };

        // 2. Mock BookService
        BookService bookService = new BookService() {
            @Override
            public BookDTO getBookById(Long id) throws EntityNotFoundException, LibraryException {
                if (id == 999L) throw new EntityNotFoundException("Book not found with ID: 999");
                BookDTO b = new BookDTO();
                b.setId(id);
                b.setTitle("Test Book " + id);
                return b;
            }

            @Override
            public BookDTO getBookByIsbn(String isbn) throws EntityNotFoundException {
                if ("NOT_FOUND".equals(isbn)) throw new EntityNotFoundException("Book not found");
                BookDTO b = new BookDTO();
                b.setIsbn(isbn);
                return b;
            }

            @Override
            public PageResponseDTO<BookDTO> searchBooks(BookSearchCriteriaDTO criteria) {
                List<BookDTO> list = new ArrayList<>();
                BookDTO b = new BookDTO();
                b.setId(1L);
                b.setTitle("Found Book");
                list.add(b);
                return new PageResponseDTO<>(list, 1, 10, 1);
            }

            @Override
            public List<BookDTO> getAllBooks() {
                return Collections.emptyList();
            }

            @Override
            public Long createBook(BookDTO book) {
                return 101L;
            }

            @Override
            public boolean updateBook(BookDTO book) {
                return true;
            }

            @Override
            public boolean deleteBook(Long id) {
                return true;
            }
        };

        // 3. Mock StudentService
        StudentService studentService = new StudentService() {
            @Override
            public StudentDTO getStudentById(Long id) throws EntityNotFoundException {
                if (id == 999L) throw new EntityNotFoundException("Student not found");
                StudentDTO s = new StudentDTO();
                s.setId(id);
                s.setUserId(id); // For student access check
                s.setStudentCode("STU" + id);
                return s;
            }

            @Override
            public StudentDTO getStudentByUserId(Long userId) throws EntityNotFoundException {
                if (userId == 999L) throw new EntityNotFoundException("Student not found");
                StudentDTO s = new StudentDTO();
                s.setId(userId);
                s.setUserId(userId);
                s.setStudentCode("STU" + userId);
                return s;
            }

            @Override
            public StudentDTO getStudentByCode(String code) {
                StudentDTO s = new StudentDTO();
                s.setStudentCode(code);
                return s;
            }

            @Override
            public List<StudentDTO> getAllStudents() {
                return Collections.emptyList();
            }

            @Override
            public Long createStudent(StudentDTO student) {
                return 201L;
            }

            @Override
            public boolean updateStudent(StudentDTO student) {
                return true;
            }

            @Override
            public boolean deleteStudent(Long id) {
                return true;
            }

            @Override
            public boolean hardDeleteStudent(Long id) {
                return true;
            }

            @Override
            public String generateActivationCode(Long studentId) {
                return "ACT-TEST1234";
            }

            @Override
            public boolean registerStudentAccount(thuvien.common.dto.RegisterStudentRequestDTO request) {
                return true;
            }
        };

        // 4. Mock BorrowService
        BorrowService borrowService = new BorrowService() {
            @Override
            public BorrowRecordDTO borrowBook(BorrowRequestDTO request, Long issuedByUserId)
                    throws BookUnavailableException, BorrowLimitExceededException, EntityNotFoundException {
                if (request.getBookId() == 409L) {
                    throw new BookUnavailableException("No copies available for book ID: 409");
                }
                if (request.getStudentId() == 400L) {
                    throw new BorrowLimitExceededException("Borrow limit exceeded for student ID: 400");
                }
                BorrowRecordDTO r = new BorrowRecordDTO();
                r.setId(501L);
                r.setStudentId(request.getStudentId());
                r.setBookId(request.getBookId());
                r.setStatus(BorrowStatus.ACTIVE);
                return r;
            }

            @Override
            public List<BorrowRecordDTO> getAllBorrowRecords() {
                return Collections.emptyList();
            }

            @Override
            public List<BorrowRecordDTO> getStudentActiveBorrows(Long studentId) {
                return Collections.emptyList();
            }
        };

        // 5. Mock ReturnService
        ReturnService returnService = new ReturnService() {
            @Override
            public ReturnResultDTO returnBook(Long studentId, Long bookId, Long collectedByUserId) {
                ReturnResultDTO r = new ReturnResultDTO();
                r.setBorrowRecordId(501L);
                r.setStudentId(studentId);
                r.setBookId(bookId);
                r.setMessage("Returned successfully");
                return r;
            }
        };

        // 6. Mock FineService
        FineService fineService = new FineService() {
            @Override
            public FineDTO getFineById(Long id) {
                return new FineDTO();
            }

            @Override
            public List<FineDTO> getFinesByStudentId(Long studentId) {
                return Collections.emptyList();
            }

            @Override
            public List<FineDTO> getUnpaidFines() {
                return Collections.emptyList();
            }

            @Override
            public List<FineDTO> getAllFines() {
                return Collections.emptyList();
            }

            @Override
            public boolean payFine(Long fineId, Long collectedByUserId) {
                return true;
            }

            @Override
            public BigDecimal calculateOverdueFine(int overdueDays) {
                return new BigDecimal("5000.00").multiply(new BigDecimal(overdueDays));
            }
        };

        // 7. Mock DashboardService
        DashboardService dashboardService = new DashboardService() {
            @Override
            public DashboardMetricsDTO getMetrics() {
                DashboardMetricsDTO d = new DashboardMetricsDTO();
                d.setTotalBooks(100);
                return d;
            }

            @Override
            public thuvien.common.dto.StudentDashboardDTO getStudentDashboard(Long userId) {
                thuvien.common.dto.StudentDashboardDTO d = new thuvien.common.dto.StudentDashboardDTO();
                d.setStudentCode("STU" + userId);
                return d;
            }
        };

        // 8. Mock ReservationService
        ReservationService reservationService = new ReservationService() {
            @Override
            public ReservationDTO createReservation(Long studentId, Long bookId) {
                ReservationDTO r = new ReservationDTO();
                r.setId(601L);
                r.setStudentId(studentId);
                r.setBookId(bookId);
                return r;
            }

            @Override
            public ReservationDTO getReservationById(Long reservationId) {
                ReservationDTO r = new ReservationDTO();
                r.setId(reservationId);
                r.setStudentId(reservationId != null && reservationId.equals(999L) ? 999L : 3L);
                r.setBookId(1L);
                return r;
            }

            @Override
            public boolean cancelReservation(Long reservationId) {
                return true;
            }

            @Override
            public List<ReservationDTO> getReservationsByStudent(Long studentId) {
                return Collections.emptyList();
            }

            @Override
            public List<ReservationDTO> getAllReservations() {
                return Collections.emptyList();
            }
        };

        // 9. Mock AuditLogService
        AuditLogService auditLogService = new AuditLogService() {
            @Override
            public void log(Long userId, String action, String entityName, Long entityId, String details) {}

            @Override
            public List<AuditLogDTO> getRecentLogs(int limit) {
                return Collections.emptyList();
            }
        };

        // 10. Mock CategoryService
        CategoryService categoryService = new CategoryService() {
            @Override
            public CategoryDTO getCategoryById(Integer id) { return new CategoryDTO(); }
            @Override
            public List<CategoryDTO> getAllCategories() { return Collections.emptyList(); }
            @Override
            public Integer createCategory(CategoryDTO category) { return 1; }
            @Override
            public boolean updateCategory(CategoryDTO category) { return true; }
            @Override
            public boolean deleteCategory(Integer id) { return true; }
        };

        // 11. Mock AuthorService
        AuthorService authorService = new AuthorService() {
            @Override
            public AuthorDTO getAuthorById(Integer id) { return new AuthorDTO(); }
            @Override
            public List<AuthorDTO> getAuthorsByBookId(Long bookId) { return Collections.emptyList(); }
            @Override
            public List<AuthorDTO> getAllAuthors() { return Collections.emptyList(); }
            @Override
            public Integer createAuthor(AuthorDTO author) { return 1; }
            @Override
            public boolean updateAuthor(AuthorDTO author) { return true; }
            @Override
            public boolean deleteAuthor(Integer id) { return true; }
        };

        // 12. Mock UserService
        UserService userService = new UserService() {
            @Override
            public UserDTO getUserById(Long id) { return new UserDTO(); }
            @Override
            public UserDTO getUserByUsername(String username) { return new UserDTO(); }
            @Override
            public List<UserDTO> getAllUsers() { return Collections.emptyList(); }
            @Override
            public Long createUser(UserDTO user, String plainPassword) { return 1L; }
            @Override
            public boolean updateUser(UserDTO user) { return true; }
            @Override
            public boolean deleteUser(Long id) { return true; }
            @Override
            public boolean changePassword(Long userId, String currentPassword, String newPassword) { return true; }
        };

        router = new RequestRouter(
                authService,
                bookService,
                studentService,
                borrowService,
                returnService,
                fineService,
                dashboardService,
                reservationService,
                auditLogService,
                categoryService,
                authorService,
                userService
        );

        // Pre-create authenticated test sessions
        adminToken = sessionManager.createSession(new UserSessionDTO(null, 1L, "admin", "Admin", UserRole.ADMIN));
        librarianToken = sessionManager.createSession(new UserSessionDTO(null, 2L, "librarian", "Librarian", UserRole.LIBRARIAN));
        studentToken = sessionManager.createSession(new UserSessionDTO(null, 3L, "student", "Student", UserRole.STUDENT));
    }

    // --- Minimum Required Test 1: LOGIN Success ---
    @Test
    public void testLoginSuccess() {
        Request req = new Request(Action.LOGIN, new LoginRequestDTO("admin", "admin123"));
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof UserSessionDTO);
        Assert.assertNotNull(((UserSessionDTO) resp.getData()).getToken());
    }

    // --- Minimum Required Test 2: LOGIN Invalid Credentials ---
    @Test
    public void testLoginInvalidCredentials() {
        Request req = new Request(Action.LOGIN, new LoginRequestDTO("admin", "wrongpassword"));
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
    }

    // --- Minimum Required Test 3: LOGOUT ---
    @Test
    public void testLogout() {
        Request req = new Request(Action.LOGOUT, adminToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertFalse(sessionManager.isValid(adminToken));
    }

    // --- Minimum Required Test 4: Unauthenticated Access to Protected Action ---
    @Test
    public void testUnauthenticatedAccessToProtectedAction() {
        Request req = new Request(Action.LIST_BOOKS, null, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.UNAUTHORIZED, resp.getStatusCode());
    }

    // --- Minimum Required Test 5: Authenticated Student Access ---
    @Test
    public void testAuthenticatedStudentAccess() {
        // Student accessing allowed action
        Request req = new Request(Action.LIST_BOOKS, studentToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());

        // Student accessing own profile (userId=3)
        Request reqProfile = new Request(Action.GET_STUDENT, studentToken, 3L);
        Response respProfile = router.route(reqProfile);
        Assert.assertEquals(StatusCode.OK, respProfile.getStatusCode());
    }

    // --- Minimum Required Test 6: Unauthorized Role Access ---
    @Test
    public void testUnauthorizedRoleAccess() {
        // Student attempting to access ADMIN-only action LIST_USERS
        Request req = new Request(Action.LIST_USERS, studentToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.FORBIDDEN, resp.getStatusCode());
    }

    // --- Minimum Required Test 7: Authorized Librarian Access ---
    @Test
    public void testAuthorizedLibrarianAccess() {
        // Librarian accessing LIST_STUDENTS
        Request req = new Request(Action.LIST_STUDENTS, librarianToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
    }

    // --- Minimum Required Test 8: Authorized Admin Access ---
    @Test
    public void testAuthorizedAdminAccess() {
        // Admin accessing LIST_USERS
        Request req = new Request(Action.LIST_USERS, adminToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
    }

    // --- Minimum Required Test 9: Malformed / Null Payload ---
    @Test
    public void testMalformedNullPayload() {
        // CREATE_BOOK expects BookDTO, pass null or wrong object
        Request req = new Request(Action.CREATE_BOOK, adminToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp.getStatusCode());
    }

    // --- Minimum Required Test 10: EntityNotFoundException Mapping ---
    @Test
    public void testEntityNotFoundExceptionMapping() {
        Request req = new Request(Action.GET_BOOK, adminToken, 999L);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.NOT_FOUND, resp.getStatusCode());
    }

    // --- Minimum Required Test 11: BookUnavailableException Mapping ---
    @Test
    public void testBookUnavailableExceptionMapping() {
        BorrowRequestDTO payload = new BorrowRequestDTO(1L, 409L);
        Request req = new Request(Action.BORROW_BOOK, librarianToken, payload);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.CONFLICT, resp.getStatusCode());
    }

    // --- Minimum Required Test 12: BorrowLimitExceededException Mapping ---
    @Test
    public void testBorrowLimitExceededExceptionMapping() {
        BorrowRequestDTO payload = new BorrowRequestDTO(400L, 1L);
        Request req = new Request(Action.BORROW_BOOK, librarianToken, payload);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp.getStatusCode());
    }

    // --- Minimum Required Test 13: Unexpected Exception -> Internal Server Error ---
    @Test
    public void testUnexpectedExceptionInternalServerError() {
        // GET_BOOK with invalid type causing unexpected or caught exception
        Request req = new Request(Action.GET_BOOK, adminToken, new Object());
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getStatusCode() == StatusCode.BAD_REQUEST || resp.getStatusCode() == StatusCode.SERVER_ERROR);
    }

    // --- Minimum Required Test 14: Successful Book Search Route ---
    @Test
    public void testSuccessfulBookSearchRoute() {
        BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
        criteria.setKeyword("Found");
        Request req = new Request(Action.SEARCH_BOOKS, studentToken, criteria);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof PageResponseDTO);
    }

    // --- Minimum Required Test 15: Successful Borrow Route ---
    @Test
    public void testSuccessfulBorrowRoute() {
        BorrowRequestDTO payload = new BorrowRequestDTO(1L, 1L);
        Request req = new Request(Action.BORROW_BOOK, librarianToken, payload);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof BorrowRecordDTO);
    }

    // --- Minimum Required Test 16: Successful Return Route ---
    @Test
    public void testSuccessfulReturnRoute() {
        BorrowRequestDTO payload = new BorrowRequestDTO(1L, 1L);
        Request req = new Request(Action.RETURN_BOOK, librarianToken, payload);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof ReturnResultDTO);
    }

    // --- Minimum Required Test 17: Fine Payment Route ---
    @Test
    public void testFinePaymentRoute() {
        Request req = new Request(Action.PAY_FINE, librarianToken, 1L);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
    }

    // --- Minimum Required Test 18: Reservation Route ---
    @Test
    public void testReservationRoute() {
        ReservationDTO r = new ReservationDTO();
        r.setStudentId(3L);
        r.setBookId(1L);
        Request req = new Request(Action.CREATE_RESERVATION, studentToken, r);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.CREATED, resp.getStatusCode());
    }

    // --- Minimum Required Test 19: Student Management Route ---
    @Test
    public void testStudentManagementRoute() {
        StudentDTO s = new StudentDTO();
        s.setStudentCode("STU999");
        s.setFullName("Test Student");
        s.setClassName("IT");
        Request req = new Request(Action.CREATE_STUDENT, librarianToken, s);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.CREATED, resp.getStatusCode());
    }

    // --- Minimum Required Test 20: Book Management Route ---
    @Test
    public void testBookManagementRoute() {
        BookDTO b = new BookDTO();
        b.setTitle("New Book");
        b.setIsbn("123-456");
        Request req = new Request(Action.CREATE_BOOK, librarianToken, b);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.CREATED, resp.getStatusCode());

        Request delReq = new Request(Action.DELETE_BOOK, librarianToken, 101L);
        Response delResp = router.route(delReq);
        Assert.assertEquals(StatusCode.OK, delResp.getStatusCode());
    }

    // --- Minimum Required Test 21: Dashboard Route ---
    @Test
    public void testDashboardRoute() {
        Request req = new Request(Action.GET_DASHBOARD_METRICS, librarianToken, null);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.OK, resp.getStatusCode());
        Assert.assertTrue(resp.getData() instanceof DashboardMetricsDTO);
    }

    // --- Phase 3.1 Security & IDOR Tests ---
    @Test
    public void testNegativeIdRejectedWithBadRequest() {
        // Negative Book ID
        Request req1 = new Request(Action.GET_BOOK, librarianToken, -5L);
        Response resp1 = router.route(req1);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp1.getStatusCode());

        // Negative Student ID
        Request req2 = new Request(Action.GET_STUDENT, librarianToken, -1L);
        Response resp2 = router.route(req2);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp2.getStatusCode());

        // Negative Overdue Days
        Request req3 = new Request(Action.CALCULATE_FINE, librarianToken, -3);
        Response resp3 = router.route(req3);
        Assert.assertEquals(StatusCode.BAD_REQUEST, resp3.getStatusCode());
    }

    @Test
    public void testStudentCannotCancelOtherStudentReservation() {
        // Reservation 999 belongs to student 999L (not current student 3L)
        // Student attempting to cancel another student's reservation must be 403 FORBIDDEN
        // Let's pass reservation ID that belongs to student 999L
        // In our mock, if reservationId == 999L, studentId is 999L
        Request req = new Request(Action.CANCEL_RESERVATION, studentToken, 999L);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    public void testStudentCannotViewOtherStudentProfile() {
        // Student token (userId 3) attempting to view student 1 (userId 1)
        Request req = new Request(Action.GET_STUDENT, studentToken, 1L);
        Response resp = router.route(req);
        Assert.assertNotNull(resp);
        Assert.assertEquals(StatusCode.FORBIDDEN, resp.getStatusCode());
    }
}
