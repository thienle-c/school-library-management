# BÁO CÁO THẮT CHẶT BẢO MẬT VẬN HÀNH (PHASE 1.30)
## Operational Security Hardening Audit & Final Verification Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.30 — Operational Security Hardening  
> **Thời điểm thực hiện**: 2026-10-23 00:23:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Được bảo vệ 100% — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã kiểm chứng cách ly)  

---

## 1. TỔNG QUAN VÀ CAM KẾT AN TOÀN

Giai đoạn Phase 1.30 tập trung giải quyết triệt để các điều kiện bảo mật vận hành (Operational Security) còn tồn đọng sau Phase 1.29, bao gồm:
1. Thắt chặt ACL thư mục và tệp sao lưu trên Windows NTFS, triệt tiêu cửa sổ thời gian phơi bày quyền kế thừa.
2. Xác lập ranh giới quyền tối thiểu (Least Privilege) cho tài khoản runtime `thuvien_app` và phân định với quyền DBA.
3. Thẩm định kênh truyền RMI qua mạng LAN, làm rõ hiện trạng mã hóa TLS và đề xuất kiến trúc VLAN/VPN.
4. Chuẩn hóa quy trình thẩm định sao lưu 3 cấp độ (3-Tier Backup Verification) và diễn tập phục hồi trên CSDL test độc lập.
5. Kiểm chứng hồi quy toàn bộ hệ thống qua chuỗi lệnh `ant clean compile`, `ant test`, `ant package-all`, `git diff --check`.

### Cam kết bảo vệ Production nghiêm ngặt:
- **Không thay đổi CSDL production `school_library`**: 0 câu lệnh DDL/DML được thực thi trên production.
- **Không chạy `RecoverLibrarian2Utility --apply`**: Mật khẩu và hash thật của toàn bộ 5 tài khoản production giữ nguyên 100%.
- **Không chạy git reset, revert, stash, commit hoặc push**: Bảo toàn nguyên trạng working tree.
- **Không tạo backup chứa dữ liệu production** trong quá trình thử nghiệm ACL.

---

## 2. KẾT QUẢ THẨM ĐỊNH VÀ KHẮC PHỤC CHI TIẾT

### 2.1. Thắt chặt Windows Backup ACL và Triệt tiêu Cửa sổ Thời gian Phơi bày

#### 2.1.1. Hiện trạng và Lỗ hổng Cửa sổ Thời gian (Time-Window Vulnerability)
- Trước Phase 1.30, tiện ích `RecoverLibrarian2Utility` tạo thư mục `backupDir` và ghi tệp trước khi áp dụng quyền truy cập.
- Khi tạo tệp trên Windows NTFS, tệp mới mặc định kế thừa danh sách điều khiển truy cập (DACL) từ thư mục cha:
  - `BUILTIN\Administrators:(I)(F)`
  - `NT AUTHORITY\SYSTEM:(I)(F)`
  - `NT AUTHORITY\Authenticated Users:(I)(M)` (Quyền Modify)
  - `BUILTIN\Users:(I)(RX)` (Quyền Read & Execute cho tất cả người dùng cục bộ)
- Nếu chỉ áp dụng quyền sau khi ghi file, tồn tại một **cửa sổ thời gian (window of vulnerability)** ngắn mà trong đó file chứa hash cũ có thể bị đọc bởi các tài khoản người dùng khác trên cùng máy chủ.

#### 2.1.2. Giải pháp Đã Triển khai và Kiểm chứng Thực nghiệm
Đã tái cấu trúc phương thức `backupOldHashSafely` trong [RecoverLibrarian2Utility.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java):
1. **Bảo vệ thư mục TRƯỚC KHI tạo tệp (`secureDirectory`)**:
   - Khi tạo hoặc mở thư mục `backupDir`, tiện ích sử dụng `java.nio.file.attribute.AclFileAttributeView` để thiết lập ACL riêng cho Owner.
   - Gán cờ kế thừa `(OI)(CI)` (`FILE_INHERIT`, `DIRECTORY_INHERIT`) với quyền `AclEntryType.ALLOW` cho `Owner`.
   - Toàn bộ quyền kế thừa từ ổ đĩa cha (`Authenticated Users`, `Users`) bị ngắt bỏ hoàn toàn khỏi thư mục.
   - Do thư mục đã được khóa trước, **mọi tệp được tạo bên trong thư mục ngay từ khoảnh khắc sinh ra đã tự động kế thừa quyền Owner-only**, loại bỏ 100% cửa sổ thời gian phơi bày.
2. **Khóa tệp riêng lẻ (`secureFile`)**:
   - Sau khi ghi hoàn tất, tiếp tục áp dụng `secureFile` xác lập lại ACL mức tệp chỉ cho phép Owner `READ_DATA`, `WRITE_DATA`, `DELETE`, `READ_ACL`, `SYNCHRONIZE`.
3. **Bằng chứng Thực nghiệm qua `icacls` trên Windows**:
   - Kết quả kiểm tra bằng lệnh `icacls` trên thư mục và tệp được bảo vệ:
     ```
     scratch\test_secure_dir          THIENUI\thien:(OI)(CI)(Rc,WDAC,WO,S,RD,WD,AD,RA,WA)
     scratch\test_secure_dir\file.txt THIENUI\thien:(Rc,S,RD,WD,RA,WA)
     ```
   - Cờ `(I)` biến mất hoàn toàn. Các nhóm `BUILTIN\Users` và `Authenticated Users` hoàn toàn không có trong danh sách phân quyền.
4. **Giới hạn kỹ thuật cần lưu ý**:
   - Cơ chế trên hoạt động trên phân vùng định dạng **NTFS**. Nếu thư mục backup được đặt trên phân vùng FAT32 hoặc exFAT (vốn không hỗ trợ DACL), hệ điều hành sẽ bỏ qua ACL và chỉ áp dụng thuộc tính Read-Only. Do đó, Quản trị viên bắt buộc phải chọn thư mục lưu trữ trên phân vùng NTFS và chạy tiện ích dưới tài khoản có thẩm quyền quản trị.

---

### 2.2. MySQL Least Privilege — Phân định Quyền Runtime và Quyền DBA

Đã kiểm tra và chuẩn hóa tài liệu [DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md):

#### 2.2.1. Phân định Rạch ròi 2 Cấp độ Tài khoản
1. **Tài khoản Runtime Ứng dụng (`thuvien_app`)**:
   - **Chỉ định danh kết nối**: `thuvien_app`@`127.0.0.1` (Chỉ chấp nhận kết nối nội bộ loopback, từ chối mọi kết nối từ bên ngoài).
   - **Quyền hạn được cấp**: `SELECT, INSERT, UPDATE, DELETE` trên `school_library.*`.
   - **Cấm tuyệt đối các quyền DDL**: Không cấp `CREATE, ALTER, DROP, TRUNCATE, RENAME`.
   - **Cấm tuyệt đối các quyền Quản trị**: Không cấp `GRANT OPTION, SUPER, RELOAD, PROCESS, FILE`.
   - **Cấm truy cập CSDL hệ thống**: Không có quyền truy cập `mysql.*`, `information_schema.*` (ngoại trừ quyền đọc metadata tiêu chuẩn), `performance_schema.*`.
2. **Tài khoản Quản trị DBA (`root` hoặc Admin DBA riêng)**:
   - Chỉ sử dụng trong các cửa sổ bảo trì ngoại tuyến để:
     - Tạo CSDL và phân quyền ban đầu.
     - Chạy tệp sao lưu `mysqldump`.
     - Thực thi script migration schema [migration_must_change_password_mysql8.sql](file:///D:/workspace/thuvien/database/migration_must_change_password_mysql8.sql).
   - **Tuyệt đối không cấu hình tài khoản DBA vào tệp `resources/db.properties`** của ứng dụng.

#### 2.2.2. Câu lệnh Khởi tạo và Kiểm tra dành cho DBA
```sql
-- 1. Tao tai khoan ung dung
CREATE USER 'thuvien_app'@'127.0.0.1' IDENTIFIED BY 'Mat_Khau_Phuc_Tap_Cua_Admin!2026';

-- 2. Cap quyen toi thieu DML
GRANT SELECT, INSERT, UPDATE, DELETE ON school_library.* TO 'thuvien_app'@'127.0.0.1';
FLUSH PRIVILEGES;

-- 3. Cau lenh kiem tra xac minh quyen (DBA Verification)
SHOW GRANTS FOR 'thuvien_app'@'127.0.0.1';
-- Ket qua phai khang dinh: Chi co GRANT USAGE ON *.* va GRANT SELECT, INSERT, UPDATE, DELETE ON `school_library`.*
```

---

### 2.3. RMI và An toàn Kênh truyền Mạng (Network Security & TLS Status)

#### 2.3.1. Hiện trạng Kênh truyền RMI
- **Cổng dịch vụ RMI**:
  - Cổng RMI Registry: `1099/TCP` (Cấu hình qua `rmi.registry.port`).
  - Cổng Remote Object Service: `1100/TCP` (Cấu hình qua `rmi.service.port`, cổng cố định non-dynamic, thuận tiện cấu hình tường lửa).
- **Cơ chế Quảng bá Địa chỉ IP (`resolveServerHost`)**:
  - Tự động quét và chọn địa chỉ IPv4 vật lý non-loopback trong mạng LAN trường học (ví dụ: `192.168.1.50` hoặc `10.0.0.15`), gán vào `java.rmi.server.hostname`.
- **Hiện trạng Mã hóa Kênh truyền**:
  - **CHƯA SỬ DỤNG TLS**: Lớp `LibraryRemoteServiceImpl` kế thừa `UnicastRemoteObject` sử dụng các socket TCP thuần (`java.net.Socket`, `java.net.ServerSocket`).
  - Dữ liệu giao thức `Request` và `Response` được truyền qua mạng dưới dạng dòng nhị phân Java Serialization chuẩn, **không có mã hóa SSL/TLS**.
  - **Ghi nhận rủi ro chính thức**: Nếu mạng LAN không được cách ly, kẻ tấn công có thể sử dụng các công cụ bắt gói tin (như Wireshark, tcpdump) để đánh cắp token phiên làm việc (`sessionToken` dạng UUID) hoặc xem trộm thông tin bạn đọc và mật khẩu trong payload.

#### 2.3.2. Yêu cầu Cách ly Mạng Bắt buộc khi Vận hành
Để bảo vệ an toàn cho hệ thống khi chưa kích hoạt TLS, Quản trị viên mạng trường học bắt buộc áp dụng các chính sách mạng sau:
1. **Phân vùng mạng VLAN riêng biệt (Private Library VLAN)**:
   - Đặt máy chủ thư viện và toàn bộ máy trạm thủ thư/OPAC trong một subnet/VLAN độc lập.
   - Cấm hoàn toàn định tuyến truy cập từ mạng Wi-Fi sinh viên hoặc mạng Internet công cộng vào các cổng `1099` và `1100`.
2. **Bắt buộc dùng VPN có mã hóa khi truy cập từ xa**:
   - Nếu thủ thư hoặc người quản trị làm việc từ xa hoặc từ các chi nhánh khác của trường học, bắt buộc phải thiết lập đường hầm VPN (IPsec, WireGuard, OpenVPN) trước khi kết nối ứng dụng Swing Client tới máy chủ.
3. **Tuyệt đối KHÔNG mở Port Forwarding ra Internet**:
   - Không cấu hình router chuyển tiếp cổng (NAT/Port Forwarding) các cổng `1099` và `1100` ra ngoài Internet công cộng.

---

### 2.4. Quy trình Sao lưu và Phục hồi CSDL MySQL (Backup & Restore)

Đã bổ sung và hoàn thiện [docs/BACKUP-RESTORE.md](file:///D:/workspace/thuvien/docs/BACKUP-RESTORE.md) với quy trình thẩm định 3 cấp độ:

#### 2.4.1. Quy trình Thẩm định Bản sao lưu 3 Cấp độ (3-Tier Backup Verification)
1. **Cấp độ 1 — Kiểm tra Tệp tồn tại & Dung lượng (`size > 0`)**:
   - Ngăn chặn lỗi dump sinh ra file rỗng 0 bytes do thiếu quyền đĩa hoặc hết dung lượng:
     ```powershell
     Get-Item D:\library_backups\school_library_backup_*.sql | Select-Object Name, Length, LastWriteTime
     ```
2. **Cấp độ 2 — Kiểm tra Cấu trúc Header & Footer (Structural Integrity Check)**:
   - Đọc 10 dòng đầu để xác nhận header MySQL dump hợp lệ (`-- MySQL dump 10.13 ...`).
   - Đọc 5 dòng cuối để xác nhận tiến trình kết thúc trọn vẹn, không bị đứt gãy giữa chừng (`-- Dump completed on YYYY-MM-DD ...`).
     ```powershell
     Get-Content D:\library_backups\school_library_backup_*.sql -TotalCount 10
     Get-Content D:\library_backups\school_library_backup_*.sql -Tail 5
     ```
3. **Cấp độ 3 — Diễn tập Phục hồi Thực sự trên CSDL Kiểm thử độc lập (Real Dry-Run Restore)**:
   - **Tuyệt đối không khôi phục đè lên `school_library`**.
   - Tạo CSDL diễn tập tạm thời `school_library_restore_test`:
     ```sql
     CREATE DATABASE IF NOT EXISTS school_library_restore_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
     ```
   - Khôi phục bản dump vào CSDL kiểm thử:
     ```cmd
     mysql -u root -p school_library_restore_test < D:\library_backups\school_library_backup_YYYYMMDD.sql
     ```
   - Chạy các câu lệnh SQL đối soát kiểm tra số lượng bản ghi của 7 bảng cốt lõi (`users`, `students`, `books`, `categories`, `borrow_records`, `fines`, `reservations`), kiểm tra không có tồn kho sách âm (`available_copies < 0`) và không có số lượng mượn âm.
   - Sau khi xác nhận thành công, dọn dẹp bằng lệnh:
     ```sql
     DROP DATABASE school_library_restore_test;
     ```

---

## 3. KẾT QUẢ KIỂM THỬ HỒI QUY TOÀN DIỆN (REGRESSION VERIFICATION)

Đã thực thi chuỗi lệnh kiểm thử và đóng gói tự động tuần tự:

| Lệnh thực thi | Mã thoát (Exit Code) | Thời gian chạy | Kết quả kiểm tra |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 2 giây | Biên dịch sạch 131 source files. |
| `ant test` | **0** | 38 giây | **17 test suites, 160 tests PASS**, 0 failures, 0 errors, 0 skipped. |
| `ant package-all` | **0** | 1 giây | Đóng gói thành công `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Hoàn toàn sạch, không có lỗi whitespace hay conflict markers. |

### Thống kê 17 Test Suites từ tệp XML mới nhất (Timestamp: `10/23/2026 12:22:29 - 12:23:06 AM`):
1. `thuvien.client.PermissionHelperTest`: 4/4 PASS
2. `thuvien.common.ProtocolTest`: 2/2 PASS
3. `thuvien.server.RequestRouterTest`: 34/34 PASS
4. `thuvien.server.TestDatabaseSafetyGuardTest`: 6/6 PASS
5. `thuvien.server.database.MySQLSmokeIntegrationTest`: 4/4 PASS
6. `thuvien.server.repository.RepositoryArchitectureTest`: 3/3 PASS
7. `thuvien.server.rmi.EnvironmentCatalogGuardTest`: 5/5 PASS
8. `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest`: 6/6 PASS
9. `thuvien.server.rmi.RMIEndToEndIntegrationTest`: 15/15 PASS
10. `thuvien.server.rmi.RMISerializationFilterTest`: 10/10 PASS
11. `thuvien.server.service.AdminResetPasswordTest`: 13/13 PASS
12. `thuvien.server.service.AuthServiceTest`: 5/5 PASS
13. `thuvien.server.service.BorrowConcurrencyIntegrationTest`: 4/4 PASS
14. `thuvien.server.service.CreateUserSecurityTest`: 4/4 PASS
15. `thuvien.server.service.FineServiceTest`: 3/3 PASS
16. `thuvien.server.service.SecurityAndFeatureHardeningTest`: 34/34 PASS
17. `thuvien.server.service.SuspendedStudentAuthTest`: 8/8 PASS
- **Tổng cộng**: **160 tests**, 0 lỗi, thời gian chạy JUnit thuần: **31.369 giây**.

---

## 4. XÁC MINH GÓI PHÁT HÀNH VÀ CƠ SỞ DỮ LIỆU PRODUCTION

### 4.1. Gói Phát hành `dist/server-release`
- Đã quét các tệp sau khi đóng gói:
  - `dist/server-release/server.jar` (195.059 bytes): Không chứa `db.properties`, không chứa cấu hình máy phát triển, manifest trỏ đúng `LibraryRMIServer`.
  - `dist/server-release/resources/db.properties.example`: Trỏ CSDL mẫu `school_library`, thông tin đăng nhập placeholder `your_db_username`/`your_db_password`.
  - `dist/server-release/resources/server.properties`: Cổng RMI `1099`, `1100`.
  - `dist/server-release/run-server.bat`: Dừng ngay với exit code 1 nếu thiếu `resources\db.properties`.
  - `dist/server-release/lib/`: Đầy đủ 4 thư viện runtime (`HikariCP`, `mysql-connector-java`, 2 file `slf4j`).

### 4.2. Trạng thái Cơ sở Dữ liệu Production (`school_library`)
- Đã truy vấn đối soát độc lập trên MySQL:
  ```
  id    username      role         is_active    hash_len
  1     admin         ADMIN        1            110
  2     librarian1    LIBRARIAN    1            110
  3     librarian2    LIBRARIAN    1            64
  4     student1      STUDENT      1            110
  8     studen2       STUDENT      1            110
  ```
- **Số tài khoản**: Đúng 5 tài khoản nguyên vẹn.
- **Tài khoản `librarian2`**: Hash length = 64 (giữ nguyên SHA-256 legacy, chưa đổi mật khẩu).
- **Cột `must_change_password`**: Chưa tồn tại (chưa chạy migration DDL).
- **Kết luận**: Cơ sở dữ liệu Production **giữ nguyên trạng 100%**.

---

## 5. CÁC BƯỚC CẦN DBA / QUẢN TRỊ VIÊN THỰC HIỆN THỦ CÔNG

Khi nhận bàn giao hệ thống, Quản trị viên/DBA cần thực hiện tuần tự các bước thủ công sau trong khung giờ bảo trì:

1. **Sao lưu CSDL Production**:
   Chạy `mysqldump` và kiểm tra đủ 3 cấp độ (kích thước > 0, header/footer đầy đủ, diễn tập restore vào `school_library_restore_test`).
2. **Chạy Migration CSDL**:
   Chạy `mysql -u root -p school_library < database/migration_must_change_password_mysql8.sql` để thêm cột `must_change_password` một cách an toàn.
3. **Tạo tài khoản Runtime có Quyền tối thiểu**:
   Tạo tài khoản `thuvien_app`@`127.0.0.1` với mật khẩu mạnh và chỉ cấp quyền `SELECT, INSERT, UPDATE, DELETE`.
4. **Cấu hình `resources/db.properties`**:
   Sao chép từ tệp mẫu trong `dist/server-release` và điền thông tin đăng nhập của `thuvien_app`.
5. **Khôi phục / Đổi mật khẩu tài khoản `librarian2`**:
   Nếu cần khôi phục quyền truy cập cho thủ thư 2, chạy tiện ích [RecoverLibrarian2Utility](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java) với cờ `--target-catalog school_library --apply` qua cửa sổ console interactive để nhập mật khẩu an toàn.
6. **Thiết lập Cách ly Mạng**:
   Cấu hình cổng switch và router firewall để đặt server vào Library VLAN riêng, không mở cổng 1099/1100 ra Internet.

---

## 6. KẾT LUẬN CUỐI CÙNG VÀ CỔNG TRIỂN KHAI (FINAL GATE VERDICTS)

Dựa trên toàn bộ kết quả kiểm toán thắt chặt bảo mật vận hành và kiểm thử thực nghiệm:

### 1. BUILD VERIFIED: PASS
> Toàn bộ 131 source files biên dịch sạch trên Java 8. Bộ kiểm thử JUnit đạt **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped). `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Gói phát hành `dist/server-release` sạch hoàn toàn, bảo đảm cách ly bí mật, có cơ chế fail-fast khi thiếu cấu hình, và tài liệu runbook đã tích hợp đầy đủ quy trình an toàn 3 cấp độ và phân quyền tối thiểu.

### 3. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Hệ thống đạt đầy đủ tiêu chuẩn chất lượng và an toàn vận hành, **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để thực thi triển khai trên môi trường thật. Tuyệt đối không tự ý triển khai trước khi có phê duyệt.
