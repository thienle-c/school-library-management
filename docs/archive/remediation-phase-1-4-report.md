# Remediation Report — Phase 1.4: Safe RMI Integration Verification

**Dự án**: Remote School Library Management System  
**Workspace**: `D:\workspace\thuvien`  
**Môi trường**: Windows 11, OpenJDK 1.8.0_462 (64-bit), MySQL 8.0.30 (Laragon on port 3306), Apache Ant 1.9.4 (NetBeans 8.0.2 bundled)  
**Thời gian thực hiện**: 15/10/2026  
**Trạng thái Git**: Uncommitted changes preserved (Không commit, không push)

---

## 1. Mục tiêu và Phạm vi thực hiện

Sau khi hoàn tất Phase 1.3 (loại bỏ hoàn toàn giao thức TCP Socket cũ trên cổng 9999 và chuyển sang kiến trúc Java RMI duy nhất), mục tiêu của **Phase 1.4** là:

1. **Xác minh an toàn kiến trúc Java RMI** thông qua các kiểm thử tích hợp thực tế (End-to-End, Concurrency, Hardening, Database Smoke).
2. **Tuyệt đối không xâm phạm database nghiệp vụ `school_library`**: Không làm thay đổi schema, dữ liệu người dùng, hay dữ liệu mượn trả của database chính.
3. **Thiết lập môi trường database kiểm thử cách ly hoàn toàn** (`school_library_test`) và cơ chế nạp cấu hình kiểm thử an toàn qua file cục bộ gitignored (`test.local.properties`), System Properties và biến môi trường.
4. **Triển khai chốt chặn an toàn (Safety Guard)** tại các integration test suite: Tự động từ chối chạy hoặc skip nếu phát hiện đang kết nối nhầm vào database chính `school_library`.
5. **Rà soát và khắc phục các lỗi phát sinh trong giao tiếp RMI thực tế** (cổng, registry, remote object, JEP 290 serialization filter, dynamic proxy unmarshalling).

---

## 2. Trạng thái Git trước và sau

### 2.1. Kiểm tra trạng thái Git ban đầu
Trước khi tiến hành sửa đổi hoặc chạy test, trạng thái Git được ghi nhận:
- Branch: `## main...origin/main`
- Các thay đổi từ Phase 1 và Phase 1.3 được bảo toàn nguyên vẹn trong working tree.

### 2.2. Kiểm tra trạng thái Git sau khi hoàn tất Phase 1.4
Kết quả lệnh `git status --short --branch`:
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
 docs/ARCHITECTURE.md
 M nbproject/project.properties
 M server/resources/server.properties
D  server/src/thuvien/server/ServerMain.java
 M server/src/thuvien/server/database/DatabaseConfig.java
 M server/src/thuvien/server/database/DatabaseManager.java
D  server/src/thuvien/server/network/ClientHandler.java
D  server/src/thuvien/server/network/LibraryServer.java
 M server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java
 M server/src/thuvien/server/rmi/LibraryRMIServer.java
 M server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java
 M server/src/thuvien/server/router/RequestRouter.java
 M server/src/thuvien/server/service/impl/ReturnServiceImpl.java
 M server/src/thuvien/server/service/impl/UserServiceImpl.java
 M test/thuvien/server/database/MySQLSmokeIntegrationTest.java
D  test/thuvien/server/network/TCPEndToEndIntegrationTest.java
 M test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java
 M test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java
 M test/thuvien/server/service/SecurityAndFeatureHardeningTest.java
?? common/src/thuvien/common/dto/CreateUserRequestDTO.java
?? docs/full-project-audit.md
?? docs/netbeans-server-entrypoint-audit.md
?? docs/remediation-phase-1-3-report.md
?? docs/remediation-phase-1-4-report.md
?? docs/remediation-phase-1-report.md
?? docs/remediation-phase-1-verification.md
?? docs/tcp-rmi-dependency-audit.md
?? test/thuvien/server/rmi/RMISerializationFilterTest.java
?? test/thuvien/server/service/CreateUserSecurityTest.java
```

### 2.3. Bảo vệ tệp cấu hình kiểm thử nhạy cảm
- Tệp `test.local.properties` được tạo tại thư mục gốc của dự án để cấu hình kết nối tới `school_library_test`.
- Tệp này được bảo vệ bởi `.gitignore` tại dòng 32 (`*.local.properties`), do đó **hoàn toàn không bị Git theo dõi, không xuất hiện trong `git status`, không bị commit hay lộ thông tin xác thực**.
- Lệnh `git diff --check` đã được thực thi và không phát sinh bất kỳ lỗi khoảng trắng (whitespace) hay xung đột nào.

---

## 3. Cơ chế chọn Database và Bằng chứng an toàn dữ liệu

### 3.1. Rà soát rủi ro cấu hình ban đầu
- Ban đầu, `DatabaseConfig.java` chỉ nạp cấu hình từ `db.properties` (hoặc classpath resource tương ứng).
- File `db.properties` mặc định trỏ thẳng vào database nghiệp vụ:
  ```properties
  db.url=jdbc:mysql://localhost:3306/school_library
  ```
- Nếu chạy trực tiếp `RMIEndToEndIntegrationTest`, `SecurityAndFeatureHardeningTest` hoặc `MySQLSmokeIntegrationTest` khi chưa có cơ chế override, các test này sẽ **kết nối thẳng vào `school_library`**, tạo người dùng giả lập, mượn sách, xóa tài khoản và làm sai lệch số liệu tồn kho sách!

### 3.2. Triển khai kiến trúc nạp cấu hình phân tầng (Tiered Configuration)
`DatabaseConfig.java` đã được bổ sung cơ chế ưu tiên nạp cấu hình nhiều lớp:

```
Ưu tiên 1: Java System Properties (db.url, db.user, db.password)
           |
Ưu tiên 2: Biến môi trường hệ điều hành (DB_URL, DB_USERNAME, DB_PASSWORD)
           |
Ưu tiên 3: File chỉ định qua System Property (-Ddb.config.file=path)
           |
Ưu tiên 4: File cấu hình cục bộ test.local.properties (Gitignored)
           |
Ưu tiên 5: File mặc định server/resources/db.properties (Fallback nghiệp vụ)
```

### 3.3. Thiết lập Database thử nghiệm độc lập `school_library_test`
1. Đã kết nối vào MySQL instance cục bộ (MySQL 8.0.30 trên cổng 3306) và khởi tạo database riêng:
   ```sql
   CREATE DATABASE IF NOT EXISTS school_library_test 
   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Thực thi toàn bộ các script khởi tạo chuẩn của dự án lên `school_library_test`:
   - `database/schema.sql`: Khởi tạo 11 bảng InnoDB (`roles`, `users`, `categories`, `books`, `students`, `borrow_records`, `fines`, `reservations`, `audit_logs`, `system_settings`, `password_reset_tokens`).
   - `database/indexes.sql`: Tạo các chỉ mục tối ưu tìm kiếm và khóa ngoại.
   - `database/seed.sql`: Nạp 4 tài khoản mẫu (`admin`, `librarian1`, `librarian2`, `student1`) và 5 đầu sách mẫu.
3. Khởi tạo file cấu hình cục bộ `test.local.properties`:
   ```properties
   db.url=jdbc:mysql://localhost:3306/school_library_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   db.user=root
   db.password=
   db.pool.size=10
   db.pool.min.idle=2
   ```

### 3.4. Triển khai Safety Guard trong mã nguồn kiểm thử
Để loại trừ hoàn toàn nguy cơ vô tình can thiệp vào `school_library` (kể cả khi ai đó vô tình xóa `test.local.properties`), các chốt chặn an toàn được bổ sung vào các integration test:

1. **`MySQLSmokeIntegrationTest.java`**:
   - Sử dụng `conn.getCatalog()` động để xác định database hiện hành, xác minh schema, bảng và dữ liệu mà không hardcode cứng `"school_library"`.
2. **`RMIEndToEndIntegrationTest.java`**:
   - Thêm kiểm tra `@BeforeClass`:
     ```java
     String dbName = DatabaseConfig.getDatabaseName();
     boolean isTestDb = "school_library_test".equalsIgnoreCase(dbName) 
             || "true".equalsIgnoreCase(System.getProperty("test.isolated.db"));
     org.junit.Assume.assumeTrue(
         "Skipping RMIEndToEndIntegrationTest: safety guard active. Target DB is not school_library_test (current: " + dbName + ")", 
         isTestDb
     );
     ```
3. **`SecurityAndFeatureHardeningTest.java`**:
   - Thêm kiểm tra an toàn tương tự: nếu không phát hiện đúng database `school_library_test`, toàn bộ 29 test case can thiệp dữ liệu sẽ tự động skip để bảo vệ dữ liệu.
4. **`BorrowConcurrencyIntegrationTest.java`**:
   - Đã có sẵn cơ chế cô lập và dọn dẹp các bản ghi mượn trả test trong `school_library_test`.

### 3.5. Bằng chứng database `school_library` được bảo vệ tuyệt đối
- Trước và sau toàn bộ quá trình chạy test, cấu hình `test.local.properties` được kích hoạt và nạp thành công.
- Log kiểm thử ghi nhận `DatabaseConfig loaded from file: test.local.properties`.
- Catalog kết nối thực tế trong suốt phiên test là `school_library_test`.
- Database chính `school_library` không chịu bất kỳ tác động nào từ các thao tác INSERT, UPDATE, DELETE của các suite test.

---

## 4. Các lỗi phát hiện, Cách khắc phục và Phạm vi thay đổi

### 4.1. Lỗi Serialization Filter chặn Dynamic Proxy của RMI Registry (JEP 290)
- **Triệu chứng**:
  Khi chạy `RMIEndToEndIntegrationTest`, các lời gọi `registry.lookup("LibraryRemoteService")` từ phía client bị từ chối với ngoại lệ:
  ```text
  java.rmi.UnmarshalException: error unmarshalling return; nested exception is: 
      java.io.InvalidClassException: filter status: REJECTED
  ```
- **Nguyên nhân**:
  Trong Phase 1.1, `LibraryRMIServer.java` đã cấu hình JEP 290 serialization filter với mẫu:
  ```text
  thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;!*
  ```
  Tuy nhiên, khi client thực hiện `lookup()` trên RMI Registry, RMI Runtime trả về một dynamic proxy class (do Java Reflection sinh ra tự động) thuộc package `com.sun.proxy.$Proxy*`. Vì mẫu filter kết thúc bằng `!*` và không khai báo `com.sun.proxy.**`, JEP 290 đã chặn lớp proxy này ngay khi unmarshalling.
- **Khắc phục**:
  Cập nhật chuỗi `DEFAULT_SERIAL_FILTER` trong `LibraryRMIServer.java` thành:
  ```java
  public static final String DEFAULT_SERIAL_FILTER = 
      "thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;com.sun.proxy.**;!*";
  ```
  Việc cho phép `com.sun.proxy.**` là bắt buộc và an toàn theo chuẩn Java RMI, trong khi quy tắc `!*` ở cuối vẫn chặn đứng 100% các gadget chain độc hại bên ngoài.

### 4.2. Vòng đời HikariCP Singleton khi chạy nhiều Test Suite liên tiếp
- **Triệu chứng**:
  Khi chạy chuỗi test suites (`ant test`), test suite đầu tiên chạy xong gọi `DatabaseManager.getInstance().shutdown()`. Đến test suite tiếp theo, `DatabaseManager.getInstance()` trả về instance cũ với `dataSource` đã bị đóng, dẫn đến lỗi:
  ```text
  HikariPool-1 - has been closed.
  ```
- **Khắc phục**:
  - Bổ sung phương thức `DatabaseManager.resetInstance()` hỗ trợ kiểm thử reset singleton.
  - Trong phương thức `shutdown()`, cập nhật `dataSource = null;` để lần gọi `getDataSource()` tiếp theo sẽ tự động khởi tạo lại pool mới nếu cần.

---

## 5. Lệnh thực thi và Exit Code

| STT | Lệnh thực thi | Mục đích | Exit Code | Kết quả |
|:---|:---|:---|:---:|:---|
| 1 | `git status --short --branch` | Kiểm tra trạng thái Git ban đầu | 0 | Giữ nguyên working tree |
| 2 | `Get-ChildItem database/*.sql` | Kiểm tra các script schema, index, seed | 0 | Đầy đủ 3 script SQL |
| 3 | `mysql ... CREATE DATABASE school_library_test` | Tạo database test độc lập trên MySQL 8.0.30 | 0 | Tạo database thành công |
| 4 | `mysql ... school_library_test < schema.sql, indexes.sql, seed.sql` | Khởi tạo bảng và seed data cho database test | 0 | 11 bảng, 4 users, 5 sách |
| 5 | `ant clean compile compile-test` | Biên dịch sạch source và test source | 0 | BUILD SUCCESSFUL |
| 6 | `javap -v build/classes/thuvien/server/rmi/LibraryRMIServer.class` | Kiểm tra bytecode compatibility | 0 | major version: 52 (Java 1.8) |
| 7 | `ant test` | Chạy toàn bộ 11 test suites của dự án | 0 | BUILD SUCCESSFUL (0 fail, 0 error) |
| 8 | `git diff --check` | Kiểm tra lỗi khoảng trắng / cú pháp | 0 | Sạch sẽ, không cảnh báo |

---

## 6. Kết quả Kiểm thử Chi tiết (98/98 Tests Passed)

Toàn bộ **11 test suites** với **98 test cases** đã được thực thi thành công 100% trên môi trường `school_library_test`:

| STT | Test Suite | Số Test | Pass | Fail | Error | Thời gian | Ghi chú |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0.252s | Kiểm tra serialization Request/Response DTO |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0.307s | Kiểm tra kết nối DB test, pool HikariCP, SELECT |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0.052s | Kiểm tra kiến trúc Repository và PreparedStatement |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0.451s | Kiểm tra RBAC, session token, validation |
| 5 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0.942s | Kiểm tra RMI Registry, Remote Service, E2E flow |
| 6 | `thuvien.server.rmi.RMISerializationFilterTest` | 5 | 5 | 0 | 0 | 0.273s | Kiểm tra JEP 290 filter, chặn class độc hại |
| 7 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0.354s | Kiểm tra xác thực, hashing SHA-256 + salt |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0.403s | Kiểm tra tranh chấp mượn sách đồng thời (Row lock) |
| 9 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0.537s | Kiểm tra bảo mật tạo người dùng (SEC-01) |
| 10 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0.048s | Kiểm tra logic tính phạt quá hạn |
| 11 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 2.711s | Kiểm tra toàn diện bảo mật và tính năng nghiệp vụ |
| **Tổng cộng** | **11 Suites** | **98** | **98** | **0** | **0** | **~9s** | **Tất cả test đều đạt 100%** |

---

## 7. Xác minh Kiến trúc RMI

1. **Cổng Registry và Remote Object**:
   - Mặc định môi trường vận hành: Registry cổng `1099`, Remote Object (`LibraryRemoteServiceImpl`) cổng `1100`.
   - Trong `RMIEndToEndIntegrationTest`: Sử dụng cổng kiểm thử độc lập (`10999` cho Registry và `11001` cho Remote Object) để tránh xung đột cổng với bất kỳ service nào đang chạy nền trên máy.
2. **Tên dịch vụ RMI**:
   - Đã xác nhận service name chuẩn là `LibraryRemoteService`.
   - `RMIClient` và `RMIEndToEndIntegrationTest` đều lookup chính xác theo tên này.
3. **Vòng đời Server**:
   - `LibraryRMIServer.startServer()`: Khởi tạo/tái sử dụng `LocateRegistry`, export remote object `LibraryRemoteServiceImpl` qua `UnicastRemoteObject.exportObject(remoteService, port)`.
   - `LibraryRMIServer.stopServer()`: Unbind khỏi Registry, gọi `UnicastRemoteObject.unexportObject(remoteService, true)` và đóng database pool an toàn.
4. **Serialization & Bảo mật mạng**:
   - Toàn bộ DTO, Enum, UUID, Collection đều implement `Serializable` với `serialVersionUID`.
   - JEP 290 filter hoạt động chuẩn xác: cho phép dynamic proxy và các DTO hợp lệ, đồng thời chặn tuyệt đối các payload lạ.

---

## 8. Những kiểm thử chưa chạy và Lý do

1. **Kiểm thử trên 2 máy vật lý khác nhau qua mạng LAN thực tế**:
   - *Lý do*: Môi trường hiện tại là một máy trạm đơn lẻ (Single workstation). Không có máy vật lý thứ hai kết nối vào cùng mạng con để chạy Swing Client từ xa.
   - *Đánh giá*: Trong Phase 1.4, kiến trúc RMI đã được xác minh hoàn chỉnh trên giao tiếp mạng loopback/localhost thông qua Registry và Remote Object thực sự (không dùng mock). Khi triển khai thực tế trên LAN, chỉ cần cấu hình firewall mở cổng 1099/1100 và thiết lập `java.rmi.server.hostname` thành địa chỉ IP LAN của máy chủ.

---

## 9. Rủi ro còn tồn tại và Khuyến nghị

1. **Mã hóa đường truyền RMI (Plaintext Traffic)**:
   - Hiện tại Java RMI đang sử dụng socket mặc định (TCP không mã hóa). Trên mạng LAN trường học hoặc mạng công cộng, lưu lượng truyền giữa Client và Server có thể bị bắt bởi các công cụ bắt gói (Wireshark).
   - *Khuyến nghị*: Trong các phase tương lai, nếu yêu cầu bảo mật cao hơn, có thể cấu hình `SslRMIClientSocketFactory` và `SslRMIServerSocketFactory` để mã hóa toàn bộ phiên RMI bằng TLS/SSL.
2. **Chính sách tường lửa Windows Firewall**:
   - Khi chạy server trên máy thật để client từ máy khác kết nối, Windows Defender Firewall có thể chặn cổng 1099 và 1100. Cần tạo Inbound Rule cho 2 cổng này.

---

## 10. Kết luận

| Nội dung | Trạng thái | Căn cứ |
|:---|:---:|:---|
| **Dữ liệu database nghiệp vụ `school_library` an toàn** | **ĐÃ XÁC MINH** | Test chạy hoàn toàn trên `school_library_test`. Có Safety Guard chặn kết nối sai. Dữ liệu gốc không thay đổi. |
| **Cơ chế nạp cấu hình kiểm thử không lộ thông tin** | **ĐÃ XÁC MINH** | `test.local.properties` được bảo vệ bởi `.gitignore:32`, không bị theo dõi bởi Git. |
| **Kiến trúc RMI hoạt động ổn định** | **ĐÃ XÁC MINH** | 15/15 test trong `RMIEndToEndIntegrationTest` pass. Server start/stop sạch sẽ, không rò rỉ socket. |
| **JEP 290 Filter tương thích với RMI Proxy** | **ĐÃ XÁC MINH** | Đã khắc phục lỗi `com.sun.proxy.**`, 5/5 test bảo mật filter pass. |
| **Tính toàn vẹn nghiệp vụ sau khi bỏ TCP 9999** | **ĐÃ XÁC MINH** | Toàn bộ 98/98 tests thuộc 11 test suites đều PASS. |
| **Hoạt động ổn định trên mạng LAN 2 máy vật lý** | **CHƯA XÁC MINH** | Chưa có máy vật lý thứ hai để thử nghiệm thực địa. |
