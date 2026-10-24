# Remediation Report — Phase 1.7: Source-Level Security Audit and LAN Acceptance Test

**Dự án**: Remote School Library Management System  
**Workspace**: `D:\workspace\thuvien`  
**Môi trường**: Windows 11, OpenJDK 1.8.0_504 (Amazon Corretto 8 64-bit), MySQL 8.0.30 (Laragon on port 3306), Apache Ant 1.9.4 (NetBeans 8.0.2 bundled)  
**Thời gian thực hiện**: 15/10/2026  
**Trạng thái Git**: Uncommitted changes preserved (Không reset, không stash, không commit, không push)

---

## 1. Danh mục Tệp đã kiểm tra và Tệp đã sửa

### 1.1. Tệp đã kiểm tra (Inspected)
1. `server/src/thuvien/server/rmi/LibraryRMIServer.java`:
   - Kiểm tra `DEFAULT_SERIAL_FILTER`, `configureSerializationFilter()`, `resolveServerHost()`, `start()`, `stop()`.
2. `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java`:
   - Kiểm tra các phương thức Remote và điểm nhận serialized object từ client qua RMI.
3. `common/src/thuvien/common/rmi/LibraryRemoteService.java`:
   - Rà soát các kiểu dữ liệu và DTO tuần tự hóa.
4. `client/src/thuvien/client/network/RMIClient.java` & `NetworkClient.java`:
   - Kiểm tra logic kết nối Registry, lookup, Remote Object ping và cơ chế chẩn đoán lỗi mạng LAN.
5. `server/src/thuvien/server/security/PasswordHasher.java`:
   - Kiểm tra thuật toán băm mật khẩu, sinh salt, số vòng lặp, định dạng lưu trữ và kiểm tra mật khẩu.
6. `test/thuvien/server/TestDatabaseSafetyGuard.java`:
   - Rà soát toàn bộ logic chặn ghi đối với `school_library`, kiểm tra catalog và URL metadata.
7. `test/thuvien/server/TestDatabaseSafetyGuardTest.java`:
   - Kiểm tra 6 ca test độc lập bao phủ các trường hợp fail-closed của safety guard.
8. Các integration test:
   - `BorrowConcurrencyIntegrationTest.java`, `RMIEndToEndIntegrationTest.java`, `SecurityAndFeatureHardeningTest.java`.
9. Tài nguyên cấu hình và build:
   - `server/resources/server.properties`, `client/resources/client.properties`, `build.xml`, `nbproject/project.properties`.

### 1.2. Tệp đã sửa đổi trong Phase 1.7
- **Không sửa đổi thêm file mã nguồn hoặc file cấu hình nào trong Phase 1.7** (tuân thủ nguyên tắc: *"Make the smallest necessary changes; prefer findings and evidence over speculative refactoring"*). Toàn bộ các bản vá từ Phase 1.4–1.5 đã đạt chuẩn và được kiểm chứng 100%.

---

## 2. Kết quả Kiểm toán Nguồn (Source-Level Audits)

### 2.1. Task 1 — Audit RMI Serialization Filter
- **JDK Runtime**:
  - `OpenJDK 64-Bit Server VM Corretto-8.504.01.1 (build 1.8.0_504-b01, mixed mode)`.
  - Hỗ trợ JEP 290 đầy đủ thông qua `sun.misc.ObjectInputFilter`.
- **Cấu hình Filter hoạt động**:
  ```text
  maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;com.sun.proxy.**;!*
  ```
- **Các luồng Deserialization được bảo vệ**:
  1. *Client-to-Server Remote Calls*: Mọi tham số truyền vào các hàm RMI (`Request`, `LoginRequestDTO`, v.v.) được deserialize qua `ConnectionInputStream` và chịu sự kiểm soát của process-wide filter.
  2. *Server-to-Client Return Values*: Mọi giá trị trả về (`Response`, DTOs, collections) được lọc tương tự khi client nhận dữ liệu.
  3. *RMI Registry Lookup*: Việc unmarshal remote stub động từ Registry được kiểm duyệt chặt chẽ.
- **Đánh giá về `com.sun.proxy.**`**:
  - `com.sun.proxy` là namespace do `java.lang.reflect.Proxy` sinh ra tại runtime cho dynamic proxy stub của RMI.
  - Khi deserialize dynamic proxy, Java kiểm tra class proxy (`com.sun.proxy.$Proxy*`), **tất cả interface** mà proxy cài đặt, và **lớp `InvocationHandler`**.
  - Kiểm thử `testDynamicProxyWithUnauthorizedInterfaceBlocked` chứng minh: Nếu interface hoặc invocation handler nằm ngoài whitelist, JEP 290 vẫn từ chối với `InvalidClassException: filter status: REJECTED`.
  - *Giới hạn bảo mật*: Cấu hình này tin cậy các interface và handler trong allowlist (`thuvien.common.**`, `java.rmi.**`), chứ không ngăn chặn hoàn toàn việc tạo proxy.
- **Khả năng cài đặt lặp lại hoặc xung đột JVM**:
  - `LibraryRMIServer.configureSerializationFilter()` gọi `System.setProperty` và `Security.setProperty`. Gọi nhiều lần là an toàn (idempotent).
  - Nếu JVM khởi động với `-Djdk.serialFilter=...`, server tôn trọng cấu hình của JVM và không ghi đè nếu đã tồn tại giá trị hợp lệ.
- **Độ bao phủ của kiểm thử giới hạn tài nguyên**:
  - `maxdepth=15`: Được xác minh bởi `testResourceLimitGraphDepthExceeded()` (depth 20 bị REJECTED).
  - `maxarray=100000`: Được xác minh bởi `testResourceLimitArraySizeExceeded()` (mảng 100.005 phần tử bị REJECTED).
  - `maxbytes=10485760` (10 MB) và `maxrefs=50000`: Được cấu hình trong filter nhưng **chưa có test case riêng** tạo payload 10MB hoặc 50.001 references để tránh làm chậm Ant test và tốn bộ nhớ.

---

### 2.2. Task 2 — Audit Database Safety
- **Xác minh `TestDatabaseSafetyGuard.java`**:
  - `conn.getCatalog()` và `conn.getMetaData().getURL()` được kiểm tra trước mọi thao tác ghi dữ liệu.
  - Nếu catalog là `"school_library"`: Bị từ chối **vô điều kiện**, trả về `false`.
  - Không có bất kỳ System Property boolean nào (`test.database.enabled`, `test.isolated.db`) có thể vượt qua guard.
  - Fail-closed: Nếu connection null, closed, hoặc catalog rỗng/không khớp với URL, guard tự động từ chối.
- **Bảo vệ trong Lifecycle Hook (`@Before`, `@After`, Teardown)**:
  - `BorrowConcurrencyIntegrationTest`: `@After tearDown()` kiểm tra `if (!dbAvailable || !isIsolatedTestDb) return;`, đảm bảo không bao giờ gọi cleanup trên database chính.
  - `RMIEndToEndIntegrationTest`: `@BeforeClass` chỉ thực hiện reset bảng khi `dbAvailable && isIsolatedTestDb`.
  - `SecurityAndFeatureHardeningTest`: Tất cả 29 test case đều được bảo vệ bởi `requireIsolatedTestDb()`.
- **Hạn chế về Snapshot Hồi tố (Database Safety Limitations)**:
  - Trước Phase 1.4, dự án không tạo file dump/snapshot tự động của `school_library`. Do đó, **không thể đưa ra bằng chứng toán học hồi tố rằng database chính hoàn toàn không bị tác động trước thời điểm đó**.
  - Tuy nhiên, từ Phase 1.4 đến nay:
    - Bảng `school_library.audit_logs` cố định ở 4427 dòng (0 log mới).
    - Toàn bộ 756 audit log phát sinh khi chạy test đều ghi nhận trên `school_library_test`.
    - Số lượng `users` (5), `students` (4), `books` (5), `borrow_records` (2) trên `school_library` được bảo toàn nguyên vẹn.

---

### 2.3. Task 3 — Audit Password Hashing
Kiểm toán mã nguồn [PasswordHasher.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/PasswordHasher.java):

| Tiêu chí | Chi tiết kỹ thuật | Đánh giá an toàn |
|:---|:---|:---|
| **Thuật toán** | `PBKDF2WithHmacSHA256` | Đạt chuẩn NIST SP 800-132, OWASP, RFC 2898. |
| **Sinh Salt** | `new SecureRandom()`, 16 bytes (128 bits) ngẫu nhiên mỗi mật khẩu | Chống tấn công Rainbow Table tuyệt đối. |
| **Work Factor (Số vòng lặp)** | 10.000 iterations, độ dài key 256 bits (32 bytes) | Hợp lý cho Java 8 desktop/server; không gây nghẽn CPU. |
| **Định dạng lưu trữ** | `PBKDF2$<iterations>$<saltHex>$<hashHex>` | Tự chứa tham số (self-contained), thuận tiện nâng cấp số vòng lặp tương lai. |
| **Xác minh mật khẩu** | `MessageDigest.isEqual(expectedHash, computedHash)` | So sánh hằng số thời gian (Constant-time), chống tấn công Timing Attack. |
| **Tương thích ngược** | Fallback sang un-salted SHA-256 đối với mật khẩu cũ từ `seed.sql` | Cho phép đăng nhập bình thường và nâng cấp trong suốt qua `isLegacyHash()`. |

**Kết luận**: Thuật toán hiện tại an toàn, phù hợp cho hệ thống thư viện trường học, không làm mất dữ liệu hoặc reset tài khoản người dùng hiện có.

---

### 2.4. Task 4 — RMI Configuration and LAN Readiness

| Thành phần | Cấu hình đã xác minh | Ghi chú |
|:---|:---:|:---|
| **RMI Registry Port** | `1099` | Cấu hình trong `server.properties` |
| **Remote Object Port** | `1100` | Cố định qua `new LibraryRemoteServiceImpl(1100, ...)` |
| **Service Name** | `LibraryRemoteService` | Đồng bộ giữa Server, Client và Registry |
| **Quảng bá Hostname** | `10.0.107.143` | Tự động phát hiện IP Wi-Fi vật lý, không quảng bá `127.0.0.1` |

#### Phân biệt 3 Cấp độ Kiểm thử Mạng (Network Testing Categories):
- **Cấp độ A — Local loopback test (`127.0.0.1:1099`)**:
  - Client và Server chạy trên cùng máy, kết nối qua adapter ảo loopback.
  - *Ý nghĩa*: Xác nhận logic RMI, serialization và nghiệp vụ.
  - *Hạn chế*: Bỏ qua hoàn toàn card mạng vật lý, định tuyến LAN và tường lửa.
- **Cấp độ B — Same-machine connection qua LAN IP (`10.0.107.143:1099`)**:
  - Client kết nối tới địa chỉ IP LAN vật lý của chính máy chủ.
  - *Ý nghĩa*: Đã xác minh trong Phase 1.6 (`PreflightRMICheck`) — kiểm chứng Server bind đúng interface LAN và quảng bá hostname `10.0.107.143` thay vì loopback.
  - *Hạn chế*: Kernel hệ điều hành vẫn định tuyến nội bộ, chưa phản ánh đầy đủ việc truyền frame qua router Wi-Fi vật lý và tường lửa đối với gói tin đến từ máy khác.
- **Cấp độ C — Actual connection từ Máy thứ hai qua Mạng LAN vật lý**:
  - Máy client vật lý khác trong cùng mạng con kết nối tới `10.0.107.143:1099` và `10.0.107.143:1100`.
  - *Trạng thái hiện tại*: **PENDING (CHƯA XÁC MINH)** vì không có máy vật lý thứ hai trong môi trường làm việc hiện tại.

---

## 3. Phân loại Phát hiện An ninh (Security Findings by Severity)

### [CRITICAL] Nghiêm trọng (0 phát hiện chưa khắc phục)
- *(Lịch sử - Đã khắc phục)*: Lỗ hổng cờ boolean system property bypass trong `BorrowConcurrencyIntegrationTest` đã được loại bỏ hoàn toàn bằng `TestDatabaseSafetyGuard` trong Phase 1.5.

### [HIGH] Mức độ Cao (2 phát hiện)
1. **SEC-HIGH-01: Đường truyền RMI không mã hóa (Plaintext Traffic)**:
   - Java RMI truyền đối tượng qua TCP socket thuần. Dữ liệu mượn trả, thông tin cá nhân độc giả và token phiên có thể bị nghe lén qua Wireshark trên mạng LAN công cộng.
   - *Khuyến nghị*: Triển khai `SslRMIClientSocketFactory` và `SslRMIServerSocketFactory` trong các phiên bản sau nếu yêu cầu bảo mật đường truyền cấp cao.
2. **SEC-HIGH-02: Windows Defender Firewall chặn cổng RMI mặc định**:
   - Mặc định Windows chặn các kết nối Inbound tới cổng 1099 và 1100 từ máy ngoài.
   - *Khuyến nghị*: Chạy lệnh PowerShell Administrator mở cổng trên Private profile trước khi client từ máy khác kết nối.

### [MEDIUM] Mức độ Trung bình (3 phát hiện)
1. **SEC-MED-01: Phạm vi của `com.sun.proxy.**` trong Serialization Filter**:
   - Mặc dù interface và invocation handler của proxy đều được lọc qua whitelist, `com.sun.proxy.**` cho phép mọi tên lớp proxy đi qua. Rủi ro chỉ phát sinh nếu trong whitelist xuất hiện một invocation handler nguy hiểm (hiện tại whitelist không chứa gadget nào).
2. **SEC-MED-02: Chưa có kiểm thử đơn vị tự động cho `maxbytes` và `maxrefs`**:
   - Bộ test `RMISerializationFilterTest` mới chỉ kiểm tra `maxdepth=15` và `maxarray=100000`. Hai giới hạn còn lại dựa trên cấu hình JEP 290 chuẩn của JDK.
3. **SEC-MED-03: Thiếu Database Dump Snapshot trước Phase 1.4**:
   - Không thể chứng minh hồi tố bằng file dump nhị phân cho giai đoạn trước Phase 1.4, dù snapshot hiện tại xác nhận dữ liệu nghiệp vụ không đổi.

### [LOW] Mức độ Thấp (2 phát hiện)
1. **SEC-LOW-01: Số vòng lặp PBKDF2 (10.000)**:
   - Đủ an toàn trên Java 8 cho đồ án trường học, nhưng thấp hơn tiêu chuẩn hiện đại của OWASP (600.000 cho PBKDF2-HMAC-SHA256 trên server mạnh).
2. **SEC-LOW-02: Phụ thuộc vào IP động Wi-Fi (DHCP)**:
   - Nếu router Wi-Fi cấp phát IP mới, cần khởi động lại Server và cập nhật file `client.properties`.

---

## 4. Các Lệnh đã thực thi và Kết quả

| Lệnh thực thi | Mục đích | Kết quả thực tế |
|:---|:---|:---:|
| `java -version` | Xác định runtime JDK | OpenJDK 1.8.0_504 (Corretto 64-bit) |
| `ant test` | Chạy toàn bộ 12 test suites của dự án | **BUILD SUCCESSFUL (107/107 PASS, 0 fail, 0 error)** |
| `mysql ... information_schema.tables` | Đối chiếu số bản ghi 2 database | `school_library`: 0 thay đổi; `school_library_test`: 756 logs |
| `git diff --check` | Kiểm tra cú pháp và whitespace | Hoàn toàn sạch, 0 cảnh báo |
| `git status --short --branch` | Kiểm tra trạng thái Git | Bảo toàn 100% working tree |

---

## 5. Tổng hợp Kết quả Kiểm thử Tự động (`ant test`)

Chạy thực tế lúc 01:24:29 ngày 15/10/2026: **12 Suites, 107 Tests, 107 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED** (thời gian: 10 giây).

| STT | Test Suite | Tests | Pass | Fail | Error | Skipped | Time |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.234s |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.306s |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.056s |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.441s |
| 5 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 0.956s |
| 6 | `thuvien.server.rmi.RMISerializationFilterTest` | 8 | 8 | 0 | 0 | 0 | 0.283s |
| 7 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.350s |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.411s |
| 9 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.540s |
| 10 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.049s |
| 11 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 0 | 2.844s |
| 12 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.068s |
| **Tổng** | **12 Suites** | **107** | **107** | **0** | **0** | **0** | **~10s** |

---

## 6. Quy trình Kiểm thử Nghiệm thu LAN khi có Máy thứ hai (Manual Procedure)

Khi người dùng chuẩn bị máy trạm thứ hai trong cùng phòng máy:

1. **Trên Máy chủ (`10.0.107.143`)**:
   - Mở PowerShell bằng quyền Administrator và chạy lệnh mở Firewall:
     ```powershell
     New-NetFirewallRule -DisplayName "School Library RMI (Private)" -Direction Inbound -LocalPort 1099,1100 -Protocol TCP -Action Allow -Profile Private
     ```
   - Khởi động server:
     ```powershell
     ant run-server
     ```
2. **Trên Máy thứ hai (Client)**:
   - Kiểm tra kết nối TCP bằng PowerShell:
     ```powershell
     Test-NetConnection -ComputerName 10.0.107.143 -Port 1099
     Test-NetConnection -ComputerName 10.0.107.143 -Port 1100
     ```
     *(Cả 2 lệnh phải trả về `TcpTestSucceeded : True`)*.
   - Sửa file `client/resources/client.properties`:
     ```properties
     rmi.server.host=10.0.107.143
     rmi.registry.port=1099
     rmi.service.name=LibraryRemoteService
     ```
   - Khởi động giao diện Swing Client:
     ```powershell
     ant run-client
     ```
   - Đăng nhập thử bằng tài khoản `librarian1` / `lib123` hoặc `student1` / `student123`.
   - Thực hiện tìm kiếm sách và kiểm tra bảng kết quả hiển thị chính xác.
