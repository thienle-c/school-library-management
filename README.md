# Hệ thống quản lý thư viện trường học

Ứng dụng quản lý thư viện theo mô hình client–server phân tán, xây dựng bằng Java Swing, Java RMI và MySQL. Dự án phục vụ mục đích học tập môn Lập trình mạng máy tính, đồng thời minh họa cách phân tách giao diện người dùng, giao tiếp mạng qua Remote Method Invocation và xử lý nghiệp vụ, giao dịch an toàn phía máy chủ.

---

## Chức năng chính

- **Xác thực & Bảo mật tài khoản**: Đăng nhập, đổi mật khẩu, cơ chế khóa tài khoản tạm thời khi đăng nhập sai nhiều lần, và quản lý phiên làm việc tập trung phía máy chủ.
- **Phân quyền theo vai trò (RBAC)**: Phân quyền chặt chẽ trên máy chủ cho các vai trò `ADMIN`, `LIBRARIAN` và `STUDENT`. Giao diện tự động điều chỉnh quyền truy cập nhưng máy chủ luôn là nơi thẩm quyền quyết định cuối cùng.
- **Quản lý danh mục & Bản sao**: Quản lý sách, thể loại, tác giả, vị trí kệ sách và số lượng bản sao khả dụng.
- **Quản lý bạn đọc**: Hồ sơ sinh viên, hạn mức mượn sách, trạng thái tài khoản (hoạt động/đình chỉ), và tự đăng ký tài khoản qua mã kích hoạt.
- **Lưu thông sách (Mượn – Trả – Đặt trước)**:
  - Thao tác mượn sách sử dụng giao dịch cơ sở dữ liệu nguyên tử (atomic transaction) và khóa dòng nhằm ngăn chặn tranh chấp khi nhiều người cùng mượn bản sao cuối cùng.
  - Quy trình trả sách tự động khôi phục số lượng bản sao và tính tiền phạt quá hạn theo biểu phí cấu hình.
  - Hỗ trợ sinh viên đặt trước sách đã hết bản sao sẵn sàng.
- **Tìm kiếm & Gợi ý**: Tìm kiếm danh mục sách theo nhiều tiêu chí, gợi ý tìm kiếm tiền tố bất đồng bộ không gây nghẽn giao diện (EDT).
- **Nhật ký kiểm toán (Audit Logging)**: Ghi nhận lịch sử các sự kiện hệ thống quan trọng mà không lưu trữ thông tin nhạy cảm.

---

## Công nghệ sử dụng

| Thành phần | Công nghệ / Thư viện | Ghi chú kỹ thuật |
|---|---|---|
| **Ngôn ngữ & Runtime** | Java 8 (OpenJDK 1.8 / Amazon Corretto 8) | Tương thích Java SE 1.8 |
| **Giao diện người dùng** | Java Swing (`javax.swing.*`, `java.awt.*`) | Chạy trên Event Dispatch Thread (EDT) |
| **Giao tiếp Client–Server** | Java RMI, Java Object Serialization | Cổng Registry `1099`, Cổng Remote Service `1100` |
| **Cơ sở dữ liệu** | MySQL 8.0+ (InnoDB Engine) | Hỗ trợ giao dịch ACID và khóa dòng |
| **Quản lý kết nối DB** | HikariCP 4.0.3 | Connection pooling hiệu năng cao phía server |
| **Hệ thống Build** | Apache Ant | Tương thích Ant 1.9+ đi kèm NetBeans |
| **IDE tương thích** | Apache NetBeans 8.x / NetBeans 12.x+ | Giữ nguyên metadata `nbproject/` |
| **Kiểm thử tự động** | JUnit 4 (4.13.2) | 17 test suites, 160 ca kiểm thử tự động |

---

## Kiến trúc hệ thống

Hệ thống được thiết kế theo kiến trúc 3 tầng phân tán nghiêm ngặt:

```text
+--------------------------------------------------------------+
|                   Java Swing Desktop Client                  |
|  - Giao diện người dùng (JFrame, JDialog, JPanel)             |
|  - Xử lý bất đồng bộ (SwingWorker - bảo vệ EDT)              |
|  - RMIClient Stub (Hoàn toàn KHÔNG chứa mã SQL hay JDBC)      |
+--------------------------------------------------------------+
                               |
                               | Java RMI (TCP Ports 1099 & 1100)
                               v
+--------------------------------------------------------------+
|                     Java Library Server                      |
|  - RMI Registry (1099) & Remote Service Object (1100)        |
|  - RequestRouter: Kiểm tra token phiên & phân quyền RBAC     |
|  - Service Layer: Nghiệp vụ mượn/trả, mật khẩu, kiểm toán    |
|  - Repository Layer: Truy vấn SQL an toàn (PreparedStatement)|
|  - DatabaseManager: Quản lý kết nối HikariCP                 |
+--------------------------------------------------------------+
                               |
                               | Native JDBC Connection Pool
                               v
+--------------------------------------------------------------+
|                   MySQL Database (InnoDB)                    |
|  - 11 bảng quan hệ, khóa ngoại, chỉ mục B-Tree tối ưu        |
+--------------------------------------------------------------+
```

### Nguyên tắc phân tách module:
1. **Client tuyệt đối không kết nối trực tiếp vào MySQL**: Toàn bộ thao tác đều phải thông qua giao diện từ xa `LibraryRemoteService`. Gói client không chứa JDBC driver hay thông tin kết nối CSDL.
2. **Cố định cổng RMI Service (1100)**: Giúp cấu hình luật tường lửa (Firewall) chính xác trên mạng LAN mà không bị phân bổ cổng ngẫu nhiên.

---

## Cấu trúc thư mục

| Thư mục / Tệp tin | Vai trò và nội dung |
|---|---|
| `common/` | Các lớp dùng chung giữa client và server: DTO, enums, exception và interface RMI |
| `server/` | Máy chủ RMI, tầng nghiệp vụ, bảo mật, repository và quản lý kết nối CSDL |
| `client/` | Ứng dụng giao diện Swing, controller giao diện và client adapter RMI |
| `database/` | DDL schema (`schema.sql`), chỉ mục (`indexes.sql`), dữ liệu mẫu (`seed.sql`) và migration |
| `docs/` | Bộ tài liệu hướng dẫn kỹ thuật cốt lõi (kiến trúc, API, cài đặt, triển khai, mạng) |
| `docs/archive/` | Thư mục lưu trữ các báo cáo kiểm toán kỹ thuật từ các giai đoạn trước |
| `scripts/` | Script hỗ trợ chẩn đoán cổng mạng và kiểm tra kết nối RMI |
| `lib/` | Các thư viện runtime và test JAR phụ thuộc |
| `test/` | Bộ kiểm thử tự động JUnit 4 kiểm chứng giao thức, nghiệp vụ và bảo mật |
| `build.xml` | Kịch bản build Ant quản lý biên dịch, chạy, kiểm thử và đóng gói phân phối |
| `test-connection.bat` | Script hỗ trợ kiểm tra thông cổng 1099 và 1100 |

---

## Yêu cầu môi trường

- **Hệ điều hành**: Windows 10 / 11 (hoặc Linux / macOS tương đương).
- **Java Development Kit**: JDK 8 (OpenJDK 1.8 hoặc Amazon Corretto 8). Kiểm tra bằng `java -version` và `javac -version`.
- **Hệ thống Build**: Apache Ant (có thể sử dụng Ant tích hợp sẵn trong NetBeans).
- **Cơ sở dữ liệu**: MySQL 8.0+ (khuyến nghị chạy trên Laragon hoặc MySQL Community Server độc lập).
- **Môi trường phát triển**: Apache NetBeans 8.x trở lên nếu muốn làm việc trực tiếp bằng IDE.

---

## Cài đặt cơ sở dữ liệu

1. Khởi động dịch vụ MySQL (đảm bảo đang lắng nghe trên cổng mặc định `3306`).
2. Mở cửa sổ dòng lệnh MySQL hoặc công cụ quản trị (HeidiSQL, MySQL Workbench, DBeaver) và chạy các script theo thứ tự:
   ```sql
   CREATE DATABASE IF NOT EXISTS school_library CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE school_library;

   SOURCE database/schema.sql;
   SOURCE database/indexes.sql;
   SOURCE database/seed.sql;
   ```
3. Nếu thiết lập môi trường kiểm thử riêng biệt, tạo database `school_library_test` tương tự để các bài unit test tự động thực thi an toàn mà không ảnh hưởng dữ liệu chính.

Chi tiết về cấu trúc bảng và ràng buộc toàn vẹn xem tại [docs/DATABASE.md](docs/DATABASE.md).

---

## Cấu hình kết nối

### 1. Cấu hình máy chủ (`server/resources/`)
- **Cấu hình RMI (`server.properties`)**:
  ```properties
  rmi.registry.port=1099
  rmi.service.port=1100
  rmi.service.name=LibraryRemoteService
  ```
- **Cấu hình CSDL (`db.properties`)**:
  Sao chép từ tệp mẫu `db.properties.example` thành `db.properties` và điều chỉnh thông tin phù hợp:
  ```properties
  db.driver=com.mysql.cj.jdbc.Driver
  db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
  db.username=your_db_username
  db.password=your_db_password
  ```
  *(Lưu ý: Tệp `db.properties` cục bộ được `.gitignore` bảo vệ, không bao giờ được commit lên kho mã nguồn).*

### 2. Cấu hình máy khách (`client/resources/client.properties`)
```properties
rmi.server.host=127.0.0.1
rmi.registry.port=1099
rmi.service.name=LibraryRemoteService
```

---

## Biên dịch, Kiểm thử và Chạy ứng dụng

Nếu máy chưa cài đặt Ant độc lập trên `PATH`, bạn có thể gọi trực tiếp binary Ant đi kèm NetBeans:
`"<ĐƯỜNG_DẪN_NETBEANS>\netbeans\extide\ant\bin\ant.bat"`

### 1. Biên dịch và Đóng gói
Từ thư mục gốc dự án:
```bash
# Dọn dẹp và biên dịch mã nguồn
ant clean compile

# Chạy toàn bộ 160 ca kiểm thử tự động
ant test

# Đóng gói phân phối hoàn chỉnh
ant package-all
```

Sau lệnh `package-all`, các gói phân phối độc lập sẽ được tạo trong thư mục `dist/`:
- `dist/client-dist/`: Gói máy khách (chỉ chứa `client.jar`, `run-client.bat`, cấu hình, không chứa mã server hay JDBC).
- `dist/server-release/`: Gói phát hành máy chủ (chứa `server.jar`, thư viện runtime, script và file mẫu cấu hình).
- `dist/server-dist/`: Gói máy chủ dành cho phát triển cục bộ.

### 2. Khởi chạy Máy chủ (Server)
Chọn một trong các cách sau:
- **Cách 1 (Dùng Ant)**:
  ```bash
  ant run-server
  ```
- **Cách 2 (Dùng script đóng gói)**:
  Truy cập `dist\server-dist\` hoặc `dist\server-release\` và chạy:
  ```bat
  run-server.bat
  ```
- **Cách 3 (Trong NetBeans)**:
  Nhấn phím **F6** (Run Project) hoặc chuột phải vào [`server/src/thuvien/server/rmi/LibraryRMIServer.java`](server/src/thuvien/server/rmi/LibraryRMIServer.java) chọn **Run File** (Shift+F6).

Khi máy chủ khởi động thành công, màn hình console sẽ hiển thị thông báo lắng nghe trên cổng 1099/1100 và danh sách địa chỉ IP LAN khả dụng.

### 3. Khởi chạy Máy khách (Client)
Chọn một trong các cách sau:
- **Cách 1 (Dùng Ant)**:
  ```bash
  ant run-client
  ```
- **Cách 2 (Dùng script đóng gói)**:
  Truy cập `dist\client-dist\` và chạy:
  ```bat
  run-client.bat
  ```
  *(Có thể truyền IP và Port máy chủ trực tiếp qua tham số: `run-client.bat 192.168.1.50 1099`)*
- **Cách 3 (Trong NetBeans)**:
  Mở [`client/src/thuvien/client/ClientMain.java`](client/src/thuvien/client/ClientMain.java), chuột phải chọn **Run File** (Shift+F6).

---

## Chạy ứng dụng qua mạng LAN / Wi-Fi

Mô hình này cho phép máy khách (laptop 2) kết nối không dây tới máy chủ (laptop 1) chạy Server và MySQL:

1. **Cấu hình trên Máy chủ**:
   - Kết nối vào cùng mạng Wi-Fi/LAN với máy khách.
   - Mở terminal chạy `ipconfig` để lấy địa chỉ IPv4 (ví dụ: `192.168.1.50`).
   - Mở cổng tường lửa Windows cho hai cổng RMI bằng PowerShell (Run as Administrator):
     ```powershell
     New-NetFirewallRule -DisplayName "School Library RMI Registry" -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow -Profile Private
     New-NetFirewallRule -DisplayName "School Library RMI Service" -Direction Inbound -LocalPort 1100 -Protocol TCP -Action Allow -Profile Private
     ```
   - Khởi động máy chủ RMI và kiểm tra console xác nhận địa chỉ IP LAN đã được nhận diện.
2. **Cấu hình trên Máy khách**:
   - Sao chép thư mục `dist/client-dist/` sang máy khách.
   - Chạy kiểm tra thông cổng bằng script chẩn đoán:
     ```bat
     test-connection.bat 192.168.1.50
     ```
   - Khởi chạy ứng dụng máy khách:
     ```bat
     run-client.bat 192.168.1.50 1099
     ```
   - Trên màn hình đăng nhập Swing, bấm **"Kiểm tra kết nối"** -> nhận thông báo xanh `"Server connected"` trước khi tiến hành đăng nhập.

---

## Các lỗi thường gặp và cách xử lý

| Tình huống lỗi | Nguyên nhân khả dĩ | Giải pháp xử lý |
|---|---|---|
| **Cổng 1099 báo Fail khi kiểm tra mạng** | Tường lửa Windows trên máy chủ đang chặn cổng hoặc Server chưa bật. | Kiểm tra máy chủ đã khởi động; chạy lệnh mở cổng 1099 trên máy chủ theo hướng dẫn mục LAN. |
| **Cổng 1099 thông nhưng cổng 1100 bị chặn** | Cổng remote service 1100 chưa được cho phép qua tường lửa. | Mở tiếp cổng 1100 trên tường lửa máy chủ với profile Private. |
| **Server báo `[DATABASE CONFIG ERROR]`** | Catalog CSDL kết nối thực tế không khớp với cấu hình bảo vệ Catalog Guard. | Kiểm tra lại tên database trong chuỗi URL tại `server/resources/db.properties`. |
| **Lỗi `CommunicationsException` tới MySQL** | Dịch vụ MySQL chưa bật hoặc cổng 3306 bị chiếm dụng. | Khởi động lại dịch vụ MySQL trong Laragon / Windows Services và kiểm tra lại cổng kết nối. |
| **Máy khách luôn kết nối về `127.0.0.1`** | Chưa truyền IP LAN của máy chủ vào tham số dòng lệnh hoặc trên form đăng nhập. | Nhập trực tiếp IP máy chủ vào ô "Server IP" trên màn hình đăng nhập hoặc chạy `run-client.bat <IP_SERVER> 1099`. |
| **Lỗi `Resources\db.properties not found`** | Chưa tạo file cấu hình kết nối CSDL cho bản release máy chủ. | Sao chép `resources\db.properties.example` thành `resources\db.properties` và điền thông tin kết nối. |
| **Lệnh `ant` báo không tìm thấy** | Apache Ant chưa được thêm vào biến môi trường `PATH`. | Dùng đường dẫn đầy đủ đến file `ant.bat` trong thư mục cài đặt NetBeans (`extide\ant\bin\ant.bat`). |

---

## Tài liệu tham khảo chi tiết

Các tài liệu kỹ thuật chuyên sâu được tổ chức trong thư mục `docs/`:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): Kiến trúc phân tầng 3-tier, chi tiết luồng xử lý và phân quyền RBAC.
- [docs/API.md](docs/API.md): Danh mục Actions, cấu trúc Request/Response envelope và mã trạng thái.
- [docs/DATABASE.md](docs/DATABASE.md): Thiết kế cơ sở dữ liệu chi tiết, từ điển dữ liệu 11 bảng và chỉ mục.
- [docs/INSTALLATION.md](docs/INSTALLATION.md): Hướng dẫn chi tiết môi trường, NetBeans và cấu hình đóng gói.
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md): Runbook triển khai máy chủ và quy trình migration MySQL 8 an toàn.
- [docs/BACKUP-RESTORE.md](docs/BACKUP-RESTORE.md): Hướng dẫn quy trình sao lưu và phục hồi CSDL.
- [docs/NETWORK.md](docs/NETWORK.md): Hướng dẫn giao thức mạng, cấu hình IP đa card mạng và kiểm tra kết nối.
- [docs/PERFORMANCE.md](docs/PERFORMANCE.md): Tiêu chuẩn hiệu năng, bảo vệ Swing EDT và kết quả đo tải.
- [docs/STAGING-HANDOFF-CHECKLIST.md](docs/STAGING-HANDOFF-CHECKLIST.md): Danh mục tiêu chí nghiệm thu và bàn giao hạ tầng.
- [docs/archive/README.md](docs/archive/README.md): Thư mục lưu trữ các báo cáo kiểm toán lịch sử và giai đoạn trước.

---

## Phạm vi học tập và Lưu ý bảo mật

- **Mục đích học tập**: Dự án được xây dựng phục vụ môn học Lập trình mạng máy tính và Công nghệ Java, minh họa kỹ thuật gọi thủ tục từ xa (RMI) và kiểm soát tranh chấp đồng thời (Concurrency Control).
- **Kênh truyền mạng**: Kênh truyền Java RMI hiện tại hoạt động dưới dạng Plaintext TCP (chưa tích hợp TLS/SSL). Do đó, ứng dụng chỉ được khuyến nghị vận hành và thử nghiệm trong mạng LAN nội bộ hoặc mạng riêng ảo (VPN) tin cậy; không mở cổng trực tiếp ra Internet công cộng.
- **Bảo vệ thông tin xác thực**: Không bao giờ sử dụng mật khẩu thật trong môi trường demo hoặc đẩy các tệp cấu hình chứa secret lên kho lưu trữ công khai.

---

## Giấy phép

Dự án được phân phối theo giấy phép MIT. Xem chi tiết tại [LICENSE](LICENSE).
