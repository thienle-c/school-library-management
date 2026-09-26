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
            // Ensure student1 password hash in database matches PasswordHasher.hash("student123")
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement psUser = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE username = 'student1'")) {
                psUser.setString(1, thuvien.server.security.PasswordHasher.hash("student123"));
                psUser.executeUpdate();
            }

            // Initialize full production router connected to real MySQL
            RequestRouter router = new RequestRouter();
            server = new LibraryServer(TEST_PORT, 5, 10000, router);
            server.start();
            // Allow server listener thread to bind
            Thread.sleep(300);
        }
    }

    @AfterClass
    public static void tearDownServer() {
        if (server != null) {
            server.stop();
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
