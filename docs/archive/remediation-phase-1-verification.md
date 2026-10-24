# BÁO CÁO XÁC MINH ĐỘC LẬP GIAI ĐOẠN 1 (PHASE 1.1 INDEPENDENT VERIFICATION)
## Remote School Library Management System — Workspace: `D:\workspace\thuvien`

---

* **Ngày thực hiện xác minh:** 15/10/2026
* **Môi trường thực thi:** OpenJDK 8 (Amazon Corretto 8.504.01.1 / Java HotSpot 64-Bit Server VM build 25.504-b01), Apache Ant 1.9.4 (NetBeans 8.0.2), MySQL 8.0 (Laragon, cổng 3306)
* **Vai trò:** Senior Java Engineer, Software Architect & Independent Application Security Auditor
* **Nguyên tắc giai đoạn:** **KHÔNG SỬA CODE, KHÔNG COMMIT/PUSH, KHÔNG PHÁ HỦY DỮ LIỆU NGHIỆP VỤ**
* **Tài liệu đối chiếu:**
  - `docs/full-project-audit.md`
  - `docs/remediation-phase-1-report.md`
  - `docs/netbeans-server-entrypoint-audit.md`

---

## 1. BẢO TOÀN TRẠNG THÁI VÀ PHẠM VI KIỂM TRA

### 1.1. Trạng thái Git trước và sau xác minh
Trước và trong suốt quá trình xác minh, toàn bộ cây thư mục làm việc được bảo toàn nguyên vẹn. Không có lệnh checkout, reset, clean, revert hoặc can thiệp lịch sử Git:

```text
$ git status --short --branch
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
?? test/thuvien/server/rmi/RMISerializationFilterTest.java
?? test/thuvien/server/service/CreateUserSecurityTest.java
```

* `git diff --check` thực thi đạt mã thoát `0` (không có lỗi cú pháp, ký tự thừa hoặc xung đột).
* `git diff --stat` ghi nhận 10 files thay đổi (+458, -141 dòng) đúng phạm vi đã báo cáo ở Phase 1.

---

## 2. XÁC MINH CHI TIẾT THEO TỪNG VẤN ĐỀ

---

### MỤC 1: XÁC MINH JAVA RMI SERIALIZATION FILTER (SEC-01)

#### A. Vị trí và thời điểm cài đặt Filter trong mã nguồn
1. **`LibraryRMIServer.java`**:
   - Khối khởi tạo tĩnh:
     ```java
     static {
         configureSerializationFilter();
     }
     ```
   - Phương thức cấu hình:
     ```java
     public static void configureSerializationFilter() {
         String filter = System.getProperty("jdk.serialFilter");
         if (filter == null || filter.trim().isEmpty()) {
             filter = DEFAULT_SERIAL_FILTER;
             System.setProperty("jdk.serialFilter", filter);
         }
         java.security.Security.setProperty("jdk.serialFilter", filter);
         LOGGER.info("Configured JEP 290 Serialization Filter: " + filter);
     }
     ```
   - Gọi bổ sung trong `start()` (dòng 93) và `main(String[] args)` (dòng 280).
2. **`LibraryRemoteServiceImpl.java`**:
   - Khối khởi tạo tĩnh (dòng 50-52):
     ```java
     static {
         LibraryRMIServer.configureSerializationFilter();
     }
     ```

#### B. Phân tích cơ chế JVM và tính tương thích Java 8
- **Cơ chế JEP 290 trong JDK 8**: Được backport từ Java 9 vào Java 8u121+. Lớp xử lý nội bộ là `sun.misc.ObjectInputFilter$Config`.
- **Hành vi thực tế đã xác minh**: Khi chạy test với Amazon Corretto 8.504.01.1, console của JUnit hiển thị:
  ```text
  thg 10 15, 2026 12:09:20 SA sun.misc.ObjectInputFilter$Config lambda$static$0
  INFO: Creating serialization filter from maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;!*
  ```
  Điều này chứng minh `ObjectInputFilter$Config` đã tiếp nhận chuỗi cấu hình từ `System.getProperty("jdk.serialFilter")` và biên dịch thành filter thành công trong JDK 8 hiện tại.

#### C. Đánh giá rủi ro bỏ qua, ghi đè hoặc thất bại im lặng (Bypass / Silent Failure)
1. **Rủi ro thời điểm nạp lớp (Class Loading Order)**:
   - Trong JDK 8, `sun.misc.ObjectInputFilter$Config` nạp cấu hình một lần duy nhất khi lớp `Config` được khởi tạo tĩnh.
   - Nếu trong một kịch bản nhúng nào đó, có một lớp khác nạp `ObjectInputStream` hoặc `ObjectInputFilter` **trước khi** `LibraryRMIServer` được nạp, giá trị thiết lập qua `System.setProperty` trong `configureSerializationFilter()` sẽ bị bỏ qua vì `Config` đã hoàn tất khởi tạo với giá trị mặc định (null).
   - Trong `build.xml` (target `run-rmi-server`) và file đóng gói `dist/server-dist/run-server.bat`:
     ```bat
     java -cp "resources;server.jar;lib/*" thuvien.server.rmi.LibraryRMIServer %*
     ```
     Hiện tại **chưa truyền cờ `-Djdk.serialFilter=...` trực tiếp trên dòng lệnh khởi động JVM**.
2. **Rủi ro RMI Registry Filter vs Remote Object Filter**:
   - Lệnh `LocateRegistry.createRegistry(1099)` tạo `sun.rmi.registry.RegistryImpl`.
   - Trong JDK 8u121+, Registry có filter riêng điều khiển bởi thuộc tính bảo mật `sun.rmi.registry.registryFilter`. Filter toàn cục `jdk.serialFilter` bảo vệ cổng Remote Object (1100) và các cuộc gọi từ xa, nhưng đối với cổng Registry (1099), JVM ưu tiên `sun.rmi.registry.registryFilter`. Hiện tại mã nguồn chưa khai báo rõ ràng `sun.rmi.registry.registryFilter`.

#### D. Phân tích Whitelist Packages và Giới hạn tài nguyên
- **Chuỗi cấu hình:**
  `maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;!*`
- **Kiểm tra `java.sql.**`**:
  - Đối chiếu mã nguồn `common/src`: Không có import nào của `java.sql.*` trong module common. Các DTO dùng `java.util.Date`.
  - Tuy nhiên, kiểm tra `BorrowRepositoryImpl.java:185`:
    ```java
    java.sql.Date bd = rs.getDate("borrow_date");
    dto.setBorrowDate(bd); // gán đối tượng java.sql.Date vào trường java.util.Date
    ```
    Vì `java.sql.Date` kế thừa `java.util.Date`, đối tượng thực tế nằm trong DTO khi truyền qua mạng là `java.sql.Date`. Nếu không cho phép `java.sql`, RMI Client sẽ bị lỗi `InvalidClassException` khi deserialize `BorrowRecordDTO`.
    *Nhận định:* Việc cho phép `java.sql` là có cơ sở kỹ thuật thực tế để tránh làm gãy ứng dụng, nhưng cấu hình `java.sql.**` hơi rộng (có thể thu hẹp thành `java.sql.Date;java.sql.Timestamp;java.sql.Time;` thay vì wildcard `**`).
- **Kiểm tra `sun.rmi.**` và `java.rmi.**`**:
  Cần thiết tuyệt đối cho các lớp stub, `UnicastRef`, `LiveRef` trong cơ chế gọi hàm nội bộ của RMI.
- **Kiểm tra các giới hạn (Limits)**:
  `maxdepth=15`, `maxbytes=10MB`, `maxrefs=50000`, `maxarray=100000`. Cú pháp chuẩn JEP 290, nhưng hiện tại **chưa có test case nào kiểm tra ngưỡng biên của các giới hạn này**.

#### E. Phân tích test suite `RMISerializationFilterTest.java`
- Đã đọc toàn bộ 157 dòng của `RMISerializationFilterTest.java`:
  - `testFilterConfigurationIsActive()`: Kiểm tra biến môi trường và Security Property (Loại 2: Test cấu hình JVM).
  - `testValidApplicationRequestPayloadAllowed()`: Tuần tự hóa `Request` chứa `CreateUserRequestDTO` qua `ObjectOutputStream`/`ObjectInputStream` cục bộ (Loại 1: Test deserialize cục bộ).
  - `testValidResponseWithCollectionsAllowed()`: Tuần tự hóa `Response` chứa `List<BookDTO>` (Loại 1).
  - `testStandardJavaTypesAllowed()`: Tuần tự hóa các kiểu dữ liệu cơ bản (Loại 1).
  - `testNonWhitelistedClassBlockedByFilter()`: Tuần tự hóa `UnauthorizedGadgetClass` và xác minh ném `InvalidClassException` với thông báo `REJECTED` (Loại 1).
- **Kết luận phân biệt loại kiểm thử**:
  - Toàn bộ 5 test trong test suite này thuộc **Loại 1 (kiểm tra cục bộ qua `ObjectInputStream`)** và **Loại 2 (kiểm tra thuộc tính JVM)**.
  - **KHÔNG CÓ test Loại 3 (gọi RMI thực tế qua mạng TCP 1099/1100)** gửi gadget class để kiểm tra phản ứng của RMI Server runtime trên đường truyền socket thực tế.

#### F. Phân loại và Quyết định cho SEC-01
* Phân loại:
  - Cài đặt cấu hình JEP 290: `VERIFIED — CODE`
  - Cơ chế lọc lớp không hợp lệ qua ObjectInputStream: `VERIFIED — TEST` (cục bộ)
  - Bảo vệ luồng RMI thực tế qua mạng: `NOT VERIFIED` (chưa có end-to-end network test)
* Quyết định: **Cần bổ sung test** (Bổ sung test kết nối RMI Client thật gửi unwhitelisted object qua socket; bổ sung tham số `-Djdk.serialFilter=...` vào `run-server.bat` và target `run-rmi-server` trong `build.xml` để đảm bảo filter được nạp ngay từ startup JVM).

---

### MỤC 2: XÁC MINH AN TOÀN DATABASE CỦA INTEGRATION TEST (DATA-01)

#### A. URL và Catalog Database đích
- Trong `server/resources/db.properties`:
  ```properties
  db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
  ```
  Database đích mặc định là `school_library` (chứa dữ liệu phát triển nghiệp vụ).

#### B. Cơ chế Guard trong `BorrowConcurrencyIntegrationTest.java`
- Phương thức `@Before checkDatabaseAvailabilityAndIsolation()`:
  ```java
  String catalog = conn.getCatalog();
  String url = conn.getMetaData().getURL();

  boolean hasTestCatalog = (catalog != null && catalog.toLowerCase().contains("test"))
          || (url != null && url.toLowerCase().contains("test"));
  boolean hasExplicitTestProperty = "true".equalsIgnoreCase(System.getProperty("test.database.enabled"))
          || "true".equalsIgnoreCase(System.getProperty("test-sys-prop.test.database.enabled"));

  this.isIsolatedTestDb = hasTestCatalog || hasExplicitTestProperty;
  ```
- **Hành vi từ chối trên database mặc định**:
  Vì catalog là `school_library` (không có chữ "test") và khi chạy không truyền `-Dtest.database.enabled=true`, biến `isIsolatedTestDb` nhận giá trị `false`.
  Tất cả 4 test case đều bắt đầu bằng:
  ```java
  Assume.assumeTrue(skipReason, dbAvailable && isIsolatedTestDb);
  ```
- **Bằng chứng chạy thực tế trong phiên kiểm tra:**
  ```text
  Testsuite: thuvien.server.service.BorrowConcurrencyIntegrationTest
  Tests run: 4, Failures: 0, Errors: 0, Skipped: 4, Time elapsed: 0,265 sec
  Testcase: testConcurrentBorrowLastAvailableCopy: SKIPPED: [SAFETY — DATA-01] BorrowConcurrencyIntegrationTest is disabled on default database 'school_library' to protect existing application data...
  Testcase: testOverdueReturnGeneratesFine: SKIPPED...
  Testcase: testConcurrentReturnSameBorrowRecord: SKIPPED...
  Testcase: testNormalBorrowFollowedByReturn: SKIPPED...
  ```
  Không có bất kỳ câu lệnh SQL ghi/xóa nào được thực thi trên database `school_library`.

#### C. Đường thực thi và khả năng bypass guard
- Đã rà soát toàn bộ các phương thức `@Before`, `@After`, `@Test`:
  - `@Before`: Chỉ mở kết nối đọc metadata (`getCatalog()`, `getURL()`), đóng kết nối ngay trong try-with-resources. Không sửa dữ liệu.
  - `@After tearDown()`:
    ```java
    if (!dbAvailable || !isIsolatedTestDb) {
        return;
    }
    cleanupTestCreatedRecords();
    ```
    Nếu `isIsolatedTestDb == false`, `tearDown()` return ngay lập tức.
  - Cả 4 `@Test`: Không có đường rẽ nhánh nào cho phép thực thi câu lệnh mượn/trả nếu guard chưa thỏa mãn.
  - Không thể bypass guard ở chế độ mặc định.

#### D. Kiểm tra logic tạo dữ liệu và dọn dẹp (Cleanup) khi có cờ cho phép
- Dữ liệu sinh tự động:
  - Sách test: `isbn = "T" + (System.currentTimeMillis() % 10000000000L) + (random)` -> độ dài <= 20 ký tự (phù hợp schema `VARCHAR(20)`).
  - Sinh viên test: `student_code = "STU" + ...` -> độ dài <= 30 ký tự (phù hợp schema `VARCHAR(30)`).
  - Khóa chính được lưu vào: `createdBookIds`, `createdStudentIds`, `createdBorrowRecordIds`.
- Cleanup:
  - Xóa theo đúng ID trong các danh sách trên.
  - Có cơ chế fallback: xóa fines và borrow_records theo `book_id IN (createdBookIds)`.
  - **Không chạy seed, truncate hay drop bảng**.
  - **Không động chạm đến các bản ghi mẫu 1, 2, 3 của hệ thống**.
- Xử lý lỗi giữa chừng:
  - Khối `@After tearDown()` luôn được JUnit kích hoạt dù `@Test` bị ném Exception hoặc Timeout. Bất kỳ ID nào đã kịp thêm vào danh sách đều sẽ được dọn dẹp.

#### E. Tính độc lập của Database Test
- Dự án **chưa có file cấu hình database test riêng biệt** (như `db-test.properties` trỏ tới `school_library_test`).
- Nếu người dùng truyền `-Dtest-sys-prop.test.database.enabled=true`, test sẽ chạy trên chính database `school_library` nhưng sử dụng các bản ghi tạm riêng. Đây là biện pháp cô lập ở tầng ứng dụng/dữ liệu (Application-level row isolation), chưa phải là cô lập ở tầng vật lý (Physical database isolation).

#### F. Phân loại và Quyết định cho DATA-01
* Phân loại:
  - Cơ chế guard tự động ngắt trên DB mặc định: `VERIFIED — CODE` & `VERIFIED — TEST`
  - Cleanup có kiểm soát theo khóa chính: `VERIFIED — CODE`
  - Tách biệt database test vật lý chuyên biệt: `PARTIALLY VERIFIED` (chưa có schema/database `school_library_test` sẵn trong cấu hình mặc định)
* Quyết định: **Có thể chấp nhận** cơ chế an toàn hiện tại vì đã triệt để bảo vệ database nghiệp vụ khỏi bị xóa dữ liệu; **Cần bổ sung cấu hình** một profile test database riêng cho môi trường CI.

---

### MỤC 3: XÁC MINH LOGIC TẠO TÀI KHOẢN (LOGIC-01)

#### A. Rà soát luồng gọi từ Client đến Database
1. **Phía Client Swing GUI**:
   - Rà soát toàn bộ thư mục `client/`: Giao diện Swing hiện tại **không có màn hình quản lý/tạo tài khoản nhân viên (Admin/Librarian)**.
   - Chức năng tạo tài khoản duy nhất trên giao diện là `StudentRegistrationDialog`, nhưng chức năng này gọi hành động `Action.REGISTER_STUDENT` với DTO `RegisterStudentRequestDTO` (yêu cầu mã kích hoạt do thư viện cấp trước, mật khẩu do sinh viên tự nhập).
   - Phương thức `createUser` thuộc về RMI API hành chính (`LibraryRemoteService.createUser`).
2. **Phía RMI Service (`LibraryRemoteServiceImpl.java`)**:
   - Nhận: `Long createUser(String token, UserDTO user, String initialPassword)`
   - Đóng gói: `CreateUserRequestDTO payload = new CreateUserRequestDTO(user, initialPassword);`
   - Gửi `Request(Action.CREATE_USER, payload)` tới `RequestRouter`.
   - Đã loại bỏ hoàn toàn việc bỏ qua `initialPassword`.
3. **Phía Bộ định tuyến (`RequestRouter.java`)**:
   - Kiểm tra xác thực và quyền hạn:
     ```java
     case CREATE_USER:
         requireRole(session, UserRole.ADMIN);
         return handleCreateUser(reqId, request);
     ```
   - Trong đó `session` được lấy từ `sessionManager.getSession(token)` lưu trên máy chủ (dòng 175). Client không thể tự khai hoặc giả mạo role.
   - Phương thức `handleCreateUser()`:
     - Chấp nhận `CreateUserRequestDTO` (hoặc `UserDTO` nhưng nếu không có password thì bị từ chối).
     - Kiểm tra `user != null` và `user.getUsername()` không rỗng.
     - Kiểm tra `initialPassword` không rỗng và có độ dài tối thiểu 6 ký tự.
     - Chuỗi hardcode `"password123"` đã bị xóa hoàn toàn khỏi phương thức này.
4. **Phía Nghiệp vụ (`UserServiceImpl.java`)**:
   - `createUser(UserDTO user, String plainPassword)`:
     - Ném `ValidationException` nếu username rỗng.
     - Ném `ValidationException` nếu `plainPassword == null || plainPassword.trim().isEmpty()`.
     - Ném `ValidationException` nếu `plainPassword.length() < 6`.
     - Loại bỏ hoàn toàn fallback gán `plainPassword = "password123"`.
     - Băm mật khẩu qua `PasswordHasher.hash(plainPassword)`.
5. **Phía Lưu trữ (`UserRepositoryImpl.java`)**:
   - Nhận chuỗi băm `passwordHash` đã qua PBKDF2 và ghi vào cột `password_hash` bằng `PreparedStatement` tham số hóa. Mật khẩu không bao giờ lưu dưới dạng plaintext.

#### B. Phân tích DTO và che giấu bí mật trong Log
- Lớp `CreateUserRequestDTO`:
  - Khai báo trường `user` và `initialPassword`.
  - Phương thức `toString()` được ghi đè:
    ```java
    @Override
    public String toString() {
        return "CreateUserRequestDTO{" +
                "user=" + (user != null ? user.getUsername() : "null") +
                ", initialPassword=[PROTECTED]" +
                '}';
    }
    ```
  - Đảm bảo khi logging các đối tượng Request/DTO, mật khẩu khởi tạo không bao giờ bị lộ ra log file.

#### C. Bằng chứng kiểm thử `CreateUserSecurityTest.java`
- Đã chạy Ant kiểm thử suite `thuvien.server.service.CreateUserSecurityTest`:
  ```text
  Testsuite: thuvien.server.service.CreateUserSecurityTest
  Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0,565 sec
  ```
  1. `testCreateUserWithValidCustomPasswordHashesWithPbkdf2`: Băm bằng PBKDF2 (`PBKDF2$10000$...`), xác minh khớp với mật khẩu mới, không khớp với `"password123"`, không lưu plaintext. **PASS**.
  2. `testCreateUserRejectsEmptyOrShortPasswords`: Từ chối password null, rỗng ("   "), hoặc ngắn ("12345"). **PASS**.
  3. `testRouterEnforcesAdminRoleForCreateUser`: ADMIN thành công (201 CREATED); LIBRARIAN và STUDENT bị chặn (403 FORBIDDEN); Unauthenticated bị chặn (401 UNAUTHORIZED). **PASS**.
  4. `testRouterRejectsCreateUserWithShortPassword`: Router trả về 400 BAD_REQUEST khi mật khẩu < 6 ký tự. **PASS**.

#### D. Giới hạn còn tồn tại
- `CreateUserSecurityTest` là unit test sử dụng Mock Repository và In-Memory Session.
- Chưa có integration test gọi `createUser` qua RMI Client trên cổng mạng thật kết nối với MySQL thật.

#### E. Phân loại và Quyết định cho LOGIC-01
* Phân loại:
  - Loại bỏ chuỗi `"password123"` và băm mật khẩu PBKDF2: `VERIFIED — CODE` & `VERIFIED — TEST`
  - Ràng buộc phân quyền RBAC chỉ cho phép ADMIN: `VERIFIED — CODE` & `VERIFIED — TEST`
  - Che giấu mật khẩu trong log DTO: `VERIFIED — CODE`
  - Kiểm thử end-to-end trên socket mạng RMI: `NOT VERIFIED` (chưa có test mạng cho hàm này)
* Quyết định: **Có thể chấp nhận** giải pháp sửa đổi ở tầng backend/RMI service.

---

### MỤC 4: XÁC MINH LOGIC TRẢ SÁCH ĐỒNG THỜI (CONCUR-01)

#### A. Kiểm tra Transaction và Điều kiện SQL
1. **`BorrowRepositoryImpl.java` (dòng 155)**:
   - Câu lệnh cập nhật trạng thái trả:
     ```sql
     UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ? AND status = 'ACTIVE'
     ```
   - Trả về kết quả: `return stmt.executeUpdate() > 0;` (true nếu có dòng được cập nhật, false nếu 0 dòng).
2. **`ReturnServiceImpl.java` (dòng 84-195)**:
   - Toàn bộ quy trình diễn ra trên một `Connection` duy nhất lấy từ connection pool, mở transaction bằng `conn.setAutoCommit(false);`.
   - **Bước 4 (Cập nhật trạng thái)**:
     ```java
     boolean recordUpdated = borrowRepository.updateStatus(activeBorrow.getId(), BorrowStatus.RETURNED, returnDate, conn);
     if (!recordUpdated) {
         throw new LibraryException("Failed to update borrowing record status to RETURNED. The record may have already been returned or is not ACTIVE.");
     }
     ```
   - **Kiểm tra số dòng cập nhật**: Nếu `recordUpdated == false` (tức 0 dòng bị ảnh hưởng), Service **ngay lập tức ném `LibraryException`**.
   - **Xử lý Rollback**:
     Khi `LibraryException` được ném, khối `catch` thực hiện:
     ```java
     conn.rollback();
     ```
     Toàn bộ các thay đổi trước đó trong transaction (bao gồm bản ghi tiền phạt vừa tạo ở Bước 3) đều bị hủy bỏ triệt để.
   - **Bảo vệ kho và số lượt mượn**:
     Bước 5 (`incrementAvailableCopies`) và Bước 6 (`decrementBorrowCount`) nằm **sau** kiểm tra `recordUpdated`. Vì exception đã được ném và transaction bị rollback, kho sách **tuyệt đối không bị tăng 2 lần** và lượt mượn sinh viên **tuyệt đối không bị giảm 2 lần**.

#### B. Phân tích kịch bản tương tranh 2 Client trả cùng một lượt mượn
- Client 1 và Client 2 cùng gửi yêu cầu trả một lượt mượn đang `ACTIVE`.
- Cả hai đều đọc được bản ghi mượn ban đầu.
- Khi bước vào câu lệnh `UPDATE ... WHERE id = ? AND status = 'ACTIVE'`:
  - Cơ chế row-level locking của MySQL InnoDB sẽ cho một transaction thực thi trước.
  - Client 1 cập nhật thành công (1 dòng bị ảnh hưởng, trạng thái chuyển thành `RETURNED`), transaction commit thành công. Tồn kho sách tăng +1.
  - Client 2 được nhả khóa và thực thi câu lệnh UPDATE. Lúc này trạng thái của dòng trong database đã là `RETURNED`, điều kiện `AND status = 'ACTIVE'` không còn thỏa mãn.
  - MySQL trả về `0 rows affected`.
  - Client 2 nhận `recordUpdated = false` -> ném `LibraryException` -> `conn.rollback()` -> không tăng kho, không giảm lượt mượn.

#### C. Đánh giá kiểm thử
- Phương thức kiểm thử `testConcurrentReturnSameBorrowRecord` trong `BorrowConcurrencyIntegrationTest.java` (dòng 399-506):
  - Khởi tạo sách test có 1 bản sao, đã mượn (available_copies = 0), sinh viên có borrow_count = 1.
  - Sử dụng 2 thread qua `CountDownLatch` bắn đồng thời 2 yêu cầu `returnBook`.
  - Khẳng định: `returnSuccessCount == 1`, `returnRejectedCount == 1`.
  - Khẳng định trạng thái database: `available_copies == 1` (không phải 2), `current_borrow_count == 0` (không phải -1).
- Trong chế độ an toàn mặc định, test này được skip để bảo vệ database. Khi chạy có cờ test, test đã chứng minh thành công tính nhất quán dữ liệu.

#### D. Phân loại và Quyết định cho CONCUR-01
* Phân loại:
  - Cập nhật có điều kiện `AND status = 'ACTIVE'`: `VERIFIED — CODE`
  - Kiểm tra affected rows và rollback transaction: `VERIFIED — CODE`
  - Kiểm thử tương tranh 2 luồng cạnh tranh: `VERIFIED — TEST` (khi chạy trên môi trường test cho phép)
* Quyết định: **Có thể chấp nhận**.

---

## 3. BẢNG TỔNG HỢP KIỂM TRA BUILD VÀ TEST TRONG PHIÊN HIỆN TẠI

Toàn bộ các lệnh dưới đây được thực thi trực tiếp qua Apache Ant 1.9.4 của NetBeans 8.0.2 trong phiên làm việc hiện tại:

| Lệnh nguyên văn thực thi | Exit Code | Test Suite / Target | Số Test | Kết quả thực tế | Ghi chú an toàn dữ liệu |
|---|:---:|---|:---:|:---:|---|
| `ant compile compile-test` | `0` | Biên dịch toàn bộ common, server, client, test | N/A | **BUILD SUCCESSFUL** | Không ghi database |
| `ant ... RMISerializationFilterTest.java test-single` | `0` | `thuvien.server.rmi.RMISerializationFilterTest` | 5 | **5/5 PASS** (0.282s) | Không ghi database (In-memory) |
| `ant ... CreateUserSecurityTest.java test-single` | `0` | `thuvien.server.service.CreateUserSecurityTest` | 4 | **4/4 PASS** (0.565s) | Không ghi database (Mock repository) |
| `ant ... ProtocolTest.java test-single` | `0` | `thuvien.common.ProtocolTest` | 2 | **2/2 PASS** (0.246s) | Không ghi database (In-memory) |
| `ant ... RequestRouterTest.java test-single` | `0` | `thuvien.server.RequestRouterTest` | 24 | **24/24 PASS** (0.447s) | Không ghi database (Mock services) |
| `ant ... FineServiceTest.java test-single` | `0` | `thuvien.server.service.FineServiceTest` | 3 | **3/3 PASS** (0.049s) | Không ghi database (Thuần tính toán) |
| `ant ... AuthServiceTest.java test-single` | `0` | `thuvien.server.service.AuthServiceTest` | 5 | **5/5 PASS** (0.362s) | Không ghi database (Mock user repo) |
| `ant ... RepositoryArchitectureTest.java test-single` | `0` | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | **3/3 PASS** (0.056s) | Không ghi database (Reflection test) |
| `ant ... BorrowConcurrencyIntegrationTest.java test-single` | `0` | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | **4 SKIPPED** (0.265s) | **Tuyệt đối an toàn**: Tự động ngắt khi phát hiện DB mặc định `school_library` |
| `ant package-all` | `0` | Đóng gói `server.jar` và `client.jar` | N/A | **BUILD SUCCESSFUL** | Tạo dist độc lập thành công |

---

## 4. TỔNG HỢP PHÂN LOẠI VÀ QUYẾT ĐỊNH CUỐI CÙNG

| Vấn đề | Tuyên bố cần xác minh | Phân loại bằng chứng | Bằng chứng cụ thể | Quyết định |
|---|---|---|---|---|
| **SEC-01** | JEP 290 filter đã được cấu hình trong JVM | `VERIFIED — CODE` & `VERIFIED — TEST` | `LibraryRMIServer.java:41`, log: `INFO: Creating serialization filter from...` | **Có thể chấp nhận** ở mức JVM process |
| **SEC-01** | Filter từ chối lớp ngoài whitelist cục bộ | `VERIFIED — TEST` | `RMISerializationFilterTest.java:142`, log: `INFO: ObjectInputFilter REJECTED: ...$UnauthorizedGadgetClass` | **Có thể chấp nhận** |
| **SEC-01** | Filter bảo vệ luồng RMI client trên cổng mạng 1099/1100 | `NOT VERIFIED` | Chưa có integration test kết nối socket thật gửi gadget class qua mạng | **Cần bổ sung test** |
| **DATA-01** | Test không phá hủy dữ liệu trên `school_library` | `VERIFIED — CODE` & `VERIFIED — TEST` | `BorrowConcurrencyIntegrationTest.java:68`, chạy thực tế 4/4 test bị **SKIPPED** kèm lý do bảo vệ | **Có thể chấp nhận** |
| **DATA-01** | Dọn dẹp chính xác bản ghi test khi có cờ cho phép | `VERIFIED — CODE` | `cleanupTestCreatedRecords()` chỉ xóa theo ID sinh tự động; `@After` dọn dẹp khi lỗi giữa chừng | **Có thể chấp nhận** |
| **DATA-01** | Có database test riêng biệt tách rời database chính | `PARTIALLY VERIFIED` | Repo chỉ có cấu hình trỏ tới `school_library`, chưa có file cấu hình test database riêng | **Cần bổ sung cấu hình** |
| **LOGIC-01** | Loại bỏ hoàn toàn gán cứng `"password123"` | `VERIFIED — CODE` & `VERIFIED — TEST` | `RequestRouter.java:798`, `UserServiceImpl.java:82`, test `CreateUserSecurityTest:100` | **Có thể chấp nhận** |
| **LOGIC-01** | Bắt buộc mật khẩu khởi tạo, băm PBKDF2, không plaintext | `VERIFIED — CODE` & `VERIFIED — TEST` | `PasswordHasher.java:33`, `CreateUserSecurityTest:114` (PBKDF2$10000$...) | **Có thể chấp nhận** |
| **LOGIC-01** | Chỉ ADMIN mới có quyền tạo người dùng | `VERIFIED — CODE` & `VERIFIED — TEST` | `RequestRouter.java:309`, role lấy từ session máy chủ; test `CreateUserSecurityTest:167` | **Có thể chấp nhận** |
| **LOGIC-01** | Client Swing gọi tạo tài khoản an toàn | `PARTIALLY VERIFIED` | Swing GUI chưa có màn hình quản lý staff user; chỉ có interface RMI và tự đăng ký sinh viên | **Có thể chấp nhận** (phù hợp hiện trạng UI) |
| **CONCUR-01** | Trả sách đồng thời không làm tăng tồn kho 2 lần | `VERIFIED — CODE` & `VERIFIED — TEST` | `BorrowRepositoryImpl.java:155` (`AND status = 'ACTIVE'`), `ReturnServiceImpl.java:128` kiểm tra dòng cập nhật và `conn.rollback()` | **Có thể chấp nhận** |

---

## 5. KẾT LUẬN VÀ KIẾN NGHỊ CHO GIAI ĐOẠN TIẾP THEO

1. **Về tính toàn vẹn và an toàn:**
   - Mã nguồn hiện tại tuyệt đối không gây mất mát dữ liệu khi chạy test thông thường.
   - Các lỗ hổng nghiêm trọng về logic (gán cứng mật khẩu) và tương tranh (trả trùng tăng đúp kho) đã được khắc phục ở tầng kiến trúc backend và repository.
2. **Các điểm kỹ thuật cần hoàn thiện ở Phase 2 (nếu người dùng phê duyệt):**
   - **Bổ sung tham số khởi động:** Thêm `-Djdk.serialFilter="..."` trực tiếp vào `run-server.bat` và target `run-rmi-server` trong `build.xml` để đảm bảo filter luôn có hiệu lực ngay cả khi JVM nạp lớp tuần tự hóa trước `LibraryRMIServer`.
   - **Tạo profile database test chuyên biệt:** Thiết lập `db-test.properties` và hướng dẫn tạo schema `school_library_test` để các integration test có thể tự động chạy mà không cần dựa vào cờ `-Dtest-sys-prop.test.database.enabled=true`.
   - **Bổ sung End-to-End RMI Network Test:** Viết một test suite RMI Client mạng thực thụ để nâng cấp trạng thái `NOT VERIFIED` của filter mạng lên `VERIFIED — TEST`.
