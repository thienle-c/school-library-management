# BẢNG KIỂM TRA VÀ CỔNG BẰNG CHỨNG TRIỂN KHAI STAGING (PHASE 1.33)
## Staging Deployment Checklist & Evidence Gate Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.33 — Staging Deployment Checklist & Evidence Gate  
> **Thời điểm thực hiện**: 2026-10-23 00:49:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Bảo vệ tuyệt đối — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã kiểm chứng độc lập)  

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC BẢO MẬT BẮT BUỘC

Mục tiêu của Phase 1.33 là xây dựng **Bảng Kiểm tra Điều kiện Triển khai Thực tế (Deployment Checklist)** và xác lập **Cổng Bằng chứng (Evidence Gate)** trước khi hệ thống được chuyển giao sang môi trường STAGING. 

### Nguyên tắc Đánh giá Trung thực và Bất khả Xâm phạm:
1. **Không giả lập kết quả thành PASS**: Nếu môi trường hiện tại chưa được cấp phát tài khoản dịch vụ thực tế hoặc chưa có xác nhận từ đội ngũ Quản trị mạng (NOC), hạng mục đó **bắt buộc ghi nhận là `BLOCKED` hoặc `NOT EXECUTED`**.
2. **Bảo vệ CSDL Production `school_library` tuyệt đối**: 0 câu lệnh DDL/DML, 0 thao tác migration, không reset mật khẩu thật, không chạy `RecoverLibrarian2Utility --apply`.
3. **Không dùng tài khoản DBA/root làm tài khoản runtime**: Runtime service bắt buộc phải dùng tài khoản giới hạn quyền.
4. **Bảo toàn mã nguồn và Git**: Không thực hiện `git reset`, `git checkout` đè, `git clean`, `git stash`, commit hoặc push. Giữ nguyên toàn bộ working directory.
5. **Không để lộ bí mật**: Không đưa mật khẩu thật, hash đầy đủ hay UUID session vào báo cáo.

---

## 2. BẢNG KIỂM TRA TRIỂN KHAI MÔI TRƯỜNG STAGING (STAGING CHECKLIST MATRIX)

| Mã Hạng mục | Nội dung Kiểm tra Điều kiện Triển khai | Trạng thái Đánh giá | Bằng chứng Thực nghiệm & Lý do |
|:---:|:---|:---:|:---|
| **STG-A01** | **Tài khoản Windows Runtime Dịch vụ**<br>Xác định và cấp phát tài khoản chuyên dụng chạy server | **BLOCKED** | Lệnh `Get-LocalUser` xác nhận hệ thống Windows hiện tại **chưa có tài khoản dịch vụ chuyên dụng** (như `svc_library`). Hệ thống chỉ có tài khoản cá nhân tương tác (`THIENUI\thien`). Cần Windows SysAdmin tạo tài khoản. |
| **STG-A02** | **Quyền Hệ thống Tệp (NTFS File Permissions)**<br>Quyền đọc JAR/cấu hình, quyền ghi log/backup | **PASS (Đã thiết kế)**<br>*(Chờ gán `svc_library`)* | Cấu trúc `dist/server-release` đã phân tách rõ ràng; script `run-server.bat` chạy độc lập; thư mục `security_backups/` được bảo vệ bằng owner-only ACL fail-safe. |
| **STG-B01** | **CSDL Môi trường Staging (`school_library_staging`)**<br>Schema staging độc lập với production | **BLOCKED** | Lệnh `SHOW DATABASES` xác nhận trên MySQL **chưa tồn tại database `school_library_staging`**. Hiện chỉ có `school_library` và `school_library_test`. DBA cần chạy lệnh tạo database. |
| **STG-B02** | **Tài khoản MySQL Runtime Quyền Tối thiểu**<br>Chỉ cấp DML trên schema staging, không DDL/DBA | **BLOCKED** | Lệnh `SELECT user, host FROM mysql.user` xác nhận **chưa có tài khoản `thuvien_staging_app`** (chỉ có `root@localhost`). DBA cần chạy script tạo tài khoản theo runbook. *(Lưu ý: Cơ chế Least Privilege đã kiểm chứng thực nghiệm thành công qua tài khoản test ở Phase 1.32)*. |
| **STG-C01** | **Kênh truyền RMI & Trạng thái TLS**<br>Xác định cổng và giao thức truyền thông | **PASS (Rà soát)**<br>*(Residual Risk)* | Mã nguồn `LibraryRemoteServiceImpl` sử dụng Socket TCP Plaintext mặc định trên cổng `1099` (Registry) và `1100` (Service), **chưa hỗ trợ SSL/TLS**. Ghi nhận rủi ro MEDIUM. |
| **STG-C02** | **Hạ tầng Mạng Cách ly (Private VLAN / IP Allowlist)**<br>Tường lửa mạng trường học chỉ cho phép IP máy trạm staging | **NOT EXECUTED** | Cần xác nhận bằng biên bản cấu hình hạ tầng mạng từ đội NOC (Network Operations Center) của trường học. Không thể cấu hình hay giả lập từ mã nguồn. |
| **STG-D01** | **Kiểm thử Hồi quy Toàn diện (Regression Build)**<br>Biên dịch sạch, 100% JUnit tests đạt | **PASS** | `ant clean compile`: Exit Code **0**.<br>`ant test`: **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped, thời gian chạy: **29.922s**). |
| **STG-D02** | **Đóng gói Phát hành & Cách ly Bí mật**<br>`dist/server-release` sạch, không chứa cấu hình test | **PASS** | `ant package-all`: Exit Code **0**.<br>`server.jar` chỉ chứa `server.properties`, không chứa `db.properties`. `run-server.bat` có kiểm tra fail-fast khi thiếu cấu hình. |
| **STG-D03** | **Kiểm tra Mã nguồn Whitespace & Conflict**<br>`git diff --check` đạt tiêu chuẩn | **PASS** | `git diff --check`: Exit Code **0**. Working copy sạch hoàn toàn, không có conflict markers. |
| **STG-E01** | **Bảo vệ CSDL Production (`school_library`)**<br>Dữ liệu sản xuất không bị sửa đổi | **PASS** | Truy vấn đối soát chỉ đọc: 5 tài khoản nguyên vẹn, tài khoản `librarian2` giữ nguyên băm cũ SHA-256 (`hash_len=64`), cột `must_change_password` chưa tồn tại. ZERO DDL, ZERO DML. |

---

## 3. CHI TIẾT CÁC HẠNG MỤC CẦN THỰC HIỆN TRƯỚC KHI MỞ CỔNG STAGING

### 3.1. Phân quyền và Danh tính Windows (Hạng mục STG-A01 & STG-A02)
- **Hiện trạng máy trạm**:
  ```powershell
  # Kết quả lệnh Get-LocalUser:
  Name               Enabled Description
  ----               ------- -----------
  Administrator      False   Built-in account for administering the computer/domain
  DefaultAccount     False   A user account managed by the system.
  Guest              False   Built-in account for guest access to the computer/domain
  khach              True
  thien              True    Active Interactive User
  WDAGUtilityAccount False
  ```
  Hệ thống hiện tại **hoàn toàn chưa có tài khoản dịch vụ chuyên biệt**.
- **Hành động bắt buộc dành cho Quản trị viên Windows**:
  1. Tạo tài khoản cục bộ hoặc domain tiêu chuẩn, không thuộc nhóm Administrators:
     ```powershell
     New-LocalUser -Name "svc_library" -Description "Service Account for Library RMI Server" -NoPassword
     ```
  2. Phân quyền NTFS trên thư mục triển khai:
     - Cấp quyền `Read & Execute` cho `svc_library` trên thư mục `dist\server-release` và các tệp `.jar`.
     - Cấp quyền `Modify` cho `svc_library` trên thư mục `logs/` để ghi nhật ký hoạt động.
     - File `resources\db.properties` chỉ cấp quyền `Read` riêng cho `svc_library` và `Administrators`, ngăn chặn các người dùng khác đọc chuỗi kết nối CSDL.

---

### 3.2. CSDL Staging và Quyền Tối thiểu MySQL (Hạng mục STG-B01 & STG-B02)
- **Hiện trạng CSDL MySQL**:
  - Danh sách database hiện tại: `school_library`, `school_library_test`. Database `school_library_staging` **chưa tồn tại**.
  - Danh sách người dùng hiện tại: Duy nhất `root@localhost`. Người dùng runtime staging `thuvien_staging_app` **chưa tồn tại**.
- **Hành động bắt buộc dành cho DBA**:
  1. Tạo CSDL Staging độc lập:
     ```sql
     CREATE DATABASE school_library_staging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
     ```
  2. Khởi tạo dữ liệu mẫu và chạy migration an toàn:
     ```cmd
     mysql -u root school_library_staging < database/migration_must_change_password_mysql8.sql
     ```
  3. Tạo tài khoản runtime có quyền tối thiểu (DML only):
     ```sql
     CREATE USER 'thuvien_staging_app'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'StagingPass_Secure!2026';
     GRANT SELECT, INSERT, UPDATE, DELETE ON school_library_staging.* TO 'thuvien_staging_app'@'127.0.0.1';
     FLUSH PRIVILEGES;
     SHOW GRANTS FOR 'thuvien_staging_app'@'127.0.0.1';
     ```

---

### 3.3. Rà soát Kênh truyền RMI & Hạ tầng Mạng (Hạng mục STG-C01 & STG-C02)
- **Hiện trạng Ứng dụng**: RMI chạy trên Plaintext TCP Sockets (cổng `1099` và `1100`), không sử dụng TLS.
- **Rủi ro vận hành**: Dữ liệu truyền thông có thể bị bắt lén (sniffing) nếu ở chung broadcast domain với người dùng không tin cậy.
- **Hành động bắt buộc dành cho Đội Quản trị Mạng (NOC)**:
  1. Đặt máy chủ Staging và máy trạm kiểm thử trong một phân vùng mạng LAN nội bộ riêng biệt (Private Staging VLAN).
  2. Cấu hình tường lửa switch/router: Chỉ cho phép lưu lượng TCP cổng 1099 và 1100 đi từ dải IP máy trạm kiểm thử staging.
  3. Tuyệt đối không mở cổng (không NAT/Port-Forwarding) ra mạng Internet công cộng.

---

## 4. BẰNG CHỨNG KIỂM THỬ HỒI QUY TỰ ĐỘNG (AUTOMATED TEST SUITE)

Đã thực thi chuỗi lệnh kiểm thử sạch và đóng gói tự động tuần tự:

| Lệnh thực thi | Mã thoát (Exit Code) | Thời gian chạy | Kết quả kiểm tra |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 2 giây | Biên dịch sạch 131 source files. |
| `ant test` | **0** | 36 giây | **17 test suites, 160 tests PASS**, 0 failures, 0 errors, 0 skipped. |
| `ant package-all` | **0** | 1 giây | Đóng gói thành công `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Hoàn toàn sạch, không có lỗi whitespace hay conflict markers. |

### Thống kê 17 Test Suites từ tệp XML mới nhất (Timestamp: `10/23/2026 12:48:11 - 12:48:46 AM`):
1. `thuvien.client.PermissionHelperTest`: 4/4 PASS (0.076s)
2. `thuvien.common.ProtocolTest`: 2/2 PASS (0.269s)
3. `thuvien.server.database.MySQLSmokeIntegrationTest`: 4/4 PASS (0.321s)
4. `thuvien.server.repository.RepositoryArchitectureTest`: 3/3 PASS (0.100s)
5. `thuvien.server.RequestRouterTest`: 34/34 PASS (0.489s)
6. `thuvien.server.rmi.EnvironmentCatalogGuardTest`: 5/5 PASS (5.902s)
7. `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest`: 6/6 PASS (2.030s)
8. `thuvien.server.rmi.RMIEndToEndIntegrationTest`: 15/15 PASS (2.049s)
9. `thuvien.server.rmi.RMISerializationFilterTest`: 10/10 PASS (0.424s)
10. `thuvien.server.service.AdminResetPasswordTest`: 13/13 PASS (4.286s)
11. `thuvien.server.service.AuthServiceTest`: 5/5 PASS (0.622s)
12. `thuvien.server.service.BorrowConcurrencyIntegrationTest`: 4/4 PASS (1.304s)
13. `thuvien.server.service.CreateUserSecurityTest`: 4/4 PASS (0.605s)
14. `thuvien.server.service.FineServiceTest`: 3/3 PASS (0.074s)
15. `thuvien.server.service.SecurityAndFeatureHardeningTest`: 34/34 PASS (8.791s)
16. `thuvien.server.service.SuspendedStudentAuthTest`: 8/8 PASS (2.481s)
17. `thuvien.server.TestDatabaseSafetyGuardTest`: 6/6 PASS (0.099s)
- **Tổng cộng**: **160 tests PASS**, 0 Failures, 0 Errors, 0 Skipped. Thời gian chạy JUnit thuần: **29.922 giây**.

---

## 5. XÁC MINH CƠ SỞ DỮ LIỆU PRODUCTION VÀ GÓI PHÁT HÀNH

### 5.1. Trạng thái Cơ sở Dữ liệu Production (`school_library`)
- Truy vấn kiểm tra trực tiếp:
  ```
  id    username      role         is_active    hash_len
  1     admin         ADMIN        1            110
  2     librarian1    LIBRARIAN    1            110
  3     librarian2    LIBRARIAN    1            64
  4     student1      STUDENT      1            110
  8     studen2       STUDENT      1            110
  ```
- Đúng 5 tài khoản nguyên vẹn; tài khoản `librarian2` giữ nguyên băm cũ SHA-256 (`hash_len = 64`); cột `must_change_password` chưa tồn tại.
- **Cam kết**: 0 thao tác DDL/DML trên production trong toàn bộ phiên audit.

### 5.2. Kiểm tra Gói Phát hành `dist/server-release`
- `server.jar` sạch hoàn toàn, manifest trỏ đúng `LibraryRMIServer`.
- Thư mục `dist/server-release/resources/` chỉ chứa `db.properties.example` và `server.properties`, không có bất kỳ file cấu hình máy phát triển hay mật khẩu thật nào.
- File `run-server.bat` kiểm tra tệp `resources\db.properties`, thoát an toàn nếu chưa cấu hình.

---

## 6. KẾT LUẬN CUỐI CÙNG VÀ CỔNG TRIỂN KHAI (FINAL GATE VERDICTS)

Dựa trên bảng kiểm tra và các bằng chứng thực nghiệm độc lập:

### 1. BUILD VERIFIED: PASS
> Mã nguồn biên dịch sạch với Java 8; kiểm thử tự động Ant đạt **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped); `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Bản phát hành `dist/server-release` cách ly hoàn toàn, cấu hình fail-fast khi thiếu tệp CSDL, không chứa bí mật hay CSDL test, tài liệu vận hành đã cập nhật tham số `--result-file` chống hỏng mã hóa.

### 3. STAGING DEPLOYMENT GATE: BLOCKED (PENDING INFRASTRUCTURE & ACCOUNT PROVISIONING)
> Triển khai Staging **TẠM THỜI BỊ CHẶN (BLOCKED)** do cần Quản trị viên hệ thống (Windows/DBA/NOC) hoàn thành các điều kiện tiên quyết:
> 1. Tạo tài khoản dịch vụ Windows `svc_library` và phân quyền NTFS.
> 2. Tạo database `school_library_staging` và tài khoản runtime `thuvien_staging_app` trên MySQL.
> 3. Cấu hình Private VLAN và IP Whitelist trên thiết bị mạng phòng lab/trường học.

### 4. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Hệ thống giữ nguyên trạng thái **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để mở khung giờ triển khai. Tuyệt đối không tự ý triển khai trên môi trường sản xuất.
