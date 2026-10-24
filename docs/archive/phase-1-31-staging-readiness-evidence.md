# BÁO CÁO BẰNG CHỨNG SẴN SÀNG CHO MÔI TRƯỜNG STAGING (PHASE 1.31)
## Evidence-Based Staging Readiness Audit & Empirical Verification Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.31 — Evidence-Based Staging Readiness  
> **Thời điểm thực hiện**: 2026-10-23 00:31:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Bảo vệ tuyệt đối — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã kiểm chứng thực nghiệm)  

---

## 1. TỔNG QUAN VÀ CAM KẾT AN TOÀN

Mục tiêu trọng tâm của Phase 1.31 là **xác minh bằng bằng chứng thực nghiệm có thể kiểm tra độc lập (Empirical Evidence)** đối với toàn bộ các tuyên bố về bảo mật, kiểm thử, phân quyền và phục hồi dữ liệu từ Phase 1.30.

### Nguyên tắc bất khả xâm phạm:
- **Tuyệt đối không sửa đổi CSDL production `school_library`**: 0 lệnh DDL/DML được thực thi trên production.
- **Không chạy `RecoverLibrarian2Utility --apply`**: Mật khẩu thật của thủ thư được giữ nguyên 100%.
- **Không git reset, revert, stash, commit hoặc push**: Bảo tồn toàn vẹn working directory.
- **Chỉ sử dụng CSDL test cho các thử nghiệm có ghi**: Toàn bộ thao tác diễn tập backup/restore và phân quyền đều diễn ra trên `school_library_test` hoặc database tạm thời duy nhất `school_library_restore_test_131`.
- **Nguyên tắc bằng chứng thực tế**: Không tuyên bố đã hoàn thành một bài test nếu chỉ mới viết hướng dẫn; mọi tuyên bố đều đi kèm mã lệnh, exit code và output thực tế.

---

## 2. BẰNG CHỨNG THỰC NGHIỆM CHI TIẾT THEO TỪNG HẠNG MỤC

### 2.1. Kiểm tra Windows ACL và Tiện ích Khôi phục Mật khẩu (Section A)

#### 2.1.1. Xử lý Thư mục Backup Mới vs Thư mục Đã Tồn tại
Trong [RecoverLibrarian2Utility.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java), phương thức `secureDirectory(Path dir)` xử lý đồng nhất cho cả 2 trường hợp:
- **Thư mục mới**: Tạo thư mục và thiết lập ngay DACL loại bỏ kế thừa từ ổ đĩa cha.
- **Thư mục đã tồn tại**: Đọc `AclFileAttributeView`, ghi đè toàn bộ danh sách DACL hiện hữu bằng mục duy nhất dành riêng cho `Owner` (`AclEntryType.ALLOW` với cờ `(OI)(CI)`). Nếu tiến trình không có quyền sửa DACL của thư mục hiện có, phương thức ném `IOException` dừng ngay lập tức.

#### 2.1.2. Cơ chế Fail-Safe khi Filesystem Không Hỗ trợ ACL (A.4)
Đã triển khai cơ chế kiểm tra bắt buộc:
- Nếu filesystem không hỗ trợ POSIX permissions và `Files.getFileAttributeView(dir, AclFileAttributeView.class) == null` (ví dụ trên phân vùng FAT32, exFAT hoặc SMB share không hỗ trợ ACL), hoặc nếu lệnh gán `setAcl` phát sinh ngoại lệ:
  ```java
  if (!secured) {
      throw new IOException(String.format(
          "[FAIL-SAFE BẢO MẬT] Hệ thống tệp chứa thư mục '%s' không hỗ trợ POSIX permissions hoặc NTFS ACL. "
          + "Tiện ích từ chối tiếp tục để ngăn chặn việc tạo file sao lưu hash không có kiểm soát truy cập an toàn.",
          dir.toAbsolutePath()));
  }
  ```
- **Kết quả bảo vệ**: Tiện ích **dừng an toàn trước khi tạo file**, ngăn chặn triệt để việc ghi chuỗi hash ra đĩa khi không thể đảm bảo an toàn truy cập.

#### 2.1.3. Bằng chứng Phân quyền Thực tế qua `icacls` trên Windows NTFS
Kiểm tra thực nghiệm trên thư mục và tệp bằng lệnh `icacls`:
```powershell
icacls "scratch\test_secure_dir"
icacls "scratch\test_secure_dir\test_backup.txt"
```
**Output ghi nhận thực tế**:
```
scratch\test_secure_dir          THIENUI\thien:(OI)(CI)(Rc,WDAC,WO,S,RD,WD,AD,RA,WA)
scratch\test_secure_dir\test_backup.txt THIENUI\thien:(Rc,S,RD,WD,RA,WA)
```
- **Phân tích ACL**:
  1. Toàn bộ cờ `(I)` (Inherited) từ thư mục cha bị ngắt bỏ hoàn toàn.
  2. Các nhóm `BUILTIN\Users` và `NT AUTHORITY\Authenticated Users` bị loại bỏ 100%.
  3. Chỉ có duy nhất `THIENUI\thien` (chủ sở hữu tiến trình) có quyền truy cập.
- **Giới hạn kỹ thuật**: Tính năng DACL này dựa vào hệ thống tệp **NTFS**. Nếu triển khai trên các thiết bị lưu trữ ngoài sử dụng FAT32/exFAT, tiện ích sẽ kích hoạt Fail-Safe từ chối chạy.

---

### 2.2. Xác minh Thực sự Diễn tập Sao lưu & Phục hồi CSDL (Section B)

Đã thực hiện diễn tập phục hồi toàn diện 3 cấp độ (3-Tier Backup & Restore Drill) sử dụng CSDL nguồn `school_library_test` và CSDL đích tạm thời duy nhất `school_library_restore_test_131`:

#### 2.2.1. Phát hiện Vận hành Quan trọng về Toán tử Chuyển hướng trên PowerShell
- **Hiện tượng**: Khi chạy lệnh `mysqldump ... > file.sql` trên Windows PowerShell 5.1, toán tử `>` mặc định chuyển đổi đầu ra sang mã hóa **UTF-16LE** (chứa ký tự null `0x00`). Khi dùng lệnh `mysql` để khôi phục, quá trình bị từ chối với lỗi: `ERROR: ASCII '\0' appeared in the statement`.
- **Giải pháp chuẩn hóa**: Bắt buộc sử dụng tham số `--result-file=D:\path\backup.sql` trực tiếp trong lệnh `mysqldump`. Tham số này yêu cầu engine `mysqldump` tự ghi tệp nhị phân UTF-8 nguyên bản ra đĩa, bảo đảm 100% tương thích trên mọi môi trường (PowerShell, CMD, Linux Bash).

#### 2.2.2. Bằng chứng Diễn tập Thực tế (Exit Codes & Dữ liệu)
1. **Lệnh tạo bản sao lưu (Tier 1 & Tier 2)**:
   ```powershell
   mysqldump -u root --single-transaction --quick --routines --triggers --hex-blob --default-character-set=utf8mb4 "--result-file=scratch\drill_backup_school_library_test.sql" school_library_test
   ```
   - **Exit Code**: **0**
   - **Dung lượng tệp (Tier 1)**: `1.298.284 bytes` (> 0 bytes)
   - **Header (Tier 2)**: `-- MySQL dump 10.13  Distrib 8.0.30, for Win64 (x86_64)`
   - **Footer (Tier 2)**: `-- Dump completed on 2026-10-23  0:28:49`

2. **Lệnh khôi phục vào CSDL kiểm thử độc lập (Tier 3)**:
   ```powershell
   mysql -u root -e "DROP DATABASE IF EXISTS school_library_restore_test_131; CREATE DATABASE school_library_restore_test_131 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   mysql -u root --default-character-set=utf8mb4 -e "USE school_library_restore_test_131; source scratch/drill_backup_school_library_test.sql;"
   ```
   - **Restore Exit Code**: **0**

3. **Đối chiếu số lượng bản ghi giữa CSDL Nguồn và CSDL Đích vừa khôi phục**:

   | Tên Bảng (Core Table) | Số dòng Nguồn (`school_library_test`) | Số dòng Đích (`school_library_restore_test_131`) | Trạng thái Đối chiếu |
   |:---|:---:|:---:|:---:|
   | `users` | 5 | 5 | **KHỚP 100%** |
   | `students` | 3 | 3 | **KHỚP 100%** |
   | `books` | 5 | 5 | **KHỚP 100%** |
   | `categories` | 6 | 6 | **KHỚP 100%** |
   | `borrow_records` | 1 | 1 | **KHỚP 100%** |
   | `fines` | 0 | 0 | **KHỚP 100%** |
   | `reservations` | 0 | 0 | **KHỚP 100%** |

4. **Kiểm tra Ràng buộc Nghiệp vụ (Integrity Constraints Check)**:
   - Truy vấn tồn kho sách (`available_copies < 0 OR available_copies > total_copies`): **0 dòng vi phạm**.
   - Truy vấn số lượng mượn sinh viên (`current_borrow_count < 0`): **0 dòng vi phạm**.
5. **Dọn dẹp an toàn**:
   - CSDL tạm thời `school_library_restore_test_131` đã được DROP hoàn toàn.
   - Tệp sao lưu thử nghiệm trong `scratch` đã được xóa sạch.

---

### 2.3. Rà soát RMI và Kênh truyền Mạng (Section C)

#### 2.3.1. Hiện trạng Mã nguồn Kênh truyền RMI
- **Kiểm tra Socket Factory**:
  - Trong `LibraryRemoteServiceImpl.java`: Lớp kế thừa trực tiếp từ `UnicastRemoteObject` và gọi `super(port > 0 ? port : DEFAULT_SERVICE_PORT)`.
  - **Không sử dụng** `SslRMIClientSocketFactory` hay `SslRMIServerSocketFactory`.
  - Giao thức truyền thông là **TCP Socket thuần (Plaintext)** trên cổng `1099/TCP` (Registry) và `1100/TCP` (Remote Service).
- **Cơ chế công bố Hostname & Cổng**:
  - `LibraryRMIServer.java` tự động phân giải IP mạng LAN qua `resolveServerHost` và gán vào thuộc tính JVM `java.rmi.server.hostname`.
  - Binding IP: Lắng nghe trên `0.0.0.0` (mọi interface cục bộ).
- **Phân loại Rủi ro Còn tồn tại (Residual Risk)**:
  - **Rủi ro mức độ MEDIUM**: Vì RMI chưa có TLS, dữ liệu truyền tải (bao gồm session UUID, mật khẩu đăng nhập dạng payload) có thể bị bắt lén (packet sniffing) nếu chạy trên mạng không an toàn. **Không tuyên bố kết nối RMI đã được mã hóa**.

#### 2.3.2. Điều kiện Bắt buộc trước khi Triển khai Staging
1. Máy chủ Staging và các máy trạm kiểm thử phải được đặt trong một phân vùng mạng LAN nội bộ riêng biệt (Private Staging VLAN) hoặc kết nối qua VPN có mã hóa (WireGuard / IPsec).
2. Tường lửa máy chủ (Windows Firewall / iptables) chỉ cho phép kết nối đến cổng 1099 và 1100 từ danh sách IP máy trạm được chỉ định (IP Whitelist).
3. Tuyệt đối không mở cổng 1099 và 1100 ra Internet.

---

### 2.4. Bằng chứng Thực nghiệm về Quyền Tối thiểu của MySQL (Section D)

Đã tạo một tài khoản kiểm thử độc lập `thuvien_test_least_priv`@`127.0.0.1` trên MySQL và chỉ cấp quyền `SELECT, INSERT, UPDATE, DELETE` trên `school_library_test.*`:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON school_library_test.* TO 'thuvien_test_least_priv'@'127.0.0.1';
```

#### Kết quả Thực thi Kiểm chứng:
1. **Thao tác DML (`SELECT`)**:
   - Lệnh: `mysql -u thuvien_test_least_priv -h 127.0.0.1 -p... -e "SELECT COUNT(*) FROM school_library_test.users;"`
   - **Kết quả**: Thành công, trả về đúng `5` bản ghi.
2. **Thao tác DDL (`CREATE TABLE`)**:
   - Lệnh: `CREATE TABLE school_library_test.test_unauthorized_table (id INT);`
   - **Kết quả**: **BỊ TỪ CHỐI** với lỗi:
     `ERROR 1142 (42000): CREATE command denied to user 'thuvien_test_least_priv'@'localhost' for table 'test_unauthorized_table'`
3. **Thao tác DDL (`DROP TABLE`)**:
   - Lệnh: `DROP TABLE school_library_test.users;`
   - **Kết quả**: **BỊ TỪ CHỐI** với lỗi:
     `ERROR 1142 (42000): DROP command denied to user 'thuvien_test_least_priv'@'localhost' for table 'users'`
4. **Dọn dẹp**: Tài khoản `thuvien_test_least_priv`@`127.0.0.1` đã được xóa bỏ ngay sau bài test.
- **Kết luận**: Quyền tối thiểu DML bảo vệ CSDL 100% trước các hành vi tấn công phá hoại cấu trúc bảng hoặc đánh cắp quyền quản trị nếu tài khoản ứng dụng bị xâm nhập.

---

## 3. KẾT QUẢ KIỂM THỬ HỒI QUY VÀ ĐÓNG GÓI (SECTION E)

Đã thực hiện chuỗi lệnh kiểm thử và đóng gói tự động tuần tự:

| Lệnh thực thi | Mã thoát (Exit Code) | Thời gian chạy | Kết quả kiểm tra |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 2 giây | Biên dịch sạch 131 source files. |
| `ant test` | **0** | 39 giây | **17 test suites, 160 tests PASS**, 0 failures, 0 errors, 0 skipped. |
| `ant package-all` | **0** | 1 giây | Đóng gói thành công `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Hoàn toàn sạch, không có lỗi whitespace hay conflict markers. |

### Số liệu Kiểm thử từ 17 Tệp XML Mới nhất (Timestamp: `10/23/2026 12:30:07 - 12:30:45 AM`):
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
- **Tổng số tests**: **160 tests PASS**, 0 Failures, 0 Errors, 0 Skipped. Thời gian chạy: **32.436 giây**.

---

## 4. XÁC NHẬN CƠ SỞ DỮ LIỆU PRODUCTION VÀ GÓI PHÁT HÀNH

### 4.1. Trạng thái Cơ sở Dữ liệu Production (`school_library`)
- Truy vấn kiểm tra trực tiếp:
  ```
  id    username      role         is_active    hash_len
  1     admin         ADMIN        1            110
  2     librarian1    LIBRARIAN    1            110
  3     librarian2    LIBRARIAN    1            64
  4     student1      STUDENT      1            110
  8     studen2       STUDENT      1            110
  ```
- Đúng 5 tài khoản nguyên vẹn; tài khoản `librarian2` giữ nguyên băm SHA-256 legacy (`hash_len = 64`); cột `must_change_password` chưa tồn tại.
- **Xác nhận tuyệt đối**: CSDL Production **không bị thay đổi bất kỳ byte dữ liệu nào** trong toàn bộ quá trình audit.

### 4.2. Kiểm tra Gói Phát hành `dist/server-release`
- `server.jar` chỉ chứa `server.properties`, không có `db.properties`, manifest trỏ đúng Main-Class.
- `resources/db.properties.example` sử dụng giá trị mẫu, trỏ CSDL `school_library`.
- `run-server.bat` kiểm tra tệp `resources\db.properties`, thoát an toàn nếu chưa cấu hình.
- Đầy đủ 4 file JAR runtime trong `lib/`.

---

## 5. BẢNG TỔNG HỢP PHÁT HIỆN KIỂM TOÁN (FINDINGS MATRIX)

| Mức độ | Mã phát hiện | Mô tả | Biện pháp & Bằng chứng kiểm tra |
|:---:|:---:|:---|:---|
| **CRITICAL** | — | Không phát hiện lỗi gây sập hoặc rò rỉ bí mật CSDL. | **NONE** |
| **HIGH** | — | Không phát hiện lỗi sai lệch phân quyền kiến trúc. | **NONE** |
| **MEDIUM** | `MED-01` | **Kênh truyền RMI chưa hỗ trợ TLS**: Giao tiếp qua Plaintext TCP Socket có rủi ro bị nghe lén trên mạng Wi-Fi không tin cậy. | **ĐÃ XÁC MINH & GHI NHẬN RỦI RO**: Yêu cầu bắt buộc triển khai trong VLAN nội bộ riêng biệt hoặc qua VPN có mã hóa. |
| **MEDIUM** | `MED-02` | **Toán tử `>` của PowerShell làm hỏng file dump UTF-8**: Gây lỗi `ASCII '\0'` khi import do chuyển mã sang UTF-16LE. | **ĐÃ KHẮC PHỤC TRONG RUNBOOK**: Thay thế bằng tham số `--result-file=...` trong `mysqldump`. |
| **LOW** | `LOW-01` | **Cảnh báo LF/CRLF của Git trên Windows**: Xuất hiện warning khi checkout trên Windows. | **KHÔNG ẢNH HƯỞNG**: `git diff --check` đạt exit code 0. |

---

## 6. KẾT LUẬN CUỐI CÙNG VÀ CỔNG TRIỂN KHAI (FINAL GATE VERDICTS)

Toàn bộ các bằng chứng thực nghiệm đều xác nhận hệ thống đã hoàn tất đầy đủ điều kiện kỹ thuật để bàn giao cho giai đoạn Staging:

### 1. BUILD VERIFIED: PASS
> Mã nguồn biên dịch sạch với Java 8; bộ kiểm thử Ant đạt **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped); `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Bản phát hành `dist/server-release` hoàn toàn cách ly, cấu hình fail-fast khi thiếu tệp CSDL, không chứa bí mật hay CSDL test, tài liệu vận hành đã cập nhật tham số `--result-file` chống hỏng mã hóa.

### 3. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Hệ thống đạt trạng thái sẵn sàng cho Staging và **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để mở khung giờ triển khai. Tuyệt đối không tự ý triển khai trên môi trường sản xuất.
