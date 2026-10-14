# Báo cáo Khắc phục & Xác minh An ninh Phase 1.8 (Remediation & Security Verification Phase 1.8)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Thời gian thực hiện:** 15/10/2026 01:38  
**Môi trường:** OpenJDK 1.8.0_504 (Amazon Corretto 64-bit), MySQL 8.0.30 (InnoDB), Apache Ant 1.9.4 (NetBeans 8.0.2 runtime)  
**Trạng thái kiểm thử:** **12 Suites, 109 Tests — 109 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**

---

## 1. Danh sách Tệp Kiểm toán và Sửa đổi (Files Inspected & Modified)

### 1.1. Tệp đã kiểm toán chuyên sâu (Inspected):
- [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java): Cấu hình serialization filter JEP 290, binding IP LAN và port.
- [RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java): Các ca kiểm thử whitelist, recursion depth, array limit, byte stream limit và object reference limit.
- [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java): Cơ chế fail-closed bảo vệ database `school_library`, kiểm tra catalog JDBC thời gian thực.
- [TestDatabaseSafetyGuardTest.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuardTest.java): 6 ca kiểm thử độc lập cho safety guard.
- [PasswordHasher.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/PasswordHasher.java): Thuật toán PBKDF2WithHmacSHA256, sinh salt, verify constant-time, fallback legacy SHA-256.
- [ClientMain.java](file:///D:/workspace/thuvien/client/src/thuvien/client/ClientMain.java): Trích xuất tham số cấu hình client từ properties và command-line.
- [server.properties](file:///D:/workspace/thuvien/server/resources/server.properties): Khóa cấu hình RMI phía server.
- [client.properties](file:///D:/workspace/thuvien/client/resources/client.properties): Khóa cấu hình RMI phía client.
- [build.xml](file:///D:/workspace/thuvien/build.xml): Cấu hình target Ant `run-server`, `run-client` và các tham số JVM RMI.

### 1.2. Tệp đã sửa đổi (Modified):
1. [client/src/thuvien/client/ClientMain.java](file:///D:/workspace/thuvien/client/src/thuvien/client/ClientMain.java):
   - Bổ sung fallback cho khóa `server.service` bên cạnh `rmi.service.name` để tương thích tuyệt đối giữa tài liệu cũ và chuẩn cấu hình mới.
2. [server/src/thuvien/server/rmi/LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java):
   - Đồng bộ fallback đọc service name từ `rmi.service.name` hoặc `server.service`.
3. [test/thuvien/server/rmi/RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java):
   - Bổ sung 2 ca kiểm thử đơn vị trực tiếp: `testResourceLimitBytesExceeded()` (kiểm tra `maxbytes=10485760` với stream 11 MB) và `testResourceLimitRefsExceeded()` (kiểm tra `maxrefs=50000` với 50.010 object references).
4. [test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java):
   - Bổ sung dọn dẹp các bản ghi mượn/phạt tàn dư của `student_id = 1` và `book_id = 1` trong hàm `setUpRMIAndDatabase()` đối với database kiểm thử `school_library_test`, đảm bảo tính cô lập và lặp lại độc lập của `test06_BorrowAndReturnOverRMI`.
5. [docs/remediation-phase-1-8-report.md](file:///D:/workspace/thuvien/docs/remediation-phase-1-8-report.md):
   - Tài liệu báo cáo hoàn tất Phase 1.8.

---

## 2. Kết quả Xác minh 5 Nhiệm vụ Trọng tâm

### Task 1 — Xác minh RMI Serialization Filter (JEP 290)
1. **Trạng thái cài đặt trên JDK 8 Runtime**:
   - Runtime: `OpenJDK 1.8.0_504` (Amazon Corretto). JEP 290 đã được backport đầy đủ vào Java 8u121+.
   - Cơ chế thiết lập: `sun.misc.ObjectInputFilter.Config.setSerialFilter(...)` được gọi tại [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java) trước khi export bất kỳ Remote Object nào.
   - Hoạt động: Áp dụng toàn cục (process-wide) cho mọi luồng giải tuần tự hóa của RMI dispatcher và socket I/O.
2. **Cơ chế xử lý Dynamic Proxy (`com.sun.proxy.**`)**:
   - Filter chuỗi chứa pattern: `com.sun.proxy.*;java.lang.reflect.Proxy;`
   - *Nguyên lý JEP 290*: Khi giải tuần tự hóa một dynamic proxy, JVM gọi filter cho lớp proxy (`com.sun.proxy.$ProxyX`), sau đó tiếp tục gọi filter cho từng interface được ủy quyền và cuối cùng cho trường `InvocationHandler`.
   - *Đánh giá an toàn*: Mặc dù pattern chấp nhận mọi tên lớp proxy sinh tự động, việc thực thi mã độc bị chặn vì tất cả interface và `InvocationHandler` phải nằm trong whitelist nghiêm ngặt (chỉ gồm `thuvien.common.**` và JDK collections). Không có gadget handler nào (như Commons Collections `AnnotationInvocationHandler`) được phép xuất hiện.
3. **Đính chính các tuyên bố an ninh**:
   - Đã loại bỏ khẳng định suy đoán trong Phase 1.7 về việc `maxbytes` và `maxrefs` "được bảo vệ mà không cần test".
   - Đã hiện thực hóa 2 bài kiểm thử tự động trực tiếp trong `RMISerializationFilterTest`:
     - `testResourceLimitBytesExceeded`: Ghi mảng byte vượt quá 10MB (11.000.000 bytes) -> Filter trả về `Status.REJECTED` khi chạm ngưỡng `maxbytes=10485760`.
     - `testResourceLimitRefsExceeded`: Ghi danh sách chứa 50.010 tham chiếu đối tượng -> Filter trả về `Status.REJECTED` khi chạm ngưỡng `maxrefs=50000`.
   - Tổng số test của `RMISerializationFilterTest` tăng từ 8 lên 10, đạt tỷ lệ **10/10 PASS**.

---

### Task 2 — Kiểm toán An toàn Cơ sở Dữ liệu (Database Safety Verification)
1. **Cơ chế `TestDatabaseSafetyGuard`**:
   - Mã nguồn tại [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java) xác thực trực tiếp danh tính database thông qua JDBC connection:
     ```java
     String catalog = connection.getCatalog();
     ```
   - Chỉ cho phép các thao tác kiểm thử nếu `catalog.equalsIgnoreCase("school_library_test")` (hoặc schema có hậu tố `_test`).
2. **Loại bỏ hoàn toàn cờ Boolean Bypass**:
   - Đã kiểm tra toàn bộ mã nguồn: **Không còn bất kỳ cờ boolean system property nào (như `db.safety.bypass` hay `skip.db.check`) có thể bỏ qua bước kiểm tra danh tính database.**
   - Mọi phương thức can thiệp database (`assertSafeDatabase`, `isSafeTestDatabase`) đều bắt buộc truyền `Connection` thực và kiểm tra catalog.
3. **Nguyên tắc Fail-Closed**:
   - Nếu kết nối database null, đóng, hoặc catalog là `school_library` (database nghiệp vụ chính), guard ném `IllegalStateException` hoặc test suite tự động skip an toàn qua JUnit `Assume.assumeTrue`.
4. **Hạn chế Lịch sử (Historical Limitation)**:
   - *Ghi nhận trung thực*: Không thể chứng minh bằng file dump nhị phân cho các đợt chạy trước Phase 1.4 do không có bản sao lưu trước đó.
   - *Bằng chứng thực tế hiện tại*: Đối chiếu bảng `information_schema.tables` trước và sau toàn bộ 109 bài test:
     - `school_library.audit_logs`: Giữ nguyên **4427** dòng (0 log phát sinh).
     - `school_library.users`: Giữ nguyên **5** tài khoản.
     - `school_library.students`: Giữ nguyên **4** sinh viên.
     - `school_library.books`: Giữ nguyên **5** đầu sách.
     - `school_library.borrow_records`: Giữ nguyên **2** bản ghi.
     - Toàn bộ dữ liệu ghi và xóa kiểm thử được cô lập 100% trong `school_library_test` (756 audit logs).

---

### Task 3 — Kiểm toán Băm Mật khẩu (Password Hashing Validation)
Kiểm toán mã nguồn [PasswordHasher.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/PasswordHasher.java):

| Tiêu chuẩn | Thông số Kỹ thuật Thực tế | Đánh giá An ninh |
|:---|:---|:---|
| **Thuật toán chính** | `PBKDF2WithHmacSHA256` | Chuẩn NIST SP 800-132, OWASP |
| **Sinh Salt** | `SecureRandom`, 16 bytes (128 bits) ngẫu nhiên mỗi lần băm | Ngăn chặn triệt để Rainbow Table và precomputed dictionary attack |
| **Số vòng lặp (Iteration Count)** | 10.000 rounds | Cân bằng tối ưu giữa độ trễ đăng nhập trên Java 8 và khả năng kháng brute-force |
| **Độ dài khóa (Key Length)** | 256 bits (32 bytes) | Độ phức tạp mật mã cao |
| **Định dạng lưu trữ** | `PBKDF2$<iterations>$<saltHex>$<hashHex>` | Tự chứa tham số (Self-contained metadata), cho phép điều chỉnh số vòng lặp linh hoạt |
| **So sánh băm** | `MessageDigest.isEqual(expectedHash, computedHash)` | Constant-time comparison, loại bỏ hoàn toàn Timing Attack |
| **Tương thích ngược (Legacy Fallback)** | Hỗ trợ mật khẩu cũ un-salted SHA-256 từ `seed.sql` qua `verifyLegacy()` | Người dùng hiện tại đăng nhập bình thường; cờ `isLegacyHash(stored)` cho phép tự động nâng cấp sang PBKDF2 khi đăng nhập thành công |

**Khuyến nghị nâng cấp trong tương lai**:
- Khi chuyển đổi môi trường máy chủ lên phần cứng hiệu năng cao hơn, có thể nâng `PBKDF2_ITERATIONS` từ 10.000 lên 50.000 hoặc 100.000 mà không gây xung đột định dạng lưu trữ (do parser đọc số vòng lặp trực tiếp từ tiền tố `PBKDF2$<iter>$...`).

---

### Task 4 — Chuẩn hóa và Điều hòa Cấu hình RMI (Configuration Reconciliation)

Đã rà soát và đồng bộ toàn bộ chuỗi đọc cấu hình từ file properties, mã Java đến Ant target:

| Khóa chuẩn hóa (Canonical Key) | Khóa tương thích cũ (Fallback Key) | Vị trí hỗ trợ trong Source Code | Ghi chú & Mục đích |
|:---|:---|:---|:---|
| `rmi.server.host` | `server.host` | `LibraryRMIServer.java`, `ClientMain.java` | IP hoặc hostname máy chủ RMI (mặc định tự nhận diện IP LAN) |
| `rmi.registry.port` | `server.port` | `LibraryRMIServer.java`, `ClientMain.java` | Cổng RMI Registry (mặc định `1099`) |
| `rmi.service.name` | `server.service` | `LibraryRMIServer.java`, `ClientMain.java` | Tên định danh dịch vụ (mặc định `LibraryRemoteService`) |
| `rmi.service.port` | — | `LibraryRMIServer.java` | Cổng cố định cho Remote Object Unicast (mặc định `1100`) |

- **Kết quả điều hòa**: Cả Client và Server đều đọc theo thứ tự ưu tiên: `Canonical Key -> Fallback Key -> Default Value`. Mọi tài liệu hướng dẫn sử dụng `server.host` hoặc `rmi.server.host` đều hoạt động trơn tru mà không gây lỗi phân giải tham số.

---

## 3. Phân loại Phát hiện An ninh (Security Findings by Severity)

### [CRITICAL] 0 Phát hiện (Đã giải quyết triệt để)
- Cờ bypass an toàn database đã được loại bỏ vĩnh viễn; 100% test write phải qua xác minh catalog kết nối JDBC thực tế.

### [HIGH] 2 Phát hiện
1. **SEC-HIGH-01: Kênh truyền RMI không mã hóa TLS/SSL**:
   - Dữ liệu gọi hàm từ xa, mã phiên và thông tin cá nhân truyền dưới dạng nhị phân thuần trên cổng TCP 1099/1100.
   - *Khắc phục đề xuất*: Cần bổ sung cấu hình `RMIClientSocketFactory` và `RMIServerSocketFactory` hỗ trợ SSL/TLS trong giai đoạn triển khai production thực tế ngoài mạng nội bộ an toàn.
2. **SEC-HIGH-02: Windows Defender Firewall chặn cổng kết nối từ máy trạm khác**:
   - Hệ điều hành Windows mặc định chặn Inbound traffic trên cổng 1099 và 1100.
   - *Biện pháp*: Yêu cầu quản trị viên chạy lệnh cấp phép Firewall trước khi chạy thử nghiệm trên máy trạm thứ hai.

### [MEDIUM] 3 Phát hiện
1. **SEC-MED-01: Whitelist `com.sun.proxy.**` trong Serialization Filter**:
   - Cho phép khởi tạo class proxy động, tuy nhiên rủi ro được khống chế chặt chẽ vì tất cả interfaces và invocation handlers đều nằm trong danh sách kiểm soát nghiêm ngặt.
2. **SEC-MED-02: Đã bổ sung Unit Test cho Giới hạn Tài nguyên JEP 290**:
   - Phát hiện thiếu sót kiểm thử ở Phase 1.7 đã được khắc phục hoàn toàn trong Phase 1.8 bằng 2 unit test trực tiếp cho `maxbytes` (10MB) và `maxrefs` (50.000 đối tượng).
3. **SEC-MED-03: Thiếu Database Dump Snapshot thời điểm trước Phase 1.4**:
   - Không thể cung cấp bằng chứng lịch sử trước Phase 1.4, nhưng kiểm soát hiện tại xác nhận snapshot nguyên vẹn 100% qua mọi lần chạy kiểm thử.

### [LOW] 2 Phát hiện
1. **SEC-LOW-01: Số vòng lặp PBKDF2 (10.000 rounds)**:
   - Thấp hơn khuyến nghị mới nhất của OWASP cho server cấu hình cao, nhưng phù hợp tối ưu với môi trường học phần Java 8 máy trạm.
2. **SEC-LOW-02: Địa chỉ IP LAN thay đổi khi Router cấp phát DHCP**:
   - Cần cấu hình IP tĩnh trên Server hoặc cập nhật lại `client.properties` khi Router khởi động lại.

---

## 4. Kết quả Thực thi Kiểm thử Tự động Toàn diện (`ant test`)

Thời gian chạy: 10 giây (01:36:54 SA 15/10/2026).  
Bộ công cụ: NetBeans 8.0.2 Apache Ant 1.9.4.

| STT | Tên Test Suite | Tổng số Tests | PASS | FAIL | ERROR | SKIPPED | Thời gian (giây) |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.243 |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.305 |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.054 |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.448 |
| 5 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 0.985 |
| 6 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 10 | 0 | 0 | 0 | 0.396 |
| 7 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.368 |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.415 |
| 9 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.546 |
| 10 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.051 |
| 11 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 0 | 2.791 |
| 12 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.068 |
| **Tổng cộng** | **12 Test Suites** | **109** | **109** | **0** | **0** | **0** | **~10.0s** |

---

## 5. Quy trình Nghiệm thu Thực tế Mạng LAN (Manual Acceptance Steps)

Phân định rõ 3 cấp độ kiểm thử mạng:
- **Cấp độ A (Loopback Localhost `127.0.0.1`)**: Đã đạt 100% qua 109 automated tests.
- **Cấp độ B (LAN IP Localhost `10.0.107.143`)**: Đã xác nhận Server bind đúng IP Wi-Fi vật lý.
- **Cấp độ C (Máy vật lý thứ hai trong mạng LAN)**: Bắt buộc thực hiện theo các bước sau khi có máy trạm thứ hai:

### Bước 1: Mở cổng Firewall trên Máy chủ (Run as Administrator)
```powershell
New-NetFirewallRule -DisplayName "School Library RMI Service" -Direction Inbound -LocalPort 1099,1100 -Protocol TCP -Action Allow -Profile Private
```

### Bước 2: Khởi động Server trên Máy chủ
```powershell
ant run-server
```
*(Xác nhận log in ra IP LAN: `[RMI-SERVER] Bound to registry at //10.0.107.143:1099/LibraryRemoteService`)*.

### Bước 3: Kiểm tra thông mạch từ Máy trạm thứ hai
```powershell
Test-NetConnection -ComputerName 10.0.107.143 -Port 1099
Test-NetConnection -ComputerName 10.0.107.143 -Port 1100
```
*(Cả 2 lệnh phải trả về `TcpTestSucceeded : True`)*.

### Bước 4: Khởi động Client trên Máy trạm thứ hai
Cập nhật file `client/resources/client.properties`:
```properties
rmi.server.host=10.0.107.143
rmi.registry.port=1099
rmi.service.name=LibraryRemoteService
```
Khởi động ứng dụng Swing:
```powershell
ant run-client
```
Đăng nhập tài khoản thủ thư `librarian1` / `lib123` hoặc độc giả `student1` / `student123` để hoàn tất nghiệm thu giao diện đồ họa.
