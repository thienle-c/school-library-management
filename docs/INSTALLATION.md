# HƯỚNG DẪN CÀI ĐẶT VÀ CẤU HÌNH HỆ THỐNG (INSTALLATION GUIDE)

> **Hệ thống**: Remote School Library Management System  
> **Kiến trúc**: 3-Tier Distributed System (Java Swing Client — Java RMI — MySQL Database)  
> **Môi trường mục tiêu**: Windows 10/11, Windows Server, Linux (Ubuntu/Debian/CentOS)  
> **Phiên bản tài liệu**: 2.0 (Release-Ready)  

---

## 1. YÊU CẦU TIỀN ĐỀ HỆ THỐNG (SYSTEM PREREQUISITES)

### 1.1. Máy chủ (Library Server Machine)
- **Hệ điều hành**: Windows 10/11, Windows Server 2016+, hoặc Linux 64-bit.
- **Java Runtime**: OpenJDK 8 / Amazon Corretto 8 / Oracle JDK 8 (chính xác phiên bản 1.8.x).
  - Kiểm tra bằng lệnh: `java -version`
  - Đảm bảo `java` đã được thêm vào biến môi trường `PATH`.
- **Cơ sở dữ liệu**: MySQL Server 8.0+ (hoặc MariaDB 10.4+ / Laragon 5.0+ / XAMPP).
- **Mạng (Network)**: Cần có địa chỉ IPv4 tĩnh hoặc cố định trong mạng LAN (ví dụ: `192.168.1.100`).
- **Cổng mạng (Ports)**:
  - `1099/TCP`: RMI Registry.
  - `1100/TCP`: RMI Remote Object Service (`LibraryRemoteService`).
  - `3306/TCP`: MySQL Database (chỉ cần cục bộ trên máy chủ).

### 1.2. Máy trạm (Library Client Machine)
- **Hệ điều hành**: Bất kỳ máy trạm Windows hoặc Linux nào có màn hình đồ họa.
- **Java Runtime**: Java 8 (JRE/JDK 1.8.x).
- **Mạng**: Kết nối được tới địa chỉ IP của máy chủ qua mạng LAN/Wi-Fi.
- **Database Driver**: **KHÔNG YÊU CẦU** (Client độc lập 100%, không cần MySQL hay driver JDBC).

---

## 2. CHUẨN BỊ CƠ SỞ DỮ LIỆU MYSQL TRÊN MÁY CHỦ

### Bước 2.1: Khởi động MySQL Server
Đảm bảo dịch vụ MySQL đang chạy (qua dịch vụ Windows Services, Laragon hoặc `systemctl start mysql`).

### Bước 2.2: Tạo Database và Phân quyền tài khoản
Mở MySQL Command Line Client hoặc công cụ quản trị (HeidiSQL, DBeaver, phpMyAdmin) và thực thi script SQL sau:

```sql
-- 1. Tạo cơ sở dữ liệu với bảng mã utf8mb4 chuẩn Unicode
CREATE DATABASE IF NOT EXISTS school_library 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- 2. Tạo người dùng chuyên dụng cho ứng dụng (áp dụng nguyên tắc đặc quyền tối thiểu)
-- THAY 'YourStrongPassword123!' BẰNG MẬT KHẨU THỰC TẾ CỦA BẠN
CREATE USER IF NOT EXISTS 'thuvien_app'@'localhost' IDENTIFIED BY 'YourStrongPassword123!';

-- 3. Cấp đặc quyền đọc/ghi nghiệp vụ trên database school_library
GRANT SELECT, INSERT, UPDATE, DELETE ON school_library.* TO 'thuvien_app'@'localhost';
FLUSH PRIVILEGES;
```

> [!TIP]
> Trong môi trường thử nghiệm / thực hành đồ án cục bộ, bạn có thể tạm thời sử dụng tài khoản `root` mặc định của Laragon/XAMPP (`username: root`, `password: ` rỗng). Tuy nhiên, khi cài đặt chính thức, hãy sử dụng tài khoản riêng biệt như trên.

### Bước 2.3: Khởi tạo Schema và Dữ liệu mẫu
Thực thi tuần tự 3 tệp SQL có sẵn trong thư mục `database/`:

1. **`database/schema.sql`**: Tạo toàn bộ 11 bảng quan hệ (Users, Students, Books, Categories, Authors, Borrow_Records, Fines, Reservations, Audit_Logs, v.v.).
2. **`database/indexes.sql`**: Tạo các chỉ mục tối ưu tìm kiếm B-Tree trên ISBN, Title, Student Code, Status.
3. **`database/seed.sql`**: Nạp dữ liệu mẫu ban đầu (tài khoản demo, các thể loại, tác giả và sách kinh điển).

Thực thi bằng dòng lệnh:
```bash
# Windows (thay đường dẫn mysql.exe tương ứng)
mysql -u root -p school_library < database/schema.sql
mysql -u root -p school_library < database/indexes.sql
mysql -u root -p school_library < database/seed.sql
```

---

## 3. CÀI ĐẶT VÀ KHỞI CHẠY BẢN PHÂN PHỐI SERVER

Bản phân phối máy chủ nằm tại thư mục `dist/server-dist` (hoặc tệp đóng gói `server-dist.zip`).

### Cấu trúc thư mục Server Distribution:
```text
dist/server-dist/
├── server.jar                  # Ứng dụng Server RMI đã đóng gói
├── run-server.bat              # Script khởi chạy một chạm cho Windows
├── lib/                        # Các thư viện phụ thuộc bắt buộc
│   ├── HikariCP-4.0.3.jar
│   ├── mysql-connector-java-8.0.33.jar
│   ├── slf4j-api-1.7.36.jar
│   └── slf4j-jdk14-1.7.36.jar
└── resources/                  # Cấu hình bên ngoài (Externalized Config)
    ├── db.properties.example   # Mẫu cấu hình database an toàn
    ├── db.properties           # Tệp cấu hình thực tế (tạo từ mẫu)
    └── server.properties       # Cấu hình cổng mạng RMI
```

### Bước 3.1: Cấu hình kết nối Database
1. Điều hướng vào thư mục `resources/`.
2. Nếu chưa có tệp `db.properties`, hãy sao chép từ `db.properties.example`:
   ```bash
   copy db.properties.example db.properties
   ```
3. Mở `db.properties` bằng bất kỳ trình soạn thảo nào (Notepad, VS Code) và chỉnh sửa thông tin phù hợp với MySQL của bạn:
   ```properties
   # Cấu hình kết nối MySQL
   db.driver=com.mysql.cj.jdbc.Driver
   db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
   db.username=thuvien_app
   db.password=YourStrongPassword123!

   # Cấu hình HikariCP Connection Pool
   db.pool.name=LibraryServerPool
   db.pool.maxSize=10
   db.pool.minIdle=2
   db.pool.idleTimeout=30000
   db.pool.connectionTimeout=10000
   db.pool.maxLifetime=1800000
   ```

### Bước 3.2: Cấu hình cổng mạng RMI (Tùy chọn)
Mở tệp `resources/server.properties`:
```properties
rmi.registry.port=1099
rmi.service.port=1100
rmi.service.name=LibraryRemoteService
server.thread.pool.size=20
```

### Bước 3.3: Khởi chạy Server
Nhấp đúp chuột vào `run-server.bat` hoặc chạy từ cửa sổ dòng lệnh:
```cmd
cd /d D:\workspace\thuvien\dist\server-dist
run-server.bat
```

**Dấu hiệu khởi động thành công trên màn hình console**:
```text
==================================================
LibraryRMIServer started successfully
RMI Registry port: 1099
RMI Remote Object port: 1100
RMI hostname: 192.168.1.100
Service: LibraryRemoteService
Available IPv4 addresses for LAN clients:
 - 192.168.1.100 (Ethernet)
==================================================
```

---

## 4. CÀI ĐẶT VÀ KHỞI CHẠY BẢN PHÂN PHỐI CLIENT

Bản phân phối máy trạm nằm tại thư mục `dist/client-dist` (hoặc tệp đóng gói `client-dist.zip`).

### Cấu trúc thư mục Client Distribution:
```text
dist/client-dist/
├── client.jar                  # Ứng dụng Desktop Swing Client độc lập
├── run-client.bat              # Script khởi chạy Client thông minh
├── test-connection.bat         # Công cụ kiểm tra kết nối mạng tới Server
└── resources/
    └── client.properties       # Cấu hình địa chỉ IP máy chủ
```

### Bước 4.1: Kiểm tra kết nối mạng tới Server
Trước khi khởi chạy Client, chạy tệp `test-connection.bat` để xác minh thông mạng:
```cmd
test-connection.bat 192.168.1.100
```
- Nếu hiển thị `[OK] CONG 1099 KET NOI TOT` và `[OK] CONG 1100 KET NOI TOT`: Mạng hoàn toàn thông suốt, có thể khởi động Client.
- Nếu hiển thị `[FAIL]`: Kiểm tra lại Windows Firewall trên máy Server (xem Mục 6).

### Bước 4.2: Khởi chạy ứng dụng Client
Có thể khởi chạy Client bằng một trong các cách sau:

1. **Cách 1: Nhấp đúp chuột trực tiếp vào `run-client.bat`**  
   Ứng dụng sẽ tự động nạp địa chỉ IP từ tệp `resources/client.properties`.
2. **Cách 2: Truyền trực tiếp IP máy chủ qua dòng lệnh**  
   ```cmd
   run-client.bat 192.168.1.100
   ```
3. **Cách 3: Chỉnh sửa trực tiếp tệp `resources/client.properties`**  
   ```properties
   rmi.server.host=192.168.1.100
   rmi.registry.port=1099
   rmi.service.name=LibraryRemoteService
   network.timeout=10000
   ```

---

## 5. TÀI KHOẢN ĐĂNG NHẬP MẪU BAN ĐẦU (SEED ACCOUNTS)

Sau khi khởi chạy Client, giao diện đăng nhập sẽ xuất hiện. Bạn có thể sử dụng các tài khoản demo đã được nạp sẵn:

| Vai trò | Tên đăng nhập | Mật khẩu mẫu | Chức năng chính |
|:---|:---|:---|:---|
| **ADMIN** (Quản trị viên) | `admin` | `admin123` | Quản lý người dùng, tạo tài khoản thủ thư, xem toàn bộ nhật ký hệ thống, thống kê |
| **LIBRARIAN** (Thủ thư) | `librarian1` | `lib123` | Quản lý kho sách, lập phiếu mượn/trả sách, thu tiền phạt, đặt trước |
| **STUDENT** (Sinh viên) | `student1` | `student123` | Tra cứu sách, xem hồ sơ cá nhân, xem lịch sử mượn trả và tiền phạt cá nhân |

> [!IMPORTANT]
> Khi người dùng đăng nhập lần đầu bằng mật khẩu SHA-256 cũ, hệ thống sẽ tự động nâng cấp mã băm sang `PBKDF2WithHmacSHA256` trong database mà không cần người dùng thao tác gì thêm.

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

### Sự cố 1: Lỗi "Java Runtime Environment (java) was not found in PATH"
- **Nguyên nhân**: Máy tính chưa cài Java hoặc chưa cấu hình biến môi trường `PATH`.
- **Cách khắc phục**:
  1. Tải và cài đặt Java 8 (OpenJDK 8 hoặc Amazon Corretto 8).
  2. Mở *System Properties* -> *Environment Variables*.
  3. Thêm đường dẫn thư mục `bin` của Java (ví dụ: `C:\Program Files\Amazon Corretto\jdk1.8.0_xxx\bin`) vào biến `PATH`.

### Sự cố 2: Lỗi "Cannot connect to server" hoặc "Connection refused"
- **Nguyên nhân**: Máy chủ chưa bật, cấu hình sai IP, hoặc Windows Firewall trên máy chủ đang chặn cổng 1099/1100.
- **Cách khắc phục**:
  1. Kiểm tra cửa sổ Server trên máy chủ xem trạng thái có báo *LibraryRMIServer started successfully* hay không.
  2. Mở cổng Firewall trên máy chủ (chạy PowerShell với quyền Administrator):
     ```powershell
     New-NetFirewallRule -DisplayName "Remote School Library RMI" -Direction Inbound -Protocol TCP -LocalPort 1099,1100 -Action Allow
     ```
  3. Sử dụng lệnh `ping <IP_SERVER>` và chạy `test-connection.bat <IP_SERVER>` từ máy trạm.

### Sự cố 3: Lỗi "Communications link failure" hoặc "Access denied for user"
- **Nguyên nhân**: MySQL Server chưa bật hoặc thông tin đăng nhập trong `resources/db.properties` không chính xác.
- **Cách khắc phục**:
  1. Kiểm tra dịch vụ MySQL trên máy chủ đã Start hay chưa.
  2. Mở tệp `dist/server-dist/resources/db.properties` và kiểm tra lại `db.url`, `db.username`, `db.password`.
