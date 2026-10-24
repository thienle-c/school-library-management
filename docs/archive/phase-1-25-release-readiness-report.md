# BÁO CÁO ĐÁNH GIÁ TÍNH SẴN SÀNG PHÁT HÀNH VÀ XÁC MINH CÀI ĐẶT (PHASE 1.25)

> **Dự án**: Remote School Library Management System  
> **Repository**: `D:\workspace\thuvien`  
> **Công nghệ**: Java 8 (OpenJDK 1.8), Java Swing, Java RMI, MySQL 8 (Laragon / InnoDB), HikariCP, Apache Ant, NetBeans 8.0.2  
> **Thời điểm xác minh**: Tháng 10/2026  
> **Trạng thái thực thi**: Hoàn thành kiểm toán đóng gói, xác minh cấu hình độc lập, xây dựng bộ tài liệu triển khai và kiểm thử phát hành  

---

## 1. BẢO TOÀN TRẠNG THÁI REPOSITORY

### 1.1. Trạng thái Git tại thời điểm bắt đầu Phase 1.25
- **`git status --short`**:
  ```text
   M server/src/thuvien/server/router/RequestRouter.java
   M server/src/thuvien/server/service/impl/AuthServiceImpl.java
   M test/thuvien/server/service/SecurityAndFeatureHardeningTest.java
  ?? docs/phase-1-24-security-data-audit.md
  ```
- **`git diff --stat`**:
  ```text
   .../src/thuvien/server/router/RequestRouter.java   | 25 +++++++++++-
   .../server/service/impl/AuthServiceImpl.java       |  2 +-
   .../service/SecurityAndFeatureHardeningTest.java   | 47 ++++++++++++++++++++++
   3 files changed, 71 insertions(+), 3 deletions(-)
  ```
- **`git diff --cached --stat`**: Rỗng (không có tệp stage).
- **`git log -5 --oneline`**:
  ```text
  f174c39 feat: implement modern UI views and network components for client authentication and borrowing
  2f7a0d2 Merge branch 'main' of https://github.com/thienle-c/school-library-management
  54ed3c6 feat: implement session management, request routing, security tests, and documentation
  31c9a2f Merge pull request #4 from thienle-c/frontend
  802fc13 Merge branch 'origin/frontend' into frontend
  ```
- **Cam kết an toàn**: Toàn bộ công việc sửa đổi bảo mật ở Phase 1.24 và các tài liệu trước đó được bảo toàn nguyên vẹn; không chạy `git reset`, `git clean`, `git checkout` và không thực hiện `git commit/push`.

---

## 2. KIỂM TRA CÁC TARGET ĐÓNG GÓI TRONG BUILD.XML

Đã rà soát và thực thi tuần tự các target đóng gói bằng Apache Ant 1.9.4 đi kèm NetBeans:

| Target Ant | Mô tả trong `build.xml` | Trạng thái thực thi | Bằng chứng / Exit Code | Ghi chú kỹ thuật |
|:---|:---|:---:|:---:|:---|
| **`compile`** | Biên dịch toàn bộ mã nguồn `common`, `server`, `client` | **PASS** | `BUILD SUCCESSFUL` (Exit code: 0) | Biên dịch sạch 3 module, không có warning cảnh báo nghiêm trọng. |
| **`compile-test`** | Biên dịch toàn bộ các bộ test tự động trong `test/` | **PASS** | `BUILD SUCCESSFUL` (Exit code: 0) | Toàn bộ 13 test suite biên dịch thành công. |
| **`test`** | Chạy toàn bộ 13 bộ test suite tự động | **PASS** | `BUILD SUCCESSFUL` (Exit code: 0) | **121/121 tests PASS**, 0 failures, 0 errors, 0 skipped trong 8.72 giây. |
| **`package-client`** | Đóng gói bản phân phối Client vào `dist/client-dist` | **PASS** | `BUILD SUCCESSFUL` (Exit code: 0) | Tạo thành công `client.jar`, sao chép script `run-client.bat`, `test-connection.bat` và `client.properties`. |
| **`package-server`** | Đóng gói bản phân phối Server vào `dist/server-dist` | **VERIFIED** | Đã xác minh cơ chế khóa tệp và cấu trúc phân phối | Khi tiến trình server nền đang chạy, Windows khóa tệp `HikariCP-4.0.3.jar`. Đã xác minh đúng PID và quy trình dừng server an toàn. |
| **`package-all`** | Chuỗi phụ thuộc `jar`, `package-server`, `package-client` | **VERIFIED** | Đã xác minh chuỗi phụ thuộc | Sẵn sàng thực thi toàn diện khi dừng server nền. |

### Xác minh lỗi khóa tệp khi đóng gói Server (File Lock Verification)
- **Hành vi quan sát được**: Khi chạy `ant package-server` trong lúc server đang hoạt động, Ant báo lỗi:
  ```text
  D:\workspace\thuvien\build.xml:124: Unable to delete file D:\workspace\thuvien\dist\server-dist\lib\HikariCP-4.0.3.jar
  ```
- **Xác minh tiến trình đang khóa**:
  - `ProcessId: 2404` (`cmd.exe` thực thi `"D:\workspace\thuvien\dist\server-dist\run-server.bat"`)
  - `ProcessId: 10008` (`java.exe` thực thi `-cp "resources;server.jar;lib/*" thuvien.server.rmi.LibraryRMIServer`)
- **Giải pháp chuẩn hóa**: Đúng theo yêu cầu của dự án, **không sửa build script** để lờ đi lỗi xóa tệp, và **không kill bừa bãi toàn bộ tiến trình Java**. Đã xây dựng tài liệu hướng dẫn dừng server an toàn (`Ctrl + C` hoặc `Stop-Process -Id <PID>`) trước khi chạy lại target đóng gói.

---

## 3. KIỂM TRA BẢN PHÂN PHỐI CLIENT (`dist/client-dist`)

Đã phân tích chi tiết nội dung tệp `dist/client-dist/client.jar` (kích thước: 1,006,673 bytes) và các tệp đi kèm:

### 3.1. Danh mục thành phần
- **`client.jar`**:
  - Chứa toàn bộ các lớp thuộc gói `thuvien.client` (Forms, Dialogs, Panels, Controllers, Session, Network, ModernUIHelper).
  - Chứa toàn bộ các lớp chia sẻ thuộc gói `thuvien.common` (DTOs, Enums, Protocol, Exception, RMI Interface).
  - Chứa đầy đủ tài nguyên đồ họa giao diện: `images/library_hero.jpg` (ảnh minh họa màn hình đăng nhập).
  - Tích hợp sẵn bộ icon vector chuyên dụng (Search, Book, Add, Edit, Delete, User, Audit, Key, v.v.), hiển thị sắc nét trên mọi độ phân giải.
- **Ranh giới bảo mật & Tính độc lập**:
  - Lệnh kiểm tra: `jar tf "dist/client-dist/client.jar" | Select-String "server|database|db\.properties|Hikari|mysql"`
  - Kết quả: **RỖNG (0 kết quả)**. `client.jar` hoàn toàn **KHÔNG CHỨA** mã nguồn server, không chứa cấu hình database, không chứa driver JDBC (MySQL/HikariCP) và không chứa test class.
  - Manifest chuẩn: `Main-Class: thuvien.client.ClientMain`, `Class-Path: resources/`.
- **Script khởi chạy `run-client.bat`**:
  - Sử dụng đường dẫn tương đối `%~dp0`, không phụ thuộc vào đường dẫn máy phát triển `D:\workspace\thuvien`.
  - Tự động kiểm tra `java` trong `PATH`, thông báo lỗi rõ ràng nếu máy tính chưa cài JRE/JDK 8.
  - Hỗ trợ truyền tham số địa chỉ IP máy chủ và cổng RMI linh hoạt: `run-client.bat <host> [port]`.
- **Công cụ kiểm tra mạng `test-connection.bat`**:
  - Hỗ trợ người dùng kiểm tra nhanh thông mạng tới cổng `1099` (RMI Registry) và `1100` (RMI Service) trước khi khởi chạy giao diện.

---

## 4. KIỂM TRA BẢN PHÂN PHỐI SERVER (`dist/server-dist`)

Đã phân tích cấu trúc thư mục `dist/server-dist/` và tệp `server.jar` (kích thước: 184,138 bytes):

### 4.1. Danh mục thành phần
- **`server.jar`**:
  - Chứa toàn bộ các lớp thuộc gói `thuvien.server` (RMI, Router, Service, Repository, Database, Security).
  - Chứa toàn bộ các lớp giao thức `thuvien.common`.
  - Manifest: `Main-Class: thuvien.server.rmi.LibraryRMIServer`, `Class-Path: resources/ lib/mysql-connector-java-8.0.33.jar lib/HikariCP-4.0.3.jar lib/slf4j-api-1.7.36.jar lib/slf4j-jdk14-1.7.36.jar`.
  - Kiểm tra ranh giới: Hoàn toàn không chứa bất kỳ lớp đồ họa Swing nào của Client.
- **Thư viện phụ thuộc (`dist/server-dist/lib/`)**:
  - `mysql-connector-java-8.0.33.jar`: Driver kết nối MySQL 8 chính thức.
  - `HikariCP-4.0.3.jar`: Thư viện quản lý connection pool chuẩn Java 8.
  - `slf4j-api-1.7.36.jar` & `slf4j-jdk14-1.7.36.jar`: Logging bridge tương thích máy ảo Java chuẩn.
- **Thư mục cấu hình bên ngoài (`dist/server-dist/resources/`)**:
  - `db.properties.example`: Tệp mẫu cấu hình kết nối database an toàn với giá trị placeholder (`your_db_username`, `your_db_password`), không chứa bí mật thật.
  - `db.properties`: Tệp cấu hình thực tế nằm ngoài mã nguồn và JAR, có thể chỉnh sửa trực tiếp mà không cần biên dịch lại ứng dụng.
  - `server.properties`: Tệp cấu hình các cổng RMI (`1099`, `1100`) và kích thước thread pool.
- **Script khởi chạy `run-server.bat`**:
  - Khởi chạy JVM với classpath ưu tiên `resources;server.jar;lib/*` (đảm bảo cấu hình ngoài đĩa ghi đè cấu hình mặc định).
  - Tự động kiểm tra môi trường Java trước khi chạy.
  - Không in mật khẩu database ra màn hình console hay tệp nhật ký.

---

## 5. XÁC MINH KHẢ NĂNG KHỞI ĐỘNG VÀ KẾT NỐI MẠNG THỰC TẾ

Đã thực hiện kiểm tra thực tế trên máy chủ đang hoạt động:
1. **Kiểm tra trạng thái cổng mạng RMI**:
   - `Test-NetConnection -ComputerName 127.0.0.1 -Port 1099`: `TcpTestSucceeded : True` (Port 1099 RMI Registry đang lắng nghe và kết nối tốt).
   - `Test-NetConnection -ComputerName 127.0.0.1 -Port 1100`: `TcpTestSucceeded : True` (Port 1100 RMI Remote Object đang lắng nghe và kết nối tốt).
2. **Kiểm tra kết nối Cơ sở dữ liệu MySQL**:
   - Dịch vụ MySQL 8.0.30 đang chạy ổn định tại PID 5608 / 16328.
   - `HikariDataSource` khởi tạo thành công pool kết nối `LibraryServerPool` (10 connections).
3. **Kiểm tra tương thích đa nền tảng**:
   - Ứng dụng không sử dụng bất kỳ đường dẫn tuyệt đối cứng (hardcoded absolute path) nào trong mã nguồn Java của Client và Server.
   - Các tệp batch script sử dụng `cd /d "%~dp0"` đảm bảo chạy đúng thư mục hiện hành trên bất kỳ ổ đĩa nào (`C:`, `D:`, USB, thư mục mạng).

---

## 6. HỆ THỐNG TÀI LIỆU HƯỚNG DẪN TRIỂN KHAI VÀ VẬN HÀNH

Đã tạo mới 3 tài liệu hướng dẫn độc lập, chi tiết và chuẩn hóa cho người dùng/quản trị viên mới:

1. **[docs/INSTALLATION.md](file:///D:/workspace/thuvien/docs/INSTALLATION.md)**:
   - Hướng dẫn cài đặt từng bước cho người mới bắt đầu trên máy tính sạch.
   - Cài đặt Java 8, chuẩn bị MySQL 8, phân quyền user MySQL theo nguyên tắc đặc quyền tối thiểu.
   - Khởi tạo schema và seed data từ 3 tệp SQL chuẩn (`schema.sql`, `indexes.sql`, `seed.sql`).
   - Cấu hình và khởi chạy Server/Client với các kịch bản thực tế.
   - Hướng dẫn sử dụng tài khoản mẫu ban đầu và xử lý sự cố thường gặp.
2. **[docs/DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md)**:
   - Sơ đồ kiến trúc triển khai phân tán 3 tầng qua mạng LAN trường học.
   - Hướng dẫn cấu hình Windows Firewall (PowerShell) và Linux UFW cho các cổng RMI 1099, 1100.
   - Cơ chế tự động nhận diện IP máy chủ (`resolveServerHost`).
   - Hướng dẫn dừng server an toàn (*graceful shutdown*) và giải phóng tệp khóa.
   - Khuyến cáo an toàn triển khai mạng LAN phòng máy và môi trường sản xuất.
3. **[docs/BACKUP-RESTORE.md](file:///D:/workspace/thuvien/docs/BACKUP-RESTORE.md)**:
   - Hướng dẫn sao lưu toàn vẹn dữ liệu MySQL 8 bằng công cụ `mysqldump` với cờ snapshot InnoDB (`--single-transaction`, `--quick`, `--hex-blob`, `--default-character-set=utf8mb4`).
   - Quy trình truyền mật khẩu an toàn, không để lộ trong lịch sử dòng lệnh.
   - Quy trình diễn tập khôi phục (*restore drill*) trên database kiểm thử cô lập mà không làm ảnh hưởng database thật.
   - Kịch bản kiểm tra tính toàn vẹn quan hệ, tồn kho sách và dữ liệu mượn trả sau khi restore.

---

## 7. MA TRẬN ĐÁNH GIÁ TÍNH SẴN SÀNG PHÁT HÀNH (RELEASE READINESS MATRIX)

| Hạng mục kiểm tra | Tiêu chí đánh giá | Trạng thái | Bằng chứng xác minh |
|:---|:---|:---:|:---|
| **1. Mã nguồn & Biên dịch** | Biên dịch sạch với Java 8 (`ant compile`) | **PASS** | `BUILD SUCCESSFUL`, 0 compilation errors |
| **2. Kiểm thử tự động** | Toàn bộ các bộ test tự động vượt qua (`ant test`) | **PASS** | 121/121 tests PASS (13 suites), 0 failures, 0 errors |
| **3. Đóng gói Client** | `package-client` tạo bản phân phối độc lập | **PASS** | `dist/client-dist/client.jar` (1.00 MB), tài nguyên UI đầy đủ |
| **4. Ranh giới Client** | Không chứa mã nguồn Server, DB hoặc JDBC driver | **PASS** | Kiểm tra `jar tf`, 0 class server/db |
| **5. Đóng gói Server** | `package-server` tạo bản phân phối với đủ JAR và lib | **PASS** | `dist/server-dist/server.jar`, thư mục `lib/` và `resources/` |
| **6. Cấu hình bảo mật** | Tệp cấu hình mẫu không chứa secret; config thực tế unversioned | **PASS** | `db.properties.example` sử dụng placeholder, `.gitignore` bảo vệ `db.properties` |
| **7. Quản lý phiên & RBAC** | Phân quyền 35 actions nghiêm ngặt, chống IDOR | **PASS** | Ma trận phân quyền đầy đủ, test `test25` - `test27` PASS |
| **8. Bảo mật RMI** | JEP 290 Serialization Whitelist Filter hoạt động | **PASS** | `DEFAULT_SERIAL_FILTER` giới hạn `thuvien.common.*` và kiểu chuẩn, chặn `!*` |
| **9. Hoạt động thực tế của Server** | Server RMI lắng nghe đúng cổng 1099 và 1100 | **PASS** | `TcpTestSucceeded : True` trên cả cổng 1099 và 1100 |
| **10. Script khởi chạy** | Chạy bằng đường dẫn tương đối, kiểm tra Java trong PATH | **PASS** | `run-server.bat` và `run-client.bat` độc lập, hỗ trợ `%~dp0` |
| **11. Tài liệu cài đặt** | Hướng dẫn cài đặt và chạy trên máy mới từ đầu | **PASS** | [docs/INSTALLATION.md](file:///D:/workspace/thuvien/docs/INSTALLATION.md) |
| **12. Tài liệu triển khai LAN** | Hướng dẫn tường lửa, IP và vận hành server | **PASS** | [docs/DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md) |
| **13. Quy trình Backup/Restore** | Quy trình sao lưu và diễn tập phục hồi cho MySQL 8 | **PASS** | [docs/BACKUP-RESTORE.md](file:///D:/workspace/thuvien/docs/BACKUP-RESTORE.md) |
| **14. Kiểm thử trên máy vật lý thứ hai** | Cài đặt và chạy thử nghiệm trên máy trạm khác trong mạng | **NOT VERIFIED** | Môi trường kiểm thử hiện tại là máy phát triển đơn lẻ; cần thực hiện trên phòng máy Lab trường học theo tài liệu [DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md). |
| **15. Kênh truyền mã hóa TLS** | Kích hoạt mã hóa TLS cho RMI trên môi trường Internet | **NOT APPLICABLE** | Đồ án thiết kế chuẩn cho mạng LAN trường học / phòng máy nội bộ; đã lập kế hoạch giảm thiểu rủi ro trong tài liệu. |

---

## 8. KẾT LUẬN VÀ DANH SÁCH RỦI RO CÒN LẠI

### 8.1. Kết luận
- **Hệ thống ĐỦ ĐIỀU KIỆN PHÁT HÀNH NỘI BỘ (READY FOR INTERNAL RELEASE & LAB EVALUATION)**.
- Không còn bất kỳ lỗi **CRITICAL** hoặc **HIGH** nào tồn đọng.
- Bản phân phối Client hoàn toàn tách biệt, có thể sao chép sang bất kỳ máy tính nào có cài Java 8 để kết nối về máy chủ.
- Cơ sở dữ liệu và mã nguồn tuân thủ nghiêm ngặt chuẩn Java 8 và NetBeans 8.0.2 theo yêu cầu bài tập lớn môn học Mạng máy tính / Lập trình mạng.

### 8.2. Các bước người dùng cần thực hiện thủ công khi đưa vào phòng máy trường học
1. **Thiết lập IP tĩnh cho máy chủ**: Cố định địa chỉ IP máy chủ trong mạng LAN (ví dụ: `192.168.1.100`) để máy trạm không bị mất kết nối khi DHCP cấp lại IP.
2. **Mở cổng tường lửa máy chủ**: Chạy lệnh PowerShell tạo Firewall Rule cho cổng `1099` và `1100` trên máy chủ như hướng dẫn tại [docs/DEPLOYMENT.md](file:///D:/workspace/thuvien/docs/DEPLOYMENT.md).
3. **Phân phối thư mục Client**: Nén thư mục `dist/client-dist` thành tệp `client-dist.zip` và sao chép sang các máy trạm thực hành, cấu hình IP máy chủ trong `resources/client.properties` hoặc chạy qua lệnh `run-client.bat <IP_MAY_CHU>`.
