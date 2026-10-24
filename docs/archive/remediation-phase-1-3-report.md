# Remediation Report — Phase 1.3: Migrate to RMI-Only Architecture

**Dự án**: Remote School Library Management System  
**Workspace**: `D:\workspace\thuvien`  
**Môi trường**: Windows 11, OpenJDK 1.8.0_462, Apache Ant (NetBeans 8.0.2 bundled)  
**Thời gian thực hiện**: 15/10/2026  
**Trạng thái Git**: Uncommitted changes preserved (Không commit, không push)

---

## 1. Mục tiêu và Phạm vi thực hiện

Hoàn tất việc loại bỏ toàn bộ giao thức TCP Socket cũ (cổng 9999), chuẩn hóa kiến trúc sang **Java RMI duy nhất** (RMI Registry: TCP 1099, RMI Remote Object: TCP 1100, Service Name: `LibraryRemoteService`).

Toàn bộ các thay đổi sửa lỗi và bảo vệ dữ liệu từ Phase 1 và Phase 1.1 (`RMISerializationFilterTest`, `CreateUserSecurityTest`, `BorrowRepositoryImpl`, `ReturnServiceImpl`, `UserServiceImpl`, `RequestRouter`) đều được bảo toàn nguyên vẹn 100%.

---

## 2. Kiểm tra phụ thuộc trước khi xóa (Dependency Audit Verification)

Trước khi thực hiện xóa hoặc sửa đổi bất kỳ tệp nào, hệ thống đã kiểm tra toàn bộ mã nguồn để đảm bảo không có bất kỳ caller hay chức năng nghiệp vụ nào phụ thuộc vào các tệp TCP cũ:

1. **`server/src/thuvien/server/ServerMain.java`**:
   - *Phụ thuộc đã kiểm tra*: Không có class nào trong dự án gọi tới `ServerMain`. Target Ant `run-server` và cấu hình NetBeans `nbproject/project.properties` (`main.class`) đã được trỏ sang `thuvien.server.rmi.LibraryRMIServer`.
   - *Đánh giá*: Tệp này chỉ đóng vai trò là entrypoint socket TCP cũ gây rủi ro khởi chạy nhầm cổng 9999 khi nhấn Shift+F6 trong NetBeans. Đủ điều kiện xóa an toàn.

2. **`server/src/thuvien/server/network/LibraryServer.java`**:
   - *Phụ thuộc đã kiểm tra*: Chỉ được gọi duy nhất bởi `ServerMain.java` (đã xóa) và `TCPEndToEndIntegrationTest.java` (đã xóa). Không có thành phần RMI nào (`LibraryRMIServer`, `LibraryRemoteServiceImpl`) tham chiếu tới `LibraryServer`.
   - *Đánh giá*: Là lớp quản lý `ServerSocket` lắng nghe TCP 9999 và luồng xử lý socket. Đủ điều kiện xóa an toàn.

3. **`server/src/thuvien/server/network/ClientHandler.java`**:
   - *Phụ thuộc đã kiểm tra*: Chỉ được khởi tạo nội bộ trong vòng lặp kết nối socket của `LibraryServer.java`. Không có controller hoặc service nào phụ thuộc vào `ClientHandler`.
   - *Đánh giá*: Là worker đọc luồng bytes socket TCP và chuyển cho `RequestRouter`. Trong kiến trúc RMI, vai trò này do `LibraryRemoteServiceImpl` (UnicastRemoteObject) đảm nhiệm. Đủ điều kiện xóa an toàn.

4. **`client/src/thuvien/client/network/TCPNetworkClient.java`**:
   - *Phụ thuộc đã kiểm tra*: Toàn bộ giao diện Swing Client (`ClientMain`, `LoginForm`, `MainDashboardForm`) đều sử dụng `RMIClient` thực thi interface `NetworkClient`. Không có class giao diện hoặc controller nào khởi tạo `TCPNetworkClient`. Caller duy nhất là test tích hợp TCP cũ.
   - *Đánh giá*: Đủ điều kiện xóa an toàn.

5. **`test/thuvien/server/network/TCPEndToEndIntegrationTest.java`**:
   - *Phụ thuộc đã kiểm tra*: Suite kiểm thử tích hợp chuyên biệt cho socket TCP 9999 qua `LibraryServer` và `TCPNetworkClient`. Các kiểm thử chức năng giao tiếp qua mạng và phân quyền đã được bao phủ bởi các suite unit test và RMI test (`RMISerializationFilterTest`, `ProtocolTest`, `RequestRouterTest`, `RMIEndToEndIntegrationTest`).
   - *Đánh giá*: Đủ điều kiện xóa an toàn.

6. **Các lớp dùng chung (DTO, Protocol, Envelopes, RequestRouter)**:
   - Các lớp `thuvien.common.protocol.Request`, `Response`, `Action`, `StatusCode`, cũng như `RequestRouter` được giữ nguyên vẹn vì `LibraryRemoteService.execute(Request)` và toàn bộ 10 Controller phía Client sử dụng mô hình Request/Response envelope qua RMI.

---

## 3. Danh sách tệp đã xóa và đã sửa

### 3.1. Tệp đã xóa (5 tệp)

| STT | Tệp đã xóa | Lý do |
|:---|:---|:---|
| 1 | `server/src/thuvien/server/ServerMain.java` | Entrypoint TCP 9999 cũ, không còn caller trong hệ thống. |
| 2 | `server/src/thuvien/server/network/LibraryServer.java` | Socket listener TCP 9999 cũ, không còn caller. |
| 3 | `server/src/thuvien/server/network/ClientHandler.java` | Worker đọc socket TCP cũ, được thay thế hoàn toàn bởi `LibraryRemoteServiceImpl`. |
| 4 | `client/src/thuvien/client/network/TCPNetworkClient.java` | Client socket TCP cũ, toàn bộ client đã chuyển sang `RMIClient`. |
| 5 | `test/thuvien/server/network/TCPEndToEndIntegrationTest.java` | Test suite cho socket TCP cũ, đã được thay thế bởi các test RMI/unit test tương ứng. |

### 3.2. Tệp mã nguồn và tài nguyên đã sửa

| STT | Tệp đã sửa | Nội dung thay đổi |
|:---|:---|:---|
| 1 | `client/src/thuvien/client/network/NetworkClient.java` | Đổi giá trị mặc định của `getPort()` từ `9999` thành `1099` (cổng RMI Registry chuẩn). |
| 2 | `client/resources/client.properties` | Xóa các khóa alias legacy `server.host=127.0.0.1` và `server.port=1099`, chỉ giữ lại cấu hình RMI chuẩn (`rmi.server.host`, `rmi.registry.port`, `rmi.service.name`). |
| 3 | `server/resources/server.properties` | Xóa hoàn toàn các cấu hình TCP legacy (`server.port=9999`, `server.threadPoolSize=20`, `server.socketTimeout=30000`), chỉ giữ cấu hình RMI chuẩn (`rmi.registry.port=1099`, `rmi.service.port=1100`, `rmi.service.name=LibraryRemoteService`). |
| 4 | `client/src/thuvien/client/view/MainDashboardForm.java` | Cập nhật subtitle tiêu đề từ `Hệ thống Quản lý Thư viện - Remote School Library (TCP Socket / Java RMI)` thành `Hệ thống Quản lý Thư viện - Remote School Library (Java RMI)`. |
| 5-11 | Các Client Controller (`ClientAuditLogController`, `ClientBookController`, `ClientBorrowController`, `ClientFineController`, `ClientReservationController`, `ClientSearchSuggestionController`, `ClientStudentController`) | Cập nhật Javadoc mô tả từ `Communicates with LibraryServer over TCP` thành `Communicates with LibraryServer over Java RMI`. |
| 12 | `README.md` | Cập nhật mục giới hạn kiến trúc (mục 3) ghi rõ kiến trúc thuần RMI, cập nhật cây thư mục test (bỏ `TCPEndToEndIntegrationTest`), xóa các cảnh báo về `ServerMain.java` cổng 9999 trong hướng dẫn chạy NetBeans và console. |
| 13 | `docs/ARCHITECTURE.md` | Cập nhật toàn bộ tài liệu thiết kế kiến trúc từ TCP socket 8888/9999 sang Java RMI (Registry 1099, Remote Object 1100), cập nhật sơ đồ luồng dữ liệu, bảng trách nhiệm module và mô hình triển khai. |

---

## 4. Chuẩn hóa cấu hình RMI và Entrypoint

1. **Server Entrypoint**:
   - `nbproject/project.properties`: `main.class=thuvien.server.rmi.LibraryRMIServer`.
   - Phím tắt NetBeans **Run Project (F6)** khởi chạy `LibraryRMIServer` mở cổng Registry `1099` và Remote Object `1100`.
   - Target Ant `run-server` và `run-rmi-server` trong `build.xml` đều trỏ vào `thuvien.server.rmi.LibraryRMIServer`.

2. **Client Entrypoint**:
   - `thuvien.client.ClientMain` khởi tạo `RMIClient` adapter kết nối tới `LibraryRemoteService` qua Registry cổng `1099`.
   - Màn hình đăng nhập `LoginForm` hỗ trợ nhập địa chỉ IP LAN của máy chủ (ví dụ `192.168.1.50`) và cổng Registry (`1099`).

3. **Cổng mạng chuẩn hóa**:
   - **RMI Registry Port**: `1099` (`LocateRegistry.createRegistry(1099)`).
   - **RMI Remote Object Port**: `1100` (cổng cố định export `UnicastRemoteObject`, tránh cổng ngẫu nhiên bị Windows Firewall chặn).
   - **RMI Service Name**: `LibraryRemoteService`.

---

## 5. Kết quả Build và Đóng gói (Ant Build Verification)

Sử dụng Apache Ant 1.9.x đi kèm NetBeans 8.0.2 (`extide\ant\bin\ant.bat`):

### 5.1. `ant clean compile compile-test`
- **Exit Code**: `0` (BUILD SUCCESSFUL)
- **Kết quả biên dịch**:
  - `common/src`: 40 source files biên dịch thành công.
  - `server/src`: 51 source files biên dịch thành công (bao gồm RMI server, services, repositories).
  - `client/src`: 31 source files biên dịch thành công (ZERO JDBC/SQL dependencies).
  - `test`: 11 source files biên dịch thành công (không còn lỗi biên dịch nào do xóa các file TCP cũ).

### 5.2. `ant package-all`
- **Exit Code**: `0` (BUILD SUCCESSFUL)
- **Gói Server (`dist/server-dist/`)**:
  - `server.jar` (`Main-Class: thuvien.server.rmi.LibraryRMIServer`).
  - `lib/`: 4 JARs (`HikariCP-4.0.3.jar`, `mysql-connector-java-8.0.33.jar`, `slf4j-api-1.7.36.jar`, `slf4j-jdk14-1.7.36.jar`).
  - `resources/`: `server.properties`, `db.properties`, `db.properties.example`.
  - `run-server.bat`: Script khởi chạy server RMI chuẩn.
- **Gói Client (`dist/client-dist/`)**:
  - `client.jar` (`Main-Class: thuvien.client.ClientMain`).
  - `lib/`: Thư mục rỗng (hoàn toàn không chứa driver JDBC hoặc thư viện server).
  - `resources/`: `client.properties`.
  - `run-client.bat`: Script khởi chạy client Swing, hỗ trợ tham số CLI `run-client.bat <IP> <PORT>`.
  - `test-connection.bat`: Script chẩn đoán kết nối cổng 1099 và 1100.

---

## 6. Kết quả Kiểm thử An toàn (Safe Test Execution)

Theo quy tắc an toàn bắt buộc:
- **Tuyệt đối không chạy các test tích hợp làm biến đổi dữ liệu trên database `school_library` đang dùng**.
- Đã chạy đầy đủ toàn bộ các Unit Test an toàn trong bộ nhớ và test có cơ chế guard an toàn:

| STT | Tên Test Suite | Loại test | Exit Code | Tests | Pass | Fail | Errors | Skipped | Thời gian |
|:---|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.server.rmi.RMISerializationFilterTest` | Unit Test (JEP 290) | 0 | 5 | 5 | 0 | 0 | 0 | 0.276s |
| 2 | `thuvien.server.service.CreateUserSecurityTest` | Unit Test (Security RBAC) | 0 | 4 | 4 | 0 | 0 | 0 | 0.544s |
| 3 | `thuvien.common.ProtocolTest` | Unit Test (Serialization) | 0 | 2 | 2 | 0 | 0 | 0 | 0.248s |
| 4 | `thuvien.server.RequestRouterTest` | Unit Test (Routing & RBAC) | 0 | 24 | 24 | 0 | 0 | 0 | 0.453s |
| 5 | `thuvien.server.service.FineServiceTest` | Unit Test (Fine Calculation) | 0 | 3 | 3 | 0 | 0 | 0 | 0.048s |
| 6 | `thuvien.server.service.AuthServiceTest` | Unit Test (Auth & Rate Limit) | 0 | 5 | 5 | 0 | 0 | 0 | 0.368s |
| 7 | `thuvien.server.repository.RepositoryArchitectureTest` | Reflection Test (Layer Guard) | 0 | 3 | 3 | 0 | 0 | 0 | 0.058s |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | Concurrency Test (Safety Guard) | 0 | 4 | 0 | 0 | 0 | 4 (Safe Skip) | 0.247s |
| **Tổng** | **8 test suites đã xác minh** | | | **50** | **46** | **0** | **0** | **4** | **~2.2s** |

### 6.1. Chi tiết các test không chạy và lý do (Testing Gaps)

1. **`thuvien.server.rmi.RMIEndToEndIntegrationTest` (15 tests)**:
   - *Lý do không chạy*: Test này khởi động `LibraryRMIServer` cục bộ và thực hiện các thao tác ghi dữ liệu (mượn sách, trả sách, nộp phạt, đăng ký tài khoản) trực tiếp lên database được cấu hình trong `db.properties` (`school_library`). Theo yêu cầu an toàn bắt buộc, test chỉ được chạy khi có database thử nghiệm độc lập (`school_library_test`) để tránh làm biến dạng dữ liệu demo.
2. **`thuvien.server.database.MySQLSmokeIntegrationTest` (4 tests)** và **`thuvien.server.service.SecurityAndFeatureHardeningTest` (29 tests)**:
   - *Lý do không chạy*: Cả 2 suite đều kết nối vào database thực tế và thực hiện ghi các bản ghi người dùng / thay đổi trạng thái tài khoản. Đã được hoãn lại để bảo vệ database nghiệp vụ.
3. **`BorrowConcurrencyIntegrationTest` (4 tests skipped)**:
   - *Cơ chế an toàn hoạt động*: Guard `[SAFETY - DATA-01]` tự động phát hiện database đang kết nối là `school_library` và chủ động `Assume.assumeTrue(false)`, an toàn bỏ qua 4 test mà không ghi/xóa bất kỳ dữ liệu nào.

---

## 7. Rà soát lần cuối cổng 9999 và mã TCP

Thực hiện `grep` toàn bộ dự án với từ khóa `9999`:
- **Trong mã nguồn Java (`*.java`)**:
  - Không còn bất kỳ tham chiếu nào tới cổng mạng `9999`.
  - Kết quả duy nhất chứa số `9999` là giới hạn số lượng sách trong `BookFormDialog.java` (`new SpinnerNumberModel(5, 1, 9999, 1)`), đây là giá trị số lượng bản sao sách tối đa của thư viện, hoàn toàn không phải cổng mạng.
- **Trong tệp cấu hình (`*.properties`, `*.xml`)**:
  - Không còn `server.port=9999` hay bất kỳ tham chiếu cổng `9999` nào.
- **Trong tài liệu audit cũ (`docs/*.md`)**:
  - Các tài liệu audit lịch sử (`full-project-audit.md`, `tcp-rmi-dependency-audit.md`, `security-audit-report.md`, `netbeans-server-entrypoint-audit.md`) vẫn chứa từ khóa `9999` để phản ánh trung thực hiện trạng đã được kiểm toán trước đây. Các tài liệu này được bảo toàn nguyên vẹn theo quy tắc.

---

## 8. Các rủi ro còn lại (Residual Risks)

1. **Kiểm thử tích hợp RMI trên Database riêng**:
   - Cần thiết lập schema thử nghiệm riêng `school_library_test` (bằng cách chạy `schema.sql`, `indexes.sql`, `seed.sql` trên cơ sở dữ liệu `school_library_test`) và cấu hình `test.properties` hoặc biến môi trường trước khi chạy trọn vẹn `RMIEndToEndIntegrationTest` và `SecurityAndFeatureHardeningTest`.
2. **Kiểm thử kết nối qua mạng LAN vật lý**:
   - Mặc dù server RMI đã tự động cấu hình `java.rmi.server.hostname` theo địa chỉ IPv4 LAN và cung cấp các script mở firewall (`New-NetFirewallRule` cho cổng 1099 và 1100), việc kiểm thử thực tế giữa hai máy tính vật lý khác nhau qua Wi-Fi/LAN cần được thực hiện trực tiếp trong môi trường mạng thực tế của người dùng.
3. **Mã hóa đường truyền RMI**:
   - Giao tiếp Java RMI hiện tại chạy qua socket TCP chuẩn không bật SSL/TLS (`SslRMIClientSocketFactory`/`SslRMIServerSocketFactory`). Cần đảm bảo chỉ chạy thử nghiệm trên mạng LAN riêng biệt, có kiểm soát, tránh các mạng công cộng không đáng tin cậy.

---

## 9. Bằng chứng Trạng thái Git (Git Verification Commands)

### 9.1. `git status --short --branch`
```text
## main...origin/main
 M README.md
 M build.xml
 M client/resources/client.properties
 M client/src/thuvien/client/controller/ClientAuditLogController.java
 M client/src/thuvien/client/controller/ClientBookController.java
 M client/src/thuvien/client/controller/ClientBorrowController.java
 M client/src/thuvien/client/controller/ClientFineController.java
 M client/src/thuvien/client/controller/ClientReservationController.java
 M client/src/thuvien/client/controller/ClientSearchSuggestionController.java
 M client/src/thuvien/client/controller/ClientStudentController.java
 M client/src/thuvien/client/network/NetworkClient.java
D  client/src/thuvien/client/network/TCPNetworkClient.java
 M client/src/thuvien/client/view/MainDashboardForm.java
 M docs/ARCHITECTURE.md
 M nbproject/project.properties
 M server/resources/server.properties
D  server/src/thuvien/server/ServerMain.java
D  server/src/thuvien/server/network/ClientHandler.java
D  server/src/thuvien/server/network/LibraryServer.java
 M server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java
 M server/src/thuvien/server/rmi/LibraryRMIServer.java
 M server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java
 M server/src/thuvien/server/router/RequestRouter.java
 M server/src/thuvien/server/service/impl/ReturnServiceImpl.java
 M server/src/thuvien/server/service/impl/UserServiceImpl.java
D  test/thuvien/server/network/TCPEndToEndIntegrationTest.java
 M test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java
?? common/src/thuvien/common/dto/CreateUserRequestDTO.java
?? docs/full-project-audit.md
?? docs/netbeans-server-entrypoint-audit.md
?? docs/remediation-phase-1-report.md
?? docs/remediation-phase-1-verification.md
?? docs/remediation-phase-1-3-report.md
?? docs/tcp-rmi-dependency-audit.md
?? test/thuvien/server/rmi/RMISerializationFilterTest.java
?? test/thuvien/server/service/CreateUserSecurityTest.java
```

### 9.2. `git diff --check`
```text
(Exit code 0 - Không có khoảng trắng thừa, merge conflict markers hay lỗi cú pháp)
```

### 9.3. `git diff --stat`
```text
 README.md                                          | 101 ++++--
 build.xml                                          |  13 +
 client/resources/client.properties                 |   4 -
 .../controller/ClientAuditLogController.java       |   2 +-
 .../client/controller/ClientBookController.java    |   2 +-
 .../client/controller/ClientBorrowController.java  |   2 +-
 .../client/controller/ClientFineController.java    |   2 +-
 .../controller/ClientReservationController.java    |   2 +-
 .../ClientSearchSuggestionController.java          |   2 +-
 .../client/controller/ClientStudentController.java |   2 +-
 .../src/thuvien/client/network/NetworkClient.java  |   2 +-
 .../src/thuvien/client/view/MainDashboardForm.java |   2 +-
 docs/ARCHITECTURE.md                               |  59 +--
 nbproject/project.properties                       |   2 +-
 server/resources/server.properties                 |   5 -
 .../repository/impl/BorrowRepositoryImpl.java      |   2 +-
 .../src/thuvien/server/rmi/LibraryRMIServer.java   |  33 ++
 .../server/rmi/LibraryRemoteServiceImpl.java       |   9 +-
 .../src/thuvien/server/router/RequestRouter.java   |  31 +-
 .../server/service/impl/ReturnServiceImpl.java     |   2 +-
 .../server/service/impl/UserServiceImpl.java       |  10 +-
 .../service/BorrowConcurrencyIntegrationTest.java  | 397 +++++++++++++++------
 22 files changed, 498 insertions(+), 188 deletions(-)
```

---

## 10. Kết luận

Giai đoạn **Phase 1.3 — Migrate to RMI-Only Architecture** đã hoàn thành đạt 100% mục tiêu đề ra:
- Toàn bộ 5 tệp giao thức TCP legacy trên cổng 9999 đã được loại bỏ an toàn.
- Cấu hình server và client đã được tinh gọn và chuẩn hóa về Java RMI (Registry 1099, Remote Object 1100, Service `LibraryRemoteService`).
- NetBeans entrypoint và các target Ant đều thống nhất chạy `thuvien.server.rmi.LibraryRMIServer`.
- Giao diện và tài liệu thiết kế đã được cập nhật mô tả Java RMI là giao thức mạng duy nhất.
- Tất cả unit test an toàn đều vượt qua 100%, bảo toàn toàn bộ mã nguồn và dữ liệu database nghiệp vụ mà không thực hiện git commit hay git push.
