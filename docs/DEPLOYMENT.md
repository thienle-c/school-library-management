# HƯỚNG DẪN TRIỂN KHAI MẠNG LAN VÀ VẬN HÀNH HỆ THỐNG (DEPLOYMENT GUIDE)

> **Hệ thống**: Remote School Library Management System  
> **Kiến trúc mạng**: 3-Tier Distributed Java RMI Architecture  
> **Môi trường**: Mạng nội bộ trường học (Campus LAN / Lab Room / Multi-subnet)  

---

## 1. MÔ HÌNH KIẾN TRÚC TRIỂN KHAI PHÂN TÁN (DISTRIBUTED ARCHITECTURE)

```
       +-------------------------------------------------------------+
       |                     MÁY CHỦ THƯ VIỆN                        |
       |                   (Library Server Node)                     |
       |                                                             |
       |   +-----------------------------------------------------+   |
       |   |               Java RMI Service Host                 |   |
       |   |                                                     |   |
       |   |  - Port 1099/TCP: RMI Registry                      |   |
       |   |  - Port 1100/TCP: Remote Object (LibraryService)    |   |
       |   |  - RequestRouter & RBAC Authorization               |   |
       |   |  - JEP 290 Serialization Whitelist Filter           |   |
       |   +-----------------------------------------------------+   |
       |                              |                              |
       |                     JDBC Connection Pool                    |
       |                     (HikariCP - Max: 10)                    |
       |                              v                              |
       |   +-----------------------------------------------------+   |
       |   |              MySQL Database Engine 8.0              |   |
       |   |          (Cục bộ: 127.0.0.1:3306 - InnoDB)          |   |
       |   +-----------------------------------------------------+   |
       +-------------------------------------------------------------+
                                      ^
                                      |
                       Mạng LAN Trường học (TCP Sockets)
                        [1099/TCP, 1100/TCP — RMI Wire]
                                      |
         +----------------------------+----------------------------+
         |                                                         |
         v                                                         v
+-------------------------------+       +----------------------------------+
|   MÁY TRẠM THỦ THƯ (Quầy)     |       |    MÁY TRẠM SINH VIÊN (Tra cứu)  |
|      (Librarian Workstation)  |       |       (Student OPAC Terminal)    |
|                               |       |                                  |
| - Java 8 Desktop Swing Client |       | - Java 8 Desktop Swing Client    |
| - RMI Client Proxy            |       | - RMI Client Proxy               |
| - Quyền: LIBRARIAN / ADMIN    |       | - Quyền: STUDENT (Self-Service)  |
| - ZERO Database Drivers       |       | - ZERO Database Drivers          |
+-------------------------------+       +----------------------------------+
```

### Nguyên tắc ranh giới kiến trúc:
1. **Cô lập Cơ sở Dữ liệu tuyệt đối**: Cơ sở dữ liệu MySQL chỉ lắng nghe trên giao diện cục bộ của máy chủ (`127.0.0.1:3306` hoặc mạng nội bộ riêng). Máy trạm Client **KHÔNG ĐƯỢC PHÉP** và **KHÔNG CẦN** kết nối trực tiếp vào MySQL.
2. **Giao tiếp duy nhất qua Java RMI**: Toàn bộ luồng dữ liệu giữa Client và Server được đóng gói qua đối tượng giao thức (`Request`, `Response`) thông qua giao diện từ xa `LibraryRemoteService`.
3. **Cổng cố định (Fixed Non-Dynamic Ports)**: Cổng Registry cố định ở `1099` và cổng dịch vụ đối tượng cố định ở `1100`, giúp việc cấu hình tường lửa mạng trường học trở nên dễ dàng và ổn định.

---

## 2. CẤU HÌNH ĐỊA CHỈ MẠNG VÀ TƯỜNG LỬA (NETWORK & FIREWALL SETUP)

### 2.1. Cơ chế tự động nhận diện IP máy chủ (`resolveServerHost`)
Khi máy chủ thư viện khởi động, lớp `LibraryRMIServer` sẽ:
1. Duyệt qua tất cả các card mạng vật lý đang hoạt động (`NetworkInterface`).
2. Tự động tìm kiếm địa chỉ IPv4 thực trong mạng LAN (bỏ qua card mạng ảo loopback `127.0.0.1`).
3. Gán thuộc tính hệ thống JVM:
   ```java
   System.setProperty("java.rmi.server.hostname", resolvedHost);
   ```
4. Đảm bảo khi Client từ xa kết nối tới RMI Registry, stub được trả về chứa đúng địa chỉ IP thực của Server trong mạng LAN thay vì `localhost`.

### 2.2. Cấu hình Windows Firewall trên Máy Chủ
Mở cổng `1099` và `1100` bằng PowerShell (chạy dưới quyền *Run as Administrator*):

```powershell
# Tạo Rule cho phép nhận kết nối RMI từ mạng LAN
New-NetFirewallRule -Name "LibraryServer_RMI" `
    -DisplayName "Remote School Library System (RMI Ports 1099, 1100)" `
    -Description "Cho phep Client trong mang LAN ket noi toi he thong thu vien" `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort 1099, 1100 `
    -Action Allow `
    -Profile Private, Domain
```

> [!CAUTION]
> Tuyệt đối **KHÔNG MỞ** cổng `3306` (MySQL) ra ngoài mạng công cộng hoặc Internet. Cổng 3306 chỉ phục vụ cho dịch vụ Java nội bộ trên máy chủ.

### 2.3. Cấu hình UFW Firewall trên Máy Chủ Linux (Nếu dùng Linux Server)
```bash
sudo ufw allow 1099/tcp comment 'RMI Registry'
sudo ufw allow 1100/tcp comment 'RMI Remote Object Service'
sudo ufw reload
```

---

## 3. QUY TRÌNH VẬN HÀNH VÀ DỪNG SERVER AN TOÀN (OPERATIONAL PROCEDURES)

### 3.1. Phân định Gói Phân phối (Test vs Production)
Hệ thống cung cấp hai gói phân phối riêng biệt được tạo tự động bởi lệnh `ant package-all`:
* **`dist/server-dist` (Gói Môi trường Kiểm thử / Development)**:
  * Được thiết lập sẵn cấu hình trỏ tới `school_library_test` trong `resources/db.properties`.
  * Phục vụ diễn tập, chạy kiểm thử tự động nội bộ và nghiệm thu trước triển khai.
* **`dist/server-release` (Gói Bản phát hành Môi trường Sản xuất / Production)**:
  * **Tuyệt đối sạch**: Không chứa bất kỳ thông tin mật khẩu phát triển hoặc database test nào.
  * Chỉ chứa tệp mẫu [db.properties.example](file:///D:/workspace/thuvien/server/resources/db.properties.example).
  * `server.jar` được đóng gói hoàn toàn cách ly, không nướng (bake-in) bất kỳ file cấu hình database nào vào bên trong mã nhị phân.

### 3.2. Quy trình Migration CSDL Production An toàn trên MySQL 8.0
> [!IMPORTANT]
> **Tương thích MySQL 8.0**: MySQL 8.0 (kể cả 8.0.30+) **KHÔNG hỗ trợ** cú pháp `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`. Nếu thực thi câu lệnh này, MySQL sẽ báo lỗi cú pháp:
> `ERROR 1064 (42000): You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'IF NOT EXISTS...'`

Để đảm bảo tính toàn vẹn và khả năng chạy lại nhiều lần (idempotent), Quản trị viên sử dụng tệp script migration được cung cấp tại [database/migration_must_change_password_mysql8.sql](file:///D:/workspace/thuvien/database/migration_must_change_password_mysql8.sql):

1. **Bước 1: Sao lưu CSDL trước khi thao tác**:
   ```cmd
   mysqldump -u root -p --single-transaction --routines --triggers --result-file=D:\backup\school_library_pre_migration_YYYYMMDD.sql school_library
   ```
   *(Lưu ý: Bắt buộc dùng tham số `--result-file` để ghi file UTF-8 trực tiếp, tránh lỗi chuyển mã UTF-16LE của PowerShell khi dùng toán tử `>`)*

2. **Bước 1b: Xác minh File Sao lưu Đọc được và Toàn vẹn (Readable Backup Verification)**:
   Quản trị viên bắt buộc phải kiểm tra file dump trước khi thực hiện bất kỳ DDL nào:
   - Kiểm tra dung lượng file: Bắt buộc `size > 0`.
   - Đọc 10 dòng đầu để xác nhận header MySQL dump hợp lệ:
     ```powershell
     Get-Content D:\backup\school_library_pre_migration_*.sql -TotalCount 10
     ```
   - Đọc 5 dòng cuối để xác nhận dump hoàn tất trọn vẹn (không bị đứt gãy giữa chừng):
     ```powershell
     Get-Content D:\backup\school_library_pre_migration_*.sql -Tail 5
     # K?t qu? ph?i ch?a: -- Dump completed on YYYY-MM-DD ...
     ```

3. **Bước 2: Thực thi Script Migration Idempotent**:
   ```cmd
   mysql -u root -p school_library < database/migration_must_change_password_mysql8.sql
   ```
   **Cơ chế bảo vệ của script**:
   - Truy vấn `INFORMATION_SCHEMA.COLUMNS` để kiểm tra sự tồn tại của cột `must_change_password`.
   - Nếu chưa tồn tại: Thực hiện thêm cột `must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active`.
   - Nếu đã tồn tại: Kiểm tra xem kiểu dữ liệu có đúng là `tinyint(1)`, `IS_NULLABLE = 'NO'`, `COLUMN_DEFAULT = '0'` hay không. Nếu đúng, in thông báo `SKIP` và bỏ qua an toàn.
   - Nếu tồn tại nhưng sai kiểu/ràng buộc: Dừng ngay lập tức với `SIGNAL SQLSTATE '45000'`, không tự ý sửa âm thầm.
   - So sánh số lượng bản ghi bảng `users` trước và sau migration; nếu phát hiện sai lệch số dòng, lập tức ném lỗi cảnh báo.

4. **Bước 2b: Xác minh Schema và Số lượng Bản ghi sau Migration (Post-Migration Verification)**:
   DBA chạy câu lệnh sau để kiểm tra trực tiếp:
   ```sql
   SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
   FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = 'school_library' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'must_change_password';
   -- K?t qu? k? v?ng: DATA_TYPE=tinyint, COLUMN_TYPE=tinyint(1), IS_NULLABLE=NO, COLUMN_DEFAULT=0

   SELECT COUNT(*) AS total_users FROM school_library.users;
   -- K?t qu? ph?i trng kh?p v?i s? l??ng b?n ghi tr??c khi migration.
   ```

5. **Bước 2c: Kế hoạch Hoàn tác Migration (Rollback Plan)**:
   Nếu migration gặp sự cố bất ngờ hoặc cần đưa CSDL về trạng thái ban đầu:
   - **Cách 1 (Khôi phục toàn diện từ bản sao lưu - Phương án phục hồi dữ liệu có thẩm quyền cao nhất)**:
     ```cmd
     mysql -u root -p school_library < D:\backup\school_library_pre_migration_YYYYMMDD.sql
     ```
   - **Cách 2 (Hoàn tác DDL cột `must_change_password` nếu chưa có dữ liệu phụ thuộc mới)**:
     ```sql
     ALTER TABLE school_library.users DROP COLUMN must_change_password;
     ```
     > [!WARNING]
     > Hoàn tác DDL (`ALTER TABLE ... DROP COLUMN`) chỉ loại bỏ cột về mặt cấu trúc bảng, **KHÔNG nhất thiết khôi phục trạng thái nghiệp vụ** của tài khoản nếu đã có phiên làm việc đăng nhập hoặc đổi mật khẩu diễn ra sau migration. Trong mọi tình huống sự cố dữ liệu, bản dump `mysqldump` tạo ở Bước 1 mới là phương án phục hồi dữ liệu toàn vẹn và chuẩn mực nhất.

6. **Bước 3: Tạo Tài khoản Database Sản xuất có Quyền tối thiểu (Least Privilege)**:
   > [!CAUTION]
   > Tuyệt đối **KHÔNG sử dụng tài khoản `root`** hoặc mật khẩu rỗng cho môi trường Production. Cần tạo một tài khoản riêng chỉ có quyền thao tác dữ liệu (DML) trên `school_library`:
   ```sql
   -- Thuc hien boi DBA tren MySQL Server
   CREATE USER 'thuvien_app'@'127.0.0.1' IDENTIFIED BY 'Mat_Khau_Phuc_Tap_Cua_Admin!2026';
   GRANT SELECT, INSERT, UPDATE, DELETE ON school_library.* TO 'thuvien_app'@'127.0.0.1';
   FLUSH PRIVILEGES;
   ```

### 3.3. Quy trình Xác minh Database Đích trước khi Khởi động
Trước khi chạy máy chủ, Quản trị viên thực hiện:
1. **Thiết lập cấu hình**:
   ```cmd
   cd /d D:\workspace\thuvien\dist\server-release
   copy resources\db.properties.example resources\db.properties
   ```
   Chỉnh sửa `resources\db.properties` trỏ đúng vào CSDL sản xuất (`school_library`), điền tài khoản `thuvien_app` và mật khẩu thực tế.
2. **Khóa chống nhầm lẫn môi trường (Environment Catalog Guard)**:
   Để loại trừ 100% rủi ro chạy nhầm database, quản trị viên bắt buộc/khuyến nghị cấu hình biến môi trường hoặc cờ JVM:
   * **Cách 1 (Biến môi trường Windows/Linux - Khuyến nghị)**:
     ```cmd
     set DB_EXPECTED_CATALOG=school_library
     ```
   * **Cách 2 (Thuộc tính JVM)**:
     Chạy server với tham số `-Ddb.expected.catalog=school_library`.
   * **Cơ chế Fail-Fast**: Nếu kết nối thực tế tới `school_library_test` (hoặc database khác), server sẽ lập tức dừng trước khi mở bất kỳ cổng RMI nào:
     `[DATABASE CONFIG ERROR] Mục tiêu cơ sở dữ liệu không khớp! Yêu cầu: 'school_library', nhưng kết nối thực tế tới: '...'`
3. **Kiểm tra thông báo khởi động**:
   Nhấp đúp chuột vào `run-server.bat` (hoặc chạy từ command line). Server sẽ kiểm tra tính toàn vẹn:
   * Nếu thiếu `resources\db.properties`: Script báo lỗi và dừng ngay.
   * Nếu `db.properties` vẫn chứa giá trị mẫu (`your_db_username` / `your_db_password`): Server ném ngoại lệ dừng ngay, không in lộ bí mật.
   * Nếu CSDL thiếu migration `must_change_password`: Server in `[SECURITY FATAL]` và từ chối bật.
   * Khi khởi động thành công, server in thông báo:
     `Connected to Target Database Catalog: 'school_library'`
     `Database connection pool and schema integrity verified (must_change_password column present).`

### 3.4. Khởi động Máy chủ
Điều hướng vào thư mục phân phối máy chủ (`dist/server-release` cho production, hoặc `dist/server-dist` cho lab test) và thực thi:
```cmd
run-server.bat
```
Server sẽ in biểu ngữ khởi động và danh sách các địa chỉ IPv4 đang hoạt động để quản trị viên đối chiếu.

### 3.5. Quy trình Cứu hộ Mật khẩu Thủ thư khẩn cấp (Emergency Account Rescue)
Tiện ích cứu hộ [RecoverLibrarian2Utility](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/RecoverLibrarian2Utility.java) là một công cụ quản trị cứu nạn độc lập, **KHÔNG được đóng gói vào bản phân phối phát hành `server-release`** để tránh rủi ro bảo mật.
* **Cách quản lý**: Quản trị viên lưu giữ file nguồn trong kho công cụ quản trị riêng biệt.
* **Bắt buộc chỉ định Catalog mục tiêu**: Tiện ích không sử dụng catalog mặc định. Quản trị viên bắt buộc truyền `--target-catalog school_library`. Nếu catalog kết nối không khớp, tiện ích lập tức từ chối và hủy thao tác.
* **Chế độ kiểm tra (Dry-Run)**: Khi chạy không có cờ `--apply`, tiện ích chỉ đọc kiểm tra CSDL và không thay đổi bất kỳ byte dữ liệu nào.
* **Cơ chế băm**: Sinh mật khẩu băm chuẩn PBKDF2 (`PBKDF2WithHmacSHA256`, 10.000 vòng, salt ngẫu nhiên 16 bytes).
* **Nhập mật khẩu an toàn**: Sử dụng `System.console().readPassword()` để nhập mật khẩu che dấu, không truyền qua tham số dòng lệnh (tránh bị lộ trong `history` hay Task Manager), bộ nhớ mảng `char[]` được xóa sạch bằng `\0` ngay sau khi băm.
* **Cờ bắt buộc đổi mật khẩu**: Tự động gán `must_change_password = TRUE`.
* **Kế hoạch Rollback và Sao lưu Hash cũ**:
  - Tiện ích tự động tạo file sao lưu hash cũ độc quyền vào thư mục `security_backups/` (phân quyền owner-only read/write) trước khi update.
  - Tuyệt đối không lưu mật khẩu tạm thời trong file sao lưu; chỉ lưu hash cũ và metadata nhận diện.
  - Điều kiện an toàn: Chỉ rollback khi tài khoản chưa đăng nhập thành công và chưa đổi mật khẩu mới. Nếu thủ thư đã đăng nhập và đổi mật khẩu, việc rollback sẽ làm mất mật khẩu mới của thủ thư!
  - Lệnh hoàn tác SQL:
    ```sql
    UPDATE users SET password_hash = '<OLD_HASH_VALUE>', must_change_password = FALSE WHERE id = 3 AND username = 'librarian2';
    ```
* **Lệnh biên dịch và chạy cứu hộ (Chỉ thực hiện khi có sự cố khẩn cấp và có phê duyệt)**:
  1. Biên dịch tiện ích:
     ```cmd
     javac -encoding UTF-8 -cp "dist\server-release\server.jar;dist\server-release\lib\*" -d scratch\classes scratch\RecoverLibrarian2Utility.java
     ```
  2. Chạy ở chế độ kiểm tra trước (Dry-Run):
     ```cmd
     java -Ddb.url="jdbc:mysql://localhost:3306/school_library" -Ddb.username="thuvien_app" -Ddb.password="admin_pass" -cp "scratch\classes;dist\server-release\server.jar;dist\server-release\lib\*" thuvien.scratch.RecoverLibrarian2Utility --target-catalog school_library
     ```
  3. Thực thi áp dụng khi đã kiểm tra an toàn:
     ```cmd
     java -Ddb.url="jdbc:mysql://localhost:3306/school_library" -Ddb.username="thuvien_app" -Ddb.password="admin_pass" -cp "scratch\classes;dist\server-release\server.jar;dist\server-release\lib\*" thuvien.scratch.RecoverLibrarian2Utility --target-catalog school_library --apply
     ```

### 3.6. Dừng Máy chủ an toàn (Graceful Shutdown)
1. **Cách thông thường**: Tại cửa sổ console đang chạy `run-server.bat`, nhấn tổ hợp phím **`Ctrl + C`**. Khi hệ thống hỏi `Terminate batch job (Y/N)?`, nhấn **`Y`** và **`Enter`**. Tiến trình JVM sẽ thực thi shutdown hook, giải phóng tài nguyên mạng và đóng pool kết nối HikariCP một cách an toàn.
2. **Xử lý khi cửa sổ chạy nền bị khóa file (File Lock Detection)**:
   Nếu cần khởi động lại hoặc đóng gói lại mã nguồn mà gặp lỗi `Unable to delete file ... HikariCP-4.0.3.jar`, thực hiện xác minh chính xác PID của server:
   ```powershell
   Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*LibraryRMIServer*" } | Select-Object ProcessId, Name, CommandLine
   ```
   Dừng chính xác tiến trình server bằng PID đã tìm thấy (thay `10008` bằng PID thực tế):
   ```powershell
   Stop-Process -Id 10008
   ```
   > [!WARNING]
   > Không chạy `taskkill /f /im java.exe` để tránh làm tắt nhầm các ứng dụng Java khác đang hoạt động trên hệ điều hành.

### 3.7. Xử lý sự cố trùng cổng (Port Conflict Resolution)
Nếu khi khởi động server thông báo lỗi `java.rmi.server.ExportException: Port already in use: 1099`:
1. Kiểm tra tiến trình nào đang chiếm cổng:
   ```cmd
   netstat -ano | findstr :1099
   ```
2. Nếu cổng bị chiếm bởi một phiên bản cũ của server bị treo, hãy kết thúc tiến trình đó qua PID ở cột cuối cùng:
   ```cmd
   taskkill /PID <PID> /F
   ```
3. Khởi động lại `run-server.bat`.

---

## 4. AN TOÀN TRIỂN KHAI VÀ BẢO MẬT (DEPLOYMENT SECURITY HARDENING)

### 4.1. Đánh giá Rủi ro Kênh truyền RMI và Cách ly Mạng (Network Transmission & TLS Status)
> [!WARNING]
> **Hiện trạng Kênh truyền RMI**: Hệ thống hiện tại sử dụng Java RMI thuần qua giao thức TCP không mã hóa (Plaintext TCP Sockets trên cổng `1099/TCP` cho RMI Registry và `1100/TCP` cho Remote Object Service).
> - **Rủi ro vận hành**: Dữ liệu truyền tải (bao gồm session token UUID, thông tin bạn đọc và mật khẩu trong payload đăng nhập/đổi mật khẩu) không được mã hóa TLS trên đường truyền mạng. Kẻ tấn công nằm trong cùng mạng Wi-Fi không an toàn hoặc cùng broadcast domain có thể thực hiện tấn công bắt gói tin (packet sniffing qua Wireshark/tcpdump) để chiếm đoạt token phiên làm việc.
> - **Biện pháp cô lập mạng bắt buộc khi vận hành**:
>   1. **Phân vùng mạng (Network Segmentation / Private Library VLAN)**: Bắt buộc đặt máy chủ thư viện và các máy trạm thủ thư trong phân vùng mạng VLAN riêng. Cấm hoàn toàn định tuyến truy cập từ mạng Wi-Fi công cộng/sinh viên trực tiếp vào cổng 1099 và 1100.
>   2. **Truy cập Liên chi nhánh / Làm việc từ xa**: Bắt buộc thiết lập đường hầm mạng riêng ảo VPN có mã hóa (IPsec / WireGuard / OpenVPN) trước khi khởi động Swing Client để kết nối về máy chủ.
>   3. **Tuyệt đối KHÔNG mở cổng ra Internet**: Cổng 1099 và 1100 chỉ phục vụ mạng nội bộ, tuyệt đối không cấu hình NAT hoặc Port Forwarding ra ngoài Internet công cộng.
>   4. **Lộ trình nâng cấp**: Đề xuất tích hợp `SslRMIClientSocketFactory` và `SslRMIServerSocketFactory` đi kèm chứng chỉ số Java Keystore (`.jks`) trong phiên bản tiếp theo.

### 4.2. Bảng khuyến nghị cấu hình bảo mật hệ thống
| Hạng mục | Khuyến nghị môi trường Phòng Lab / Đồ án | Khuyến nghị môi trường Sản xuất (Production) |
|:---|:---|:---|
| **Kênh truyền RMI** | Plaintext TCP Socket qua mạng LAN cục bộ | Bắt buộc chạy trong VLAN nội bộ riêng biệt hoặc qua đường hầm VPN có mã hóa |
| **Tài khoản Database** | Dùng tài khoản `root` (Laragon mặc định) | Bắt buộc tạo tài khoản riêng `thuvien_app`, mật khẩu mạnh, chỉ cấp `SELECT, INSERT, UPDATE, DELETE` |
| **Bộ lọc Serialization** | JEP 290 Whitelist mặc định (`DEFAULT_SERIAL_FILTER`) | Giữ nguyên danh sách trắng, từ chối mọi đối tượng nằm ngoài `thuvien.common` |
| **Giới hạn kết nối** | Pool tối đa 10 connections (HikariCP) | Điều chỉnh theo lưu lượng đồng thời (khuyến nghị 20-30 cho trường 5.000 sinh viên) |
| **Bảo mật bí mật** | Không đưa file `db.properties` vào Git | Lưu `db.properties` với quyền đọc chỉ dành riêng cho tài khoản chạy dịch vụ server |

---

## 5. BẢN PHÂN PHỐI MÁY TRẠM KHÔNG CẦN CÀI ĐẶT (ZERO-INSTALL CLIENT)

1. Sao chép toàn bộ thư mục `dist/client-dist` sang máy trạm (hoặc giải nén tệp `client-dist.zip`).
2. Chạy `test-connection.bat <IP_SERVER>` để kiểm tra kết nối mạng.
3. Chạy `run-client.bat <IP_SERVER> 1099` để vào ngay màn hình đăng nhập.
4. Mọi tài nguyên giao diện (ảnh bìa `library_hero.jpg`, biểu tượng vector, phông chữ Segoe UI) đã được đóng gói trực tiếp vào `client.jar`, không phụ thuộc vào bất kỳ đường dẫn nào của máy phát triển.
