# BÁO CÁO CỔNG KIỂM CHỨNG ĐỘC LẬP MÔI TRƯỜNG STAGING (PHASE 1.32)
## Independent Staging Gate Verification & Compliance Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Phase 1.32 — Independent Staging Gate Verification  
> **Thời điểm thực hiện**: 2026-10-23 00:41:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Bảo vệ tuyệt đối — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã kiểm chứng độc lập)  

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM CHỨNG ĐỘC LẬP

Giai đoạn Phase 1.32 thực hiện kiểm chứng độc lập toàn bộ các tuyên bố và bằng chứng tại [phase-1-31-staging-readiness-evidence.md](file:///D:/workspace/thuvien/docs/phase-1-31-staging-readiness-evidence.md). Báo cáo phân định rạch ròi giữa **bằng chứng thực nghiệm trực tiếp** và **nhận định/điều kiện giả định**, bảo đảm không chuyển sang trạng thái triển khai sản xuất khi chưa có phê duyệt chính thức từ Quản trị viên.

### Tuân thủ các nguyên tắc ràng buộc bất khả xâm phạm:
1. **Dữ liệu Production**: CSDL `school_library` được bảo vệ nghiêm ngặt, chỉ thực hiện các truy vấn đọc `SELECT/SHOW`. Không có bất kỳ thao tác `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP` hoặc migration nào.
2. **Tiện ích Khôi phục**: Không chạy `RecoverLibrarian2Utility --apply`, không đổi mật khẩu thật của bất kỳ tài khoản nào.
3. **Mạng và Hạ tầng**: Không can thiệp, không sửa đổi firewall máy thật, không tự động cấu hình VPN/VLAN.
4. **Mã nguồn và Git**: Không thực hiện `git reset`, `git checkout` hủy thay đổi, `git clean` hoặc `commit/push`. Giữ nguyên vẹn working directory.
5. **Bảo mật bí mật**: Không đưa mật khẩu thật, chuỗi hash đầy đủ, UUID session hay thông tin cá nhân vào báo cáo.

---

## 2. BẢNG TỔNG HỢP TRẠNG THÁI KIỂM CHỨNG TỪNG HẠNG MỤC

| Hạng mục kiểm chứng | Phương pháp kiểm chứng | Kết quả đánh giá | Phân định Bằng chứng vs Nhận định |
|:---|:---|:---:|:---|
| **1. Windows Backup ACL** | Thực thi lệnh Java NIO + `icacls` đo lường trước/sau | **PASS** | **Bằng chứng trực tiếp**: Toàn bộ cờ `(I)` kế thừa bị ngắt; các nhóm `Users` và `Authenticated Users` bị loại bỏ 100%; Fail-safe từ chối chạy trên filesystem không hỗ trợ ACL. |
| **2. Diễn tập Sao lưu & Phục hồi** | `mysqldump --result-file` vào `school_library_restore_test_132` | **PASS** | **Bằng chứng trực tiếp**: 11/11 bảng, 14 khóa ngoại, 1 routine, đối chiếu 100% số dòng; kiểm tra phát hiện lỗi SQL trả về Exit Code 1; CSDL tạm được dọn dẹp sạch. |
| **3. Quyền Tối thiểu MySQL** | Tạo user `thuvien_test_gate_132` trên `school_library_test` | **PASS** | **Bằng chứng trực tiếp**: `SELECT USER(), CURRENT_USER()`, DML an toàn thành công; `CREATE/DROP/ALTER` bị từ chối với `ERROR 1142`; dọn dẹp user thành công. |
| **4. Kênh truyền RMI & TLS** | Rà soát `LibraryRemoteServiceImpl` & Socket Factory | **PASS (Rà soát)** / **RISK** | **Bằng chứng trực tiếp**: RMI chạy trên TCP Plaintext Socket thuần, **chưa có TLS**. Việc cách ly bằng VLAN/VPN là **điều kiện vận hành hạ tầng** cần NOC trường học cấu hình. |
| **5. Build & Release Artifacts** | `ant clean compile`, `ant test`, `ant package-all`, `git diff --check` | **PASS** | **Bằng chứng trực tiếp**: 131 files biên dịch sạch; 17 suites, 160 tests PASS (0 fail/error); gói `dist/server-release` sạch 100%, không chứa CSDL hay bí mật. |
| **6. An toàn Database Production** | Truy vấn đối soát chỉ đọc `SELECT/SHOW` trên `school_library` | **PASS** | **Bằng chứng trực tiếp**: 5 tài khoản nguyên vẹn; tài khoản `librarian2` giữ nguyên băm cũ SHA-256 (`hash_len=64`); 0 DDL, 0 DML. |

---

## 3. CHI TIẾT BẰNG CHỨNG THỰC NGHIỆM ĐỘC LẬP

### 3.1. Kiểm chứng Windows ACL và Cơ chế Fail-Safe (Mục 1)
- **Mã nguồn thực tế**: Trong [RecoverLibrarian2Utility.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java), phương thức `secureDirectory(backupDir)` được thực thi ở dòng 318, **trước khi bất kỳ tệp nào được tạo hoặc ghi dữ liệu**.
- **Xử lý thư mục mới vs thư mục đã có**:
  - Thư mục mới: Tạo mới và gán ngay DACL chỉ cho phép Owner với cờ kế thừa `(OI)(CI)`.
  - Thư mục đã có: Ghi đè toàn bộ DACL hiện tại bằng mục duy nhất dành cho Owner; nếu tài khoản không có quyền ghi đè ACL, ném `IOException` dừng ngay.
- **Cơ chế Fail-Safe trên Filesystem không hỗ trợ ACL**:
  - Nếu hệ thống tệp không hỗ trợ POSIX và `Files.getFileAttributeView(dir, AclFileAttributeView.class) == null` (như FAT32/exFAT), tiện ích ném ngoại lệ dừng toàn bộ tiến trình trước khi tạo tệp.
- **Bằng chứng Thực nghiệm qua `icacls`**:
  ```powershell
  # Trước khi bảo vệ (Kế thừa mặc định của Windows):
  scratch\test_acl_verify_132 BUILTIN\Administrators:(I)(F)
                              NT AUTHORITY\Authenticated Users:(I)(M)
                              BUILTIN\Users:(I)(RX)
  
  # Sau khi bảo vệ:
  scratch\test_acl_verify_132            THIENUI\thien:(OI)(CI)(D,Rc,WDAC,WO,RD,WD,AD,DC,RA,WA)
  scratch\test_acl_verify_132\backup.txt THIENUI\thien:(D,Rc,RD,WD,RA,WA)
  ```
- **Xác định Danh tính Owner & Phân quyền Vận hành**:
  - Owner được lấy qua `Files.getOwner(dir)`, tương ứng với tài khoản Windows đang chạy lệnh (ví dụ `THIENUI\thien` hoặc tài khoản dịch vụ `svc_library`).
  - **Lưu ý Vận hành**: Nếu một quản trị viên khác hoặc tài khoản dịch vụ chạy ngầm cần đọc tệp sao lưu này, họ sẽ bị từ chối truy cập cho đến khi Owner cấp quyền. Đây là hành vi mong muốn theo nguyên tắc đặc quyền tối thiểu.

---

### 3.2. Kiểm chứng Toàn diện Diễn tập Sao lưu & Phục hồi (Mục 2)
Đã thực hiện diễn tập độc lập với CSDL nguồn `school_library_test` và CSDL đích tạm thời duy nhất `school_library_restore_test_132`:

1. **Lệnh Sao lưu với `--result-file`**:
   ```powershell
   mysqldump -u root --single-transaction --quick --routines --triggers --hex-blob --default-character-set=utf8mb4 "--result-file=scratch\drill_backup_132.sql" school_library_test
   ```
   - **Exit Code**: **0** | Kích thước file: **1.329.456 bytes** (> 0 bytes, Tier 1 PASS).
   - **Header**: `-- MySQL dump 10.13 Distrib 8.0.30` | **Footer**: `-- Dump completed on 2026-10-23 0:28:49` (Tier 2 PASS).
2. **Khôi phục vào CSDL tạm duy nhất (`school_library_restore_test_132`)**:
   - Lệnh: `mysql -u root --default-character-set=utf8mb4 -e "USE school_library_restore_test_132; source scratch/drill_backup_132.sql;"`
   - **Exit Code**: **0** (Tier 3 PASS).
3. **Đối chiếu Chi tiết Cấu trúc và Dữ liệu**:
   - **Số bảng**: Nguồn `11` bảng — Đích `11` bảng (Khớp 100%).
   - **Khóa ngoại (Foreign Keys)**: Nguồn `14` FK — Đích `14` FK (Khớp 100%).
   - **Thủ tục lưu trữ (Routines)**: Nguồn `1` — Đích `1` (Khớp 100%).
   - **Triggers**: Nguồn `0` — Đích `0` (Khớp 100%).
   - **Số dòng dữ liệu**: `users: 5`, `students: 3`, `books: 5`, `categories: 6`, `borrow_records: 1`, `fines: 0`, `reservations: 0` (Khớp tuyệt đối 100%).
   - **Bất biến nghiệp vụ**: 0 dòng tồn kho sách vi phạm (`available_copies < 0 OR > total_copies`), 0 dòng sinh viên mượn sách âm.
4. **Kiểm tra Khả năng Phát hiện Lỗi SQL thực sự**:
   - Thực thi tệp script có cú pháp sai: Lệnh `mysql` phát hiện lỗi và trả về **Exit Code: 1**.
5. **Dọn dẹp**: CSDL `school_library_restore_test_132` và file dump tạm đã được xóa hoàn toàn.

---

### 3.3. Xác minh Danh tính và Quyền Hạn MySQL (Mục 3)
Đã tạo tài khoản kiểm thử độc lập `thuvien_test_gate_132`@`127.0.0.1` với mật khẩu xác thực và chỉ cấp quyền `SELECT, INSERT, UPDATE, DELETE` trên `school_library_test.*`:

1. **Ghi nhận Danh tính Thực tế của MySQL**:
   ```
   client_user: thuvien_test_gate_132@localhost
   auth_user:   thuvien_test_gate_132@127.0.0.1
   active_db:   school_library_test
   ```
2. **Quyền Hạn ghi nhận qua `SHOW GRANTS`**:
   - `GRANT USAGE ON *.* TO 'thuvien_test_gate_132'@'127.0.0.1'`
   - `GRANT SELECT, INSERT, UPDATE, DELETE ON \`school_library_test\`.* TO 'thuvien_test_gate_132'@'127.0.0.1'`
3. **Thử nghiệm DML an toàn**:
   - `SELECT COUNT(*)` ban đầu trên `categories`: 6 dòng.
   - `INSERT INTO categories` dòng tạm: Tăng lên 7 dòng.
   - `DELETE FROM categories` dòng tạm: Trở về đúng 6 dòng ban đầu.
4. **Kiểm chứng Từ chối Lệnh DDL**:
   - `CREATE TABLE test_unauth (id INT);` -> **TỪ CHỐI** (`ERROR 1142 (42000)`, Exit Code: 1).
   - `DROP TABLE users;` -> **TỪ CHỐI** (`ERROR 1142 (42000)`, Exit Code: 1).
   - `ALTER TABLE users ADD COLUMN unauth_col INT;` -> **TỪ CHỐI** (`ERROR 1142 (42000)`, Exit Code: 1).
5. **Kiểm tra Toàn vẹn Dữ liệu**: Bảng `users` giữ nguyên 5 dòng, không bị thêm bất kỳ cột lạ nào.
6. **Dọn dẹp và Xác nhận Dọn dẹp**: Tài khoản `thuvien_test_gate_132` đã bị DROP; kết nối lại bị từ chối với `ERROR 1045 (28000)`, Exit Code: 1.
- **Lưu ý Giới hạn**: Kết quả trên chứng minh cơ chế phân quyền tối thiểu của MySQL 8 hoạt động chính xác trên môi trường test, **không suy rộng rằng tài khoản production đã được cấu hình sẵn**. DBA phải thực hiện lệnh tạo tài khoản runtime trên production khi triển khai.

---

### 3.4. Rà soát Kênh truyền RMI và Điều kiện Mạng Staging (Mục 4)
- **Kiểm tra Mã nguồn**:
  - `LibraryRemoteServiceImpl.java` kế thừa `UnicastRemoteObject` sử dụng socket TCP mặc định (`java.net.Socket`, `java.net.ServerSocket`).
  - **Không có cấu hình SSL/TLS** (`SslRMIClientSocketFactory` / `SslRMIServerSocketFactory` không được sử dụng).
  - Cổng RMI Registry: `1099/TCP`; Cổng RMI Service: `1100/TCP`.
- **Phân định Bằng chứng vs Nhận định**:
  - **Bằng chứng**: Kênh truyền RMI là **Plaintext TCP hoàn toàn**.
  - **Nhận định**: Mạng VLAN hoặc VPN chỉ bảo vệ ở lớp mạng (Network Layer Encapsulation), **không mã hóa dòng nhị phân Java Serialization bên trong ứng dụng**.
  - **Rủi ro còn tồn tại**: **MEDIUM (Residual Risk)**. Kẻ tấn công trong cùng broadcast domain có thể bắt trích xuất session UUID hoặc payload.
- **Bằng chứng Hạ tầng Còn thiếu cho Staging**:
  - Việc cấu hình Private VLAN, IP Whitelisting trên Router/Switch và tường lửa phòng máy chủ là nhiệm vụ thuộc thẩm quyền của đội ngũ Quản trị mạng trường học (NOC/Infra), không thể chứng minh hoặc cấu hình từ phạm vi mã nguồn của dự án này.

---

### 3.5. Kiểm tra Build, Kiểm thử và Gói Phát hành (Mục 5)

| Lệnh thực thi | Exit Code | Thời gian chạy | Kết quả chi tiết |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 2 giây | Biên dịch sạch 131 source files. |
| `ant test` | **0** | 37 giây | **17 test suites, 160 tests PASS**, 0 failures, 0 errors, 0 skipped. |
| `ant package-all` | **0** | 1 giây | Đóng gói thành công `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Hoàn toàn sạch, không có lỗi whitespace hay conflict markers. |

- **Số liệu Test XML mới nhất** (Timestamp: `10/23/2026 12:39:18 - 12:39:54 AM`):
  - 17 test suites, 160 tests PASS 100%, thời gian chạy JUnit thuần: **30.906 giây**.
- **Quét Gói Phát hành `dist/server-release`**:
  - `server.jar` chỉ chứa `server.properties`, không có `db.properties` hay cấu hình máy phát triển.
  - `resources/db.properties.example` chỉ chứa thông tin mẫu placeholder.
  - `run-server.bat` kiểm tra tệp `resources\db.properties`, báo lỗi và dừng an toàn với exit code 1 nếu chưa cấu hình. Không tự ý sao chép file mẫu thành file chạy thật.

---

### 3.6. Kiểm tra Chỉ đọc Cơ sở Dữ liệu Production (`school_library`) (Mục 6)
Đã thực hiện truy vấn đối soát chỉ đọc:
```sql
SELECT id, username, role, is_active, LENGTH(password_hash) as hash_len FROM users;
SHOW COLUMNS FROM users LIKE 'must_change_password';
```
**Kết quả thực tế**:
- 5 tài khoản nguyên vẹn (`admin`, `librarian1`, `librarian2`, `student1`, `studen2`).
- Tài khoản `librarian2`: Hash length = 64 (giữ nguyên SHA-256 legacy, chưa bị reset hay thay đổi).
- Cột `must_change_password`: **Chưa tồn tại** (chưa chạy DDL migration).
- **Xác nhận**: Dữ liệu Production nguyên vẹn 100%, không bị can thiệp.

---

## 4. DANH SÁCH FILE ĐÃ THAY ĐỔI TRONG PHASE 1.32

Trong giai đoạn Phase 1.32, chỉ có các tệp tài liệu và báo cáo kiểm toán được cập nhật để ghi nhận bằng chứng thực nghiệm:
1. [docs/BACKUP-RESTORE.md](file:///D:/workspace/thuvien/docs/BACKUP-RESTORE.md): Cập nhật tham số `--result-file` trong lệnh `mysqldump` để loại bỏ lỗi chuyển mã UTF-16LE của PowerShell.
2. [docs/DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md): Cập nhật tham số `--result-file` và hướng dẫn phân quyền tối thiểu MySQL 8.
3. [docs/phase-1-32-independent-staging-gate-report.md](file:///D:/workspace/thuvien/docs/phase-1-32-independent-staging-gate-report.md): Báo cáo cổng kiểm chứng độc lập hoàn chỉnh.

*(Không có bất kỳ tệp mã nguồn sản xuất hoặc build script nào bị sửa đổi trái phép).*

---

## 5. KẾT LUẬN CỔNG KIỂM CHỨNG VÀ TRẠNG THÁI HỆ THỐNG

### 1. BUILD VERIFIED: PASS
> Toàn bộ mã nguồn biên dịch sạch với Java 8; kiểm thử tự động Ant đạt **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped); `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Gói phát hành `dist/server-release` sạch 100%, bảo đảm cách ly bí mật, có cơ chế fail-fast khi thiếu cấu hình, và tài liệu runbook đã tích hợp đầy đủ quy trình an toàn `--result-file`.

### 3. STAGING READINESS: CONDITIONALLY READY (CHỜ HẠ TẦNG MẠNG TỪ ĐỘI NOC)
> Mã nguồn và gói phát hành đã sẵn sàng cho Staging. Tuy nhiên, do kênh truyền RMI chưa hỗ trợ TLS, **điều kiện tiên quyết bắt buộc trước khi đưa vào chạy thử Staging** là đội ngũ Quản trị mạng trường học (NOC) phải thiết lập Private VLAN và IP Whitelist trên firewall phòng máy.

### 4. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Hệ thống giữ nguyên trạng thái **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để mở khung giờ triển khai. Tuyệt đối không tự ý triển khai trên môi trường sản xuất.
