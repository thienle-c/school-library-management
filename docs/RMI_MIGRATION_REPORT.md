# BÁO CÁO MIGRATION TOÀN DIỆN: TCP SOCKET SANG JAVA RMI

**Đề tài**: Hệ thống quản lý mượn/trả sách thư viện trường học (Remote School Library Management)  
**Môn học**: Lập trình mạng  
**Thời gian hoàn thành**: 07/10/2026  
**Trạng thái kiểm thử**: **99/99 tests PASS (100%)**

---

## 1. Kiến trúc trước migration
Hệ thống sử dụng tầng giao tiếp:
* Raw TCP Sockets (`java.net.Socket`, `java.net.ServerSocket`) trên port `9999`.
* Gửi nhận các đối tượng qua `ObjectInputStream` / `ObjectOutputStream`.
* `TCPNetworkClient` duy trì kết nối persistent socket và gửi `Request(Action, payload)`.
* `LibraryServer` tiếp nhận socket, giao cho `ClientHandler` chạy trong `ExecutorService`.
* `RequestRouter` giải mã `Action`, kiểm tra session/RBAC, và ủy quyền cho Service.

---

## 2. Kiến trúc sau migration
Hệ thống chuyển đổi hoàn toàn sang **Java RMI (Remote Method Invocation)** theo chuẩn công nghệ Java:
```
Java Swing Client GUI
        │
        │  Java RMI (Port 1099, Service: LibraryRemoteService)
        ▼
RMIClient (Client Adapter implements NetworkClient)
        │
        │  Naming / LocateRegistry lookup & Remote Method Calls
        ▼
LibraryRemoteServiceImpl (UnicastRemoteObject)
        │
        ▼
RequestRouter (Enforces Session, Token, RBAC & Account Lockout)
        │
        ▼
Service Layer (Atomic Concurrency Locking & Business Rules)
        │
        ▼
Repository Layer (PreparedStatements & HikariCP Pool)
        │
        ▼
MySQL 8.x InnoDB (localhost:3306)
```

Client GUI giao tiếp qua Remote Interface và Remote Object, không còn phụ thuộc vào socket I/O thủ công.

---

## 3. Các file đã thêm mới
1. `common/src/thuvien/common/rmi/LibraryRemoteService.java`: Interface kế thừa `java.rmi.Remote`, khai báo các phương thức remote với `throws RemoteException`.
2. `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java`: Lớp remote implementation kế thừa `java.rmi.server.UnicastRemoteObject`, hiện thực `LibraryRemoteService`.
3. `server/src/thuvien/server/rmi/LibraryRMIServer.java`: Server bootstrap khởi tạo RMI Registry trên port `1099` (`LocateRegistry.createRegistry`), đăng ký remote object (`rebind`), in danh sách IP LAN.
4. `client/src/thuvien/client/network/RMIClient.java`: Client adapter kết nối Registry (`LocateRegistry.getRegistry`), lookup remote service, và gửi nhận lời gọi hàm từ xa.
5. `test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java`: Bộ 15 bài kiểm thử tự động toàn diện cho toàn bộ luồng RMI và Concurrency.

---

## 4. Các file đã chỉnh sửa
1. `client/src/thuvien/client/ClientMain.java`: Chuyển bootstrap mặc định sang `RMIClient` trên port `1099` (hỗ trợ CLI argument và system properties `rmi.server.host`, `rmi.registry.port`).
2. `client/src/thuvien/client/view/LoginForm.java`: Cập nhật subheader giao diện sang "Ứng Dụng Khách Kết Nối Java RMI", port mặc định `1099`, nút "Kiểm tra kết nối" gọi RMI `ping()`.
3. `server/resources/server.properties`: Thêm cấu hình `rmi.registry.port=1099`, `rmi.service.name=LibraryRemoteService`.
4. `client/resources/client.properties`: Thêm cấu hình `rmi.server.host=127.0.0.1`, `rmi.registry.port=1099`, `rmi.service.name=LibraryRemoteService`.
5. `build.xml`: Thêm các Ant targets `run-rmi-server`, `run-rmi-client`, cập nhật `run-server`, `run-client`, `package-server`, `package-client`, `package-all` đóng gói đúng entrypoint RMI server (`LibraryRMIServer`).
6. `docs/NETWORK.md` & `README.md`: Cập nhật tài liệu kỹ thuật, kiến trúc RMI, hướng dẫn triển khai 2 máy và bảng kết quả kiểm thử.

---

## 5. Các file legacy (giữ lại phục vụ tương thích ngược)
* `server/src/thuvien/server/network/LibraryServer.java` & `ClientHandler.java`: Server socket TCP cũ. Đã được thay thế hoàn toàn bởi `LibraryRMIServer` làm server chính của hệ thống.
* `client/src/thuvien/client/network/TCPNetworkClient.java`: Client socket TCP cũ. Đã được thay thế bởi `RMIClient`.
* `test/thuvien/server/network/TCPEndToEndIntegrationTest.java`: Vẫn giữ nguyên 11 test case và đều PASS để đảm bảo không phá vỡ bất kỳ thành phần nào.

---

## 6. Remote Interface
Interface `thuvien.common.rmi.LibraryRemoteService`:
```java
public interface LibraryRemoteService extends Remote {
    String ping() throws RemoteException;
    UserSessionDTO login(LoginRequestDTO request) throws RemoteException, AuthenticationException, LibraryException;
    void logout(String token) throws RemoteException;
    UserSessionDTO getCurrentUser(String token) throws RemoteException, AuthenticationException, LibraryException;
    boolean registerStudent(RegisterStudentRequestDTO request) throws RemoteException, ValidationException, LibraryException;
    boolean changePassword(String token, ChangePasswordRequestDTO request) throws RemoteException, ValidationException, LibraryException;
    List<BookDTO> getAllBooks(String token) throws RemoteException, LibraryException;
    PageResponseDTO<BookDTO> searchBooks(String token, BookSearchCriteriaDTO criteria) throws RemoteException, LibraryException;
    BookDTO getBook(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException;
    Long createBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean updateBook(String token, BookDTO book) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean deleteBook(String token, Long id) throws RemoteException, AuthorizationException, LibraryException;
    List<CategoryDTO> getAllCategories(String token) throws RemoteException, LibraryException;
    List<AuthorDTO> getAllAuthors(String token) throws RemoteException, LibraryException;
    List<StudentDTO> getAllStudents(String token) throws RemoteException, AuthorizationException, LibraryException;
    StudentDTO getStudent(String token, Long id) throws RemoteException, EntityNotFoundException, LibraryException;
    StudentDTO getStudentByCode(String token, String studentCode) throws RemoteException, EntityNotFoundException, LibraryException;
    Long createStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean updateStudent(String token, StudentDTO student) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean deleteStudent(String token, DeleteStudentRequestDTO request) throws RemoteException, AuthorizationException, LibraryException;
    String generateActivationCode(String token, Long studentId) throws RemoteException, AuthorizationException, LibraryException;
    BorrowRecordDTO borrowBook(String token, BorrowRequestDTO request) throws RemoteException, LibraryException;
    ReturnResultDTO returnBook(String token, Long studentId, Long bookId) throws RemoteException, LibraryException;
    List<BorrowRecordDTO> getAllBorrowRecords(String token) throws RemoteException, AuthorizationException, LibraryException;
    List<BorrowRecordDTO> getStudentActiveBorrows(String token, Long studentId) throws RemoteException, LibraryException;
    List<FineDTO> getFines(String token, Long studentId) throws RemoteException, LibraryException;
    boolean payFine(String token, Long fineId) throws RemoteException, AuthorizationException, LibraryException;
    ReservationDTO createReservation(String token, Long studentId, Long bookId) throws RemoteException, LibraryException;
    boolean cancelReservation(String token, Long reservationId) throws RemoteException, LibraryException;
    List<ReservationDTO> getReservations(String token, Long studentId) throws RemoteException, LibraryException;
    DashboardMetricsDTO getDashboardMetrics(String token) throws RemoteException, AuthorizationException, LibraryException;
    StudentDashboardDTO getStudentDashboard(String token) throws RemoteException, LibraryException;
    List<String> getSearchSuggestions(String token, SearchSuggestionRequestDTO request) throws RemoteException, LibraryException;
    List<AuditLogDTO> getRecentAuditLogs(String token, int limit) throws RemoteException, AuthorizationException, LibraryException;
    List<UserDTO> getAllUsers(String token) throws RemoteException, AuthorizationException, LibraryException;
    Long createUser(String token, UserDTO user, String initialPassword) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean updateUser(String token, UserDTO user) throws RemoteException, AuthorizationException, ValidationException, LibraryException;
    boolean resetUserPassword(String token, Long userId, String newPassword) throws RemoteException, AuthorizationException, LibraryException;
    Response execute(Request request) throws RemoteException;
}
```

---

## 7. RMI Registry & Server Bootstrap
* Khởi tạo Registry: `LocateRegistry.createRegistry(1099)`.
* Đăng ký đối tượng: `registry.rebind("LibraryRemoteService", remoteService)`.
* Quản lý vòng đời: Có hook shutdown tự động unbind, unexport đối tượng và đóng connection pool HikariCP.
* Khả năng lắng nghe: Lắng nghe trên `0.0.0.0`, tự động quét và in ra toàn bộ IPv4 LAN khi khởi động.

---

## 8. Serialization
* Toàn bộ 21 DTO trong `thuvien.common.dto` đều `implements Serializable` và có `private static final long serialVersionUID = 1L`.
* Các envelope `Request`, `Response` đều `implements Serializable`.
* Toàn bộ các Exception (`LibraryException`, `AuthenticationException`, `BookUnavailableException`, ...) đều có `serialVersionUID`.
* Không có bất kỳ resource server-side nào (`Connection`, `DataSource`, `Repository`, `Thread`, `Socket`) bị rò rỉ hoặc serialize sang client.

---

## 9. Authentication & Session
* Giữ nguyên cơ chế bảo mật PBKDF2 with SHA-256 + salt.
* Client gọi `login()` nhận `UserSessionDTO` chứa `token` (UUID).
* Các cuộc gọi tiếp theo truyền kèm `token` qua tham số hoặc tự động đính kèm qua `ClientSession`.
* Server kiểm tra token hợp lệ, kiểm tra phân quyền (RBAC: `ADMIN`, `LIBRARIAN`, `STUDENT`) trên từng phương thức.
* Tính năng lockout sau 5 lần đăng nhập sai và cơ chế revoke session ngay lập tức vẫn hoạt động 100%.

---

## 10. Concurrency khi mượn sách (Yêu cầu cốt lõi)
* Nghiệp vụ mượn sách được bảo vệ bằng transaction cô lập cấp database:
  ```sql
  UPDATE books
  SET available_copies = available_copies - 1,
      status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END
  WHERE id = ? AND available_copies > 0;
  ```
* Khi 2 client đồng thời gửi request mượn cuốn sách cuối cùng (`available_copies = 1`):
  1. Chỉ một transaction thực hiện `UPDATE` thành công (`rows affected = 1`).
  2. Transaction còn lại nhận `rows affected = 0`, bị rollback toàn bộ và ném ngoại lệ `BookUnavailableException`.
  3. Giá trị `available_copies` không bao giờ bị âm.
  4. Đã được kiểm chứng thực tế trong test case `RMIEndToEndIntegrationTest.test13_ConcurrentBorrowSameBookOverRMI`.

---

## 11. Kết quả kiểm thử tự động (Test Results)
Chạy lệnh `ant clean compile test`:
```text
thuvien.common.ProtocolTest: tests=2, failures=0, errors=0, skipped=0
thuvien.server.database.MySQLSmokeIntegrationTest: tests=4, failures=0, errors=0, skipped=0
thuvien.server.network.TCPEndToEndIntegrationTest: tests=11, failures=0, errors=0, skipped=0
thuvien.server.repository.RepositoryArchitectureTest: tests=3, failures=0, errors=0, skipped=0
thuvien.server.RequestRouterTest: tests=24, failures=0, errors=0, skipped=0
thuvien.server.rmi.RMIEndToEndIntegrationTest: tests=15, failures=0, errors=0, skipped=0
thuvien.server.service.AuthServiceTest: tests=5, failures=0, errors=0, skipped=0
thuvien.server.service.BorrowConcurrencyIntegrationTest: tests=3, failures=0, errors=0, skipped=0
thuvien.server.service.FineServiceTest: tests=3, failures=0, errors=0, skipped=0
thuvien.server.service.SecurityAndFeatureHardeningTest: tests=29, failures=0, errors=0, skipped=0
--------------------------------------------------
TOTAL: tests=99, failures=0, errors=0, skipped=0
BUILD SUCCESSFUL
```
Tất cả **99/99 tests** đều **PASS**, 0 failure, 0 error.

---

## 12. Packaging & Kiểm tra phân phối
Chạy lệnh `ant package-all`:
* `dist/server-dist/server.jar`: Chứa `Main-Class: thuvien.server.rmi.LibraryRMIServer`, thư viện MySQL JDBC, HikariCP, file cấu hình `server.properties`, `db.properties`, và script `run-server.bat`. Không chứa mã GUI client.
* `dist/client-dist/client.jar`: Chứa `Main-Class: thuvien.client.ClientMain`, chỉ gồm mã client và common. Hoàn toàn **không** chứa JDBC driver, HikariCP, `db.properties`, `server.properties` hay bất kỳ class Server/Repository nào.
* Đã chạy kiểm thử thực tế từ distribution độc lập (`dist/client-dist` gọi `dist/server-dist`):
  - Ping RMI: `true`
  - Đăng nhập RMI: `librarian1`
  - Tìm kiếm sách RMI: 5 cuốn
  - Mượn sách RMI (bookId=5): record #315, số lượng giảm `6 -> 5`
  - Trả sách RMI (bookId=5): hoàn trả thành công, số lượng phục hồi `5 -> 6`
  - Đăng xuất RMI: session cleared thành công.

---

## 13. Hướng dẫn chạy Server
1. Đảm bảo MySQL đang chạy trên máy chủ (port 3306).
2. Vào thư mục `dist/server-dist/` và chạy:
   ```cmd
   run-server.bat
   ```
3. Server sẽ khởi tạo RMI Registry trên port `1099`, bind service `LibraryRemoteService` và in danh sách địa chỉ IPv4 LAN.

---

## 14. Hướng dẫn chạy Client
1. Vào thư mục `dist/client-dist/`.
2. Chạy:
   ```cmd
   run-client.bat
   ```
   hoặc truyền trực tiếp IP Server:
   ```cmd
   run-client.bat 192.168.1.100 1099
   ```
3. Trên màn hình Đăng nhập:
   - Nhập Server IP.
   - Nhập Port: `1099`.
   - Nhấn **"Kiểm tra kết nối"** để ping qua RMI.
   - Nhập tài khoản/mật khẩu và nhấn **"Đăng nhập"**.

---

## 15. Hướng dẫn kết nối hai máy qua Wi-Fi/LAN

### Nguyên nhân cốt lõi khiến RMI thường thất bại giữa 2 máy:
1. **Dynamic Remote Object Port**: Mặc định `UnicastRemoteObject` dùng port 0 (ngẫu nhiên). Khi Client lookup thành công ở 1099, việc gọi remote method sẽ gọi tới port ngẫu nhiên này và bị Firewall máy Server chặn.
   -> **Giải pháp**: Cố định Remote Object trên cổng **TCP 1100**.
2. **Sai IP trong Stub (`java.rmi.server.hostname`)**: Nếu Server không cấu hình hostname trước khi export object, stub sẽ chứa `127.0.0.1` hoặc hostname nội bộ khiến máy khác không thể kết nối.
   -> **Giải pháp**: Tự động nhận diện IPv4 LAN thực tế và gán `System.setProperty("java.rmi.server.hostname", serverIp)`.

### Các bước triển khai:
1. **Máy Server**:
   - Mở PowerShell quyền Administrator, chạy lệnh mở Firewall cho cả 2 cổng 1099 và 1100:
     ```powershell
     New-NetFirewallRule -DisplayName "School Library RMI Registry" -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow
     New-NetFirewallRule -DisplayName "School Library RMI Object" -Direction Inbound -LocalPort 1100 -Protocol TCP -Action Allow
     ```
   - Chạy server: `run-server.bat`. Server sẽ in thông tin:
     ```text
     RMI Server started
     RMI Registry port: 1099
     RMI Remote Object port: 1100
     RMI hostname: 192.168.1.100
     Service: LibraryRemoteService
     ```
2. **Máy Client**:
   - Kết nối cùng mạng Wi-Fi/LAN với Server.
   - Kiểm tra kết nối 2 cổng từ máy Client bằng script:
     ```cmd
     dist\client-dist\test-connection.bat 192.168.1.100
     ```
     Hoặc trong PowerShell:
     ```powershell
     Test-NetConnection -ComputerName 192.168.1.100 -Port 1099
     Test-NetConnection -ComputerName 192.168.1.100 -Port 1100
     ```
   - Khởi động Client:
     ```cmd
     dist\client-dist\run-client.bat 192.168.1.100 1099
     ```
   - Bấm **"Kiểm tra kết nối"** -> nhận thông báo `"Server connected"`.
   - Đăng nhập và tra cứu/mượn/trả sách qua Java RMI.

