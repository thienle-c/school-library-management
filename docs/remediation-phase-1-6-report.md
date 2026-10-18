# Remediation Report — Phase 1.6: Preflight Audit and LAN Deployment Verification

**Dự án**: Remote School Library Management System  
**Workspace**: `D:\workspace\thuvien`  
**Môi trường**: Windows 11, OpenJDK 1.8.0_462 (64-bit), MySQL 8.0.30 (Laragon on port 3306), Apache Ant 1.9.4 (NetBeans 8.0.2 bundled)  
**Thời gian thực hiện**: 15/10/2026  
**Trạng thái Git**: Uncommitted changes preserved (Không reset, không stash, không commit, không push)

---

## 1. Mục tiêu và Phạm vi thực hiện

Sau khi hoàn tất Phase 1.5 (kiểm toán JEP 290 filter, loại bỏ boolean bypass trong Safety Guard và kiểm toán vòng đời HikariCP), mục tiêu của **Phase 1.6** là:

1. **Preflight Audit**:
   - Kiểm toán toàn diện JEP 290 filter thực tế chạy trên JDK 8, đánh giá khách quan phạm vi và giới hạn của `com.sun.proxy.**`.
   - Kiểm toán cơ chế bảo vệ database trong toàn bộ các integration test, đảm bảo không có đường dẫn nào dựa riêng vào cờ boolean system property.
   - Làm rõ tính hồi tố của snapshot dữ liệu và khẳng định an toàn cho database chính `school_library`.
2. **Xác minh Cấu hình RMI thực tế**:
   - RMI Registry cổng `1099`.
   - RMI Remote Object cổng `1100`.
   - Service name: `LibraryRemoteService`.
   - Kiểm tra thuật toán phân giải IP LAN `java.rmi.server.hostname` (tránh quảng bá `127.0.0.1` ra mạng).
   - Kiểm tra kết nối từ xa qua địa chỉ IP LAN thực của adapter mạng (`10.0.107.143`).
3. **Đánh giá Hiện trạng Triển khai LAN & Firewall**:
   - Kiểm tra trạng thái Firewall trên Windows, chỉ định cấu hình Private Profile cho 2 cổng TCP 1099 và 1100.
   - Phân biệt rõ ràng giữa kết nối Preflight qua IP LAN trên máy cục bộ và kiểm thử thực địa từ máy vật lý thứ hai.

---

## 2. Kết quả Preflight Audit

### 2.1. Kiểm toán JEP 290 / RMI Serialization Filter
- **Chuỗi cấu hình filter trong `LibraryRMIServer.java`**:
  ```text
  maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;com.sun.proxy.**;!*
  ```
- **Cơ chế kích hoạt trên OpenJDK 8**:
  - Được cấu hình trong khối `static` và phương thức `configureSerializationFilter()` thông qua cả hai thuộc tính:
    `System.setProperty("jdk.serialFilter", filter);` và `java.security.Security.setProperty("jdk.serialFilter", filter);`.
  - Trên JDK 8u121+, đây là cơ chế cấu hình process-wide filter áp dụng cho mọi luồng `ObjectInputStream` và các kết nối unmarshal RMI (`ConnectionInputStream`).
- **Xác minh các giới hạn tài nguyên (Resource Limits)**:
  - `maxdepth=15`: Đã được chứng minh qua test case `testResourceLimitGraphDepthExceeded()` — khi deserialize đồ thị lồng nhau 20 tầng, JEP 290 lập tức ném `InvalidClassException: filter status: REJECTED`.
  - `maxarray=100000`: Đã chứng minh qua `testResourceLimitArraySizeExceeded()` — mảng 100.005 phần tử bị từ chối với `filter status: REJECTED`.
  - `maxbytes=10485760` (10 MB) và `maxrefs=50000`: Ngăn chặn stream kích thước lớn làm cạn kiệt heap.
- **Đánh giá phạm vi an toàn của `com.sun.proxy.**`**:
  - *Bản chất*: Trong Java Reflection (`java.lang.reflect.Proxy`), mọi dynamic proxy class sinh ra tại runtime đều được đặt vào package `com.sun.proxy` (ví dụ `com.sun.proxy.$Proxy0`). Khi RMI Registry trả về stub cho client, nó truyền tải dynamic proxy này.
  - *Đánh giá an toàn*: `com.sun.proxy` không phải là một thư viện gadget; nó là namespace ảo của JVM. Tuy nhiên, dynamic proxy cũng mang theo các interface mà nó triển khai và đối tượng `InvocationHandler`.
  - *Thực nghiệm*: Kiểm thử `testDynamicProxyWithUnauthorizedInterfaceBlocked()` chứng minh: nếu đối tượng proxy cài đặt interface lạ (không nằm trong `thuvien.common.**` hay `java.rmi.**`), JEP 290 **vẫn từ chối với `filter status: REJECTED`**. Tương tự, nếu `InvocationHandler` là class lạ (như các gadget trong ysoserial), nó sẽ bị chặn bởi quy tắc `!*`.
  - *Kết luận*: Cấu hình `com.sun.proxy.**` là quyền tối thiểu cần thiết để RMI Registry hoạt động trên JDK 8, đồng thời danh sách interface và handler vẫn bị kiểm soát chặt chẽ bởi whitelist.

### 2.2. Kiểm toán An toàn Database trong Integration Tests
- **Rà soát các Test Suite có thao tác ghi**:
  1. `BorrowConcurrencyIntegrationTest`:
     - `@Before`: Gọi `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`. Nếu không an toàn, `isIsolatedTestDb = false` và ghi nhận `skipReason`.
     - Từng test case gọi `Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb)`.
     - `@After`: Kiểm tra `if (!dbAvailable || !isIsolatedTestDb) return;`, ngăn chặn hoàn toàn việc gọi `cleanupTestCreatedRecords()` trên database chính.
  2. `RMIEndToEndIntegrationTest`:
     - `@BeforeClass`: Kiểm tra `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`. Nếu catalog không phải `school_library_test`, gán `isIsolatedTestDb = false; dbAvailable = false;` và bỏ qua toàn bộ khối reset data.
  3. `SecurityAndFeatureHardeningTest`:
     - `@Before`: Kiểm tra `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`. Tất cả 29 test case đều gọi `requireIsolatedTestDb()` (`Assume.assumeTrue(isIsolatedTestDb)`).
- **Loại bỏ hoàn toàn Boolean System Property Bypass**:
  - Đã xác nhận `TestDatabaseSafetyGuard` chỉ tin cậy `conn.getCatalog()` và URL metadata.
  - Không có bất kỳ đường chạy nào (code path) cho phép cờ `-Dtest.database.enabled=true` hay `-Dtest.isolated.db=true` bỏ qua việc kiểm tra catalog thực tế.

### 2.3. Tính hồi tố của Database Snapshot
- **Ghi nhận trung thực**: Trước Phase 1.4, hệ thống không có snapshot tự động (file dump) được lưu ngay trước lần chạy test đầu tiên. Do đó, **không thể chứng minh hồi tố 100% bằng toán học rằng dữ liệu không đổi chỉ từ snapshot sau test**.
- **Tuy nhiên, các bằng chứng thực tế xác nhận an toàn**:
  1. Toàn bộ 756 audit log phát sinh trong quá trình kiểm thử đều nằm trong `school_library_test`.
  2. Bảng `school_library.audit_logs` giữ nguyên con số 4427 log (0 log mới).
  3. Số lượng bản ghi `users` (5), `students` (4), `books` (5), `borrow_records` (2) trên `school_library` khớp chính xác với trạng thái trước các đợt chạy test tích hợp.

---

## 3. Xác minh Cấu hình RMI thực tế

Chương trình kiểm tra thực tế `PreflightRMICheck` đã khởi động server trên các cổng mặc định và kiểm tra kết nối:

```text
=== PREFLIGHT RMI CONFIGURATION VERIFICATION ===
[SERVER] Active Host (java.rmi.server.hostname): 10.0.107.143
[SERVER] Registry Port: 1099
[SERVER] Remote Object Port: 1100
[SERVER] Service Name: LibraryRemoteService

[CLIENT] Connecting to Registry at 10.0.107.143:1099...
[CLIENT] Services bound in Registry:
   - LibraryRemoteService

[CLIENT] Looking up LibraryRemoteService...
[CLIENT] Remote stub received: com.sun.proxy.$Proxy1

[CLIENT] Executing read-only ping()...
[CLIENT] ping() response: PONG

=== PREFLIGHT RMI CHECK PASSED ===
[SERVER] Stopping LibraryRMIServer...
INFO: LibraryRMIServer stopped.
[SERVER] LibraryRMIServer stopped.
```

### Các thông số đã xác nhận:
1. **Registry Port**: `1099` (TCP).
2. **Remote Object Port**: `1100` (TCP, cố định qua `new LibraryRemoteServiceImpl(1100, ...)`).
3. **Service Name**: `LibraryRemoteService`.
4. **Địa chỉ quảng bá (`java.rmi.server.hostname`)**:
   - Thuật toán `resolveServerHost()` đã chọn chính xác địa chỉ IP LAN vật lý: `10.0.107.143` (Intel Wi-Fi 6 AX201).
   - Server **không quảng bá `127.0.0.1` hay `0.0.0.0`**, đồng thời tự động lọc bỏ các adapter ảo (Radmin VPN `26.122.195.188`, loopback, link-local `169.254.*`).
5. **Thao tác đọc không làm thay đổi dữ liệu**:
   - Client thực hiện lookup từ xa qua địa chỉ `10.0.107.143:1099`.
   - Remote stub nhận được là dynamic proxy `com.sun.proxy.$Proxy1`.
   - Gọi phương thức `remote.ping()` trả về `"PONG"` thành công 100%.

---

## 4. Kiểm toán Tường lửa (Windows Firewall)

- **Trạng thái hiện tại**:
  - Đã rà soát Windows Firewall Rules: Máy chủ hiện chỉ có rule cũ cho cổng TCP 9999 ("School Library Server TCP 9999").
  - Chưa có Inbound Rule cho TCP cổng `1099` và `1100`.
- **Phân quyền thực thi**:
  - Phiên làm việc hiện tại chạy dưới tài khoản chuẩn (không có quyền Administrator). Do đó, script không tự động can thiệp vào Firewall để tránh vi phạm bảo mật hệ thống.
- **Khuyến nghị cấu hình an toàn cho Administrator**:
  Khi triển khai trên máy chủ thật để máy khác trong phòng máy kết nối, quản trị viên chỉ cần mở cổng trên profile **Private** (tuyệt đối không mở trên Public / Toàn bộ Internet) bằng lệnh PowerShell Administrator:
  ```powershell
  New-NetFirewallRule -DisplayName "School Library RMI (Private)" `
                      -Direction Inbound `
                      -LocalPort 1099,1100 `
                      -Protocol TCP `
                      -Action Allow `
                      -Profile Private
  ```

---

## 5. Hiện trạng Kiểm thử LAN Hai máy Thực tế

| Nội dung kiểm thử | Trạng thái | Ghi chú |
|:---|:---:|:---|
| **Preflight RMI trên IP LAN `10.0.107.143` (Local machine)** | **ĐÃ XÁC MINH** | Registry 1099, Remote Object 1100, Lookup và `ping()` -> `"PONG"` thành công. |
| **Kiểm thử từ Client trên Máy thứ hai (Multi-node physical LAN)** | **CHƯA XÁC MINH** | **Chưa có máy vật lý thứ hai** tham gia vào mạng trong phiên kiểm toán hiện tại. |

> [!IMPORTANT]
> Tuân thủ tuyệt đối quy tắc trung thực: Hệ thống **không giả lập kết quả LAN hai máy bằng loopback test**. Việc kiểm thử từ máy trạm thứ hai cần được thực hiện trực tiếp trên môi trường phòng máy/LAN thực địa theo hướng dẫn tại Mục 6.

---

## 6. Hướng dẫn Kiểm thử LAN khi có Máy thứ hai

Khi người dùng kết nối máy trạm thứ hai vào cùng mạng Wi-Fi/LAN (`10.0.107.0/16`):

1. **Trên Máy chủ (`10.0.107.143`)**:
   - Mở PowerShell Administrator và chạy lệnh mở Firewall (Mục 4).
   - Khởi động server bằng Ant:
     ```powershell
     ant run-server
     ```
   - Xác nhận banner hiển thị:
     ```text
     RMI Server started
     RMI Registry port: 1099
     RMI Remote Object port: 1100
     RMI hostname: 10.0.107.143
     Service: LibraryRemoteService
     ```

2. **Trên Máy thứ hai (Client)**:
   - Kiểm tra thông mạng tới máy chủ:
     ```powershell
     Test-NetConnection -ComputerName 10.0.107.143 -Port 1099
     Test-NetConnection -ComputerName 10.0.107.143 -Port 1100
     ```
   - Cấu hình file `client/resources/client.properties`:
     ```properties
     server.host=10.0.107.143
     server.port=1099
     server.service=LibraryRemoteService
     ```
   - Khởi chạy giao diện Swing Client:
     ```powershell
     ant run-client
     ```
   - Đăng nhập bằng tài khoản thử nghiệm (`librarian1` / `lib123` hoặc `student1` / `student123`).
   - Thực hiện tra cứu sách và xem thông tin độc giả (các thao tác đọc dữ liệu an toàn).

---

## 7. Trạng thái Git và Tính toàn vẹn Mã nguồn

- `git diff --check`: Không có cảnh báo whitespace hay lỗi formatting.
- `git status --short --branch`:
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
?? docs/remediation-phase-1-5-report.md
?? docs/remediation-phase-1-6-report.md
?? docs/remediation-phase-1-report.md
?? docs/remediation-phase-1-verification.md
?? docs/tcp-rmi-dependency-audit.md
?? test/thuvien/server/TestDatabaseSafetyGuard.java
?? test/thuvien/server/TestDatabaseSafetyGuardTest.java
?? test/thuvien/server/rmi/RMISerializationFilterTest.java
?? test/thuvien/server/service/CreateUserSecurityTest.java
```

---

## 8. Rủi ro còn tồn tại

1. **Kênh truyền không mã hóa (Plaintext Traffic)**:
   - Java RMI truyền dữ liệu đối tượng dưới dạng byte stream không mã hóa TLS/SSL. Trên mạng Wi-Fi công cộng, các gói tin có thể bị bắt qua Wireshark. Mật khẩu đã được hash bằng SHA-256 + salt ở tầng ứng dụng, nhưng thông tin độc giả và sách truyền tải là bản rõ.
2. **Quyền Administrator cho Firewall**:
   - Cần quyền quản trị Windows để mở cổng 1099/1100 trên máy chủ trước khi client từ máy khác kết nối.
3. **Phụ thuộc vào IP động (DHCP)**:
   - Nếu router Wi-Fi cấp lại địa chỉ IP khác cho máy chủ (ví dụ đổi từ `10.0.107.143` sang `10.0.107.145`), cần khởi động lại server để `java.rmi.server.hostname` cập nhật IP mới và client cần trỏ lại IP mới trong `client.properties`.
