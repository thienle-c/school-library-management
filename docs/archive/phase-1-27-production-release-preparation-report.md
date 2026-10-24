# BÁO CÁO KIỂM TOÁN AN TOÀN PHÁT HÀNH VÀ CHUẨN BỊ BẢN RELEASE PRODUCTION (PHASE 1.27)

> **Dự án**: Remote School Library Management System  
> **Repository**: `D:\workspace\thuvien`  
> **Môi trường**: Java 8 (OpenJDK 1.8.0_504 Amazon Corretto), Java Swing, Java RMI, MySQL 8.0.30 (Laragon / InnoDB), HikariCP 4.0.3, Apache Ant 1.10.15, NetBeans 8.0.2  
> **Thời điểm thực hiện**: 22/10/2026 23:54 (Local Time)  
>
> **KẾT LUẬN PHÂN ĐỊNH TRẠNG THÁI (STATUS VERDICTS)**:
> - **`BUILD VERIFIED`**: **PASS** (Biên dịch sạch Ant Java 8, 0 lỗi biên dịch)
> - **`TESTS VERIFIED`**: **PASS** (17 test suites, 158 tests, 0 failures, 0 errors, 0 skipped)
> - **`RELEASE PACKAGE VERIFIED`**: **PASS** (Gói `dist/server-release` độc lập, zero secrets, Catalog Guard & Pre-flight hoạt động chính xác)
> - **`PRODUCTION DEPLOYMENT APPROVED`**: **PENDING ADMINISTRATOR APPROVAL** (Bản phát hành sẵn sàng an toàn; chờ Quản trị viên phê duyệt chạy migration và cấu hình tài khoản thật trên CSDL sản xuất)

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC BẢO MẬT BẮT BUỘC

Đợt kiểm toán an toàn phát hành (Production Release Safety Audit) tuân thủ nghiêm ngặt 4 nguyên tắc:
1. **Tuyệt đối không can thiệp CSDL sản xuất**: Không kết nối ghi vào `school_library`, không chạy migration DDL trên `school_library`, không reset mật khẩu `librarian2`.
2. **Không nướng bí mật vào bản phát hành**: `server.jar` và `dist/server-release` hoàn toàn không chứa file `db.properties` thật, mật khẩu sản xuất, hay cấu hình test.
3. **Fail-Fast & Environment Guard**: Máy chủ phải dừng ngay lập tức nếu thiếu cấu hình, dùng giá trị placeholder, hoặc kết nối sai catalog cơ sở dữ liệu mong đợi mà không làm lộ mật khẩu ra log.
4. **Bảo toàn quy trình kiểm thử và Git tree**: Không commit, push, stash, reset, revert; không chạy lại GUI/LAN acceptance test; tất cả bài kiểm thử tự động phải vượt qua 100%.

---

## 2. DANH SÁCH FILE ĐÃ SỬA ĐỔI VÀ TẠO MỚI

| STT | Đường dẫn File | Loại thay đổi | Chi tiết mục đích |
|---|---|---|---|
| 1 | `build.xml` | Modified | Cập nhật target `package-server` (loại trừ `db.properties` khỏi `server.jar`); bổ sung target `package-server-release` (đóng gói `dist/server-release` sạch chỉ chứa `db.properties.example` và các thư viện runtime); cập nhật `package-all`. |
| 2 | `server/src/thuvien/server/database/DatabaseConfig.java` | Modified | Loại bỏ fallback ngầm định sang `root`/`root`; chặn các giá trị placeholder (`your_db_username`/`your_db_password`); chặn nạp ngầm định `test.local.properties` (chỉ cho phép khi có cờ `-Dapp.env=test` hoặc `-Ddb.config.file=...`); bổ sung `getDatabaseName()`. |
| 3 | `server/src/thuvien/server/rmi/LibraryRMIServer.java` | Modified | Tích hợp Environment Catalog Guard (`db.expected.catalog` / `DB_EXPECTED_CATALOG`); in log catalog an toàn; fail-fast nếu sai database; bổ sung cờ `createdRegistry` và unexport `Registry` khi `stop()` để giải phóng cổng 1099 triệt để. |
| 4 | `database/migration_must_change_password_mysql8.sql` | Created | Script migration MySQL 8.0 an toàn và có thể chạy lại nhiều lần (idempotent), kiểm tra `INFORMATION_SCHEMA.COLUMNS`, xác thực kiểu dữ liệu và ràng buộc, đối chiếu số dòng trước/sau. |
| 5 | `docs/DEPLOYMENT.md` | Modified | Bổ sung Mục 3.2 (Quy trình migration MySQL 8.0 an toàn, tạo tài khoản least privilege `thuvien_app`), Mục 3.3 (Xác minh database đích và Environment Catalog Guard), Mục 3.5 (Quy trình cứu hộ tài khoản thủ thư). Sửa lỗi cú pháp markdown. |
| 6 | `test/thuvien/server/rmi/EnvironmentCatalogGuardTest.java` | Created | Bộ kiểm thử tự động 3 ca kiểm chứng Catalog Guard (mismatch abort, matching success, omitted warning) và giải phóng tài nguyên cổng mạng độc lập. |
| 7 | `scratch/RecoverLibrarian2Utility.java` | Scratch Audit | Cập nhật tiện ích cứu hộ khẩn cấp: kiểm tra schema migration, gán `must_change_password = TRUE`, cơ chế sao lưu độc quyền và rollback. Giữ nguyên chế độ Dry-Run. |

---

## 3. CHI TIẾT SỰ CỐ TEST TỪNG GẶP VÀ CÁCH KHẮC PHỤC TRIỆT ĐỂ

Trong lần chạy `ant test` ban đầu sau khi thêm `EnvironmentCatalogGuardTest`, đã ghi nhận 1 lỗi kiểm thử:
* **Test suite bị lỗi**: `thuvien.server.rmi.EnvironmentCatalogGuardTest`
* **Test case thất bại**: `testMismatchCatalogGuard_AbortsStartupWithoutLingeringPorts`
* **Thông báo lỗi (Failure message)**: `junit.framework.AssertionFailedError: Registry port 10996 must NOT be listening after failed startup`
* **Phân tích nguyên nhân gốc rễ (Root Cause Analysis)**:
  1. Trong JUnit, ca kiểm thử `testMatchingCatalogGuard_AllowsStartupWhenSchemaValid` chạy trước trên cổng 10996 và gọi `server.stop()`.
  2. Tại lớp `LibraryRMIServer.java`, phương thức `stop()` ban đầu chỉ thực hiện:
     ```java
     if (registry != null) { registry.unbind(serviceName); }
     ```
     Trong Java RMI, việc `unbind` một dịch vụ **không hề đóng socket hoặc unexport Registry**. Do đó, đối tượng Registry nội bộ của JVM vẫn tiếp tục lắng nghe trên cổng 10996.
  3. Khi test case tiếp theo `testMismatchCatalogGuard_AbortsStartupWithoutLingeringPorts` chạy (dùng chung cổng 10996) và cố tình ném ngoại lệ dừng khởi động tại bước kiểm tra catalog, server thực sự không khởi tạo registry mới. Tuy nhiên, khi câu lệnh `isPortListening("127.0.0.1", 10996)` kiểm tra, nó vẫn kết nối thành công vào Registry còn sót lại từ test case trước, dẫn tới assertion thất bại.
* **Biện pháp khắc phục đã triển khai**:
  1. **Khắc phục trong mã nguồn chính (`LibraryRMIServer.java`)**:
     Thêm trường `private boolean createdRegistry = false;`. Khi server tự tạo registry qua `LocateRegistry.createRegistry(registryPort)`, cờ này được bật `true`. Trong phương thức `stop()`, bổ sung lệnh unexport chuẩn của Java RMI:
     ```java
     if (createdRegistry && registry != null) {
         java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
     }
     ```
     Điều này đảm bảo khi server dừng, socket cổng mạng của Registry được đóng hoàn toàn.
  2. **Khắc phục trong bài test (`EnvironmentCatalogGuardTest.java`)**:
     Phân định các cổng mạng độc lập hoàn toàn cho từng ca kiểm thử:
     - `MISMATCH_REGISTRY_PORT = 10995`, `MISMATCH_SERVICE_PORT = 11005`
     - `MATCH_REGISTRY_PORT = 10996`, `MATCH_SERVICE_PORT = 11006`
     - `OMIT_REGISTRY_PORT = 10997`, `OMIT_SERVICE_PORT = 11007`
* **Kết quả lần chạy cuối**: Toàn bộ 3 bài kiểm thử trong suite đạt **PASS 100%**, thời gian chạy 3.931s, không còn bất kỳ cổng nào bị chiếm dụng hay treo.

---

## 4. KẾT QUẢ KIỂM TOÁN TƯƠNG THÍCH MIGRATION MYSQL 8

### 4.1. Xác minh thực nghiệm cú pháp `ADD COLUMN IF NOT EXISTS` trên MySQL 8.0.30
- **Phiên bản MySQL thực tế**: `MySQL Community Server 8.0.30` (`D:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysqld.exe`).
- **Thực thi thử nghiệm**: `ALTER TABLE tmp ADD COLUMN IF NOT EXISTS col2 INT;`
- **Kết quả thực tế**:
  ```text
  ERROR 1064 (42000): You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'IF NOT EXISTS col2 INT' at line 1
  ```
- **Kết luận**: MySQL 8.0 **không hỗ trợ** cú pháp `ADD COLUMN IF NOT EXISTS`. Mọi kịch bản dùng cú pháp này trên MySQL 8.0 sẽ báo lỗi và dừng tiến trình.

### 4.2. Kiểm chứng thực tế Script Migration Idempotent trên bảng thử nghiệm
Đã tạo bảng thử nghiệm `test_users_migration_verification` trên CSDL `school_library_test` và thực thi kiểm chứng thực tế:
1. **Chạy lần 1 (Bảng chưa có cột)**:
   - Trạng thái trả về: `SUCCESS: Cột must_change_password đã được thêm thành công.`
   - Số lượng bản ghi: Giữ nguyên vẹn 2 dòng (`users_count = 2`).
   - Xác minh metadata từ `INFORMATION_SCHEMA.COLUMNS`:
     - `COLUMN_NAME`: `must_change_password`
     - `DATA_TYPE`: `tinyint`
     - `COLUMN_TYPE`: `tinyint(1)`
     - `IS_NULLABLE`: `NO`
     - `COLUMN_DEFAULT`: `'0'`
     - `ORDINAL_POSITION`: `4` (Nằm chính xác ngay sau `is_active` ở vị trí 3).
2. **Chạy lần 2 (Bảng đã có cột hợp lệ)**:
   - Trạng thái trả về: `SKIP: Cột must_change_password đã tồn tại với định nghĩa hợp lệ.`
   - Schema không hề bị thay đổi, dữ liệu giữ nguyên 100%.
3. **Chạy lần 3 (Bảng có cột nhưng sai định nghĩa - ví dụ VARCHAR)**:
   - Kết quả: Throws `ERROR 1644 (45000): MIGRATION ERROR: Cột must_change_password có định nghĩa sai!` và dừng ngay lập tức, không tự sửa âm thầm.
4. **Dọn dẹp sau kiểm tra**:
   - Đã xóa bảng và thủ tục thử nghiệm: `DROP TABLE test_users_migration_verification; DROP TABLE test_migration_dummy;`.
   - Xác nhận: `SHOW TABLES LIKE 'test_%'` trả về **0 bảng**.
   - CSDL sản xuất `school_library` hoàn toàn **không bị tác động**.

---

## 5. KIỂM TOÁN THỨ TỰ NẠP CẤU HÌNH VÀ GÓI SẢN XUẤT

### 5.1. Thứ tự nạp cấu hình ([DatabaseConfig.java](file:///D:/workspace/thuvien/server/src/thuvien/server/database/DatabaseConfig.java))
```
1. JVM System Properties (-Ddb.url=..., -Ddb.username=..., -Ddb.password=...)
   v
2. Environment Variables (DB_URL, DB_USERNAME, DB_PASSWORD)
   v
3. Custom Config File (-Ddb.config.file=...) HOẶC test.local.properties (CHỈ KHI -Dapp.env=test)
   v
4. External Properties File (resources/db.properties)
```
* **Chặn nguy cơ nạp nhầm `test.local.properties`**: Nếu tệp `test.local.properties` vô tình nằm trong thư mục chạy của server production, hệ thống sẽ **bỏ qua hoàn toàn** vì không có cờ `-Dapp.env=test`.

### 5.2. Kiểm toán gói `dist/server-release` thực tế
- **Quét đệ quy thư mục**:
  - `dist/server-release/server.jar` (194.952 bytes)
  - `dist/server-release/run-server.bat` (1.087 bytes)
  - `dist/server-release/resources/db.properties.example` (541 bytes)
  - `dist/server-release/resources/server.properties` (207 bytes)
  - `dist/server-release/lib/HikariCP-4.0.3.jar`
  - `dist/server-release/lib/mysql-connector-java-8.0.33.jar`
  - `dist/server-release/lib/slf4j-api-1.7.36.jar`
  - `dist/server-release/lib/slf4j-jdk14-1.7.36.jar`
- **Quét nội dung bên trong JAR**:
  - `server.jar` chỉ chứa `server.properties`, không chứa bất kỳ file `db.properties` nào.
  - Quét chuỗi `school_library_test` trong toàn bộ gói `dist/server-release`: **0 kết quả**.
- **Kịch bản `run-server.bat`**: Kiểm tra sự tồn tại của `resources\db.properties`. Nếu thiếu sẽ dừng ngay; không tự động sao chép template; không tự động chọn database.

---

## 6. KIỂM TOÁN TIỆN ÍCH CỨU HỘ `RecoverLibrarian2Utility`

1. **Vị trí lưu trữ**: Nằm tại `scratch/RecoverLibrarian2Utility.java` bên ngoài bản phân phối phát hành, không được đóng gói vào `server.jar` hay `dist/server-release`.
2. **Đối chiếu lệnh chạy trong [docs/DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md)**:
   - Biên dịch: `javac -encoding UTF-8 -cp "dist\server-release\server.jar;dist\server-release\lib\*" -d scratch\classes scratch\RecoverLibrarian2Utility.java`
   - Chạy Dry-Run: `java -Ddb.url="..." -Ddb.username="..." -Ddb.password="..." -cp "scratch\classes;dist\server-release\server.jar;dist\server-release\lib\*" thuvien.scratch.RecoverLibrarian2Utility`
3. **An toàn bảo mật**:
   - Mặc định là Dry-Run (chỉ đọc).
   - Nhập mật khẩu qua `System.console().readPassword()` (không qua CLI arg), xóa `char[]` bằng `\0` trong `finally`.
   - Sử dụng chuẩn băm `PBKDF2WithHmacSHA256` của dự án; gán `must_change_password = TRUE`.
   - Sao lưu hash cũ ra file độc quyền trước khi update; tự động rollback nếu affectedRows != 1 hoặc verify thất bại.
4. **Cam kết**: **Tuyệt đối không chạy `--apply`**; dữ liệu trên `school_library` giữ nguyên trạng thái cũ 100%.

---

## 7. BẰNG CHỨNG THỰC THI KIỂM THỬ MỚI NHẤT

Chuỗi lệnh kiểm thử tuần tự đã thực thi thành công:

1. **`ant clean compile`**: Exit code `0` (`BUILD SUCCESSFUL`, 2s).
2. **`ant test`**: Exit code `0` (`BUILD SUCCESSFUL`, 33s).
3. **`ant package-all`**: Exit code `0` (`BUILD SUCCESSFUL`, 1s).
4. **`git diff --check`**: Exit code `0` (Không có lỗi định dạng hay xung đột).

### Bảng chi tiết toàn bộ 17 Test Suites từ file JUnit XML mới nhất:

| STT | Tên Test Suite | Tests | Failures | Errors | Skipped | Thời gian (s) | Timestamp |
|:---:|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.client.PermissionHelperTest` | 4 | 0 | 0 | 0 | 0.078 | 2026-10-22T16:52:21 |
| 2 | `thuvien.common.ProtocolTest` | 2 | 0 | 0 | 0 | 0.274 | 2026-10-22T16:52:21 |
| 3 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 0 | 0 | 0 | 0.321 | 2026-10-22T16:52:23 |
| 4 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 0 | 0 | 0 | 0.084 | 2026-10-22T16:52:23 |
| 5 | `thuvien.server.RequestRouterTest` | 34 | 0 | 0 | 0 | 0.498 | 2026-10-22T16:52:22 |
| 6 | `thuvien.server.rmi.EnvironmentCatalogGuardTest` | 3 | 0 | 0 | 0 | 3.931 | 2026-10-22T16:52:24 |
| 7 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 0 | 0 | 0 | 2.504 | 2026-10-22T16:52:28 |
| 8 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 0 | 0 | 0 | 1.795 | 2026-10-22T16:52:31 |
| 9 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 0 | 0 | 0 | 0.453 | 2026-10-22T16:52:34 |
| 10 | `thuvien.server.service.AdminResetPasswordTest` | 13 | 0 | 0 | 0 | 3.659 | 2026-10-22T16:52:35 |
| 11 | `thuvien.server.service.AuthServiceTest` | 5 | 0 | 0 | 0 | 0.646 | 2026-10-22T16:52:39 |
| 12 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 0 | 0 | 0 | 1.294 | 2026-10-22T16:52:39 |
| 13 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 0 | 0 | 0 | 0.600 | 2026-10-22T16:52:41 |
| 14 | `thuvien.server.service.FineServiceTest` | 3 | 0 | 0 | 0 | 0.114 | 2026-10-22T16:52:42 |
| 15 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 34 | 0 | 0 | 0 | 7.926 | 2026-10-22T16:52:42 |
| 16 | `thuvien.server.service.SuspendedStudentAuthTest` | 8 | 0 | 0 | 0 | 2.364 | 2026-10-22T16:52:51 |
| 17 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 0 | 0 | 0 | 0.097 | 2026-10-22T16:52:22 |
| **TỔNG** | **17 Test Suites** | **158** | **0** | **0** | **0** | **25.64s** | **TẤT CẢ ĐẠT (PASS)** |

---

## 8. BẢNG TỔNG HỢP HIỆN TRẠNG (VERIFICATION MATRIX)

| Hạng mục kiểm tra | Phương pháp | Kết luận | Bằng chứng thực tế |
|---|---|:---:|---|
| Tương thích cú pháp MySQL 8 | Động (MySQL 8.0.30) | **PASS** | Error 1064 khi dùng `IF NOT EXISTS`; script procedure hoạt động chính xác |
| Script migration MySQL 8 idempotent | Động (Test DB) | **PASS** | Lần 1: SUCCESS; Lần 2: SKIP; Kiểu sai: Error 45000 |
| Dọn dẹp sạch bảng thử nghiệm | Động (MySQL query) | **PASS** | `SHOW TABLES LIKE 'test_%'` trả về 0 bảng |
| Loại trừ `db.properties` khỏi `server.jar` | Động (`jar tf`) | **PASS** | `server.jar` chỉ chứa `server.properties` |
| Quét toàn bộ JAR phụ thuộc | Động (`jar tf`) | **PASS** | Tất cả 4 JAR trong `lib/` đều không chứa bí mật |
| Gói `server-release` sạch 100% | Động (File inspection) | **PASS** | `dist/server-release/resources` chỉ có `.example` |
| Giữ nguyên cấu hình kiểm thử lab | Động (File inspection) | **PASS** | `dist/server-dist` tiếp tục trỏ `school_library_test` |
| Chặn nạp ngầm `test.local.properties` | Động (Code review & test) | **PASS** | Chỉ cho phép khi có `-Dapp.env=test` hoặc `-Ddb.config.file` |
| Dynamic test: Catalog Guard mismatch | Động (JUnit test) | **PASS** | Server hủy bật, cổng RMI 10995 không lắng nghe |
| Dynamic test: Catalog Guard match | Động (JUnit test) | **PASS** | Server bật thành công, unexport cổng 10996 khi dừng |
| Dynamic test: Catalog Guard omitted | Động (JUnit test) | **PASS** | Server ghi log cảnh báo và chạy bình thường |
| Toàn bộ bài kiểm thử tự động hệ thống | Động (`ant test`) | **PASS** | 17 suites / 158 tests pass 100% |
| Đóng gói đầy đủ các bản phân phối | Động (`ant package-all`) | **PASS** | `dist/server-release`, `dist/server-dist`, `dist/client-dist` |
| Kiểm tra cú pháp và định dạng Git | Động (`git diff --check`) | **PASS** | Mã thoát 0, không có lỗi định dạng |
| Kiểm toán `RecoverLibrarian2Utility` | Tĩnh (Code inspection) | **STATIC-VERIFIED** | Tiện ích độc lập ngoài release, PBKDF2, Zero console leak |
| Không chạy `--apply` trên Production | Tĩnh & Động (DB query) | **PASS** | CSDL `school_library` và tài khoản `librarian2` giữ nguyên 100% |
| Migration schema trên Production | Quy trình quản trị | **BLOCKED-PENDING-APPROVAL** | Đợi Quản trị viên phê duyệt chạy trên MySQL thật |
| Khởi động Server trên Production | Quy trình vận hành | **BLOCKED-PENDING-APPROVAL** | Đợi Quản trị viên cấu hình `db.properties` thật |

---

## 9. CÁC BƯỚC CẦN QUẢN TRỊ VIÊN PHÊ DUYỆT TRƯỚC KHI TRIỂN KHAI PRODUCTION

1. **Phê duyệt Sao lưu CSDL Production**:
   ```cmd
   mysqldump -u root -p --single-transaction --routines --triggers school_library > D:\backup\school_library_pre_migration_%date:~10,4%%date:~4,2%%date:~7,2%.sql
   ```
2. **Phê duyệt và Thực thi Migration Idempotent**:
   ```cmd
   mysql -u root -p school_library < D:\workspace\thuvien\database\migration_must_change_password_mysql8.sql
   ```
3. **Phê duyệt Cấp tài khoản CSDL Quyền tối thiểu**:
   ```sql
   CREATE USER 'thuvien_app'@'127.0.0.1' IDENTIFIED BY 'Mat_Khau_Manh_Cua_Admin!2026';
   GRANT SELECT, INSERT, UPDATE, DELETE ON school_library.* TO 'thuvien_app'@'127.0.0.1';
   FLUSH PRIVILEGES;
   ```
4. **Phê duyệt Cấu hình Sản xuất**:
   Trong `dist/server-release`, sao chép `resources/db.properties.example` thành `resources/db.properties` và điền thông tin tài khoản `thuvien_app`.
5. **Phê duyệt Khởi động Production**:
   Thiết lập biến môi trường `set DB_EXPECTED_CATALOG=school_library` và chạy `run-server.bat`.

---

## 10. KẾT LUẬN TÁCH BIỆT (FINAL VERDICTS)

- **`BUILD VERIFIED`**: **PASS** — Mã nguồn biên dịch sạch với Apache Ant và Java 8; không có lỗi hoặc cảnh báo cú pháp.
- **`TESTS VERIFIED`**: **PASS** — 17 test suites với 158 bài kiểm thử tự động đạt 100% PASS, kiểm chứng đầy đủ bảo mật, luồng nghiệp vụ, Catalog Guard và ngắt phiên.
- **`RELEASE PACKAGE VERIFIED`**: **PASS** — Bản phân phối sản xuất tại `dist/server-release` được đóng gói sạch hoàn toàn, độc lập môi trường, không chứa bí mật hay cấu hình thử nghiệm.
- **`PRODUCTION DEPLOYMENT APPROVED`**: **PENDING ADMINISTRATOR APPROVAL** — Hệ thống không tự động triển khai hoặc can thiệp dữ liệu production; sẵn sàng bàn giao để Quản trị viên thực hiện theo Runbook.
