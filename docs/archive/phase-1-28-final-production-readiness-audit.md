# BÁO CÁO KIỂM TOÁN SẴN SÀNG TRIỂN KHAI PRODUCTION (PHASE 1.28)
## Final Production Readiness Audit & Safety Verification Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.28 — Final Production Readiness Audit  
> **Thời điểm thực hiện**: 2026-10-23 00:05:00 UTC+7  
> **Môi trường thẩm định**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_482 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Được bảo vệ 100% — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã xác minh kiểm thử cách ly)  

---

## 1. TỔNG QUAN VÀ MỤC TIÊU KIỂM TOÁN

Báo cáo Phase 1.28 thực hiện kiểm toán toàn diện tính sẵn sàng triển khai (Production Readiness Audit) cho toàn bộ hệ thống thư viện trường học theo kiến trúc 3 tầng Java RMI.

### Cam kết an toàn nghiêm ngặt đã thực thi:
1. **Không chạy migration trên Production (`school_library`)**: 0 câu lệnh DDL/DML được thực thi trên CSDL sản xuất.
2. **Không chạy `RecoverLibrarian2Utility --apply`**: Chỉ thẩm định mã nguồn tĩnh và chạy thử nghiệm chế độ Dry-Run trên CSDL test; mật khẩu thật của thủ thư được giữ nguyên 100%.
3. **Không commit, push, stash, reset, revert Git**: Giữ nguyên toàn bộ lịch sử và các thay đổi đang có trong working directory.
4. **Không in lộ bí mật**: Tất cả mật khẩu, khóa bí mật, chuỗi hash đầy đủ đều được che giấu hoặc chỉ thể hiện độ dài băm trong báo cáo và console log.

---

## 2. KẾT QUẢ KIỂM TOÁN CHI TIẾT THEO CÁC HẠNG MỤC

### 2.1. Kiểm toán Catalog Guard và Vòng đời RMI (Catalog Guard & Lifecycle)

#### 2.1.1. Thẩm tra mã nguồn `LibraryRMIServer.java`
- **Khởi tạo và kiểm tra Fail-Fast sớm**:
  - Khi `LibraryRMIServer.start()` được gọi, hệ thống mở kết nối qua `DatabaseManager` để xác thực `conn.getCatalog()` với `db.expected.catalog` (hoặc `DB_EXPECTED_CATALOG`) và kiểm tra cột `must_change_password` qua `UserRepositoryImpl.isSchemaMigrated(conn)`.
  - Nếu catalog không khớp hoặc schema chưa migration, hệ thống ném `IllegalStateException` và **dừng ngay trước khi tạo RMI Registry hoặc xuất Remote Object**.
- **Giải phóng tài nguyên khi khởi động thất bại**:
  - Khối khởi tạo RMI (bước 2 và bước 3) được bọc trong `try ... catch (Exception ex)`. Nếu xảy ra lỗi giữa chừng (ví dụ xung đột cổng hoặc lỗi mạng), phương thức `cleanupRmiResources()` được gọi ngay lập tức trước khi ném lại ngoại lệ, đảm bảo không để lại tiến trình treo hay socket rò rỉ.
- **Vòng đời `stop()` an toàn và lũy kế (Idempotent)**:
  - Trường `createdRegistry` phân biệt giữa Registry do server tự tạo (`LocateRegistry.createRegistry`) hay Registry đã có sẵn (`LocateRegistry.getRegistry`).
  - Khi dừng server hoặc cleanup, server chỉ thực hiện unexport đối với Registry do chính nó tạo:
    ```java
    if (createdRegistry) {
        java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
    }
    ```
  - Gọi `stop()` trước khi `start()` hoặc gọi `stop()` nhiều lần liên tiếp hoàn toàn an toàn, không ném ngoại lệ (`no-op safe`).

#### 2.1.2. Thẩm tra bộ kiểm thử `EnvironmentCatalogGuardTest.java`
Đã xây dựng và xác minh bộ test tự động độc lập gồm 5 test cases chạy trên các cổng riêng biệt (10994-10997, 11005-11008):
1. `testMismatchCatalogGuard_AbortsStartupWithoutLingeringPorts`:
   - Thiết lập `db.expected.catalog = school_library`, CSDL thực tế là `school_library_test`.
   - Kết quả: Ném `IllegalStateException` chính xác; server `isRunning() == false`; cả cổng Registry (`10995`) và cổng Service (`11005`) đều đóng hoàn toàn (`isPortListening == false`).
2. `testMatchingCatalogGuard_AllowsStartupWhenSchemaValid`:
   - Thiết lập `db.expected.catalog = school_library_test`.
   - Kết quả: Server khởi động thành công, lắng nghe trên cổng `10996`, `stop()` giải phóng sạch sẽ.
3. `testOmittedCatalogGuard_AllowsStartupByDefault`:
   - Không cấu hình `db.expected.catalog`.
   - Kết quả: Server ghi log cảnh báo và cho phép khởi động mặc định, phục vụ môi trường phát triển linh hoạt.
4. `testStopMethod_SafeWhenCalledBeforeStartOrMultipleTimes`:
   - Gọi `stop()` trên server chưa chạy, sau đó gọi lại `stop()`.
   - Kết quả: Hoàn thành không có ngoại lệ, trạng thái an toàn.
5. `testSchemaValidation_ChecksNullConnectionAndDetectsUnmigratedSchema`:
   - Kiểm tra `isSchemaMigrated` với kết nối null hoặc kết nối đã đóng.
   - Kết quả: Trả về `false` an toàn, không ném `NullPointerException`.

*Ghi chú giới hạn thực nghiệm*: Kiểm thử cổng RMI đã được chứng minh động trên giao diện loopback cục bộ (`127.0.0.1`). Việc định tuyến qua các subnet vật lý khác nhau trong mạng trường học phụ thuộc vào cấu hình card mạng vật lý và tường lửa của hệ điều hành máy chủ.

---

### 2.2. Kiểm toán Tiện ích Cứu hộ Mật khẩu (`RecoverLibrarian2Utility.java`)

Đã tiến hành rà soát mã nguồn tiện ích tại thư mục `scratch/`:
1. **Định danh mục tiêu duy nhất**:
   - Câu lệnh truy vấn kiểm tra đồng thời: `id = 3`, `username = 'librarian2'`, `role = 'LIBRARIAN'`, `is_active = 1`.
   - Bắt buộc số lượng bản ghi tìm thấy `matchCount == 1`.
2. **Khóa Catalog bắt buộc — Không sử dụng giá trị mặc định**:
   - Tiện ích loại bỏ hoàn toàn catalog mặc định. Quản trị viên bắt buộc phải chỉ định catalog mục tiêu qua tham số dòng lệnh `--target-catalog <tên_catalog>` hoặc `-Dtarget.catalog=<tên_catalog>`.
   - Tiện ích từ chối thực thi và dừng với mã lỗi 1 nếu tham số bị thiếu hoặc nếu catalog kết nối thực tế khác với catalog chỉ định.
3. **Giao dịch kiểm soát và Rollback**:
   - Khởi tạo giao dịch với `conn.setAutoCommit(false)`.
   - Sử dụng `SELECT ... FOR UPDATE` để khóa bản ghi thủ thư trước khi cập nhật.
   - Bắt buộc kiểm tra `affectedRows == 1`.
   - Kiểm tra xác nhận ngay trên cùng kết nối: Chuỗi hash mới phải bắt đầu bằng `PBKDF2$`, cờ `must_change_password` phải là `TRUE` (nếu đã migration), và kiểm chứng `PasswordHasher.verify(plainPassword, newHash) == true`.
   - Bất kỳ ngoại lệ nào phát sinh đều kích hoạt `conn.rollback()`.
4. **File sao lưu hash cũ (Old Hash Backup)**:
   - **Thư mục lưu trữ**: Cấu hình qua `--backup-dir` hoặc `-Dbackup.dir`, mặc định `./security_backups/`.
   - **Tên file độc quyền**: `backup_librarian2_hash_yyyyMMdd_HHmmss[_seq].txt`, sử dụng cờ `StandardOpenOption.CREATE_NEW` để chống ghi đè.
   - **Quyền truy cập**: Thiết lập quyền chỉ dành riêng cho chủ sở hữu file (`PosixFilePermissions` 600 trên POSIX, hoặc `setReadable(true, true)` / `setWritable(true, true)` trên Windows).
   - **Nội dung sao lưu**: Chỉ lưu metadata định danh (`USER_ID`, `USERNAME`) và `OLD_HASH`. **Tuyệt đối không lưu mật khẩu tạm thời hoặc mật khẩu mới**.
   - **Chính sách lưu trữ (Retention)**: Khuyến nghị lưu trữ 30 ngày phục vụ audit/sự cố, sau đó hủy an toàn.
   - **Quy trình phục hồi (Rollback)**: Quản trị viên lấy `OLD_HASH` từ file backup và thực thi câu lệnh SQL hoàn tác:
     ```sql
     UPDATE users SET password_hash = '<OLD_HASH>', must_change_password = FALSE WHERE id = 3 AND username = 'librarian2';
     ```
5. **Bảo mật nhập liệu và bộ nhớ**:
   - Sử dụng `System.console().readPassword()` để ẩn ký tự khi nhập.
   - Toàn bộ mảng `char[]` mật khẩu được xóa sạch bằng ký tự `\0` trong khối `finally`.
   - Tuyệt đối không in mật khẩu hay hash ra console log (chỉ in `hash_len`).
   - Đã kiểm chứng thực nghiệm bằng chế độ Dry-Run trên `school_library_test`: Kiểm tra thành công, 0 thay đổi ghi vào CSDL.

---

### 2.3. Kiểm toán Bí mật trong Gói Phát hành (`dist/server-release`)

Quét đệ quy toàn bộ thư mục `dist/server-release`:
- **Cấu trúc gói**:
  - `server.jar` (195.059 bytes)
  - `run-server.bat` (1.087 bytes)
  - `resources/db.properties.example` (541 bytes)
  - `resources/server.properties` (207 bytes)
  - `lib/` (4 JARs: `HikariCP-4.0.3.jar`, `mysql-connector-java-8.0.33.jar`, `slf4j-api-1.7.36.jar`, `slf4j-jdk14-1.7.36.jar`)
- **Kết quả quét bí mật**:
  - `server.jar` **không nướng bất kỳ file `db.properties` nào** vào mã nhị phân (chỉ chứa `server.properties`).
  - Không có file `test.local.properties`, file dump `.sql`, file log, hoặc thư mục `scratch/` trong bản release.
  - Tệp mẫu `db.properties.example` chỉ chứa chuỗi giữ chỗ: `your_db_username`, `your_db_password`.
  - Tệp `run-server.bat` kiểm tra nếu thiếu `resources\db.properties` sẽ dừng với mã lỗi 1; không tự ý tạo file thật và không fallback sang CSDL mặc định.
- **Quyền tối thiểu của Database User trong Runbook**:
  - Tài khoản khuyến nghị: `thuvien_app`@`127.0.0.1`
  - Quyền hạn: Chỉ cấp `SELECT, INSERT, UPDATE, DELETE` trên `school_library.*`.
  - Không cấp quyền DDL (`ALTER`, `DROP`, `CREATE`) hay quyền quản trị (`GRANT`, `SUPER`).

---

### 2.4. Kiểm tra Script Migration và Runbook Triển khai

#### 2.4.1. Đối chiếu tính tương thích MySQL 8.0.30
- Đã xác thực thực nghiệm: Câu lệnh `ALTER TABLE users ADD COLUMN IF NOT EXISTS ...` **thất bại trên MySQL 8.0** với lỗi `ERROR 1064 (42000)`.
- Script [migration_must_change_password_mysql8.sql](file:///D:/workspace/thuvien/database/migration_must_change_password_mysql8.sql) sử dụng Stored Procedure tạm thời truy vấn `INFORMATION_SCHEMA.COLUMNS`:
  - **Lần chạy đầu**: Thêm cột `must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active`.
  - **Lần chạy lại (Idempotent)**: Phát hiện cột đã tồn tại với kiểu `tinyint(1)` và mặc định `0`, trả về thông báo `SKIP` và không làm gián đoạn CSDL.
  - **Phát hiện cột sai định nghĩa**: Ném lỗi `SIGNAL SQLSTATE '45000'` nếu cột tồn tại nhưng sai kiểu dữ liệu hoặc sai ràng buộc.
  - **Bảo toàn số bản ghi**: So sánh `COUNT(*)` bảng `users` trước và sau DDL, ném ngoại lệ nếu số bản ghi bị sai lệch.

#### 2.4.2. Thẩm tra Runbook Vận hành (`docs/DEPLOYMENT.md`)
Runbook đã được cập nhật đầy đủ các bước:
1. **Bước 1**: Lệnh sao lưu `mysqldump` trước migration.
2. **Bước 1b**: Xác minh file sao lưu đọc được (`size > 0`, kiểm tra header 10 dòng đầu và footer `-- Dump completed on ...`).
3. **Bước 2**: Thực thi migration an toàn.
4. **Bước 2b**: Truy vấn kiểm tra schema và đếm số lượng bản ghi sau migration.
5. **Bước 2c**: Kế hoạch hoàn tác (Rollback Plan) qua phục hồi bản dump hoặc câu lệnh DDL hoàn tác.
6. **Bước 3**: Thiết lập tài khoản `thuvien_app` với quyền tối thiểu.

---

### 2.5. Kiểm thử và Đóng gói Cuối cùng (Final Verification & Packaging)

Đã thực thi quy trình đóng gói và kiểm thử độc lập:

| Lệnh thực thi | Mã thoát (Exit Code) | Thời gian thực thi | Kết quả chi tiết |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 2 giây | Biên dịch thành công 131 source files (common: 42, server: 52, client: 37). |
| `ant test` | **0** | 35 giây | **17 test suites, 160 tests PASS**, 0 Failures, 0 Errors, 0 Skipped. |
| `ant package-all` | **0** | 1 giây | Tạo thành công 3 gói: `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Không có lỗi khoảng trắng thừa, không có conflict markers. |

#### Thống kê chi tiết 17 Test Suites (160 Tests):
1. `thuvien.client.PermissionHelperTest`: 4/4 PASS
2. `thuvien.common.ProtocolTest`: 2/2 PASS
3. `thuvien.server.database.MySQLSmokeIntegrationTest`: 4/4 PASS
4. `thuvien.server.repository.RepositoryArchitectureTest`: 3/3 PASS
5. `thuvien.server.RequestRouterTest`: 34/34 PASS
6. `thuvien.server.rmi.EnvironmentCatalogGuardTest`: 5/5 PASS
7. `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest`: 6/6 PASS
8. `thuvien.server.rmi.RMIEndToEndIntegrationTest`: 15/15 PASS
9. `thuvien.server.rmi.RMISerializationFilterTest`: 10/10 PASS
10. `thuvien.server.service.AdminResetPasswordTest`: 13/13 PASS
11. `thuvien.server.service.AuthServiceTest`: 5/5 PASS
12. `thuvien.server.service.BorrowConcurrencyIntegrationTest`: 4/4 PASS
13. `thuvien.server.service.CreateUserSecurityTest`: 4/4 PASS
14. `thuvien.server.service.FineServiceTest`: 3/3 PASS
15. `thuvien.server.service.SecurityAndFeatureHardeningTest`: 34/34 PASS
16. `thuvien.server.service.SuspendedStudentAuthTest`: 8/8 PASS
17. `thuvien.server.TestDatabaseSafetyGuardTest`: 6/6 PASS

---

## 3. TRẠNG THÁI CƠ SỞ DỮ LIỆU PRODUCTION (`school_library`)

Đã thực hiện truy vấn trực tiếp đối soát trên `school_library`:
```
id    username      role         is_active    hash_len
1     admin         ADMIN        1            110
2     librarian1    LIBRARIAN    1            110
3     librarian2    LIBRARIAN    1            64
4     student1      STUDENT      1            110
8     studen2       STUDENT      1            110
```
- **Số tài khoản**: Đúng 5 tài khoản nguyên vẹn.
- **Tài khoản `librarian2`**: Giữ nguyên chuỗi băm cũ SHA-256 (`hash_len = 64`), chưa bị thay đổi mật khẩu.
- **Cấu trúc bảng `users`**: Cột `must_change_password` **chưa xuất hiện** (chưa chạy migration).
- **Kết luận**: CSDL Production được giữ nguyên trạng 100%, sẵn sàng cho quy trình triển khai có kiểm soát của DBA.

---

## 4. DANH SÁCH FILE ĐÃ THAY ĐỔI VÀ LÝ DO

| File | Hành động | Lý do |
|:---|:---:|:---|
| `scratch/RecoverLibrarian2Utility.java` | Cập nhật | Loại bỏ catalog mặc định, bắt buộc `--target-catalog`, bảo vệ quyền file backup owner-only, chuẩn hóa quy trình cứu hộ. |
| `docs/DEPLOYMENT.md` | Cập nhật | Bổ sung xác minh file backup đọc được, post-migration schema check, rollback plan và tham số `--target-catalog`. |
| `docs/phase-1-28-final-production-readiness-audit.md` | Tạo mới | Tài liệu báo cáo kiểm toán tính sẵn sàng triển khai chính thức cho Phase 1.28. |

---

## 5. CÁC HẠNG MỤC CHỜ QUẢN TRỊ VIÊN PHÊ DUYỆT (UNRESOLVED / PENDING ACTIONS)

1. **Thực thi Migration Schema trên Production**: Cần Quản trị viên/DBA thực thi `database/migration_must_change_password_mysql8.sql` trên `school_library` trong khung giờ bảo trì được phê duyệt.
2. **Khôi phục / Đổi mật khẩu tài khoản `librarian2`**: Sau khi migration hoàn tất, Quản trị viên thực hiện khôi phục mật khẩu tài khoản thủ thư theo quy trình cứu hộ có lưu backup hash.
3. **Cấu hình `resources/db.properties` trên Máy chủ thật**: Quản trị viên cấu hình tài khoản `thuvien_app` với mật khẩu sản xuất thực tế trên máy chủ triển khai.

---

## 6. KẾT LUẬN CUỐI CÙNG (FINAL VERDICTS)

Dựa trên toàn bộ kết quả kiểm toán tĩnh, kiểm thử tự động động và thẩm định gói phát hành, đưa ra 3 kết luận chính thức:

### 1. BUILD VERIFIED: PASS
> Toàn bộ mã nguồn hệ thống biên dịch sạch sẽ trên Java 8, Ant build thành công với 17 test suites, 160/160 tests PASS (0 failures, 0 errors, 0 skipped), mã nguồn tuân thủ nghiêm ngặt quy tắc kiến trúc và không có lỗi định dạng Git.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Gói phân phối `dist/server-release` hoàn toàn sạch, không chứa thông tin mật khẩu phát triển, không nhúng file cấu hình CSDL trong file JAR, script khởi động có cơ chế bảo vệ chống chạy thiếu cấu hình, và tài liệu runbook đã hoàn thiện đầy đủ các bước an toàn.

### 3. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Mặc định hệ thống sẵn sàng về mặt kỹ thuật nhưng **chờ phê duyệt chính thức từ Quản trị viên hệ thống (Administrator/DBA)** để mở khung giờ triển khai, chạy migration và kích hoạt dịch vụ trên môi trường sản xuất thật. Tuyệt đối không tự ý thay đổi CSDL Production trước khi có quyết định phê duyệt.
