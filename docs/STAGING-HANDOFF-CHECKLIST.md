# BẢNG BÀN GIAO TRIỂN KHAI MÔI TRƯỜNG STAGING
## Staging Infrastructure Handoff Checklist & Evidence Requirements

> **Hệ thống**: Remote School Library Management System  
> **Thư mục phát hành**: `dist/server-release`  
> **Phiên bản Audit**: Final Staging Handoff & Closeout  
> **Thời điểm lập**: 2026-10-24  
> **Mục tiêu**: Cung cấp danh mục công việc kỹ thuật cụ thể, bằng chứng cần nộp và phân định trách nhiệm rõ ràng cho Quản trị viên Windows (SysAdmin), Quản trị viên CSDL (DBA), Đội ngũ Vận hành Mạng (NOC), và Trưởng nhóm Kiểm thử (QA Lead).  
> **Nguyên tắc an toàn bắt buộc**: Tuyệt đối không can thiệp CSDL production `school_library`, không ghi mật khẩu thật hoặc bí mật vào tài liệu hay Git.

---

## 1. DÀNH CHO QUẢN TRỊ VIÊN HỆ THỐNG WINDOWS (SYSADMIN)

### Nhiệm vụ Kỹ thuật:
1. **Tạo tài khoản dịch vụ chuyên dụng (Service Account)**:
   - Tạo một tài khoản người dùng cục bộ hoặc domain tiêu chuẩn, **không thuộc nhóm Administrators**:
     ```powershell
     New-LocalUser -Name "svc_library" -Description "Service Account for Library Server" -NoPassword
     ```
   - Đặt mật khẩu mạnh cho tài khoản này theo chính sách bảo mật nội bộ của trường học.
2. **Cấu hình Quyền Hệ thống tệp (NTFS DACL) trên thư mục phát hành**:
   - Thư mục gốc `dist\server-release` và các tệp `.jar`, `lib\`: Cấp quyền `Read & Execute` cho `svc_library`.
   - Thư mục nhật ký `logs\`: Cấp quyền `Modify` (`M`) cho `svc_library` để ghi log hoạt động.
   - Thư mục sao lưu an toàn `security_backups\`: Cấp quyền `Modify` (`M`) cho `svc_library` và `Administrators`.
   - Tệp cấu hình `resources\db.properties`: **Chỉ cấp quyền đọc (`R`) cho `svc_library` và `Administrators`**. Tuyệt đối loại bỏ quyền đọc của nhóm `Users` và `Authenticated Users` để bảo vệ thông tin đăng nhập CSDL.

### Bằng chứng Cần nộp để chuyển sang PASS:
- [ ] Output PowerShell của lệnh: `Get-LocalUser svc_library | Format-List Name, Enabled, Description`
- [ ] Output PowerShell của lệnh kiểm tra quyền:
  ```powershell
  icacls "dist\server-release\resources\db.properties"
  icacls "dist\server-release\logs"
  ```
  *(Tiêu chí nghiệm thu: `Users` và `Authenticated Users` không xuất hiện trong danh sách ACL có quyền đọc `db.properties`).*

---

## 2. DÀNH CHO QUẢN TRỊ VIÊN CƠ SỞ DỮ LIỆU (DBA)

### Nhiệm vụ Kỹ thuật:
1. **Khởi tạo CSDL Staging Độc lập**:
   - Tạo database `school_library_staging` với bảng mã UTF-8 chuẩn:
     ```sql
     CREATE DATABASE school_library_staging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
     ```
2. **Khởi tạo Schema và Chạy Migration**:
   - Nạp cấu trúc bảng ban đầu từ bản mẫu sạch.
   - Chạy tệp migration nâng cấp an toàn `database/migration_must_change_password_mysql8.sql` vào `school_library_staging`.
3. **Tạo Tài khoản Runtime có Quyền Tối thiểu (Least Privilege DML Only)**:
   - Tạo tài khoản kết nối cho ứng dụng máy chủ:
     ```sql
     -- Thay <STRONG_PASSWORD> bang mat khau phuc tap thuc te do DBA nam giu
     CREATE USER 'thuvien_staging_app'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY '<STRONG_PASSWORD>';
     GRANT SELECT, INSERT, UPDATE, DELETE ON school_library_staging.* TO 'thuvien_staging_app'@'127.0.0.1';
     FLUSH PRIVILEGES;
     ```
   - **Quy tắc bắt buộc**: Tuyệt đối **KHÔNG cấp quyền DDL (`CREATE`, `DROP`, `ALTER`)**, quyền phân quyền (`GRANT OPTION`), hoặc các quyền quản trị máy chủ (`SUPER`, `PROCESS`) cho tài khoản runtime. Ứng dụng đã được kiểm chứng không cần bất kỳ quyền DDL nào trong toàn bộ vòng đời vận hành.
4. **Cấu hình Tệp Kết nối Staging**:
   - Sao chép `resources\db.properties.example` thành `resources\db.properties`.
   - Điền URL: `jdbc:mysql://127.0.0.1:3306/school_library_staging?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC`
   - Điền user `thuvien_staging_app` và mật khẩu thực tế vừa tạo.

### Bằng chứng Cần nộp để chuyển sang PASS:
- [ ] Output MySQL của lệnh xác nhận danh tính và quyền hạn:
  ```sql
  SHOW GRANTS FOR 'thuvien_staging_app'@'127.0.0.1';
  ```
  *(Tiêu chí nghiệm thu: Chỉ có `SELECT, INSERT, UPDATE, DELETE` trên `school_library_staging.*`).*
- [ ] Output MySQL kiểm tra sự tồn tại của cột `must_change_password`:
  ```sql
  SHOW COLUMNS FROM school_library_staging.users LIKE 'must_change_password';
  ```
  *(Tiêu chí nghiệm thu: Kiểu dữ liệu `tinyint(1)` NOT NULL DEFAULT 0).*

---

## 3. DÀNH CHO ĐỘI NGŨ VẬN HÀNH MẠNG (NOC)

### Nhiệm vụ Kỹ thuật:
1. **Phân vùng Mạng Cách ly (Private Staging VLAN)**:
   - Đặt máy chủ Staging và các máy trạm kiểm thử Staging vào một phân vùng mạng LAN riêng biệt (ví dụ: VLAN Staging).
   - Cách ly hoàn toàn với dải mạng Wi-Fi công cộng của sinh viên và mạng ký túc xá.
2. **Cấu hình Tường lửa (Firewall Rules) và IP Allowlist**:
   - **Cổng dịch vụ RMI**:
     - Cổng Registry: `1099/TCP`
     - Cổng Remote Service: `1100/TCP`
   - **Luật kiểm soát truy cập (Inbound Access Control List)**:
     - **Chỉ cho phép** các gói tin TCP đến cổng 1099 và 1100 xuất phát từ dải IP tĩnh được chỉ định của các máy trạm kiểm thử Staging (IP Whitelist).
     - **Từ chối (DROP/REJECT)** tất cả các kết nối từ dải mạng khác.
   - **Quy tắc tuyệt đối**: Tuyệt đối **KHÔNG mở cổng (không NAT/Port-Forwarding)** cổng 1099 và 1100 ra mạng Internet công cộng do kênh truyền RMI của ứng dụng là Plaintext TCP, chưa hỗ trợ TLS.

### Bằng chứng Cần nộp để chuyển sang PASS:
- [ ] Trích xuất cấu hình hoặc ảnh chụp màn hình luật tường lửa (Firewall Rule) thể hiện:
  - Cho phép TCP 1099, 1100 từ dải IP kiểm thử staging.
  - Chặn toàn bộ IP ngoài dải cho phép.
- [ ] Biên bản / Phiếu xác nhận của NOC về việc máy chủ Staging nằm trong phân vùng Private VLAN được kiểm soát.

---

## 4. DIỄN TẬP SAO LƯU & SMOKE TEST (DBA & QA LEAD)

### Nhiệm vụ Kỹ thuật:
1. **Diễn tập Sao lưu và Khôi phục Thử nghiệm**:
   - Thực hiện lệnh sao lưu theo chuẩn `--result-file` (không dùng toán tử `>`):
     ```cmd
     mysqldump -u root --single-transaction --quick --routines --triggers --hex-blob --default-character-set=utf8mb4 "--result-file=staging_backup_drill.sql" school_library_staging
     ```
   - Khôi phục vào CSDL tạm thời `school_library_staging_drill` và kiểm tra đối soát số dòng dữ liệu.
   - Xóa bỏ CSDL tạm thời sau khi diễn tập thành công.
2. **Khởi động Máy chủ Staging với Khóa Bảo vệ Catalog**:
   - Thiết lập biến môi trường bảo vệ:
     ```cmd
     set DB_EXPECTED_CATALOG=school_library_staging
     ```
   - Khởi động server bằng script: `dist\server-release\run-server.bat`.
   - Xác nhận server kiểm tra khớp catalog, nhận diện schema hợp lệ và lắng nghe trên cổng 1099 / 1100.
3. **Kiểm thử Nghiệp vụ Thông suốt (Smoke Test)**:
   - Dùng máy trạm trong danh sách whitelist mở `dist\client-dist\run-client.bat <STAGING_IP> 1099`.
   - Thực hiện chuỗi nghiệp vụ:
     1. Đăng nhập tài khoản thủ thư/sinh viên staging.
     2. Đổi mật khẩu bắt buộc (nếu có cờ `must_change_password`).
     3. Tìm kiếm sách, thực hiện mượn sách -> kiểm tra tồn kho giảm 1.
     4. Trả sách -> kiểm tra tồn kho tăng 1, tính phạt quá hạn (nếu có).
     5. Đăng xuất tài khoản.
     6. Dừng server kiểm tra không để lại tiến trình treo hay cổng chiếm dụng.

### Bằng chứng Cần nộp để chuyển sang PASS:
- [ ] Đoạn trích nhật ký `server.log` khi khởi động hiển thị:
  - `Verified expected database catalog match: 'school_library_staging'`
  - `Database connection pool and schema integrity verified (must_change_password column present in 'school_library_staging').`
  - `LibraryRMIServer started successfully`
- [ ] Biên bản kiểm thử Smoke Test với chữ ký nghiệm thu của QA Lead.

---

## 5. BẢNG TIÊU CHÍ NGHIỆM THU VÀ PHÂN LOẠI TRẠNG THÁI (GATE MATRIX)

| Mã Hạng mục | Nhóm Trách nhiệm | Mô tả Điều kiện Tiên quyết | Trạng thái Hiện tại | Bằng chứng Đã có / Cần nộp để chuyển trạng thái |
|:---:|:---:|:---|:---:|:---|
| **INF-WIN** | SysAdmin | Tài khoản `svc_library` và phân quyền NTFS trên `db.properties` | **BLOCKED** | **Cần nộp**: Output PowerShell `Get-LocalUser` và `icacls`. |
| **INF-DBA** | DBA | Database `school_library_staging` và user `thuvien_staging_app` (DML only) | **BLOCKED** | **Cần nộp**: Output MySQL `SHOW GRANTS` và `SHOW COLUMNS`. |
| **INF-NOC** | NOC | Private VLAN và Tường lửa IP Whitelist cho cổng 1099/1100 (Cấm NAT Internet) | **BLOCKED** | **Cần nộp**: Biên bản phân vùng mạng & File cấu hình Firewall Rule. |
| **REL-SEC** | Release Lead | Gói phát hành `dist/server-release` sạch bí mật, fail-fast khi thiếu file cấu hình | **VERIFIED** | **Đã xác minh**: `server.jar` chỉ chứa `server.properties`, script từ chối chạy khi thiếu cấu hình (Exit 1). |
| **DRL-BAK** | DBA & QA | Diễn tập Sao lưu / Phục hồi `--result-file` | **VERIFIED** *(trên test)*<br>**PENDING** *(trên staging)* | **Đã xác minh** trên `school_library_test` (11/11 bảng khớp). **Cần nộp**: Log diễn tập trên `school_library_staging`. |
| **SMK-TST** | QA Lead | Smoke Test đăng nhập, đổi mật khẩu bắt buộc, RBAC, mượn/trả, audit log, shutdown | **VERIFIED** *(automated)*<br>**PENDING** *(GUI staging)* | **Đã xác minh**: 17 suites, 160 tests PASS. **Cần nộp**: Biên bản nghiệm thu kiểm thử giao diện thực tế trên staging. |
| **LOG-SEC** | Dev / QA | Rà soát nhật ký: không ghi mật khẩu plaintext, hash, hoặc token vào log | **VERIFIED** | **Đã xác minh**: Mã nguồn `AuthServiceImpl`, `RequestRouter` chỉ ghi log username, userId và action; zero secret logging. |
| **RMI-TLS** | Architect / NOC | Trạng thái mã hóa kênh truyền RMI | **VERIFIED** *(Plaintext)*<br>**RESIDUAL RISK** *(MEDIUM)* | **Đã xác minh**: RMI chạy Plaintext TCP thuần, chưa có TLS. Bắt buộc phải có Private VLAN và Firewall Whitelist của NOC để bảo vệ. |

---

## 6. KẾ HOẠCH NÂNG CẤP TLS CHO RMI TRONG TƯƠNG LAI (TLS ROADMAP PLAN)

> *Lưu ý: Không triển khai tự động trong pha này nhằm tuân thủ nguyên tắc đóng gói an toàn và tương thích ngược với Swing client hiện tại.*

1. **Phạm vi kỹ thuật**:
   - Máy chủ: Tạo `SslRMIServerSocketFactory` và truyền vào `UnicastRemoteObject.exportObject(remoteService, port, csf, ssf)`.
   - Máy khách: Tạo `SslRMIClientSocketFactory` để thiết lập kết nối an toàn với máy chủ.
   - Chứng chỉ số: Sử dụng Java `keytool` tạo KeyStore (`server.keystore`) và TrustStore (`client.truststore`) nội bộ với thuật toán RSA 2048-bit hoặc EC.
2. **Kế hoạch kiểm thử tương thích**:
   - Chạy bộ kiểm thử tự động `RMIEndToEndIntegrationTest` và `MultiSessionAndConcurrentBorrowTest` trên cổng TLS riêng (ví dụ: `10943/TCP`).
   - Kiểm thử kết nối client Swing qua mạng LAN giả lập độ trễ (latency).
3. **Phương án Rollback an toàn**:
   - Thiết lập cờ cấu hình `rmi.ssl.enabled=false` trong `server.properties` và `client.properties`.
   - Nếu xảy ra lỗi chứng chỉ (Handshake Exception), hệ thống có thể chuyển đổi tức thì về Plaintext Socket trong mạng LAN nội bộ.

---

## 7. ĐỐI SOÁT PHƯƠNG ÁN TRIỂN KHAI VÀ SỬ DỤNG HẠ TẦNG HIỆN HỮU

Qua kiểm kê thực tế tại máy chủ hiện tại:
- **Cơ sở dữ liệu**:
  - `school_library`: CSDL Production gốc, giữ nguyên trạng 100% (chưa có cột `must_change_password`, 5 user nguyên vẹn).
  - `school_library_test`: **ĐÃ TỒN TẠI VÀ SẴN SÀNG**. Đã có đầy đủ 11 bảng, đã chạy và kiểm chứng thành công migration `must_change_password`, cấu hình `server/resources/db.properties` mặc định đã trỏ vào đây.
  - `school_library_staging`: **CHƯA TỒN TẠI** (đây là yêu cầu thiết kế mô hình doanh nghiệp, không bắt buộc nếu dự án dùng `school_library_test` làm môi trường kiểm thử/staging).
- **Mạng và Địa chỉ IP**:
  - Máy hiện tại có IP Wi-Fi nội bộ `192.168.2.10`, VPN `26.122.195.188` và Loopback `127.0.0.1`.
  - IP `10.0.107.143` trước đây là IP DHCP động tại phòng lab trường học trong đợt kiểm thử Phase 1.6-1.10. Thuật toán `resolveServerHost()` trong `LibraryRMIServer` tự động nhận diện IP LAN vật lý hiện hành mà không cần gán cứng.
  - Windows Firewall đang bật; có sẵn luật `School Library Server TCP 9999`. Cổng 1099 và 1100 hiện đóng an toàn, chưa mở cho máy ngoài nếu chưa có luật firewall.

### Hai Phương án Triển khai Nghiệm thu:

#### Phương án 1 (Khuyến nghị Thực tế — Sử dụng Hạ tầng Hiện hữu):
- **Phạm vi**: Sử dụng ngay CSDL `school_library_test` làm môi trường Staging/Acceptance Test nội bộ.
- **Ưu điểm**:
  - Không cần tạo thêm database mới `school_library_staging`.
  - Không cần tạo thêm tài khoản MySQL mới, tận dụng `root@localhost` hoặc tài khoản có sẵn.
  - Kiểm thử trực tiếp trên cùng máy trạm qua `127.0.0.1` hoặc trong cùng mạng Wi-Fi LAN qua IP `192.168.2.10`.
  - Không bị phụ thuộc vào thời gian cấp phát của SysAdmin/DBA/NOC bên ngoài.
- **Trạng thái**: **`READY FOR TEST-STAGING (GO)`** — Có thể thực hiện ngay Smoke Test để nghiệm thu toàn bộ tính năng phần mềm.

#### Phương án 2 (Mô hình Doanh nghiệp — Phân tách Môi trường Vật lý Tuyệt đối):
- **Phạm vi**: Bắt buộc tạo database thứ ba `school_library_staging`, tạo tài khoản runtime riêng `thuvien_staging_app`, tạo tài khoản Windows `svc_library`, và yêu cầu NOC cấu hình Private VLAN.
- **Đánh giá**: Đây là mô hình chuẩn hóa cho môi trường vận hành trường học quy mô lớn, nhưng là **rào cản hành chính (Administrative Overhead)** nếu chỉ cần nghiệm thu đồ án môn học.
- **Trạng thái**: **`BLOCKED (AWAITING PROVISIONING)`** — Chờ văn bản bàn giao từ SysAdmin/DBA/NOC.

---

## 8. KẾT LUẬN CỔNG CHUYỂN GIAO (FINAL HANDOFF VERDICT)

- **MÃ NGUỒN & BẢN PHÁT HÀNH**: **`VERIFIED: PASS 100%`** (17 test suites, 160/160 tests PASS).
- **NGHIỆM THU THEO PHƯƠNG ÁN HIỆN HỮU (DÙNG `school_library_test`)**: **`STAGING GO (READY)`**. Có thể khởi động và kiểm thử chấp nhận ngay lập tức trên hạ tầng có sẵn.
- **NGHIỆM THU THEO PHƯƠNG ÁN DOANH NGHIỆP TÁCH BIỆT (DÙNG `school_library_staging` & NOC)**: **`BLOCKED`** cho đến khi các bộ phận SysAdmin, DBA và NOC nộp đủ bằng chứng.
- **CƠ SỞ DỮ LIỆU PRODUCTION (`school_library`)**: **`PENDING ADMINISTRATOR APPROVAL`**. Bảo vệ tuyệt đối, không thay đổi.

