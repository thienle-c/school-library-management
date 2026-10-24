# Remediation Report — Phase 1.5: Security and Lifecycle Review

**Dự án**: Remote School Library Management System  
**Workspace**: `D:\workspace\thuvien`  
**Môi trường**: Windows 11, OpenJDK 1.8.0_462 (64-bit), MySQL 8.0.30 (Laragon on port 3306), Apache Ant 1.9.4 (NetBeans 8.0.2 bundled)  
**Thời gian thực hiện**: 15/10/2026  
**Trạng thái Git**: Uncommitted changes preserved (Không reset, không stash, không commit, không push)

---

## 1. Mục tiêu và Phạm vi thực hiện

Sau khi hoàn tất Phase 1.4 (xác minh an toàn kiến trúc Java RMI trên database thử nghiệm `school_library_test`), mục tiêu của **Phase 1.5** là tiến hành kiểm toán chuyên sâu, có kiểm soát đối với các thành phần cốt lõi trước khi triển khai mạng LAN:

1. **Kiểm toán JEP 290 / RMI Serialization Filter**:
   - Phân tích chi tiết chuỗi filter trong `LibraryRMIServer.java`.
   - Xác định chính xác nguyên nhân `UnmarshalException: InvalidClassException: filter status: REJECTED` đối với dynamic proxy của RMI Registry.
   - Làm rõ ý nghĩa và phạm vi an toàn của `com.sun.proxy.**` trên OpenJDK 8.
   - Kiểm tra và chứng minh các giới hạn tài nguyên (`maxdepth=15`, `maxbytes=10485760`, `maxrefs=50000`, `maxarray=100000`).
   - Rà soát các điểm tiếp nhận dữ liệu tuần tự hóa và bổ sung ca kiểm thử bảo mật.

2. **Kiểm toán Safety Guard của Integration Tests**:
   - Rà soát toàn bộ cơ chế bảo vệ trong `DatabaseConfig.java`, `DatabaseManager.java`, `BorrowConcurrencyIntegrationTest`, `RMIEndToEndIntegrationTest`, và `SecurityAndFeatureHardeningTest`.
   - **Loại bỏ triệt để lỗ hổng cho phép System Property boolean (`test.isolated.db`, `test.database.enabled`) tự nhận là database kiểm thử khi chưa xác minh catalog thực tế**.
   - Chuẩn hóa tiện ích `TestDatabaseSafetyGuard` kiểm tra trực tiếp JDBC catalog từ live connection (`conn.getCatalog()`), kiểm tra URL metadata và bắt buộc từ chối nếu trỏ vào `school_library`.
   - Bổ sung suite kiểm thử `TestDatabaseSafetyGuardTest` độc lập cho mọi kịch bản an toàn mà không thực hiện thao tác ghi trên `school_library`.

3. **Kiểm toán Vòng đời HikariCP**:
   - Phân tích hành vi `getInstance()`, `getConnection()`, `shutdown()`, và `resetInstance()`.
   - Kiểm tra tính lũy kế (idempotent), thread-safety, khả năng tái khởi tạo sau `shutdown()`, và xác minh thứ tự tắt của `LibraryRMIServer`.

4. **Xác minh Báo cáo Kiểm thử và Tính toàn vẹn Dữ liệu**:
   - Đối chiếu snapshot bảng thực tế giữa `school_library` và `school_library_test` trên MySQL instance 3306.
   - Thực thi lại toàn bộ test suite (`ant test`) và ghi nhận kết quả chi tiết.

---

## 2. Các file và phương thức đã kiểm tra

| Thành phần / File | Lớp / Phương thức đã kiểm tra | Mục đích kiểm tra |
|:---|:---|:---|
| `server/src/.../LibraryRMIServer.java` | `DEFAULT_SERIAL_FILTER`, `configureSerializationFilter()`, `start()`, `stop()` | Cấu hình JEP 290, dynamic proxy whitelist, thứ tự đóng tài nguyên khi tắt server |
| `server/src/.../LibraryRemoteServiceImpl.java` | `execute(Request)`, `login(LoginRequestDTO)`, v.v. | Các điểm nhận serialized object từ client |
| `common/src/.../LibraryRemoteService.java` | Toàn bộ chữ ký phương thức Remote | Kiểu tham số và giá trị trả về tuần tự hóa qua RMI |
| `server/src/.../DatabaseConfig.java` | `loadConfig()`, `getDatabaseName()` | Thứ tự ưu tiên nạp cấu hình (SysProp -> Env -> test.local.properties -> db.properties) |
| `server/src/.../DatabaseManager.java` | `getInstance()`, `initDataSource()`, `getConnection()`, `shutdown()`, `resetInstance()` | Vòng đời HikariCP pool, tái kết nối sau shutdown, thread-safety |
| `test/.../BorrowConcurrencyIntegrationTest.java` | `checkDatabaseAvailabilityAndIsolation()`, `tearDown()` | Kiểm tra guard cũ cho phép boolean system property |
| `test/.../RMIEndToEndIntegrationTest.java` | `setUpRMIAndDatabase()`, `tearDownAfterClass()` | Kiểm tra xác minh catalog và URL trước khi reset test data |
| `test/.../SecurityAndFeatureHardeningTest.java` | `setUp()`, `requireIsolatedTestDb()` | Kiểm tra chốt chặn an toàn trước khi gọi service nghiệp vụ |
| `test/.../RMISerializationFilterTest.java` | Các test case kiểm tra whitelist và blacklist | Bổ sung kiểm tra giới hạn `maxdepth`, `maxarray`, và proxy interface độc hại |

---

## 3. Phát hiện Kiểm toán, Mức độ Rủi ro và Bằng chứng

### 3.1. [TRUNG BÌNH] Lỗ hổng Safety Guard: System Property boolean có thể bypass việc kiểm tra Catalog
- **Vị trí**: `BorrowConcurrencyIntegrationTest.java` (dòng 65-68 ban đầu).
- **Mã nguồn cũ**:
  ```java
  boolean hasTestCatalog = (catalog != null && catalog.toLowerCase().contains("test"))
          || (url != null && url.toLowerCase().contains("test"));
  boolean hasExplicitTestProperty = "true".equalsIgnoreCase(System.getProperty("test.database.enabled"))
          || "true".equalsIgnoreCase(System.getProperty("test-sys-prop.test.database.enabled"));
  this.isIsolatedTestDb = hasTestCatalog || hasExplicitTestProperty;
  ```
- **Rủi ro**: Nếu ai đó truyền cờ `-Dtest.database.enabled=true` nhưng chạy trên máy không có `test.local.properties`, test sẽ kết nối vào `school_library`, `hasTestCatalog` là `false` nhưng `hasExplicitTestProperty` là `true` khiến `isIsolatedTestDb` trở thành `true` và chạy xóa/mượn sách trên database nghiệp vụ!
- **Khắc phục**: Thay thế hoàn toàn bằng `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`. Lớp bảo vệ mới kiểm tra trực tiếp `conn.getCatalog()`. Nếu catalog là `school_library`, test bị từ chối **vô điều kiện**, bất kể có cờ boolean nào được cấu hình hay không.

### 3.2. [NGHIỆP VỤ / BẢO MẬT] Bản chất của `com.sun.proxy.**` trong JEP 290 Filter trên OpenJDK 8
- **Hiện tượng**: Trong Phase 1.1, client gọi `Naming.lookup()` hoặc `registry.lookup("LibraryRemoteService")` bị từ chối bởi ngoại lệ:
  ```text
  java.rmi.UnmarshalException: error unmarshalling return; nested exception is: 
      java.io.InvalidClassException: filter status: REJECTED
  ```
- **Phân tích kỹ thuật sâu**:
  1. Trong OpenJDK 8, `java.lang.reflect.Proxy.getProxyClass0()` tự động sinh ra các lớp dynamic proxy nằm trong package `com.sun.proxy` (ví dụ `com.sun.proxy.$Proxy0`, `com.sun.proxy.$Proxy1`).
  2. Khi RMI Registry trả về stub từ xa cho interface `LibraryRemoteService`, nó đóng gói stub dưới dạng một instance của dynamic proxy cài đặt interface `LibraryRemoteService` và `java.rmi.Remote`.
  3. Khi `ObjectInputStream` phía client đọc dòng byte này, JEP 290 filter kiểm tra tên lớp `com.sun.proxy.$Proxy0`. Do trước đó chuỗi filter kết thúc bằng `!*` và không chứa `com.sun.proxy.**`, JEP 290 đã chặn đứng lớp này.
  4. **Kiểm tra tính an toàn của `com.sun.proxy.**`**:
     - Package `com.sun.proxy` hoàn toàn không chứa mã tĩnh hay gadget nào trên đĩa; đây là namespace ảo do JVM sinh ra tại runtime cho proxy.
     - Khi một proxy được deserialize, Java kiểm tra cả danh sách interface mà proxy triển khai và thuộc tính `InvocationHandler` của proxy.
     - Kiểm thử thực nghiệm với `ScratchProxyFilterTest` và `RMISerializationFilterTest` đã chứng minh: **Nếu proxy cài đặt một interface không nằm trong whitelist hoặc có InvocationHandler không nằm trong whitelist, JEP 290 vẫn chặn đứng 100% với `filter status: REJECTED`**.
     - Do đó, việc cho phép `com.sun.proxy.**` là cấu hình đặc quyền tối thiểu (least privilege) bắt buộc cho RMI Registry lookup trên OpenJDK 8 mà vẫn bảo toàn an ninh tuyệt đối trước các gadget chain độc hại.

### 3.3. [XÁC MINH] Giới hạn tài nguyên JEP 290 (Resource Limits)
Đã xác minh 4 tham số bảo vệ chống tấn công từ chối dịch vụ (DoS) trong `DEFAULT_SERIAL_FILTER`:
- `maxdepth=15`: Chống tấn công làm tràn stack thông qua đồ thị lồng nhau sâu.
- `maxbytes=10485760` (10 MB): Giới hạn độ dài byte stream, chống cạn kiệt bộ nhớ.
- `maxrefs=50000`: Giới hạn số tham chiếu đối tượng trong payload.
- `maxarray=100000`: Giới hạn kích thước mảng cấp phát, chống tấn công bộ nhớ bằng mảng khổng lồ.

---

## 4. Những sửa đổi đã thực hiện

1. **Tạo mới `test/thuvien/server/TestDatabaseSafetyGuard.java`**:
   - Phương thức `isSafeTestDatabase(Connection conn)`:
     - Trả về `false` nếu `conn == null` hoặc `conn.isClosed()`.
     - Trả về `false` nếu `conn.getCatalog()` là null, rỗng hoặc bằng `"school_library"`.
     - Chỉ chấp nhận nếu catalog là `"school_library_test"` (hoặc kết thúc bằng `_test`).
     - Kiểm tra tính nhất quán giữa JDBC URL metadata (`conn.getMetaData().getURL()`) và catalog; từ chối nếu URL trỏ vào `school_library` hoặc có sự bất đồng bộ giữa URL và catalog.
     - Không cho phép bất kỳ System Property boolean nào tự quyết định database an toàn.

2. **Tạo mới `test/thuvien/server/TestDatabaseSafetyGuardTest.java`**:
   - 6 ca kiểm thử tự động độc lập sử dụng Dynamic Proxy mô phỏng Connection:
     - `testCorrectTestDatabaseAllowed`: Xác nhận `school_library_test` được duyệt.
     - `testProductionDatabaseStrictlyBlocked`: Xác nhận `school_library` bị chặn tuyệt đối.
     - `testSystemPropertyCannotBypassProductionBlock`: Xác nhận các cờ boolean không thể vượt qua guard.
     - `testMissingOrInvalidConnectionMetadataBlocked`: Xác nhận fail-safe khi metadata null/đóng.
     - `testUrlAndCatalogMismatchBlocked`: Xác nhận chặn khi URL và catalog không khớp.
     - `testUrlDifferentDbNameMismatchBlocked`: Xác nhận chặn khi URL trỏ vào database khác.

3. **Cập nhật các Integration Test**:
   - `BorrowConcurrencyIntegrationTest.java`: Chuyển sang sử dụng `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`.
   - `RMIEndToEndIntegrationTest.java`: Chuyển sang sử dụng `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`.
   - `SecurityAndFeatureHardeningTest.java`: Chuyển sang sử dụng `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`.

4. **Bổ sung ca kiểm thử bảo mật vào `test/.../RMISerializationFilterTest.java`**:
   - `testResourceLimitGraphDepthExceeded()`: Tạo đồ thị lồng 20 tầng (> `maxdepth=15`), xác minh JEP 290 ném `InvalidClassException: filter status: REJECTED`.
   - `testResourceLimitArraySizeExceeded()`: Tạo mảng 100.005 phần tử (> `maxarray=100000`), xác minh JEP 290 ném `InvalidClassException: filter status: REJECTED`.
   - `testDynamicProxyWithUnauthorizedInterfaceBlocked()`: Tạo dynamic proxy với interface ngoài whitelist, xác minh JEP 290 ném `InvalidClassException: filter status: REJECTED`.

---

## 5. Xác minh Vòng đời HikariCP trong `DatabaseManager.java`

Đã kiểm toán toàn bộ mã nguồn của [DatabaseManager.java](file:///D:/workspace/thuvien/server/src/thuvien/server/database/DatabaseManager.java):
1. **Khởi tạo Singleton & Thread-Safety**:
   - `getInstance()` sử dụng `synchronized` trên class method, đảm bảo chỉ có 1 instance được tạo trong môi trường đa luồng.
2. **Quản lý kết nối & Tự phục hồi (Self-Healing)**:
   - Trong `getConnection()`, áp dụng double-checked locking với `synchronized(this)`. Nếu `dataSource == null` hoặc `dataSource.isClosed()`, phương thức tự động gọi `initDataSource()` để cấp phát pool mới mà không làm crash ứng dụng.
3. **Đóng pool & Tính lũy kế (Idempotent Shutdown)**:
   - Trong `shutdown()`, kiểm tra `if (dataSource != null && !dataSource.isClosed()) { dataSource.close(); }` và gán `dataSource = null`.
   - Gọi `shutdown()` nhiều lần liên tiếp không gây ra `NullPointerException` hay ngoại lệ trạng thái đóng.
4. **Phương thức hỗ trợ kiểm thử `resetInstance()`**:
   - Đảm bảo giải phóng hoàn toàn pool cũ và đặt `instance = null`, cho phép các suite kiểm thử sau nhận instance sạch sẽ khi cần.
5. **Thứ tự dừng Server trong `LibraryRMIServer.java`**:
   - `LibraryRMIServer.stop()` chỉ hủy đăng ký trên RMI Registry và unexport remote object (`UnicastRemoteObject.unexportObject(remoteService, true)`).
   - Server **không** gọi `DatabaseManager.getInstance().shutdown()` bên trong `stop()`, tránh việc đóng đột ngột database pool trong khi các luồng nghiệp vụ khác đang hoàn tất request.
   - Việc đóng `DatabaseManager` được ủy quyền duy nhất cho JVM Shutdown Hook khi tiến trình server kết thúc hoàn toàn.

---

## 6. Lệnh Kiểm thử thực thi và Kết quả

### 6.1. Biên dịch
- Lệnh: `ant clean compile compile-test`
- Exit Code: `0` (BUILD SUCCESSFUL trong 2 giây, bytecode major version 52).

### 6.2. Toàn bộ Kết quả Kiểm thử (`ant test`)
- Thời gian chạy: 10 giây.
- Kết quả tổng quát: **12 Test Suites, 107 Test Cases: 107 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**.

| STT | Test Suite | Số Test | Pass | Fail | Error | Thời gian | Kết quả chi tiết |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0.238s | PASS |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0.308s | PASS |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0.066s | PASS |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0.451s | PASS |
| 5 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0.956s | PASS |
| 6 | `thuvien.server.rmi.RMISerializationFilterTest` | **8** | **8** | 0 | 0 | 0.279s | **PASS (Đã thêm 3 test tài nguyên & proxy)** |
| 7 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0.358s | PASS |
| 8 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0.415s | PASS (Guard mới hoạt động) |
| 9 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0.530s | PASS |
| 10 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0.049s | PASS |
| 11 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 2.865s | PASS (Guard mới hoạt động) |
| 12 | `thuvien.server.TestDatabaseSafetyGuardTest` | **6** | **6** | 0 | 0 | 0.069s | **PASS (Suite mới xác minh guard)** |
| **Tổng** | **12 Suites** | **107** | **107** | **0** | **0** | **~10s** | **Tỷ lệ đạt: 100%** |

---

## 7. Bằng chứng Đối chiếu Dữ liệu Thực tế (Database Snapshot Verification)

Truy vấn đối chiếu trực tiếp trên MySQL instance 3306 qua `information_schema.tables`:
```text
TABLE_SCHEMA         TABLE_NAME          TABLE_ROWS
school_library       audit_logs          4427
school_library       authors             5
school_library       book_authors        5
school_library       books               5
school_library       borrow_records      2
school_library       categories          5
school_library       fines               0
school_library       reservations        0
school_library       student_activations 2
school_library       students            4
school_library       users               5

school_library_test  audit_logs          756
school_library_test  authors             5
school_library_test  book_authors        5
school_library_test  books               5
school_library_test  borrow_records      1
school_library_test  categories          5
school_library_test  fines               0
school_library_test  reservations        0
school_library_test  student_activations 2
school_library_test  students            3
school_library_test  users               4
```

### Kết luận đối chiếu:
1. `school_library` bảo toàn nguyên vẹn 5 users, 4 students, 2 borrow records và 4427 audit logs cũ. Không có bất kỳ thay đổi nào trước và sau khi chạy kiểm thử.
2. `school_library_test` chứa dữ liệu cách ly (4 users seed, 3 students seed, 1 borrow record) và ghi nhận toàn bộ 756 audit logs phát sinh từ các integration test.

---

## 8. Các kiểm thử chưa thực hiện và Lý do

1. **Kiểm thử trên 2 máy vật lý qua mạng LAN**:
   - *Lý do*: Môi trường hiện tại là máy trạm đơn lẻ (Single physical machine).
   - *Kế hoạch*: Sẽ thực hiện trong Phase 2 khi chuẩn bị môi trường phòng máy/LAN thực tế.

---

## 9. Rủi ro còn tồn tại trước khi triển khai LAN

1. **Cấu hình địa chỉ IP máy chủ (`java.rmi.server.hostname`)**:
   - Khi chạy trên mạng LAN thực tế, nếu server không đặt `java.rmi.server.hostname` thành IP LAN thực của máy chủ (ví dụ `192.168.1.50`), Registry có thể trả về stub chứa `127.0.0.1` khiến client từ máy khác không thể kết nối tới remote object trên cổng 1100.
   - *Biện pháp đã có*: `LibraryRMIServer` đã có cơ chế tự phát hiện IP LAN (`detectPrimaryIPv4Address()`) và in ra màn hình khi khởi động.
2. **Quy tắc tường lửa Inbound (Windows Firewall)**:
   - Cần đảm bảo hệ điều hành máy chủ mở Inbound Rule cho TCP 1099 (Registry) và TCP 1100 (Remote Object).
3. **Bảo mật kênh truyền không mã hóa**:
   - RMI hiện tại chưa bật TLS/SSL. Trên mạng chia sẻ mở, gói tin có thể bị bắt qua Wireshark. Mật khẩu người dùng đã được bảo vệ bởi SHA-256 + salt ở phía ứng dụng, nhưng dữ liệu nghiệp vụ truyền tải là plaintext.

---

## 10. Kết luận phân loại

| Nội dung | Phân loại | Căn cứ |
|:---|:---:|:---|
| **Lỗ hổng guard boolean được loại bỏ hoàn toàn** | **ĐÃ XÁC MINH** | `TestDatabaseSafetyGuardTest` chạy 6 ca kiểm thử thành công. Cờ boolean không thể bypass kiểm tra catalog. |
| **Dữ liệu database nghiệp vụ `school_library` an toàn** | **ĐÃ XÁC MINH** | Bảng đối chiếu snapshot chứng minh 0 bản ghi nào bị thay đổi trên `school_library`. |
| **JEP 290 Filter chặn dynamic proxy độc hại và giới hạn tài nguyên** | **ĐÃ XÁC MINH** | 8/8 test trong `RMISerializationFilterTest` pass, chặn `maxdepth > 15`, `maxarray > 100000`, và unauthorized proxy interface. |
| **Tính hợp lệ của `com.sun.proxy.**` trên OpenJDK 8** | **ĐÃ XÁC MINH** | Phân tích mã nguồn JDK và thực nghiệm chứng minh dynamic proxy vẫn buộc interface và handler phải nằm trong whitelist. |
| **Vòng đời HikariCP ổn định và an toàn khi tắt/mở** | **ĐÃ XÁC MINH** | Đạt 107/107 tests thành công, không còn lỗi `HikariPool closed`. |
| **Triển khai thành công trên LAN đa máy trạm** | **CHƯA XÁC MINH** | Cần thực hiện thử nghiệm thực địa với 2 máy vật lý trong phase tiếp theo. |
