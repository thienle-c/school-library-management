# BÁO CÁO TỔNG KẾT TOÀN DIỆN DỰ ÁN, CỦNG CỐ BẢO MẬT VÀ SẴN SÀNG CHO STAGING
## Master Finalization, Security Hardening & Staging Readiness Audit Report

> **Hệ thống**: Remote School Library Management System  
> **Phiên bản Audit**: Master Finalization Audit & Staging Readiness Gate  
> **Thời điểm thực hiện**: 2026-10-23 00:57:00 UTC+7  
> **Môi trường thực thi**: Windows 11 / Amazon Corretto OpenJDK 1.8.0_504 / NetBeans Ant / MySQL 8.0.30  
> **Cơ sở dữ liệu Production**: `school_library` (Bảo vệ tuyệt đối — ZERO DDL / ZERO DML)  
> **Cơ sở dữ liệu Test**: `school_library_test` (Đã kiểm chứng độc lập)  

---

## I. TỔNG QUAN HỆ THỐNG VÀ KIẾN TRÚC PHÂN TẦNG

Dự án **Remote School Library Management System** được thiết kế theo mô hình kiến trúc 3 tầng (3-Tier Distributed Architecture) nghiêm ngặt dành cho môn Lập trình mạng (Network Programming):

```
Java Swing Client (Desktop)
        |
        | TCP RMI (Port 1099/1100) — Envelopes Request / Response
        v
Java Library Server
        |
        +-- RequestRouter (Authentication Guard, RBAC, Mandatory Password Change Guard)
        +-- Service Layer (Multi-Statement Atomic Transactions, Concurrency Controls)
        +-- Repository Layer (PreparedStatement Parameter Binding, Result Mappings)
        +-- DatabaseManager (HikariCP Connection Pool)
        v
MySQL 8.0 (InnoDB Engine)
```

### Ranh giới Kiến trúc Bất biến:
- **Client tuyệt đối không kết nối trực tiếp vào MySQL**: Gói `client.jar` và thư mục `dist/client-dist/lib` hoàn toàn không chứa bất kỳ JDBC driver hay thư viện kết nối CSDL nào.
- **Server kiểm soát toàn bộ truy cập CSDL**: Kết nối qua HikariCP Connection Pool, cô lập credentials trong `resources/db.properties`.
- **Giao tiếp qua mạng**: Đóng gói qua các envelopes giao thức hướng đối tượng (`Request`, `Response`, `Action`, `StatusCode`).

---

## II. XÁC MINH BASELINE VÀ KIỂM SOÁT THAY ĐỔI

### 1. Trạng thái Repository và Git
- **Nhánh Git**: `main` (Up to date with `origin/main`).
- **Bảo toàn công việc**: Toàn bộ 32 tệp sửa đổi và 23 tệp mới chưa commit được bảo tồn nguyên vẹn 100%. Không thực hiện bất kỳ lệnh `git reset`, `git clean`, `git checkout` đè, hay `git stash` nào.
- **Kiểm tra Whitespace & Conflict Markers**:
  - Lệnh: `git diff --check`
  - **Exit Code**: **0** (Hoàn toàn sạch, không có lỗi định dạng hay conflict markers).

### 2. Kết quả Chuỗi Kiểm tra Build và Đóng gói Tuần tự

| Lệnh thực thi | Exit Code | Thời gian chạy | Kết quả chi tiết |
|:---|:---:|:---:|:---|
| `ant clean compile` | **0** | 3 giây | Biên dịch sạch toàn bộ 131 source files (common, server, client). |
| `ant test` | **0** | 38 giây | **17 test suites, 160/160 tests PASS**, 0 failures, 0 errors, 0 skipped. |
| `ant package-all` | **0** | 1 giây | Đóng gói thành công `server-dist`, `server-release`, `client-dist`. |
| `git diff --check` | **0** | < 1 giây | Đạt chuẩn, exit code 0. |

- **Thời gian chạy JUnit thuần**: **30.981 giây** từ 17 tệp XML mới nhất (Timestamp: `10/23/2026 12:55:37 - 12:56:13 AM`).
- **Danh sách 17 Test Suites đạt chuẩn**:
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

## III. KẾT QUẢ RÀ SOÁT CHẤT LƯỢNG CODE VÀ BẢO MẬT HỆ THỐNG

### 1. Xác thực và Bảo mật Mật khẩu (Authentication & Password Security)
- **Chuẩn băm mật khẩu**: `PBKDF2WithHmacSHA256` kết hợp salt ngẫu nhiên 16 bytes và 10.000 vòng lặp (độ dài chuỗi băm 110 ký tự).
- **Tương thích ngược và Tự động nâng cấp (Legacy SHA-256 Hash Migration)**:
  - Tài khoản cũ lưu băm SHA-256 64 ký tự (như `librarian2`) được nhận diện tự động qua `PasswordHasher.isLegacyHash(storedHash)`.
  - Khi đăng nhập thành công với mật khẩu đúng, `AuthServiceImpl` tự động nâng cấp chuỗi băm lên PBKDF2 trực tiếp trong CSDL mà không làm gián đoạn người dùng.
- **Khóa tài khoản chống brute-force (Account Lockout)**:
  - Sai mật khẩu 5 lần liên tiếp sẽ tạm khóa tài khoản 15 phút.
  - Quản trị viên (ADMIN) có quyền mở khóa tài khoản tức thời hoặc reset mật khẩu tạm thời.
- **Cờ bắt buộc đổi mật khẩu (`must_change_password`)**:
  - Khi tài khoản được cấp mật khẩu tạm thời, cờ `must_change_password` được kích hoạt.
  - `RequestRouter` chặn tất cả các hành động nghiệp vụ (trả về `403 FORBIDDEN`), chỉ cho phép duy nhất hành động `CHANGE_PASSWORD` và `LOGOUT`.
  - Trên giao diện Swing, `MainDashboardForm` tự động khóa thanh điều hướng và bật hộp thoại yêu cầu đổi mật khẩu.

### 2. Phân quyền và Kiểm soát Vai trò (Authorization & RBAC)
- **Phân định 3 vai trò**: `ADMIN`, `LIBRARIAN`, `STUDENT`.
- **Kiểm soát chặt chẽ ở tầng Server**: `RequestRouter` thực thi phương thức `requireRole(session, UserRole...)` trước khi phân phối request. Sinh viên không thể gọi các API quản trị sách, mượn trả, hoặc cấp mã kích hoạt.
- **Đặc quyền của Admin**: Duy nhất vai trò `ADMIN` mới có quyền xóa sinh viên, reset mật khẩu thủ thư, xem log kiểm toán (audit logs) và cấp mã kích hoạt.
- **Trợ lý phân quyền Client**: `PermissionHelper` tập trung hóa logic ẩn/hiện menu và nút bấm trên giao diện Swing, bảo đảm nhất quán trải nghiệm người dùng.

### 3. Giao dịch Đồng thời và Tính Toàn vẹn Dữ liệu (Concurrency & Invariants)
- **Giao dịch Mượn sách Nguyên tử (Atomic Borrow Transaction)**:
  - Giảm tồn kho bằng câu lệnh điều kiện nguyên tử:
    `UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0;`
  - Đảm bảo khi nhiều sinh viên mượn cuốn sách cuối cùng cùng lúc, chỉ có đúng 1 giao dịch thành công, tồn kho không bao giờ bị âm.
- **Giao dịch Trả sách Nguyên tử (Atomic Return Transaction)**:
  - Toàn bộ thao tác tính phạt quá hạn, tạo bản ghi phạt, cập nhật trạng thái phiếu mượn sang `RETURNED`, hoàn trả tồn kho sách và trừ số sách đang mượn của sinh viên đều chạy trên **CÙNG MỘT CONNECTION** (`conn.setAutoCommit(false)`), commit hoặc rollback đồng thời.
- **Không có N+1 Query & Chống SQL Injection**:
  - 100% các câu lệnh truy vấn sử dụng `PreparedStatement` với parameter binding. Không có thao tác nối chuỗi raw SQL.
  - Sử dụng try-with-resources giải phóng triệt để `ResultSet`, `PreparedStatement`, và `Connection`.

### 4. Vòng đời Dịch vụ RMI và Kênh truyền Mạng (RMI Transport)
- **Cổng cố định**: RMI Registry chạy cổng `1099/TCP`, Remote Service chạy cổng `1100/TCP` (không sử dụng cổng ngẫu nhiên).
- **Khóa bảo vệ Catalog Môi trường (Environment Catalog Guard)**:
  - Kiểm tra biến môi trường `DB_EXPECTED_CATALOG` / `db.expected.catalog`. Nếu catalog CSDL kết nối thực tế không khớp với mục tiêu dự kiến, máy chủ dừng khởi động ngay lập tức để bảo vệ dữ liệu.
  - Kiểm tra schema bắt buộc cột `must_change_password`; nếu thiếu cột, máy chủ từ chối mở cổng RMI.
- **Dọn dẹp Tài nguyên RMI sạch sẽ**:
  - Phương thức `stop()` và `cleanupRmiResources()` gọi `UnicastRemoteObject.unexportObject(registry, true)` và `UnicastRemoteObject.unexportObject(remoteService, true)`, triệt tiêu hoàn toàn hiện tượng cổng socket RMI bị treo sau khi dừng.
- **Hiện trạng Kênh truyền & Rủi ro còn tồn tại (Residual Risk)**:
  - Kênh truyền RMI là **Plaintext TCP Socket thuần, chưa có mã hóa TLS/SSL**.
  - **Đánh giá rủi ro**: **MEDIUM**. RMI Plaintext bắt buộc phải được bảo vệ ở tầng hạ tầng mạng bằng Private Staging VLAN riêng và IP Whitelist tường lửa, cấm mở cổng ra Internet.

---

## IV. BẰNG CHỨNG QUY TRÌNH VẬN HÀNH VÀ SAO LƯU

### 1. Diễn tập Sao lưu và Khôi phục CSDL Thực tế (3-Tier Drill)
- **Đã chuẩn hóa lệnh với tham số `--result-file`**:
  ```powershell
  mysqldump -u root --single-transaction --quick --routines --triggers --hex-blob --default-character-set=utf8mb4 "--result-file=scratch\drill_backup_132.sql" school_library_test
  ```
  *(Loại bỏ 100% lỗi chuyển mã UTF-16LE của PowerShell khi dùng toán tử `>`)*.
- **Bằng chứng diễn tập**:
  - Sao lưu thành công: Exit Code: **0**, dung lượng: **1.329.456 bytes**.
  - Khôi phục thành công vào CSDL tạm duy nhất: Exit Code: **0**.
  - Đối chiếu số dòng dữ liệu giữa nguồn và đích: 11/11 bảng khớp 100%, 14 khóa ngoại, 1 routine, 0 vi phạm bất biến nghiệp vụ.
  - Kiểm tra phát hiện lỗi SQL trả về đúng Exit Code: 1.
  - Dọn dẹp sạch CSDL tạm và file dump sau diễn tập.

### 2. Tiện ích Khôi phục Tài khoản Cứu nạn (`RecoverLibrarian2Utility`)
- Được lưu tại thư mục cách ly `scratch/`, không đóng gói vào bản phát hành sản xuất.
- Bắt buộc truyền tham số `--target-catalog <tên>` hoặc `-Dtarget.catalog=<tên>`; từ chối chạy nếu thiếu.
- Tự động áp dụng DACL (NTFS ACL) trên thư mục và tệp sao lưu: **Loại bỏ quyền kế thừa, loại bỏ các nhóm `Users` và `Authenticated Users`, chỉ cho phép Owner duy nhất truy cập**.
- Fail-safe ném `IOException` từ chối tạo file nếu filesystem không hỗ trợ POSIX hoặc NTFS ACL.

### 3. Trạng thái Cơ sở Dữ liệu Production (`school_library`)
- Truy vấn kiểm tra đối soát chỉ đọc:
  ```
  id    username      role         is_active    hash_len
  1     admin         ADMIN        1            110
  2     librarian1    LIBRARIAN    1            110
  3     librarian2    LIBRARIAN    1            64
  4     student1      STUDENT      1            110
  8     studen2       STUDENT      1            110
  ```
- **Cam kết**: Đúng 5 tài khoản nguyên vẹn; tài khoản `librarian2` giữ nguyên băm cũ SHA-256 (`hash_len=64`); cột `must_change_password` chưa tồn tại. ZERO DDL, ZERO DML trên production.

---

## V. CỔNG SẴN SÀNG TRIỂN KHAI VÀ BÀN GIAO STAGING

### 1. Bảng Trạng thái Các Điều kiện Chuyển giao

| Cổng Đánh giá | Trạng thái | Ghi chú & Trách nhiệm Thực hiện |
|:---|:---:|:---|
| **Build & Compilation** | **PASS** | Biên dịch sạch Java 8 qua Ant; cấu trúc module cách ly hoàn hảo. |
| **Automated Testing** | **PASS** | 17 suites, 160/160 tests PASS (0 fail, 0 error, 0 skipped). |
| **Release Artifacts Packaging** | **PASS** | `dist/server-release` sạch, không chứa CSDL hay mật khẩu, `run-server.bat` fail-fast. |
| **Windows Service Account** | **BLOCKED** | Đang chờ SysAdmin Windows tạo tài khoản chuyên dụng `svc_library` và phân quyền NTFS. |
| **MySQL Staging Schema & User** | **BLOCKED** | Đang chờ DBA tạo database `school_library_staging` và tài khoản `thuvien_staging_app`. |
| **Campus Network Security** | **BLOCKED** | Đang chờ đội NOC cấu hình Private Staging VLAN và IP Whitelist trên tường lửa phòng máy. |
| **Production Deployment** | **PENDING** | **BẮT BUỘC CHỜ PHÊ DUYỆT CHÍNH THỨC CỦA QUẢN TRỊ VIÊN/DBA**. |

---

## VI. KẾT LUẬN CUỐI CÙNG VÀ CỔNG TRIỂN KHAI (FINAL GATE VERDICTS)

### 1. BUILD VERIFIED: PASS
> Toàn bộ mã nguồn biên dịch sạch với Java 8; kiểm thử tự động Ant đạt **17 test suites, 160/160 tests PASS** (0 failures, 0 errors, 0 skipped); `git diff --check` đạt exit code 0.

### 2. RELEASE PACKAGE VERIFIED: PASS
> Gói phát hành `dist/server-release` sạch 100%, bảo đảm cách ly bí mật, có cơ chế fail-fast khi thiếu cấu hình, và tài liệu runbook đã tích hợp đầy đủ quy trình an toàn `--result-file`.

### 3. STAGING READINESS: NO-GO (BLOCKED — CHƯA ĐỦ ĐIỀU KIỆN HẠ TẦNG)
> **Kết luận**: **STAGING NO-GO**.
> **Lý do**:
> 1. Kênh truyền RMI là **Plaintext TCP hoàn toàn**, chưa có mã hóa TLS/SSL.
> 2. **Chưa có bằng chứng xác nhận từ đội ngũ Quản trị mạng (NOC)** về việc phân vùng Private VLAN và thiết lập IP Whitelist trên tường lửa phòng lab/trường học.
> 3. Hệ thống máy chủ Windows đích **chưa được cấp phát tài khoản dịch vụ chuyên dụng `svc_library`** và phân quyền NTFS.
> 4. CSDL MySQL **chưa được DBA khởi tạo database `school_library_staging`** và tài khoản runtime giới hạn quyền `thuvien_staging_app`.
>
> Ứng dụng, bản build, kiểm thử tự động và gói phát hành đã **PASS 100%**, nhưng cổng Staging bắt buộc phải giữ trạng thái **NO-GO (BLOCKED)** cho đến khi đội ngũ hạ tầng (SysAdmin, DBA, NOC) bàn giao đầy đủ các bằng chứng theo checklist tại [STAGING-HANDOFF-CHECKLIST.md](file:///D:/workspace/thuvien/docs/STAGING-HANDOFF-CHECKLIST.md).

### 4. PRODUCTION DEPLOYMENT APPROVED: PENDING ADMINISTRATOR APPROVAL
> Hệ thống giữ nguyên trạng thái **CHỜ PHÊ DUYỆT CHÍNH THỨC TỪ QUẢN TRỊ VIÊN HỆ THỐNG (ADMINISTRATOR/DBA)** để mở khung giờ triển khai. Tuyệt đối không tự ý triển khai trên môi trường sản xuất.

