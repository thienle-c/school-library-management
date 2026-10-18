# Báo cáo Xác minh Hồi quy và Sẵn sàng Triển khai Phase 1.9 (Regression Verification & Deployment Readiness Phase 1.9)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Thời gian thực hiện:** 15/10/2026 14:38  
**Môi trường thực thi:** OpenJDK 1.8.0_504 (Amazon Corretto 64-bit), MySQL 8.0.30 (InnoDB), Apache Ant 1.9.4 (NetBeans 8.0.2 runtime)  
**Trạng thái kiểm thử:** **12 Test Suites, 109 Tests — 109 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**

---

## 1. Danh sách Tệp Kiểm toán và Sửa đổi trong Phase 1.9

### 1.1. Tệp đã kiểm toán chuyên sâu (Inspected):
- [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java): Rà soát toàn bộ chuỗi nạp cấu hình RMI từ properties file, system properties và CLI arguments; cấu hình JEP 290 process-wide serialization filter.
- [ClientMain.java](file:///D:/workspace/thuvien/client/src/thuvien/client/ClientMain.java): Rà soát cơ chế fallback keys giữa `rmi.*` và `server.*`.
- [RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java): Kiểm tra tính chất thực thi của filter JEP 290 (xác nhận sử dụng active filter của hệ thống, không dùng filter giả lập của riêng test).
- [RMIEndToEndIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java): Rà soát teardown và setup kiểm thử; xác thực tính cô lập không can thiệp `school_library`.
- [PasswordHasher.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/PasswordHasher.java): Kiểm toán thuật toán PBKDF2WithHmacSHA256, 16-byte secure random salt, 10.000 vòng lặp, constant-time compare (`MessageDigest.isEqual`), khả năng nâng cấp tương thích ngược với SHA-256.
- [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java): Xác thực JDBC catalog thời gian thực, loại trừ hoàn toàn bypass cờ boolean.
- [MySQLSmokeIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/database/MySQLSmokeIntegrationTest.java): Xác nhận 100% chỉ đọc (read-only).
- [BorrowConcurrencyIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java): Xác nhận mọi thao tác ghi/xóa đều bọc trong guard `isSafeTestDatabase`.
- [SecurityAndFeatureHardeningTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/SecurityAndFeatureHardeningTest.java): Xác nhận mọi thao tác ghi đều kiểm tra `requireIsolatedTestDb()`.
- [CreateUserSecurityTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/CreateUserSecurityTest.java): Xác nhận hoàn toàn dùng Mock repository in-memory, không kết nối DB.

### 1.2. Tệp đã sửa đổi (Modified):
1. [server/src/thuvien/server/rmi/LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java):
   - Bổ sung fallback `System.getProperty("server.host")` cho biến `sysHost` tại dòng 323:
     ```java
     String sysHost = System.getProperty("rmi.server.host", System.getProperty("server.host"));
     ```
     Đảm bảo khi khởi chạy server với tham số JVM `-Dserver.host=...` thì hệ thống nhận diện đúng địa chỉ IP cấu hình giống hệt `-Drmi.server.host=...`.
2. [docs/remediation-phase-1-9-report.md](file:///D:/workspace/thuvien/docs/remediation-phase-1-9-report.md):
   - Tạo báo cáo toàn diện cho Phase 1.9.

---

## 2. Kết quả Xác minh Chi tiết theo Từng Nhiệm vụ

### Task 1 — Xác minh Thay đổi từ Phase 1.8 và Kiểm tra Hồi quy (Regression Verification)
- **Kiểm tra Diff mã nguồn**:
  - Không có thay đổi ngoài ý muốn (unintended changes) hoặc phá vỡ tương thích (compatibility regressions).
  - Khóa cấu hình canonical (`rmi.*`) và fallback (`server.*`) hoạt động đồng bộ trên cả Client và Server.
  - Các bài test trong `RMISerializationFilterTest` và `RMIEndToEndIntegrationTest` kiểm tra trực tiếp các kiểm soát an ninh thực tế (active controls) thay vì pass giả tạo.
  - Xóa bỏ tình trạng flaky test trong `test06_BorrowAndReturnOverRMI` bằng cách dọn dẹp các bản ghi mượn tàn dư của `student_id = 1` và `book_id = 1` trong database test `school_library_test` khi khởi tạo suite.

---

### Task 2 — Kiểm chứng Độc lập Cấu hình RMI và Cơ chế Quảng bá Stub (Stub Advertising)
Đã thực thi kiểm thử độc lập qua chương trình kiểm chứng [VerifyRMIConfigurationKeys.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/VerifyRMIConfigurationKeys.java):

1. **Thông số cổng và tên dịch vụ chuẩn hóa**:
   - `DEFAULT_REGISTRY_PORT = 1099` (Xác nhận khớp mã nguồn và `server.properties`).
   - `DEFAULT_SERVICE_PORT = 1100` (Xác nhận remote object được export trên port cố định 1100 qua `super(servicePort)` trong [LibraryRemoteServiceImpl.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java)).
   - `DEFAULT_SERVICE_NAME = "LibraryRemoteService"` (Xác nhận đồng bộ giữa Server và Client).
2. **Kiểm tra Stub Endpoint & Quảng bá Host**:
   - Trích xuất thông tin Endpoint từ `RemoteRef` (`UnicastServerRef` $\rightarrow$ `LiveRef` $\rightarrow$ `TCPEndpoint`):
     - `Stub Advertised Host`: Trùng khớp 100% với host được cấu hình (`127.0.0.1` hoặc IP LAN vật lý).
     - `Stub Advertised Port`: Trùng khớp 100% với `servicePort` (1100/12100).
     - Phương thức `ping()` gọi qua stub từ xa trả về `"PONG"` thành công.
3. **Phân định Ranh giới Kỹ thuật Trọng yếu (Firewall & Remote Reachability Limitation)**:
   - **Lưu ý kiến trúc bắt buộc**: Việc Client tìm kiếm Registry thành công trên cổng 1099 **KHÔNG** chứng minh được cổng Remote Object 1100 có thể truy cập được từ máy khác qua mạng LAN.
   - Khi Client thực hiện gọi hàm (ví dụ: `login`, `searchBooks`), Java RMI mở kết nối TCP thứ hai trực tiếp tới cổng được ghi trong Stub (1100). Nếu Windows Defender Firewall trên máy chủ chỉ mở cổng 1099 mà không mở 1100, Client trên máy thứ hai sẽ bị treo (hang) hoặc nhận lỗi `java.rmi.ConnectException: Connection refused` / timeout.

---

### Task 3 — Kiểm chứng Hoạt động của Serialization Filter (JEP 290)
1. **Bản chất của Filter đang chạy**:
   - Hàm `LibraryRMIServer.configureSerializationFilter()` thiết lập biến toàn cục `jdk.serialFilter` của máy ảo Java qua cả `System.setProperty` và `java.security.Security.setProperty`.
   - Các bài kiểm thử trong [RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java) gọi trực tiếp `ObjectInputStream ois = new ObjectInputStream(...)` mà **không** tự tạo hoặc nạp một filter riêng biệt của test.
   - Cơ chế JEP 290 tự động gắn active filter của JVM vào mọi phiên bản `ObjectInputStream` được khởi tạo, do đó các bài kiểm thử `maxbytes` (10 MB), `maxrefs` (50.000 tham chiếu), `maxdepth` (15 cấp) và `maxarray` (100.000 phần tử) thực thi trên chính cấu hình filter thực tế của ứng dụng.
2. **Đánh giá Khách quan về Khả năng Chống Gadget (No Unsupported Claims)**:
   - **Phạm vi bảo vệ thực tế đã được chứng minh**:
     - 100% các lớp ngoài whitelist (ví dụ: các gadget class phổ biến từ thư viện bên ngoài như Apache Commons Collections, Spring, Groovy...) và các lớp ứng dụng không khai báo đều bị từ chối với `InvalidClassException (filter status: REJECTED)`.
     - Kẻ tấn công không thể tạo các đồ thị đối tượng sâu quá 15 cấp, mảng lớn quá 100.000 phần tử, luồng byte quá 10 MB, hoặc đồ thị chứa quá 50.000 tham chiếu để gây cạn kiệt bộ nhớ (DoS/OOM).
     - Lớp Proxy động (`com.sun.proxy.**`) chỉ được chấp nhận nếu tất cả các interface được ủy quyền và `InvocationHandler` đều thuộc whitelist.
   - **Hạn chế bảo mật còn tồn tại (Residual Risk)**:
     - Whitelist hiện tại sử dụng wildcard cho các gói thư viện chuẩn Java: `java.lang.**`, `java.util.**`, `java.math.**`, `java.sql.**`.
     - Hệ thống **chưa** đạt đến mức độ whitelist danh sách lá cụ thể (fine-grained leaf-level class whitelist như chỉ cho phép đích danh `java.lang.String`, `java.util.ArrayList`, v.v.). Nếu trong tương lai phát hiện gadget nội bộ nằm thuần túy trong `java.util.*` của phiên bản JDK 8, wildcard này có thể không ngăn chặn được.

---

### Task 4 — Xác minh Tính Cô lập Cơ sở Dữ liệu (Database Isolation Verification)
1. **Kiểm tra toàn bộ luồng ghi và dọn dẹp kiểm thử**:
   - Mọi integration test có khả năng thay đổi dữ liệu đều truy vấn `connection.getCatalog()` và kiểm tra qua `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`.
   - Nếu kết nối trỏ tới `school_library`, guard lập tức chặn đứng, trả về `false`, và test suite tự động bỏ qua (skip an toàn).
   - Không tồn tại bất kỳ cờ boolean nào có thể vượt qua (bypass) bước xác thực catalog của kết nối JDBC.
2. **Bằng chứng Thực nghiệm Tuyệt đối (Empirical Proof)**:
   - Đối chiếu số lượng bản ghi thực tế trên MySQL trước và sau khi chạy toàn bộ 109 bài test:
     - `school_library.audit_logs`: Cố định ở **5631** bản ghi (0 bản ghi mới).
     - `school_library.books`: Cố định ở **5** đầu sách.
     - `school_library.users`: Cố định ở **7** tài khoản người dùng.
     - `school_library.students`: Cố định ở **4** sinh viên.
     - `school_library.borrow_records`: Cố định ở **2** bản ghi mượn.
     - `school_library_test.audit_logs`: Tăng từ 1322 lên 1700 (+378 log phát sinh hoàn toàn trên database test).
3. **Giới hạn Lịch sử (Historical Limitation Explicit Statement)**:
   - Nhắc lại rõ ràng: Do không có bản snapshot sao lưu nhị phân trước Phase 1.4, không thể chứng minh một cách hồi tố rằng các đợt chạy trước Phase 1.4 chưa từng ghi vào `school_library`. Tuy nhiên, từ Phase 1.4 đến Phase 1.9 hiện tại, dữ liệu nghiệp vụ đã được bảo vệ nguyên vẹn 100%.

---

## 3. Phân loại Phát hiện Hồi quy & An ninh (Findings by Severity)

### [CRITICAL] 0 Phát hiện

### [HIGH] 2 Phát hiện (Kiến trúc & Hạ tầng LAN)
1. **SEC-HIGH-01: Yêu cầu mở cả 2 cổng 1099 và 1100 trên Firewall cho Máy thứ hai**:
   - Nếu chỉ mở cổng Registry 1099, client trên máy thứ hai sẽ kết nối được bước lookup nhưng thất bại khi thực hiện gọi hàm nghiệp vụ trên cổng Remote Object 1100.
2. **SEC-HIGH-02: Đường truyền RMI nhị phân chưa mã hóa SSL/TLS**:
   - Dữ liệu gọi hàm từ xa truyền dưới dạng TCP binary thuần túy qua mạng LAN, phù hợp cho môi trường bài tập lớn mạng máy tính nội bộ nhưng chưa bảo mật trên Wi-Fi công cộng.

### [MEDIUM] 2 Phát hiện
1. **SEC-MED-01: Whitelist JEP 290 còn chứa Package Wildcards (`java.util.**`, `java.lang.**`)**:
   - Chưa đạt đến mức độ whitelist từng lớp đơn lẻ. Mặc dù chặn toàn bộ thư viện gadget bên thứ ba, về mặt lý thuyết vẫn có thể tồn tại rủi ro nếu có chuỗi gadget thuần JDK.
2. **SEC-MED-02: Thiếu snapshot ban đầu trước Phase 1.4**:
   - Giới hạn lịch sử không thể chứng minh hồi tố cho dữ liệu trước Phase 1.4.

### [LOW] 2 Phát hiện
1. **SEC-LOW-01: Số vòng lặp PBKDF2 (10.000)**:
   - Thấp hơn chuẩn hiện đại cho server mạnh nhưng tối ưu cho Java 8 desktop/client.
2. **SEC-LOW-02: IP LAN phụ thuộc DHCP Wi-Fi**:
   - Cần kiểm tra lại IP LAN khi router cấp phát IP mới.

---

## 4. Bảng Kết quả Thực thi Kiểm thử Tự động (`ant test`)

Chạy thực tế lúc 14:36:36 ngày 15/10/2026: **12 Test Suites, 109 Tests — 109 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED** (thời gian: 13 giây).

| STT | Tên Test Suite | Tests | PASS | FAIL | ERROR | SKIPPED | Thời gian (giây) |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.348 |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.359 |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.067 |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.579 |
| 5 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 1.145 |
| 6 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 10 | 0 | 0 | 0 | 0.439 |
| 7 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.382 |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.448 |
| 9 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.606 |
| 10 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.052 |
| 11 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 0 | 5.020 |
| 12 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.075 |
| **Tổng** | **12 Test Suites** | **109** | **109** | **0** | **0** | **0** | **~13.0s** |

---

## 5. Rào cản Còn lại cho Nghiệm thu Thực tế Mạng LAN (Remaining Blockers for Level C Acceptance)

Để tiến hành nghiệm thu Cấp độ C (máy trạm thứ hai kết nối qua mạng LAN vật lý), cần tháo gỡ các rào cản ngoại vi sau:

1. **Rào cản Thiết bị Vật lý (Hardware Availability)**:
   - Hiện tại chỉ có duy nhất 1 máy chủ phát triển trong phiên làm việc. Nghiệm thu Cấp độ C bắt buộc phải có máy trạm thứ hai kết nối cùng router Wi-Fi / switch LAN.
2. **Quyền Quản trị Mở Cổng Firewall (Administrator Privileges)**:
   - Cần người dùng mở PowerShell Administrator trên máy chủ để cấp phép Inbound cho cả 2 cổng 1099 và 1100:
     ```powershell
     New-NetFirewallRule -DisplayName "School Library RMI (Private)" -Direction Inbound -LocalPort 1099,1100 -Protocol TCP -Action Allow -Profile Private
     ```
3. **Đồng bộ Địa chỉ IP LAN trên Client**:
   - Cần xác nhận IP LAN máy chủ (ví dụ: `10.0.107.143`) và điền chính xác vào `client/resources/client.properties` trên máy thứ hai trước khi khởi chạy `ant run-client`.
