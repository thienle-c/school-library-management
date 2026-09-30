package thuvien.server.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.database.DatabaseManager;
import thuvien.server.router.RequestRouter;

/**
 * Real TCP End-to-End Integration Test:
 * Client Socket -> TCP Port 8889 -> LibraryServer -> ClientHandler -> RequestRouter -> Service -> Repository -> MySQL (Laragon).
 *
 * Verifies real TCP wire pipeline:
 * 1. LOGIN
 * 2. SEARCH_BOOKS
 * 3. BORROW_BOOK
 * 4. RETURN_BOOK
 */
public class TCPEndToEndIntegrationTest {

    private static final int TEST_PORT = 8889;
    private static LibraryServer server;
    private static boolean dbAvailable = false;

    @BeforeClass
    public static void setUpServerAndDatabase() throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            dbAvailable = false;
        }

        if (dbAvailable) {
            // Ensure standard seed user password hashes in database match PasswordHasher
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                try (PreparedStatement psAdmin = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'admin'")) {
                    psAdmin.setString(1, thuvien.server.security.PasswordHasher.hash("admin123"));
                    psAdmin.executeUpdate();
                }
                try (PreparedStatement psLib = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'librarian1'")) {
                    psLib.setString(1, thuvien.server.security.PasswordHasher.hash("lib123"));
                    psLib.executeUpdate();
                }
                try (PreparedStatement psStu = conn.prepareStatement("UPDATE users SET password_hash = ?, is_active = 1 WHERE username = 'student1'")) {
                    psStu.setString(1, thuvien.server.security.PasswordHasher.hash("student123"));
                    psStu.executeUpdate();
                }
            }
        }

        // Start LibraryServer unconditionally so PING/PONG test works even without DB
        RequestRouter router = new RequestRouter();
        server = new LibraryServer(TEST_PORT, 5, 10000, router);
        server.start();
        // Allow server listener thread to bind
        Thread.sleep(300);
    }

    @AfterClass
    public static void tearDownServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testPingPongWithoutLoginOrDatabase() throws Exception {
        // 1. Raw TCP Socket: Client connects -> sends Action.PING without token -> receives PONG
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            out.flush();
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()))) {
                Request pingReq = new Request(Action.PING, "PING");
                Assert.assertNull("PING request must not require a session token", pingReq.getToken());
                Assert.assertTrue("Action.PING must be marked read-only", Action.PING.isReadOnly());

                out.writeObject(pingReq);
                out.flush();

                Object respObj = in.readObject();
                Assert.assertTrue("Server response must be Response object", respObj instanceof Response);
                Response pongResp = (Response) respObj;

                Assert.assertEquals("RequestId must match", pingReq.getRequestId(), pongResp.getRequestId());
                Assert.assertEquals("PING must return 200 OK", StatusCode.OK, pongResp.getStatusCode());
                Assert.assertEquals("PING message must be PONG", "PONG", pongResp.getMessage());
                Assert.assertEquals("PING data must be PONG", "PONG", pongResp.getData());
            }
        }

        // 2. TCPNetworkClient: connect(host, port) and ping() without login
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        try {
            Assert.assertTrue("TCPNetworkClient.connect(host, port) must succeed",
                    tcpClient.connect("127.0.0.1", TEST_PORT));
            Assert.assertTrue("TCPNetworkClient.ping() must return true on PONG",
                    tcpClient.ping());
        } finally {
            tcpClient.disconnect();
        }
    }

    @Test
    public void testLoginOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testLoginOverTcp", dbAvailable);

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        thuvien.client.controller.ClientAuthController authController =
                new thuvien.client.controller.ClientAuthController(tcpClient);

        try {
            // 1. Wrong password -> 401 UNAUTHORIZED (AuthenticationException)
            try {
                authController.login("admin", "wrong_pass_123");
                Assert.fail("Login with wrong password must throw AuthenticationException");
            } catch (thuvien.common.exception.AuthenticationException expected) {
                Assert.assertNull("ClientSession token must remain null after failed login",
                        thuvien.client.session.ClientSession.getInstance().getToken());
            }

            // 2. Non-existent account -> 401 UNAUTHORIZED (AuthenticationException)
            try {
                authController.login("non_existent_user_xyz_999", "some_pass");
                Assert.fail("Login with non-existent username must throw AuthenticationException");
            } catch (thuvien.common.exception.AuthenticationException expected) {
                Assert.assertNull(thuvien.client.session.ClientSession.getInstance().getToken());
            }

            // 3. Account lockout after 5 consecutive failed attempts on a test username
            String lockoutUser = "tcp_lock_user_" + System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                Response r = tcpClient.send(new Request(Action.LOGIN, new LoginRequestDTO(lockoutUser, "bad_pass")));
                Assert.assertEquals(StatusCode.UNAUTHORIZED, r.getStatusCode());
            }
            Response lockedResp = tcpClient.send(new Request(Action.LOGIN, new LoginRequestDTO(lockoutUser, "bad_pass")));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, lockedResp.getStatusCode());
            Assert.assertTrue("Locked account message must indicate temporary lock",
                    lockedResp.getMessage() != null && lockedResp.getMessage().contains("15"));

            // 4. Successful LOGIN via ClientAuthController -> token stored in ClientSession
            UserSessionDTO session = authController.login("admin", "admin123");
            Assert.assertNotNull("Session must not be null", session);
            Assert.assertNotNull("Session token must be generated", session.getToken());
            Assert.assertEquals("ClientSession must store logged-in token",
                    session.getToken(), thuvien.client.session.ClientSession.getInstance().getToken());

            String activeToken = session.getToken();

            // 5. Subsequent authenticated request uses stored token automatically
            Request getMeReq = new Request(Action.GET_CURRENT_USER, null);
            Response getMeResp = tcpClient.send(getMeReq);
            Assert.assertEquals(getMeReq.getRequestId(), getMeResp.getRequestId());
            Assert.assertEquals(StatusCode.OK, getMeResp.getStatusCode());
            Assert.assertTrue(getMeResp.getData() instanceof UserSessionDTO);
            Assert.assertEquals("admin", ((UserSessionDTO) getMeResp.getData()).getUsername());

            // 6. Logout invalidates token on both client and server
            authController.logout();
            Assert.assertNull("ClientSession must be cleared after logout",
                    thuvien.client.session.ClientSession.getInstance().getToken());

            // Verify old token is rejected with 401 UNAUTHORIZED by server
            Request postLogoutReq = new Request(Action.GET_CURRENT_USER, activeToken, null);
            Response postLogoutResp = tcpClient.send(postLogoutReq);
            Assert.assertEquals("Old token after logout must return 401 UNAUTHORIZED",
                    StatusCode.UNAUTHORIZED, postLogoutResp.getStatusCode());
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testSearchBooksOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testSearchBooksOverTcp", dbAvailable);

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        thuvien.client.controller.ClientAuthController authController =
                new thuvien.client.controller.ClientAuthController(tcpClient);
        thuvien.client.controller.ClientBookController bookController =
                new thuvien.client.controller.ClientBookController(tcpClient);

        try {
            UserSessionDTO session = authController.login("student1", "student123");
            Assert.assertNotNull(session.getToken());

            // Verify raw Request/Response preserves requestId and deserializes PageResponseDTO<BookDTO>
            BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
            criteria.setKeyword("Computer");
            criteria.setPage(1);
            criteria.setPageSize(10);

            Request rawReq = new Request(Action.SEARCH_BOOKS, criteria);
            Response rawResp = tcpClient.send(rawReq);
            Assert.assertEquals("RequestId must be preserved across TCP", rawReq.getRequestId(), rawResp.getRequestId());
            Assert.assertEquals("SEARCH_BOOKS must return 200 OK", StatusCode.OK, rawResp.getStatusCode());
            Assert.assertTrue("Data must be PageResponseDTO", rawResp.getData() instanceof PageResponseDTO);

            // Verify via ClientBookController
            PageResponseDTO<thuvien.common.dto.BookDTO> page = bookController.searchBooks(criteria);
            Assert.assertNotNull("PageResponseDTO must not be null", page);
            Assert.assertNotNull("Book items list must not be null", page.getItems());
            Assert.assertFalse("Search for 'Computer' must return at least 1 seeded book", page.getItems().isEmpty());
            thuvien.common.dto.BookDTO firstBook = page.getItems().get(0);
            Assert.assertNotNull("Deserialized BookDTO ID must not be null", firstBook.getId());
            Assert.assertNotNull("Deserialized BookDTO Title must not be null", firstBook.getTitle());

            authController.logout();
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testBorrowBookOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testBorrowBookOverTcp", dbAvailable);

        // Reset state for Student 1 and Book 1
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE student_id = 1")) {
                psDelFines.executeUpdate();
            }
            try (PreparedStatement psDelBorrows = conn.prepareStatement("DELETE FROM borrow_records WHERE student_id = 1")) {
                psDelBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET total_copies = 5, available_copies = 5, status = 'AVAILABLE' WHERE id = 1")) {
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement("UPDATE students SET current_borrow_count = 0, max_borrow_limit = 5, status = 'ACTIVE' WHERE id = 1")) {
                psStudent.executeUpdate();
            }
        }

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);

        try {
            // 1. Unauthenticated borrow attempt -> 401 UNAUTHORIZED
            Response noAuthResp = tcpClient.send(new Request(Action.BORROW_BOOK, null, new BorrowRequestDTO(1L, 1L, 14, "No token")));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, noAuthResp.getStatusCode());

            // 2. Fake token borrow attempt -> 401 UNAUTHORIZED
            Response fakeTokenResp = tcpClient.send(new Request(Action.BORROW_BOOK, "fake-token-12345", new BorrowRequestDTO(1L, 1L, 14, "Fake token")));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, fakeTokenResp.getStatusCode());

            // 3. Login as librarian1 -> SEARCH_BOOKS -> BORROW_BOOK
            thuvien.client.controller.ClientAuthController authController =
                    new thuvien.client.controller.ClientAuthController(tcpClient);
            thuvien.client.controller.ClientBookController bookController =
                    new thuvien.client.controller.ClientBookController(tcpClient);
            thuvien.client.controller.ClientBorrowController borrowController =
                    new thuvien.client.controller.ClientBorrowController(tcpClient);

            authController.login("librarian1", "lib123");

            thuvien.common.dto.BookDTO bookBefore = bookController.getBook(1L);
            Assert.assertEquals(5, bookBefore.getAvailableCopies());

            BorrowRecordDTO record = borrowController.borrowBook(new BorrowRequestDTO(1L, 1L, 14, "Phase 5 Borrow Test"));
            Assert.assertNotNull("BorrowRecordDTO must be returned", record);
            Assert.assertNotNull("BorrowRecord ID must be generated", record.getId());
            Assert.assertEquals(Long.valueOf(1L), record.getStudentId());
            Assert.assertEquals(Long.valueOf(1L), record.getBookId());

            // Verify book available_copies decremented to 4 on server DB via TCP GET_BOOK
            thuvien.common.dto.BookDTO bookAfter = bookController.getBook(1L);
            Assert.assertEquals(4, bookAfter.getAvailableCopies());

            // Clean up by returning the book
            ReturnResultDTO ret = borrowController.returnBook(1L, 1L);
            Assert.assertNotNull(ret);
            Assert.assertEquals(5, bookController.getBook(1L).getAvailableCopies());

            authController.logout();
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testReturnBookOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testReturnBookOverTcp", dbAvailable);

        // Reset state for Student 1 and Book 1
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE student_id = 1")) {
                psDelFines.executeUpdate();
            }
            try (PreparedStatement psDelBorrows = conn.prepareStatement("DELETE FROM borrow_records WHERE student_id = 1")) {
                psDelBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET total_copies = 5, available_copies = 5, status = 'AVAILABLE' WHERE id = 1")) {
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement("UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id = 1")) {
                psStudent.executeUpdate();
            }
        }

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        thuvien.client.controller.ClientAuthController authController =
                new thuvien.client.controller.ClientAuthController(tcpClient);
        thuvien.client.controller.ClientBorrowController borrowController =
                new thuvien.client.controller.ClientBorrowController(tcpClient);
        thuvien.client.controller.ClientBookController bookController =
                new thuvien.client.controller.ClientBookController(tcpClient);

        try {
            authController.login("librarian1", "lib123");

            // 1. Borrow book 1 for student 1
            BorrowRecordDTO borrowed = borrowController.borrowBook(new BorrowRequestDTO(1L, 1L, 14, "Return flow test"));
            Assert.assertNotNull(borrowed);
            Assert.assertEquals(4, bookController.getBook(1L).getAvailableCopies());

            // 2. Return book 1 on time
            ReturnResultDTO returnResult = borrowController.returnBook(1L, 1L);
            Assert.assertNotNull("ReturnResultDTO must be returned", returnResult);
            Assert.assertEquals(borrowed.getId(), returnResult.getBorrowRecordId());
            Assert.assertFalse("On-time return must not be overdue", returnResult.isOverdue());
            Assert.assertEquals(0, returnResult.getOverdueDays());
            Assert.assertEquals(5, bookController.getBook(1L).getAvailableCopies());

            authController.logout();
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testReservationOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testReservationOverTcp", dbAvailable);

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement psClean = conn.prepareStatement("DELETE FROM reservations WHERE student_id = 1 AND book_id = 1")) {
            psClean.executeUpdate();
        }

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        thuvien.client.controller.ClientAuthController authController =
                new thuvien.client.controller.ClientAuthController(tcpClient);
        thuvien.client.controller.ClientReservationController reservationController =
                new thuvien.client.controller.ClientReservationController(tcpClient);

        try {
            // 1. Unauthenticated reservation request -> 401 UNAUTHORIZED
            ReservationDTO unauthDto = new ReservationDTO();
            unauthDto.setBookId(1L);
            unauthDto.setStudentId(1L);
            Response unauthResp = tcpClient.send(new Request(Action.CREATE_RESERVATION, null, unauthDto));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, unauthResp.getStatusCode());

            // 2. Login as student1 and create reservation over TCP
            authController.login("student1", "student123");
            ReservationDTO created = reservationController.createReservation(1L, 1L);
            Assert.assertNotNull("Created ReservationDTO must not be null", created);
            Assert.assertNotNull("Reservation ID must be populated", created.getId());
            Assert.assertEquals(Long.valueOf(1L), created.getStudentId());
            Assert.assertEquals(Long.valueOf(1L), created.getBookId());
            Assert.assertEquals(thuvien.common.enums.ReservationStatus.PENDING, created.getStatus());

            // 3. List student reservations over TCP
            java.util.List<ReservationDTO> list = reservationController.listStudentReservations(1L);
            Assert.assertFalse("Student reservation list must contain the created reservation", list.isEmpty());

            // 4. Cancel reservation over TCP
            boolean cancelled = reservationController.cancelReservation(created.getId());
            Assert.assertTrue("Cancel reservation must return true", cancelled);

            // Cleanup DB record
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement psDel = conn.prepareStatement("DELETE FROM reservations WHERE id = ?")) {
                psDel.setLong(1, created.getId());
                psDel.executeUpdate();
            }

            authController.logout();
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testPayFineOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testPayFineOverTcp", dbAvailable);

        // Seed an overdue borrow & return to generate a real unpaid fine, then pay it over TCP
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE student_id = 1")) {
                psDelFines.executeUpdate();
            }
            try (PreparedStatement psDelBorrows = conn.prepareStatement("DELETE FROM borrow_records WHERE student_id = 1")) {
                psDelBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET total_copies = 5, available_copies = 5, status = 'AVAILABLE' WHERE id = 1")) {
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement("UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id = 1")) {
                psStudent.executeUpdate();
            }
        }

        thuvien.client.session.ClientSession.getInstance().clear();
        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
        thuvien.client.controller.ClientAuthController authController =
                new thuvien.client.controller.ClientAuthController(tcpClient);
        thuvien.client.controller.ClientBorrowController borrowController =
                new thuvien.client.controller.ClientBorrowController(tcpClient);
        thuvien.client.controller.ClientFineController fineController =
                new thuvien.client.controller.ClientFineController(tcpClient);

        try {
            authController.login("librarian1", "lib123");

            // 1. Borrow book 1 for student 1
            BorrowRecordDTO borrow = borrowController.borrowBook(new BorrowRequestDTO(1L, 1L, 7, "Overdue fine test"));
            Assert.assertNotNull(borrow);

            // Backdate due_date by 3 days in DB so RETURN_BOOK generates an overdue fine
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement psBackdate = conn.prepareStatement(
                         "UPDATE borrow_records SET borrow_date = DATE_SUB(NOW(), INTERVAL 10 DAY), due_date = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id = ?")) {
                psBackdate.setLong(1, borrow.getId());
                psBackdate.executeUpdate();
            }

            // 2. Return overdue book over TCP -> generates unpaid fine
            ReturnResultDTO returnResult = borrowController.returnBook(1L, 1L);
            Assert.assertNotNull(returnResult);
            Assert.assertTrue("Return must be flagged as overdue", returnResult.isOverdue());
            Assert.assertTrue("Overdue days must be >= 3", returnResult.getOverdueDays() >= 3);
            Assert.assertTrue("Fine amount must be > 0", returnResult.getFineAmount().compareTo(java.math.BigDecimal.ZERO) > 0);

            // 3. List fines for student 1 over TCP
            java.util.List<thuvien.common.dto.FineDTO> fines = fineController.listStudentFines(1L);
            Assert.assertFalse("Student 1 must have an unpaid fine", fines.isEmpty());
            thuvien.common.dto.FineDTO unpaidFine = fines.get(0);
            Assert.assertFalse("Fine must initially be unpaid", unpaidFine.isPaid());

            // 4. Pay fine over TCP via ClientFineController
            boolean paidOk = fineController.payFine(unpaidFine.getId());
            Assert.assertTrue("PAY_FINE over TCP must succeed", paidOk);

            // 5. Verify fine is now marked as paid in DB via TCP LIST_FINES
            java.util.List<thuvien.common.dto.FineDTO> finesAfter = fineController.listStudentFines(1L);
            Assert.assertTrue("Fine must be marked as paid after PAY_FINE", finesAfter.get(0).isPaid());

            // Cleanup test fine and borrow record
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE id = ?")) {
                    psDelFines.setLong(1, unpaidFine.getId());
                    psDelFines.executeUpdate();
                }
                try (PreparedStatement psDelBorrow = conn.prepareStatement("DELETE FROM borrow_records WHERE id = ?")) {
                    psDelBorrow.setLong(1, borrow.getId());
                    psDelBorrow.executeUpdate();
                }
            }

            authController.logout();
        } finally {
            thuvien.client.session.ClientSession.getInstance().clear();
            tcpClient.disconnect();
        }
    }

    @Test
    public void testAuthorizationOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testAuthorizationOverTcp", dbAvailable);

        thuvien.client.network.TCPNetworkClient tcpClient =
                new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);

        try {
            // 1. Missing token on protected action -> 401 UNAUTHORIZED
            Response noTokenResp = tcpClient.send(new Request(Action.LIST_BOOKS, null, null));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, noTokenResp.getStatusCode());

            // 2. Fake token on protected action -> 401 UNAUTHORIZED
            Response fakeTokenResp = tcpClient.send(new Request(Action.LIST_BOOKS, "invalid-fake-token", null));
            Assert.assertEquals(StatusCode.UNAUTHORIZED, fakeTokenResp.getStatusCode());

            // 3. Login as STUDENT
            Response stuLogin = tcpClient.send(new Request(Action.LOGIN, new LoginRequestDTO("student1", "student123")));
            Assert.assertEquals(StatusCode.OK, stuLogin.getStatusCode());
            String studentToken = ((UserSessionDTO) stuLogin.getData()).getToken();

            // STUDENT cannot call ADMIN operation (LIST_USERS, LIST_AUDIT_LOGS) -> 403 FORBIDDEN
            Response stuAdminUsers = tcpClient.send(new Request(Action.LIST_USERS, studentToken, null));
            Assert.assertEquals(StatusCode.FORBIDDEN, stuAdminUsers.getStatusCode());
            Response stuAdminLogs = tcpClient.send(new Request(Action.LIST_AUDIT_LOGS, studentToken, null));
            Assert.assertEquals(StatusCode.FORBIDDEN, stuAdminLogs.getStatusCode());

            // STUDENT cannot call LIBRARIAN operation (BORROW_BOOK, RETURN_BOOK, PAY_FINE, LIST_STUDENTS) -> 403 FORBIDDEN
            Response stuBorrow = tcpClient.send(new Request(Action.BORROW_BOOK, studentToken, new BorrowRequestDTO(1L, 1L)));
            Assert.assertEquals(StatusCode.FORBIDDEN, stuBorrow.getStatusCode());
            Response stuPayFine = tcpClient.send(new Request(Action.PAY_FINE, studentToken, 1L));
            Assert.assertEquals(StatusCode.FORBIDDEN, stuPayFine.getStatusCode());
            Response stuListStudents = tcpClient.send(new Request(Action.LIST_STUDENTS, studentToken, null));
            Assert.assertEquals(StatusCode.FORBIDDEN, stuListStudents.getStatusCode());

            // 4. Login as LIBRARIAN
            Response libLogin = tcpClient.send(new Request(Action.LOGIN, new LoginRequestDTO("librarian1", "lib123")));
            Assert.assertEquals(StatusCode.OK, libLogin.getStatusCode());
            String libToken = ((UserSessionDTO) libLogin.getData()).getToken();

            // LIBRARIAN cannot call ADMIN-only operation (LIST_USERS, LIST_AUDIT_LOGS, DELETE_STUDENT) -> 403 FORBIDDEN
            Response libAdminUsers = tcpClient.send(new Request(Action.LIST_USERS, libToken, null));
            Assert.assertEquals(StatusCode.FORBIDDEN, libAdminUsers.getStatusCode());
            Response libAdminLogs = tcpClient.send(new Request(Action.LIST_AUDIT_LOGS, libToken, null));
            Assert.assertEquals(StatusCode.FORBIDDEN, libAdminLogs.getStatusCode());
            Response libDeleteStudent = tcpClient.send(new Request(Action.DELETE_STUDENT, libToken, 999L));
            Assert.assertEquals(StatusCode.FORBIDDEN, libDeleteStudent.getStatusCode());

            // Cleanup sessions
            tcpClient.send(new Request(Action.LOGOUT, studentToken, null));
            tcpClient.send(new Request(Action.LOGOUT, libToken, null));
        } finally {
            tcpClient.disconnect();
        }
    }

    @Test
    public void testConcurrentClientsOverTcp() throws Exception {
        Assume.assumeTrue("MySQL required for testConcurrentClientsOverTcp", dbAvailable);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> clientATask = () -> {
                thuvien.client.network.TCPNetworkClient clientA =
                        new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
                try {
                    // 1. Client A -> PING
                    Assert.assertTrue(clientA.ping());

                    // 2. Client A -> LOGIN as admin
                    Request loginA = new Request(Action.LOGIN, new LoginRequestDTO("admin", "admin123"));
                    Response respLoginA = clientA.send(loginA);
                    Assert.assertEquals(loginA.getRequestId(), respLoginA.getRequestId());
                    Assert.assertEquals(StatusCode.OK, respLoginA.getStatusCode());
                    UserSessionDTO sessionA = (UserSessionDTO) respLoginA.getData();
                    Assert.assertEquals("admin", sessionA.getUsername());

                    // 3. Client A -> multiple authenticated requests verifying session and requestId isolation
                    for (int i = 0; i < 5; i++) {
                        Request reqMe = new Request(Action.GET_CURRENT_USER, sessionA.getToken(), null);
                        Response respMe = clientA.send(reqMe);
                        Assert.assertEquals(reqMe.getRequestId(), respMe.getRequestId());
                        Assert.assertEquals(StatusCode.OK, respMe.getStatusCode());
                        UserSessionDTO me = (UserSessionDTO) respMe.getData();
                        Assert.assertEquals("admin", me.getUsername());
                        Assert.assertEquals(thuvien.common.enums.UserRole.ADMIN, me.getRole());
                    }

                    clientA.send(new Request(Action.LOGOUT, sessionA.getToken(), null));
                    return true;
                } finally {
                    clientA.disconnect();
                }
            };

            java.util.concurrent.Callable<Boolean> clientBTask = () -> {
                thuvien.client.network.TCPNetworkClient clientB =
                        new thuvien.client.network.TCPNetworkClient("127.0.0.1", TEST_PORT, 5000);
                try {
                    // 1. Client B -> PING
                    Assert.assertTrue(clientB.ping());

                    // 2. Client B -> LOGIN as student1
                    Request loginB = new Request(Action.LOGIN, new LoginRequestDTO("student1", "student123"));
                    Response respLoginB = clientB.send(loginB);
                    Assert.assertEquals(loginB.getRequestId(), respLoginB.getRequestId());
                    Assert.assertEquals(StatusCode.OK, respLoginB.getStatusCode());
                    UserSessionDTO sessionB = (UserSessionDTO) respLoginB.getData();
                    Assert.assertEquals("student1", sessionB.getUsername());

                    // 3. Client B -> multiple authenticated requests verifying session and requestId isolation
                    for (int i = 0; i < 5; i++) {
                        Request reqMe = new Request(Action.GET_CURRENT_USER, sessionB.getToken(), null);
                        Response respMe = clientB.send(reqMe);
                        Assert.assertEquals(reqMe.getRequestId(), respMe.getRequestId());
                        Assert.assertEquals(StatusCode.OK, respMe.getStatusCode());
                        UserSessionDTO me = (UserSessionDTO) respMe.getData();
                        Assert.assertEquals("student1", me.getUsername());
                        Assert.assertEquals(thuvien.common.enums.UserRole.STUDENT, me.getRole());
                    }

                    clientB.send(new Request(Action.LOGOUT, sessionB.getToken(), null));
                    return true;
                } finally {
                    clientB.disconnect();
                }
            };

            java.util.List<java.util.concurrent.Future<Boolean>> futures =
                    pool.invokeAll(java.util.Arrays.asList(clientATask, clientBTask));
            for (java.util.concurrent.Future<Boolean> f : futures) {
                Assert.assertTrue("Concurrent client task must succeed without session or requestId mixup", f.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testFullTcpEndToEndPipeline() throws Exception {
        Assume.assumeTrue(
                "Live MySQL database is not reachable on localhost:3306. TCP End-to-End test marked as NOT EXECUTED.",
                dbAvailable
        );

        // Reset database state for test book and student
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (PreparedStatement psDelFines = conn.prepareStatement("DELETE FROM fines WHERE student_id = 1")) {
                psDelFines.executeUpdate();
            }
            try (PreparedStatement psDelBorrows = conn.prepareStatement("DELETE FROM borrow_records WHERE student_id = 1")) {
                psDelBorrows.executeUpdate();
            }
            try (PreparedStatement psBook = conn.prepareStatement("UPDATE books SET total_copies = 5, available_copies = 5 WHERE id = 1")) {
                psBook.executeUpdate();
            }
            try (PreparedStatement psStudent = conn.prepareStatement("UPDATE students SET current_borrow_count = 0 WHERE id = 1")) {
                psStudent.executeUpdate();
            }
        }

        // Establish real TCP connection to LibraryServer
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            out.flush();
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()))) {

                // 1. TCP Step 1: LOGIN as admin
                Request loginReq = new Request(Action.LOGIN, new LoginRequestDTO("admin", "admin123"));
                out.writeObject(loginReq);
                out.flush();

                Object respObj1 = in.readObject();
                Assert.assertTrue("Server response must be Response object", respObj1 instanceof Response);
                Response loginResp = (Response) respObj1;
                Assert.assertEquals("TCP Login must return 200 OK", StatusCode.OK, loginResp.getStatusCode());
                Assert.assertTrue("Login data must be UserSessionDTO", loginResp.getData() instanceof UserSessionDTO);

                UserSessionDTO session = (UserSessionDTO) loginResp.getData();
                String token = session.getToken();
                Assert.assertNotNull("Session token must be populated", token);

                // 2. TCP Step 2: SEARCH_BOOKS
                BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
                criteria.setKeyword("Computer");
                Request searchReq = new Request(Action.SEARCH_BOOKS, token, criteria);
                out.writeObject(searchReq);
                out.flush();

                Object respObj2 = in.readObject();
                Assert.assertTrue(respObj2 instanceof Response);
                Response searchResp = (Response) respObj2;
                Assert.assertEquals("TCP Search must return 200 OK", StatusCode.OK, searchResp.getStatusCode());
                Assert.assertTrue("Search data must be PageResponseDTO", searchResp.getData() instanceof PageResponseDTO);

                // 3. TCP Step 3: BORROW_BOOK
                BorrowRequestDTO borrowReq = new BorrowRequestDTO(1L, 1L, 14, "TCP E2E Borrow");
                Request reqBorrow = new Request(Action.BORROW_BOOK, token, borrowReq);
                out.writeObject(reqBorrow);
                out.flush();

                Object respObj3 = in.readObject();
                Assert.assertTrue(respObj3 instanceof Response);
                Response borrowResp = (Response) respObj3;
                Assert.assertEquals("TCP Borrow must return 200 OK", StatusCode.OK, borrowResp.getStatusCode());
                Assert.assertTrue("Borrow data must be BorrowRecordDTO", borrowResp.getData() instanceof BorrowRecordDTO);
                BorrowRecordDTO borrowRecord = (BorrowRecordDTO) borrowResp.getData();
                Assert.assertNotNull(borrowRecord.getId());

                // 4. TCP Step 4: RETURN_BOOK
                BorrowRequestDTO returnReq = new BorrowRequestDTO(1L, 1L);
                Request reqReturn = new Request(Action.RETURN_BOOK, token, returnReq);
                out.writeObject(reqReturn);
                out.flush();

                Object respObj4 = in.readObject();
                Assert.assertTrue(respObj4 instanceof Response);
                Response returnResp = (Response) respObj4;
                Assert.assertEquals("TCP Return must return 200 OK", StatusCode.OK, returnResp.getStatusCode());
                Assert.assertTrue("Return data must be ReturnResultDTO", returnResp.getData() instanceof ReturnResultDTO);
                ReturnResultDTO returnResult = (ReturnResultDTO) returnResp.getData();
                Assert.assertEquals(borrowRecord.getId(), returnResult.getBorrowRecordId());
                Assert.assertFalse("On-time return must not be overdue", returnResult.isOverdue());

                // 5. TCP Step 5: LOGOUT
                Request reqLogout = new Request(Action.LOGOUT, token, null);
                out.writeObject(reqLogout);
                out.flush();

                Object respObj5 = in.readObject();
                Assert.assertTrue(respObj5 instanceof Response);
                Response logoutResp = (Response) respObj5;
                Assert.assertEquals("TCP Logout must return 200 OK", StatusCode.OK, logoutResp.getStatusCode());

                // 6. TCP Step 6: Attempt protected action with old logged-out token
                Request reqProtected = new Request(Action.GET_CURRENT_USER, token, null);
                out.writeObject(reqProtected);
                out.flush();

                Object respObj6 = in.readObject();
                Assert.assertTrue(respObj6 instanceof Response);
                Response oldTokenResp = (Response) respObj6;
                Assert.assertEquals("Request with invalidated token must return 401 UNAUTHORIZED",
                        StatusCode.UNAUTHORIZED, oldTokenResp.getStatusCode());
            }
        }
    }

    @Test
    public void testStudentOwnershipAndIdorProtectionOverTcp() throws Exception {
        Assume.assumeTrue(
                "Live MySQL database is not reachable on localhost:3306. TCP End-to-End test marked as NOT EXECUTED.",
                dbAvailable
        );

        try (Socket socket = new Socket("127.0.0.1", TEST_PORT);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            out.flush();
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()))) {

                // 1. LOGIN as student1 (seed data: username 'student1', pass 'student123', studentId 1, userId 4)
                Request loginReq = new Request(Action.LOGIN, new LoginRequestDTO("student1", "student123"));
                out.writeObject(loginReq);
                out.flush();

                Response loginResp = (Response) in.readObject();
                Assert.assertEquals(StatusCode.OK, loginResp.getStatusCode());
                UserSessionDTO session = (UserSessionDTO) loginResp.getData();
                String studentToken = session.getToken();
                Assert.assertNotNull(studentToken);

                // 2. Student attempts GET_STUDENT for Student 2 (STU002) -> must be 403 FORBIDDEN
                Request reqOtherStudent = new Request(Action.GET_STUDENT, studentToken, 2L);
                out.writeObject(reqOtherStudent);
                out.flush();

                Response otherStudentResp = (Response) in.readObject();
                Assert.assertEquals("Student cannot inspect other student profile (IDOR)",
                        StatusCode.FORBIDDEN, otherStudentResp.getStatusCode());

                // 3. Student attempts BORROW_BOOK (librarian action) -> must be 403 FORBIDDEN
                Request reqBorrowForbidden = new Request(Action.BORROW_BOOK, studentToken, new BorrowRequestDTO(1L, 1L));
                out.writeObject(reqBorrowForbidden);
                out.flush();

                Response borrowForbiddenResp = (Response) in.readObject();
                Assert.assertEquals("Student cannot execute staff borrow action",
                        StatusCode.FORBIDDEN, borrowForbiddenResp.getStatusCode());

                // 4. Create reservation for student 1 on book 1
                ReservationDTO res1Req = new ReservationDTO();
                res1Req.setStudentId(1L);
                res1Req.setBookId(1L);
                Request reqCreateRes = new Request(Action.CREATE_RESERVATION, studentToken, res1Req);
                out.writeObject(reqCreateRes);
                out.flush();

                Response createResResp = (Response) in.readObject();
                Assert.assertEquals(StatusCode.CREATED, createResResp.getStatusCode());
                ReservationDTO student1Res = (ReservationDTO) createResResp.getData();
                Assert.assertNotNull(student1Res.getId());

                // 5. Seed a reservation for Student 2 directly in DB
                Long student2ResId;
                try (Connection conn = DatabaseManager.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO reservations (student_id, book_id, reservation_date, expiry_date, status) VALUES (2, 1, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'PENDING')",
                             PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.executeUpdate();
                    try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        student2ResId = rs.getLong(1);
                    }
                }

                // 6. Student 1 attempts to cancel Student 2's reservation -> must be 403 FORBIDDEN (IDOR)
                Request reqCancelOther = new Request(Action.CANCEL_RESERVATION, studentToken, student2ResId);
                out.writeObject(reqCancelOther);
                out.flush();

                Response cancelOtherResp = (Response) in.readObject();
                Assert.assertEquals("Student cannot cancel another student's reservation (IDOR)",
                        StatusCode.FORBIDDEN, cancelOtherResp.getStatusCode());

                // 7. Student 1 cancels OWN reservation -> must be 200 OK
                Request reqCancelOwn = new Request(Action.CANCEL_RESERVATION, studentToken, student1Res.getId());
                out.writeObject(reqCancelOwn);
                out.flush();

                Response cancelOwnResp = (Response) in.readObject();
                Assert.assertEquals(StatusCode.OK, cancelOwnResp.getStatusCode());

                // Cleanup reservations in DB
                try (Connection conn = DatabaseManager.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM reservations WHERE id IN (?, ?)")) {
                    ps.setLong(1, student1Res.getId());
                    ps.setLong(2, student2ResId);
                    ps.executeUpdate();
                }
            }
        }
    }
}
