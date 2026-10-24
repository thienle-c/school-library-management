# BÁO CÁO KIỂM TOÁN PHỤ THUỘC GIAO THỨC TCP VÀ JAVA RMI (PHASE 1.2 AUDIT)
## Remote School Library Management System — Workspace: `D:\workspace\thuvien`

---

* **Ngày thực hiện kiểm toán:** 15/10/2026
* **Chế độ kiểm toán:** **READ-ONLY AUDIT** (Không sửa mã nguồn, không xóa/đổi tên file, không commit/push, không chạy test phá hủy dữ liệu)
* **Môi trường đối chiếu:** OpenJDK 8 (Amazon Corretto 8.504.01.1), Apache Ant 1.9.4 (NetBeans 8.0.2), MySQL 8.0 (Laragon)
* **Tài liệu tham chiếu:**
  - `docs/full-project-audit.md`
  - `docs/remediation-phase-1-report.md`
  - `docs/remediation-phase-1-verification.md`
  - `docs/netbeans-server-entrypoint-audit.md`
  - `docs/RMI_MIGRATION_REPORT.md`

---

## 1. BẢO TOÀN TRẠNG THÁI TRƯỚC VÀ SAU KIỂM TOÁN

Kết quả thực thi các lệnh kiểm tra trạng thái ban đầu:
- `git status --short --branch`:
  ```text
  ## main...origin/main
   M README.md
   M build.xml
   M nbproject/project.properties
   M server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java
   M server/src/thuvien/server/rmi/LibraryRMIServer.java
   M server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java
   M server/src/thuvien/server/router/RequestRouter.java
   M server/src/thuvien/server/service/impl/ReturnServiceImpl.java
   M server/src/thuvien/server/service/impl/UserServiceImpl.java
   M test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java
  ?? common/src/thuvien/common/dto/CreateUserRequestDTO.java
  ?? docs/full-project-audit.md
  ?? docs/netbeans-server-entrypoint-audit.md
  ?? docs/remediation-phase-1-report.md
  ?? docs/remediation-phase-1-verification.md
  ?? test/thuvien/server/rmi/RMISerializationFilterTest.java
  ?? test/thuvien/server/service/CreateUserSecurityTest.java
  ```
- `git diff --check`: Exit code `0` (Không có lỗi khoảng trắng, ký tự điều khiển hay xung đột).
- `git diff --stat`: 10 files modified (+458, -141 dòng), toàn bộ thay đổi từ Phase 1 được bảo toàn nguyên trạng.

---

## 2. TỔNG QUAN KIẾN TRÚC HIỆN TẠI (CURRENT ARCHITECTURAL REALITY)

Dự án hiện đang tồn tại **song song hai kiến trúc mạng**:

```text
               ┌───────────────────────────────────────────────────────────────┐
               │                     CLIENT TIER (Swing GUI)                   │
               │  LoginForm, MainDashboardForm, BookPanel, BorrowPanel, ...    │
               │                 (Tất cả sử dụng NetworkClient)                 │
               └───────────────────────────────┬───────────────────────────────┘
                                               │
                                 (Đang cắm qua RMIClient)
                                               │
                                               ▼
                         ┌───────────────────────────────────────────┐
                         │   thuvien.client.network.RMIClient        │
                         │   (Lookup Registry 1099, gọi Object 1100) │
                         └─────────────────────┬─────────────────────┘
                                               │
                        Java RMI (TCP 1100)    │  service.execute(Request)
                                               ▼
                         ┌───────────────────────────────────────────┐
                         │ thuvien.server.rmi.LibraryRMIServer       │
                         │   └─ LibraryRemoteServiceImpl (port 1100) │
                         └─────────────────────┬─────────────────────┘
                                               │
                                               │ router.route(req)
                                               ▼
                         ┌───────────────────────────────────────────┐
                         │       RequestRouter (Command Dispatcher)  │
                         └─────────────────────┬─────────────────────┘
                                               │
                                               ▼
                         ┌───────────────────────────────────────────┐
                         │     Service Layer & HikariCP Repository   │
                         └───────────────────────────────────────────┘
```

```text
 [NHÁNH TCP CŨ CÒN TỒN TẠI TRONG MÃ NGUỒN NHƯNG BỊ CÔ LẬP KHỎI CLIENT CHÍNH]:
  thuvien.client.network.TCPNetworkClient (port 9999) ──[KHÔNG CÓ CALLER TRONG CLIENT]
                                                              │
                                                        (Socket TCP 9999)
                                                              │
                                                              ▼
  ServerMain.java (chạy khi Shift+F6) ──> LibraryServer (port 9999) ──> ClientHandler ──> RequestRouter
```

* **Thực tế vận hành:** Toàn bộ giao diện người dùng Swing của máy khách (`ClientMain`, `LoginForm`, `MainDashboardForm`, và 10 controllers) **100% đang hoạt động qua `RMIClient`**.
* **Nhánh TCP cũ:** Bao gồm `ServerMain`, `LibraryServer`, `ClientHandler`, `TCPNetworkClient` và test suite `TCPEndToEndIntegrationTest`. Nhánh này hoàn toàn độc lập, không còn bất kỳ màn hình nào của Client kết nối tới cổng 9999 trong luồng sử dụng thông thường.

---

## 3. KIỂM KÊ CHI TIẾT CÁC THÀNH PHẦN TCP CŨ (LEGACY TCP INVENTORY)

| STT | Thành phần / Tệp nguồn | Lớp / Phương thức liên quan | Chức năng chi tiết | Caller thực tế trong dự án | Tình trạng sử dụng hiện tại |
|:---:|---|---|---|---|---|
| 1 | `server/src/thuvien/server/ServerMain.java` | `main(String[] args)` | Điểm khởi chạy của máy chủ TCP cũ. Đọc `server.properties`, khởi tạo `LibraryServer` trên cổng 9999, in banner cổng 9999. | NetBeans **Run File (Shift+F6)** trên chính file này; tài liệu cũ. | **LEGACY / RUNTIME RISK**: Nếu người dùng ấn Shift+F6 vào file này, máy chủ sẽ lắng nghe cổng 9999, client RMI không thể kết nối. |
| 2 | `server/src/thuvien/server/network/LibraryServer.java` | `ServerSocket serverSocket`, `listen()`, `start()`, `stop()` | Tạo `ServerSocket(port)` lắng nghe kết nối socket TCP. Tạo `ClientHandler` cho mỗi kết nối và ném vào `ExecutorService`. | `ServerMain.java:89`<br>`TCPEndToEndIntegrationTest.java:74` | **DEAD IN PROD**: Chỉ được gọi bởi `ServerMain` và integration test cũ. |
| 3 | `server/src/thuvien/server/network/ClientHandler.java` | `run()`, `ObjectInputStream`, `ObjectOutputStream` | Luồng xử lý một kết nối Socket client TCP. Đọc `Request` từ stream, chuyển cho `RequestRouter.route()`, ghi trả `Response`. | `LibraryServer.java:55` | **DEAD IN PROD**: Chỉ phục vụ `LibraryServer`. |
| 4 | `client/src/thuvien/client/network/TCPNetworkClient.java` | `connect()`, `Socket socket`, `send(Request)` | Hiện thực interface `NetworkClient` bằng raw Java Socket và `ObjectOutputStream`/`ObjectInputStream` tới cổng 9999. | `TCPEndToEndIntegrationTest.java` (dòng 113, 131, 203, ...) | **DEAD IN CLIENT**: Toàn bộ UI client (`ClientMain`, `LoginForm`, `MainDashboardForm`) đã chuyển sang dùng `RMIClient`. Không có class giao diện nào khởi tạo `TCPNetworkClient`. |
| 5 | `server/resources/server.properties` | `server.port=9999`, `server.threadPoolSize=20`, `server.socketTimeout=30000` | Khai báo tham số cấu hình cho máy chủ TCP socket. | `ServerMain.java:42` | **LEGACY CONFIG**: Chỉ có `ServerMain` đọc các khóa này. |
| 6 | `client/src/thuvien/client/network/NetworkClient.java` | `default int getPort() { return 9999; }` | Interface định nghĩa phương thức kết nối client. Có phương thức mặc định trả về cổng 9999. | `LoginForm.java:46` (đọc hiển thị lên UI nếu không cấu hình) | **REFACTOR NEEDED**: Giá trị mặc định 9999 gây hiểu nhầm, cần đổi thành 1099. |
| 7 | `test/thuvien/server/network/TCPEndToEndIntegrationTest.java` | Toàn bộ 846 dòng | Bộ kiểm thử tích hợp kiểm tra toàn bộ chu trình TCP socket (Login, Ping, Search, Borrow, Return) trên cổng test 8889. | Ant test runner (`ant test` hoặc `test-single`) | **TEST ONLY**: Bộ test duy nhất duy trì sự phụ thuộc vào `LibraryServer` và `TCPNetworkClient`. |

---

## 4. KIỂM KÊ CHI TIẾT CÁC THÀNH PHẦN JAVA RMI (JAVA RMI INVENTORY)

| STT | Thành phần / Tệp nguồn | Lớp / Phương thức liên quan | Chức năng chi tiết | Caller / Thành phần phụ thuộc |
|:---:|---|---|---|---|
| 1 | `server/src/thuvien/server/rmi/LibraryRMIServer.java` | `main(String[] args)`, `start()`, `configureSerializationFilter()` | Entrypoint máy chủ chính của hệ thống. Kích hoạt JEP 290 filter, phân giải IPv4 LAN tự động, bật Registry trên port 1099, export Remote Object trên port 1100, bind `"LibraryRemoteService"`. | NetBeans **F6 (Run Project)** qua `nbproject/project.properties`; Ant target `run-server` và `run-rmi-server`; script `dist/server-dist/run-server.bat`. |
| 2 | `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java` | `UnicastRemoteObject`, `execute(Request)`, 30+ typed remote methods | Triển khai của `LibraryRemoteService`. Nhận các lệnh gọi hàm từ xa trên cổng 1100, ủy quyền cho `RequestRouter.route()` và các Services. | Được `LibraryRMIServer` khởi tạo và bind vào Registry; được `RMIClient` gọi qua stub. |
| 3 | `common/src/thuvien/common/rmi/LibraryRemoteService.java` | Interface kế thừa `java.rmi.Remote` | Hợp đồng giao tiếp từ xa RMI. Chứa `String ping()`, `Response execute(Request)` và các phương thức nghiệp vụ mượn, trả, tìm sách, xác thực. | Dùng chung cho Client (`RMIClient`) và Server (`LibraryRemoteServiceImpl`). |
| 4 | `client/src/thuvien/client/network/RMIClient.java` | `connect()`, `getRemoteService()`, `send(Request)`, `ping()` | Hiện thực `NetworkClient` bằng RMI stub lookup (`LocateRegistry.getRegistry(host, port)`). Tự động chèn session token vào request và gọi `remoteService.execute(request)`. | `ClientMain.java`, `LoginForm.java`, `MainDashboardForm.java`, tất cả 10 client controllers. |
| 5 | `client/src/thuvien/client/ClientMain.java` | `main(String[] args)` | Điểm khởi chạy của ứng dụng khách Swing. Đọc `client.properties`, khởi tạo `RMIClient(host, port, serviceName)` và hiển thị `LoginForm`. | NetBeans Shift+F6 trên `ClientMain.java`; Ant target `run-client` / `run-rmi-client`; script `dist/client-dist/run-client.bat`. |
| 6 | `client/resources/client.properties` | `rmi.server.host=127.0.0.1`, `rmi.registry.port=1099`, `rmi.service.name=LibraryRemoteService` | Cấu hình tham số kết nối RMI cho máy khách. | `ClientMain.java:29-35` |
| 7 | `server/resources/server.properties` | `rmi.registry.port=1099`, `rmi.service.port=1100`, `rmi.service.name=LibraryRemoteService` | Cấu hình tham số cổng Registry và Remote Object cho máy chủ RMI. | `LibraryRMIServer.java:292-304` |
| 8 | `scripts/test-rmi-ports.ps1` & `test-connection.bat` | Kiểm tra TCP handshake trên cổng 1099 và 1100 | Script chẩn đoán mạng cho người dùng máy khách kiểm tra tường lửa máy chủ trước khi mở ứng dụng. | Người dùng / Kỹ thuật viên triển khai LAN. |

---

## 5. BẢN ĐỒ GIAO TIẾP TỪNG CHỨC NĂNG GIAO DIỆN CLIENT

Đã kiểm tra mã nguồn của toàn bộ các Controller và Panel trong `client/src/thuvien/client`:

| Chức năng nghiệp vụ trên UI | Giao diện Swing / Controller | Action Protocol gửi đi | Giao thức mạng thực tế | Kênh Server tiếp nhận |
|---|---|---|:---:|---|
| **Đăng nhập hệ thống** | `LoginForm` -> `ClientAuthController` | `Action.LOGIN` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter.handleLogin`) |
| **Đăng xuất / Hủy phiên** | `MainDashboardForm` -> `ClientAuthController` | `Action.LOGOUT` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter.handleLogout`) |
| **Đổi mật khẩu** | `ChangePasswordDialog` -> `ClientAuthController` | `Action.CHANGE_PASSWORD` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter.handleChangePassword`) |
| **Đăng ký tài khoản sinh viên** | `StudentRegistrationDialog` -> `ClientAuthController` | `Action.REGISTER_STUDENT` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter.handleRegisterStudent`) |
| **Tra cứu & Quản lý sách** | `BookManagementPanel` -> `ClientBookController` | `LIST_BOOKS`, `SEARCH_BOOKS`, `CREATE_BOOK`, `UPDATE_BOOK`, `DELETE_BOOK` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter`) |
| **Hồ sơ sinh viên** | `StudentManagementPanel` -> `ClientStudentController` | `LIST_STUDENTS`, `GET_STUDENT`, `CREATE_STUDENT`, `UPDATE_STUDENT`, `DELETE_STUDENT` | **Java RMI** | Cổng 1100 (`execute` -> `RequestRouter`) |
| **Mượn sách** | `BorrowManagementPanel` -> `ClientBorrowController` | `Action.BORROW_BOOK` | **Java RMI** | Cổng 1100 (`execute` -> `BorrowService.borrowBook`) |
| **Trả sách** | `BorrowManagementPanel` -> `ClientBorrowController` | `Action.RETURN_BOOK` | **Java RMI** | Cổng 1100 (`execute` -> `ReturnService.returnBook`) |
| **Quản lý & Nộp phạt** | `FineManagementPanel` -> `ClientFineController` | `LIST_FINES`, `PAY_FINE` | **Java RMI** | Cổng 1100 (`execute` -> `FineService`) |
| **Đặt trước sách** | `ReservationManagementPanel` -> `ClientReservationController` | `CREATE_RESERVATION`, `CANCEL_RESERVATION`, `LIST_RESERVATIONS` | **Java RMI** | Cổng 1100 (`execute` -> `ReservationService`) |
| **Xem báo cáo & Thống kê** | `DashboardOverviewPanel` -> `ClientDashboardController` | `Action.GET_DASHBOARD_METRICS` | **Java RMI** | Cổng 1100 (`execute` -> `DashboardService`) |
| **Xem nhật ký kiểm toán** | `AuditLogManagementPanel` -> `ClientAuditLogController` | `Action.LIST_AUDIT_LOGS` | **Java RMI** | Cổng 1100 (`execute` -> `AuditLogService`) |

👉 **Kết luận xác minh:** **100% chức năng nghiệp vụ của ứng dụng khách Swing đi qua Java RMI (Cổng 1100)**. Không có bất kỳ chức năng nào đi qua Socket TCP cổng 9999.

---

## 6. XÁC ĐỊNH ĐIỂM KHỞI CHẠY THỰC TẾ (NETBEANS / ANT / SCRIPTS)

### 6.1. Khi nhấn F6 (Run Project) trong NetBeans
- **Tệp cấu hình quy định:** `nbproject/project.properties` (dòng 60):
  ```properties
  main.class=thuvien.server.rmi.LibraryRMIServer
  ```
- **Hành vi thực tế:** NetBeans thực thi `LibraryRMIServer.main()`.
- **Đầu ra Console:**
  ```text
  Configured JEP 290 Serialization Filter: ...
  RMI Server started
  RMI Registry port: 1099
  RMI Remote Object port: 1100
  RMI hostname: 192.168.x.x
  Service: LibraryRemoteService
  ```

### 6.2. Tại sao máy chủ có thể vẫn in cổng 9999 khi khởi chạy?
- **Nguyên nhân gốc đã xác minh:**
  Trong NetBeans, nếu người dùng mở cây thư mục `server/src/thuvien/server`, nhấp chuột phải vào tệp [`ServerMain.java`](file:///D:/workspace/thuvien/server/src/thuvien/server/ServerMain.java) và chọn **Run File (Shift+F6)**:
  NetBeans sẽ bỏ qua `main.class` của dự án và chạy thẳng `ServerMain.main()`.
- Phương thức `ServerMain.printStartupBanner()` (dòng 110-111):
  ```java
  System.out.println("Server started");
  System.out.println("Port: " + port); // port = 9999 lấy từ server.properties
  ```
  Lúc này máy chủ khởi động lắng nghe cổng TCP 9999. Khi Client mở lên (kết nối cổng 1099/1100), Client sẽ báo lỗi kết nối vì cổng 1099 chưa được mở.

### 6.3. Khởi chạy qua Ant Targets (`build.xml`)
- `ant run-server` -> Chạy `thuvien.server.rmi.LibraryRMIServer` (RMI).
- `ant run-rmi-server` -> Chạy `thuvien.server.rmi.LibraryRMIServer` (RMI).
- `ant run-client` -> Chạy `thuvien.client.ClientMain` (RMI).
- `ant run-rmi-client` -> Chạy `thuvien.client.ClientMain` (RMI).
*Không có Ant target nào chạy `ServerMain`.*

### 6.4. Khởi chạy qua file đóng gói (`dist/`)
- `dist/server-dist/run-server.bat`:
  ```bat
  java -cp "resources;server.jar;lib/*" thuvien.server.rmi.LibraryRMIServer %*
  ```
- `dist/client-dist/run-client.bat`:
  ```bat
  java %JVM_ARGS% -cp "resources;client.jar;lib/*" thuvien.client.ClientMain %*
  ```
*Cả hai gói phân phối độc lập hoàn toàn chạy RMI, không chứa bất kỳ script nào chạy TCP socket.*

---

## 7. PHÂN LOẠI CHI TIẾT THEO 4 NHÓM QUYẾT ĐỊNH

### Nhóm 1: REMOVE CANDIDATE (Các tệp chỉ phục vụ TCP cũ, sẵn sàng loại bỏ)
1. **`server/src/thuvien/server/ServerMain.java`**:
   - *Lý do:* Điểm khởi chạy TCP cũ, là nguồn gốc gây nhầm lẫn khi người dùng bấm Run File (Shift+F6).
2. **`server/src/thuvien/server/network/LibraryServer.java`**:
   - *Lý do:* Lớp quản lý `ServerSocket` lắng nghe TCP 9999. Không có bất kỳ thành phần RMI nào gọi tới.
3. **`server/src/thuvien/server/network/ClientHandler.java`**:
   - *Lý do:* Worker thread của TCP `ServerSocket`. Không phục vụ RMI.
4. **`client/src/thuvien/client/network/TCPNetworkClient.java`**:
   - *Lý do:* Client socket TCP cũ. Hoàn toàn không có caller trong toàn bộ UI của Client.
5. **`test/thuvien/server/network/TCPEndToEndIntegrationTest.java`**:
   - *Lý do:* Bộ test chỉ dùng để kiểm tra 3 lớp TCP trên. Test này còn có rủi ro ghi đè dữ liệu mẫu trên database nếu không có cờ an toàn.

### Nhóm 2: KEEP (Các thành phần RMI và lõi chia sẻ bắt buộc giữ)
1. **`server/src/thuvien/server/rmi/LibraryRMIServer.java`**: Entrypoint máy chủ RMI.
2. **`server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java`**: Remote object implementation.
3. **`common/src/thuvien/common/rmi/LibraryRemoteService.java`**: RMI Remote Interface.
4. **`client/src/thuvien/client/network/RMIClient.java`**: Bộ điều hợp mạng RMI cho Client.
5. **`client/src/thuvien/client/ClientMain.java`**: Entrypoint client Swing.
6. **`client/src/thuvien/client/network/NetworkClient.java`**: Interface trừu tượng hóa tầng mạng cho các Controller.
7. **`common/src/thuvien/common/protocol/*` (`Request`, `Response`, `Action`, `StatusCode`)**:
   - *Lý do quan trọng:* Mặc dù ban đầu được thiết kế dạng envelop, nhưng hiện tại phương thức `Response execute(Request request)` của RMI và toàn bộ 10 Controller đều sử dụng cặp `Request`/`Response` để trao đổi dữ liệu qua RMI. `RequestRouter` cũng điều phối qua `Request`. **Bắt buộc phải giữ**.
8. **`server/src/thuvien/server/router/RequestRouter.java`**: Bộ điều phối lệnh trung tâm, được `LibraryRemoteServiceImpl` sử dụng.
9. **Toàn bộ DTO, Enums, Exceptions, Services, Repositories, DatabaseManager, Security**: Giữ nguyên.
10. **Toàn bộ Client View (Forms, Dialogs, Panels, Controllers)**: Giữ nguyên.

### Nhóm 3: REFACTOR (Cần tinh chỉnh dọn dẹp phụ thuộc ngầm)
1. **`client/src/thuvien/client/network/NetworkClient.java`**:
   - *Thay đổi cần làm:* Sửa dòng 21:
     ```java
     default int getPort() {
         return 1099; // Đổi từ 9999 sang 1099 (RMI Registry port)
     }
     ```
2. **`server/resources/server.properties`**:
   - *Thay đổi cần làm:* Xóa bỏ các dòng cấu hình TCP thừa:
     ```properties
     # XÓA:
     server.port=9999
     server.threadPoolSize=20
     server.socketTimeout=30000
     ```
     Chỉ giữ lại cấu hình RMI thuần túy (`rmi.registry.port`, `rmi.service.port`, `rmi.service.name`, `rmi.server.host`).
3. **`client/resources/client.properties`**:
   - *Thay đổi cần làm:* Dọn dẹp các alias cũ (`server.port=1099`, `server.host=127.0.0.1`), chuẩn hóa về các khóa `rmi.*`.
4. **`client/src/thuvien/client/view/MainDashboardForm.java`**:
   - *Thay đổi cần làm:* Sửa dòng 381:
     ```java
     // Đổi chuỗi:
     "Ứng Dụng Quản Lý Thư Viện Trường Học (Giao thức mạng TCP Socket / Java RMI)"
     // Thành:
     "Ứng Dụng Quản Lý Thư Viện Trường Học (Java RMI)"
     ```
5. **`AGENTS.md` & `docs/ARCHITECTURE.md`**:
   - *Thay đổi cần làm:* Cập nhật lại tài liệu kiến trúc dự án vì `AGENTS.md` vẫn còn ghi quy tắc cũ *"Networking Transport: Raw Java TCP Sockets on port 8888. No RMI"*.

### Nhóm 4: INVESTIGATE (Cần xem xét trước khi thực hiện)
1. **`test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java`**:
   - *Đánh giá:* Sau khi xóa `TCPEndToEndIntegrationTest.java`, bộ test này sẽ là bộ test tích hợp RMI chính. Tuy nhiên, test này hiện có đoạn `@BeforeClass` ghi đè mật khẩu của tài khoản `admin`, `librarian1`, `student1` và cập nhật sách ID 1.
   - *Hành động cần làm:* Trước khi kích hoạt test này thường xuyên, cần bổ sung cơ chế guard dữ liệu (`Assume.assumeTrue`) giống như đã làm với `BorrowConcurrencyIntegrationTest` để bảo vệ an toàn database phát triển.

---

## 8. ĐÁNH GIÁ KHẢ NĂNG CHẠY RMI QUA MẠNG LAN

Đã kiểm tra mã nguồn `LibraryRMIServer.java` và `RMIClient.java`:

### 8.1. Cấu hình cổng Registry và Remote Object
- **RMI Registry Port:** Cố định `1099` (TCP).
- **RMI Remote Object Port:** Cố định `1100` (TCP), cấu hình tường minh tại:
  ```java
  remoteService = new LibraryRemoteServiceImpl(servicePort, new RequestRouter());
  ```
  *Đánh giá:* Remote Object **không sử dụng cổng ngẫu nhiên (dynamic port/anonymous port)**. Điều này đáp ứng 100% tiêu chuẩn mở cổng tường lửa trên Windows Server/LAN.

### 8.2. Quảng bá địa chỉ IP cho máy khách từ xa (RMI Hostname Advertisement)
- Trong `LibraryRMIServer.java` (dòng 104-106):
  ```java
  activeHost = resolveServerHost(configuredHost);
  System.setProperty("java.rmi.server.hostname", activeHost);
  ```
- Phương thức `resolveServerHost()` duyệt qua danh sách card mạng vật lý:
  - Bỏ qua loopback (`127.0.0.1`), card ảo (VMware, VirtualBox, Hyper-V, WSL, veth).
  - Tự động lấy địa chỉ IPv4 `isSiteLocalAddress()` (dải `192.168.x.x` hoặc `10.x.x.x`).
  - Gán vào `java.rmi.server.hostname` **trước khi export Remote Object**.
- *Đánh giá:* RMI Stub gửi về Client sẽ chứa IP LAN thực của Server thay vì `localhost`. Client trên máy khác trong cùng mạng LAN/Wi-Fi có thể kết nối trực tiếp đến cổng 1100 mà không bị lỗi `UnknownHostException` hay treo kết nối.

### 8.3. Khả năng nhập IP linh hoạt phía Client
- Trong `LoginForm.java`: Giao diện có sẵn 2 trường `serverHostField` và `serverPortField`.
- Khi sinh viên hoặc thủ thư ngồi ở máy khác, chỉ cần nhập IP máy chủ (ví dụ `192.168.1.100`) và cổng `1099`, nhấn **Kiểm tra kết nối** là ứng dụng tự động kết nối qua RMI.
- Không bị hardcode cứng vào `localhost`.

### 8.4. Giới hạn bảo mật còn tồn tại trên LAN
- RMI hiện tại chạy trên giao thức nhị phân Java Serialization thuần qua TCP, **chưa tích hợp TLS/SSL** (`SslRMIClientSocketFactory`).
- Khuyến nghị: Sử dụng hệ thống trong mạng LAN nội bộ an toàn của trường học hoặc mạng Wi-Fi phòng lab có mật khẩu WPA2/WPA3.

---

## 9. KẾ HOẠCH VÀ THỨ TỰ CHUYỂN ĐỔI AN TOÀN SANG RMI DUY NHẤT

Khi được người dùng phê duyệt thực hiện chuyển đổi (giai đoạn sau), trình tự thực hiện an toàn như sau:

```text
BƯỚC 1: DỌN DẸP CẤU HÌNH & GIAO DIỆN (REFACTOR)
  ├─ Sửa NetworkClient.java: đổi getPort() mặc định từ 9999 -> 1099.
  ├─ Dọn server.properties & client.properties: xóa các khóa liên quan port 9999.
  └─ Sửa MainDashboardForm.java: bỏ chữ "TCP Socket" trên banner chào.

BƯỚC 2: BẢO VỆ DỮ LIỆU CỦA TEST RMI (HARDEN RMI TEST)
  └─ Bổ sung guard checkDatabaseAvailabilityAndIsolation vào RMIEndToEndIntegrationTest.java
     để đảm bảo không làm bẩn dữ liệu khi chạy ant test.

BƯỚC 3: XÓA CÁC THÀNH PHẦN TCP CŨ (DELETE CANDIDATES)
  ├─ Xóa server/src/thuvien/server/ServerMain.java
  ├─ Xóa server/src/thuvien/server/network/LibraryServer.java
  ├─ Xóa server/src/thuvien/server/network/ClientHandler.java
  ├─ Xóa client/src/thuvien/client/network/TCPNetworkClient.java
  └─ Xóa test/thuvien/server/network/TCPEndToEndIntegrationTest.java

BƯỚC 4: XÁC MINH BIÊN DỊCH VÀ ĐÓNG GÓI (VERIFY)
  ├─ Chạy ant compile compile-test (xác nhận 0 lỗi biên dịch, không còn class mồ côi).
  ├─ Chạy các unit test: ProtocolTest, RMISerializationFilterTest, CreateUserSecurityTest, RequestRouterTest.
  ├─ Chạy ant package-all (xác nhận server.jar và client.jar tạo thành công).
  └─ Kiểm tra khởi động F6 NetBeans và Run Client.
```

---

## 10. KẾT LUẬN KIỂM TOÁN

1. **Về tính phụ thuộc:** Dự án **đã hoàn tất chuyển đổi luồng nghiệp vụ sang Java RMI từ trước**. Các lớp TCP socket cũ (`ServerMain`, `LibraryServer`, `ClientHandler`, `TCPNetworkClient`) hiện là **mã nguồn dư thừa (dead code)**, chỉ còn tồn tại do lịch sử bài tập lớn và được tham chiếu bởi duy nhất một bộ test cũ `TCPEndToEndIntegrationTest`.
2. **Về tính an toàn khi xóa:** Việc loại bỏ các lớp TCP cũ hoàn toàn **không ảnh hưởng** đến bất kỳ màn hình nào của Client hay bất kỳ dịch vụ nghiệp vụ nào của Server.
3. **Về tính toàn vẹn:** Trong phiên kiểm toán này, **không có bất kỳ tệp mã nguồn hay cấu hình nào bị thay đổi**.
