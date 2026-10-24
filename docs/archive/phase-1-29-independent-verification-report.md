# BÁO CÁO KIỂM CHỨNG ĐỘC LẬP VÀ CỔNG TRIỂN KHAI (PHASE 1.29)
## Independent Verification & Deployment Gate Audit Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.29 — Independent Verification & Deployment Gate  
> **Thời điểm thực hiện**: 2026-10-23 00:14:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Được bảo vệ nghiêm ngặt — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã xác minh kiểm thử cách ly)  

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC BẢO VỆ NGHIÊM NGẶT

Mục tiêu của Phase 1.29 là thực hiện **kiểm chứng độc lập (Independent Verification)** toàn bộ các phát hiện, số liệu và kết luận của Phase 1.28, đóng vai trò như một Cổng triển khai (Deployment Gate) trước khi bàn giao hệ thống cho Quản trị viên (DBA/Administrator).

### Các nguyên tắc an toàn bắt buộc đã tuân thủ tuyệt đối:
1. **Không chạy `RecoverLibrarian2Utility --apply`**: Chỉ thực hiện kiểm tra mã nguồn tĩnh và chạy thử nghiệm Dry-Run trên CSDL test. Mật khẩu và hash của toàn bộ tài khoản production giữ nguyên 100%.
2. **Không chạy migration trên `school_library`**: Không thực thi bất kỳ câu lệnh DDL hoặc DML nào trên CSDL sản xuất.
3. **Không đổi mật khẩu hoặc hash tài khoản production**: Giữ nguyên trạng dữ liệu.
4. **Không git reset, revert, stash, commit hoặc push**: Bảo toàn toàn bộ các file thay đổi đang có trong working directory.
5. **Chỉ dùng `school_library_test` cho kiểm thử**: Toàn bộ thao tác ghi kiểm thử chỉ diễn ra trong môi trường test cách ly.
6. **Không đưa bí mật vào báo cáo**: Mật khẩu, token, hash đầy đủ đều được ẩn hoặc chỉ ghi nhận độ dài/tiền tố.

---

## 2. XÁC MINH KẾT QUẢ KIỂM THỬ (SECTION A)

### 2.1. Cấu hình Ant và Vòng đời Thư mục `build/test/results`
- **Cơ chế dọn dẹp sạch sẽ**:
  - Tệp [nbproject/build-impl.xml](file:///D:/workspace/thuvien/nbproject/build-impl.xml) định nghĩa thuộc tính: `build.test.results.dir=${build.dir}/test/results`.
  - Mục tiêu `clean` thực thi `-do-clean` chứa `<delete dir="${build.dir}"/>`.
  - Mục tiêu `-pre-test-run` chứa `<mkdir dir="${build.test.results.dir}"/>`.
  - **Kết luận**: Khi chạy `ant clean compile`, toàn bộ thư mục `build/` (bao gồm `build/test/results`) bị xóa bỏ hoàn toàn. Do đó, các tệp `TEST-*.xml` sinh ra trong lần chạy `ant test` tiếp theo chắc chắn 100% là kết quả của lần chạy mới nhất, **không có khả năng sót lại tệp XML từ bản build cũ**.

### 2.2. Thời điểm và Số liệu Đo lường Trực tiếp từ Tệp XML Mới
Đã đọc và phân tích trực tiếp toàn bộ 17 tệp `TEST-*.xml` được tạo lúc **10/23/2026 12:13:xx AM**:

| STT | Tên Test Suite | Số Tests | Failures | Errors | Skipped | Thời gian (s) | Timestamp tạo XML |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.client.PermissionHelperTest` | 4 | 0 | 0 | 0 | 0.074 | 2026-10-23 00:13:07 |
| 2 | `thuvien.common.ProtocolTest` | 2 | 0 | 0 | 0 | 0.270 | 2026-10-23 00:13:08 |
| 3 | `thuvien.server.RequestRouterTest` | 34 | 0 | 0 | 0 | 0.497 | 2026-10-23 00:13:08 |
| 4 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 0 | 0 | 0 | 0.094 | 2026-10-23 00:13:09 |
| 5 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 0 | 0 | 0 | 0.318 | 2026-10-23 00:13:09 |
| 6 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 0 | 0 | 0 | 0.089 | 2026-10-23 00:13:10 |
| 7 | `thuvien.server.rmi.EnvironmentCatalogGuardTest` | 5 | 0 | 0 | 0 | 5.860 | 2026-10-23 00:13:16 |
| 8 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 0 | 0 | 0 | 2.534 | 2026-10-23 00:13:19 |
| 9 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 0 | 0 | 0 | 1.751 | 2026-10-23 00:13:21 |
| 10 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 0 | 0 | 0 | 0.397 | 2026-10-23 00:13:22 |
| 11 | `thuvien.server.service.AdminResetPasswordTest` | 13 | 0 | 0 | 0 | 2.671 | 2026-10-23 00:13:25 |
| 12 | `thuvien.server.service.AuthServiceTest` | 5 | 0 | 0 | 0 | 0.618 | 2026-10-23 00:13:26 |
| 13 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 0 | 0 | 0 | 1.185 | 2026-10-23 00:13:27 |
| 14 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 0 | 0 | 0 | 0.535 | 2026-10-23 00:13:28 |
| 15 | `thuvien.server.service.FineServiceTest` | 3 | 0 | 0 | 0 | 0.070 | 2026-10-23 00:13:28 |
| 16 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 34 | 0 | 0 | 0 | 7.697 | 2026-10-23 00:13:36 |
| 17 | `thuvien.server.service.SuspendedStudentAuthTest` | 8 | 0 | 0 | 0 | 2.433 | 2026-10-23 00:13:39 |
| **TỔNG CỘNG** | **17 Test Suites** | **160** | **0** | **0** | **0** | **27.093 s** | **PASS 100%** |

### 2.3. Xác định Chính xác 2 Test Mới Làm Tăng Số Lượng Từ 158 lên 160
Đối chiếu với báo cáo Phase 1.27 (158 tests), suite `thuvien.server.rmi.EnvironmentCatalogGuardTest` trước đây có 3 tests, nay có 5 tests. Hai bài kiểm thử mới được bổ sung chính xác là:
1. `testStopMethod_SafeWhenCalledBeforeStartOrMultipleTimes`:
   - Xác minh việc gọi `stop()` khi server chưa khởi động hoặc gọi liên tiếp nhiều lần là an toàn, không ném ngoại lệ (`safe idempotent no-op`).
2. `testSchemaValidation_ChecksNullConnectionAndDetectsUnmigratedSchema`:
   - Xác minh phương thức `UserRepositoryImpl.isSchemaMigrated()` xử lý an toàn khi kết nối bị `null` hoặc kết nối đã đóng mà không phát sinh lỗi ngoại lệ `NullPointerException`.

### 2.4. Kết quả Chuỗi Lệnh Xác minh Tuần tự

| Lệnh | Mã thoát (Exit Code) | Thời gian | Trạng thái |
|:---|:---:|:---:|:---:|
| `ant clean compile` | **0** | 2 giây | Thành công (BUILD SUCCESSFUL) |
| `ant test` | **0** | 33 giây | Thành công (17 suites, 160 tests PASS) |
| `ant package-all` | **0** | 1 giây | Thành công (Đóng gói 3 bản phân phối) |
| `git diff --check` | **0** | < 1 giây | Hoàn toàn sạch (Không có lỗi whitespace/conflict) |

---

## 3. RÀ SOÁT CẤU HÌNH DATABASE VÀ THỨ TỰ ƯU TIÊN (SECTION B)

### 3.1. Bảng Thứ tự Ưu tiên Cấu hình Database (`DatabaseConfig.java`)
Đã phân tích mã nguồn trực tiếp tại [DatabaseConfig.java](file:///D:/workspace/thuvien/server/src/thuvien/server/database/DatabaseConfig.java). Thứ tự nạp cấu hình được áp dụng theo thứ tự ưu tiên ghi đè từ thấp đến cao (càng xuống dưới càng có quyền ghi đè cao hơn):

| Mức ưu tiên | Nguồn cấu hình | Điều kiện kích hoạt | Phạm vi ảnh hưởng |
|:---:|:---|:---|:---|
| **1 (Mặc định)** | Classpath `db.properties` | Đọc qua `getResourceAsStream("db.properties")` | Thiết lập giá trị cơ sở ban đầu (baseline). |
| **2 (Tệp tùy chỉnh / Test)** | `test.local.properties` hoặc file chỉ định qua `-Ddb.config.file` | **Bắt buộc** có `-Ddb.config.file=...` HOẶC có `-Dapp.env=test` kèm file `test.local.properties` tồn tại | Ghi đè cấu hình cho môi trường kiểm thử tự động. Khi chạy production release, điều kiện này **không kích hoạt**. |
| **3 (Biến môi trường)** | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Có giá trị trong `System.getenv(...)` | Ghi đè cấu hình cho môi trường container/cloud. |
| **4 (Thuộc tính JVM cao nhất)**| `-Ddb.url`, `-Ddb.username`, `-Ddb.password` | Có giá trị trong `System.getProperty(...)` | Quyền ưu tiên cao nhất, ghi đè toàn bộ các nguồn trên. |

### 3.2. Xác nhận Cơ chế Chống Chuyển nhầm Database trong Kiểm thử
- Các lớp kiểm thử tự động đều sử dụng `TestDatabaseSafetyGuard.isSafeTestDatabase(conn)`, yêu cầu tên CSDL kết nối phải có hậu tố `_test`.
- Nếu có bất kỳ cấu hình nào vô tình trỏ vào `school_library`, `TestDatabaseSafetyGuard` sẽ lập tức ném `AssertionError` dừng kiểm thử, bảo đảm không bao giờ ghi dữ liệu kiểm thử vào database sản xuất.

### 3.3. Xác nhận Release Server Không Fallback Sang Database Mặc định
- Nếu không có bất kỳ nguồn cấu hình hợp lệ nào được nạp (`!loadedFromClasspath && !loadedFromTestFile && !hasEnvOverride && !hasSysOverride`), `DatabaseConfig` ném `IllegalStateException("[DATABASE CONFIG ERROR] Không tìm thấy tệp cấu hình 'db.properties'...")`.
- Nếu `db.url` bị trống, ném `IllegalStateException`.
- Nếu thông tin đăng nhập còn giữ giá trị placeholder (`your_db_username`, `your_db_password`), ném `IllegalStateException`.
- **Kết luận**: Bản release máy chủ **tuyệt đối không tự fallback** sang CSDL mặc định.

### 3.4. Xác nhận Thời điểm Catalog Guard và Vòng đời Giải phóng Tài nguyên
- Trong `LibraryRMIServer.start()`:
  - Catalog Guard và Schema Verification chạy ở **dòng 99–141**, trước khi thực hiện phân giải IP (dòng 144) và trước khi gọi `LocateRegistry.createRegistry` (dòng 151) hay `exportObject` (dòng 161).
  - Nếu catalog không khớp hoặc schema thiếu cột, server hủy khởi động trước khi mở bất kỳ cổng RMI nào.
- Mọi khối lệnh trong `start()` sau bước kiểm tra database đều được bọc trong `try ... catch (Exception ex)` gọi `cleanupRmiResources()`, bảo đảm giải phóng registry và unexport remote service nếu có lỗi phát sinh.

---

## 4. KIỂM TRA ĐỘC LẬP RECOVERLIBRARIAN2UTILITY (SECTION C)

Đã rà soát chi tiết [RecoverLibrarian2Utility.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java):

1. **Cú pháp tham số và cách truyền kết nối**:
   - Tham số dòng lệnh: `--target-catalog <tên_catalog>`, `--backup-dir <thư_mục>`, `--apply`.
   - Thuộc tính JVM: `-Dtarget.catalog=<catalog>`, `-Dbackup.dir=<path>`.
   - Kết nối CSDL được nạp an toàn thông qua `DatabaseConfig` (hỗ trợ `-Ddb.url`, `-Ddb.username`, `-Ddb.password`).
2. **Điều kiện xác minh mục tiêu**:
   - Truy vấn: `SELECT id, username, role, is_active, LENGTH(password_hash) as hash_len FROM users WHERE id = ? AND username = ? AND role = ? AND is_active = 1`.
   - Tham số: `TARGET_ID = 3L`, `TARGET_USERNAME = "librarian2"`, `TARGET_ROLE = "LIBRARIAN"`.
   - Bắt buộc kiểm tra `matchCount == 1`.
3. **Cơ chế Dry-Run an toàn**:
   - Cờ `apply` mặc định là `false`. Chỉ khi dòng lệnh có `--apply` thì `apply` mới thành `true`.
   - Ở chế độ Dry-Run, tiện ích dừng ở dòng 148 với thông báo `[DRY-RUN HOÀN TẤT]`, không thực hiện bất kỳ lệnh ghi nào.
4. **Kiểm soát giao dịch và Rollback**:
   - `conn.setAutoCommit(false)`.
   - Sử dụng `SELECT ... FOR UPDATE` khóa bản ghi mục tiêu.
   - Sao lưu hash cũ trước khi UPDATE; nếu sao lưu thất bại, hủy toàn bộ giao dịch trước khi UPDATE.
   - Bắt buộc `affectedRows == 1`.
   - Đọc lại hash vừa cập nhật trên cùng kết nối, xác minh tiền tố `PBKDF2$`, cờ `must_change_password == true` và gọi `PasswordHasher.verify(plainPassword, newHash) == true`.
   - Chỉ khi toàn bộ bước đều thành công mới gọi `conn.commit()`. Bất kỳ lỗi nào phát sinh đều kích hoạt `conn.rollback()`.
5. **Đặc tính Phân quyền File Backup trên Windows vs POSIX (Lưu ý Kỹ thuật quan trọng)**:
   - File sao lưu **chỉ chứa `USER_ID`, `USERNAME`, `OLD_HASH`**, tuyệt đối không lưu mật khẩu tạm.
   - Trên hệ thống POSIX: Áp dụng `PosixFilePermissions` (600 - Owner Read/Write).
   - Trên hệ thống Windows NTFS: Tiện ích đã được nâng cấp để áp dụng `AclFileAttributeView` cấp quyền riêng cho Owner (`AclEntryType.ALLOW`). Tuy nhiên, trên Windows, nếu thư mục cha kế thừa quyền hạn (NTFS inheritance) hoặc chạy trên phân vùng FAT32/exFAT, phương thức `File.setReadable()` chỉ can thiệp vào thuộc tính DOS Read-Only mà không khóa được hoàn toàn DACL của người dùng khác trong cùng máy.
   - **Khuyến nghị vận hành**: Quản trị viên khi triển khai trên Windows cần cấu hình quyền truy cập thư mục `./security_backups/` bằng lệnh `icacls` để bảo đảm chỉ có tài khoản Administrator hoặc tài khoản dịch vụ đọc được.

---

## 5. KIỂM TRA ĐỘC LẬP RELEASE ARTIFACT (SECTION D)

### 5.1. Bảng Băm SHA-256 Các Tệp Trong Gói `dist/server-release`
Đã quét đệ quy và tính toán mã băm SHA-256 cho toàn bộ các tệp vừa đóng gói:

| Đường dẫn tệp | Kích thước (bytes) | SHA-256 Hash |
|:---|:---:|:---|
| `dist/server-release/run-server.bat` | 1.087 | `4BD371179985B7E1B51E8670F8E85831D42A6937B15BE3DB685AE22ED76B3D66` |
| `dist/server-release/server.jar` | 195.059 | `578078E2D1B57087AE393D7CA860F29FDECB1A6168C32DD5B01550EB98701B4B` |
| `dist/server-release/resources/db.properties.example` | 541 | `104455A0BCDA578E2BFB4C74E43C4E76C579B509AD290B1984E3472C67B665F0` |
| `dist/server-release/resources/server.properties` | 207 | `7D730EE8A56CA3DD307AE65CB06FB9D43A5FC02E6DE4578D6A7BF343542EBCB9` |
| `dist/server-release/lib/HikariCP-4.0.3.jar` | 159.222 | `7C024AEFF1C1063576D74453513F9DE6447D8E624D17F8E27F302D4E12C81DFE` |
| `dist/server-release/lib/mysql-connector-java-8.0.33.jar` | 2.481.560 | `E2A3B2FC726A1AC64E998585DB86B30FA8BF3F706195B78BB77CE751846A316C` |
| `dist/server-release/lib/slf4j-api-1.7.36.jar` | 41.125 | `D3EF575E3E4979678DC01BF1DCCE51021493B4D11FB7F1BE8AD9ED2E42B8B841` |
| `dist/server-release/lib/slf4j-jdk14-1.7.36.jar` | 8.436 | `5BF64690AF4E59876B8902BB0DB3DD39C686A40ABFED97D3837EB9EAC43F59E9` |

### 5.2. Xác minh Chi tiết Nội dung JAR và Tệp Cấu hình
- Đã giải nén trực tiếp và kiểm tra `server.properties` bên trong `server.jar`: Chỉ cấu hình cổng RMI (`1099`, `1100`) và tên dịch vụ, **không có thông tin database hay mật khẩu**.
- `MANIFEST.MF` cấu hình `Main-Class: thuvien.server.rmi.LibraryRMIServer`, `Class-Path` trỏ đúng vào `resources/` và 4 file JAR trong `lib/`.
- Tệp mẫu `db.properties.example` trỏ vào CSDL mẫu `school_library` (không trỏ vào `school_library_test`), sử dụng username/password placeholder.
- Tệp `run-server.bat` kiểm tra tệp `resources\db.properties`, báo lỗi và thoát nếu chưa được tạo; không tự ý tạo file và không fallback.

---

## 6. KIỂM TRA ĐỘC LẬP MIGRATION VÀ RUNBOOK (SECTION E)

### 6.1. Đánh giá Script `migration_must_change_password_mysql8.sql`
- **Khả năng tương thích MySQL 8.0.30**: Cú pháp `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` đã được chứng minh là lỗi thời và không hỗ trợ trên MySQL 8.0. Script sử dụng Stored Procedure an toàn:
  - Khi cột chưa có: Thêm cột `must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active`.
  - Khi cột đã có và đúng kiểu `tinyint(1)`: In thông báo `SKIP` và bỏ qua không sửa đổi.
  - Khi cột đã có nhưng sai kiểu/ràng buộc: Dừng ngay với `SIGNAL SQLSTATE '45000'`.
  - So sánh `COUNT(*)` trước và sau migration, bảo đảm không làm mất hoặc thay đổi số lượng bản ghi.
- **Quyền DDL**: Script yêu cầu quyền `ALTER, CREATE ROUTINE, DROP, SELECT` trên `school_library`, được thực thi riêng biệt bởi DBA qua tài khoản có quyền quản trị.

### 6.2. Phân định Rõ ràng về Cơ chế Hoàn tác (Rollback Mechanism)
> [!IMPORTANT]
> **Khác biệt cốt lõi giữa Rollback DDL và Phục hồi Dữ liệu**:
> - Câu lệnh hoàn tác DDL (`ALTER TABLE users DROP COLUMN must_change_password`) chỉ loại bỏ định nghĩa cột về mặt cấu trúc bảng.
> - Nếu sau khi migration, hệ thống đã đi vào hoạt động và người dùng đã đăng nhập hoặc thay đổi mật khẩu, việc chỉ chạy DROP COLUMN **KHÔNG THỂ khôi phục lại trạng thái nghiệp vụ ban đầu**.
> - Vì vậy, bản sao lưu `mysqldump` trước migration (`D:\backup\school_library_pre_migration_YYYYMMDD.sql`) được quy định trong runbook là **phương án phục hồi dữ liệu duy nhất có thẩm quyền khôi phục toàn vẹn trạng thái nghiệp vụ**.

---

## 7. PHÂN LOẠI PHÁT HIỆN KIỂM TOÁN (FINDINGS CLASSIFICATION)

| Mức độ | Mã phát hiện | Mô tả phát hiện | Trạng thái / Biện pháp xử lý |
|:---:|:---:|:---|:---|
| **CRITICAL** | — | Không phát hiện lỗi nghiêm trọng nào gây mất an toàn dữ liệu hoặc rò rỉ bí mật. | **NONE** |
| **HIGH** | — | Không phát hiện lỗi mức độ cao trong kiến trúc hoặc phân quyền runtime. | **NONE** |
| **MEDIUM** | `MED-01` | **Phân quyền File Backup trên Windows**: Phương thức `File.setReadable()` trên Windows chỉ đổi thuộc tính DOS Read-Only, không thay thế hoàn toàn NTFS DACL nếu thư mục cha có quyền kế thừa (inheritance). | **ĐÃ XỬ LÝ & TÀI LIỆU HÓA**: Đã cập nhật `RecoverLibrarian2Utility` hỗ trợ `AclFileAttributeView` và bổ sung hướng dẫn cấu hình `icacls` trong runbook triển khai. |
| **LOW** | `LOW-01` | **Cảnh báo Git LF/CRLF**: Git hiển thị warning thay thế LF bằng CRLF khi checkout trên môi trường Windows. | **KHÔNG ẢNH HƯỞNG**: `git diff --check` cho kết quả exit code 0, không có lỗi whitespace hay conflict marker. |

---

## 8. XÁC MINH TRẠNG THÁI CƠ SỞ DỮ LIỆU PRODUCTION (`school_library`)

Đã thực hiện truy vấn trực tiếp kiểm tra trên CSDL `school_library`:
```sql
SELECT id, username, role, is_active, LENGTH(password_hash) as hash_len FROM users;
```
**Kết quả thực tế**:
```
id    username      role         is_active    hash_len
1     admin         ADMIN        1            110
2     librarian1    LIBRARIAN    1            110
3     librarian2    LIBRARIAN    1            64
4     student1      STUDENT      1            110
8     studen2       STUDENT      1            110
```
- Số lượng bản ghi: 5 tài khoản nguyên vẹn.
- Tài khoản `librarian2`: Hash length = 64 (giữ nguyên chuỗi SHA-256 legacy, chưa đổi mật khẩu).
- Cột `must_change_password`: **Chưa tồn tại** (chưa chạy DDL migration).
- **Kết luận xác minh**: Database Production hoàn toàn chưa bị can thiệp, dữ liệu nguyên trạng 100%.

---

## 9. ĐIỀU KIỆN KẾT THÚC VÀ KẾT LUẬN CUỐI CÙNG

Các kiểm chứng độc lập xác nhận báo cáo Phase 1.28 phản ánh chính xác trạng thái thực tế của hệ thống. Các kết luận chính thức được xác lập như sau:

### 1. BUILD VERIFIED: PASS
> Toàn bộ 131 mã nguồn biên dịch sạch với Java 8; kiểm thử tự động Ant chạy thành công 17 test suites với 160/160 tests PASS (0 failures, 0 errors, 0 skipped); `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Bản phát hành `dist/server-release` hoàn toàn cách ly, không chứa thông tin database kiểm thử, không nhúng file cấu hình bí mật vào JAR, script khởi động có cơ chế bảo vệ bắt buộc và runbook vận hành đầy đủ các bước kiểm tra an toàn.

### 3. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Mặc định hệ thống đã sẵn sàng về mặt kỹ thuật và chất lượng đóng gói, nhưng **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để mở khung giờ bảo trì và thực thi triển khai. Tuyệt đối không tự ý triển khai hoặc tự động chạy tiện ích phục hồi trên môi trường sản xuất.
