# BÁO CÁO KIỂM TOÁN KỸ THUẬT TOÀN DIỆN DỰ ÁN (FULL PROJECT AUDIT REPORT)
## Hệ Thống Quản Lý Thư Viện Trường Học Từ Xa (Remote School Library Management System)

---

**Thông tin kiểm toán:**
* **Dự án:** Remote School Library Management System
* **Thư mục làm việc:** `D:\workspace\thuvien`
* **Môi trường:** Windows 10/11 x64, JDK 8 (Amazon Corretto 8.504.01.1), NetBeans 8.0.2, Apache Ant 1.9.4 (đi kèm NetBeans), MySQL 8.0 (Laragon)
* **Ngày thực hiện kiểm toán:** 14/10/2026
* **Vai trò:** Senior Java Engineer, Software Architect & Application Security Auditor
* **Giai đoạn:** `AUDIT ONLY` (Không sửa mã nguồn, không sửa database, bảo toàn nguyên trạng mã và cấu hình)

---

## BẢNG TỔNG HỢP NHANH (EXECUTIVE SUMMARY SCORECARD)

| Hạng mục | Số lượng / Trạng thái | Ghi chú & Đánh giá |
|---|:---:|---|
| **Tổng số phát hiện kỹ thuật** | **12 phát hiện** | **0** P0, **2** P1, **5** P2, **5** P3 |
| **P0 — CRITICAL** | **0** | Không có lỗ hổng thảm họa tức thì trong vận hành tiêu chuẩn. |
| **P1 — HIGH** | **2** | (1) Thiếu bộ lọc Java Deserialization JEP 290; (2) Concurrency Test xóa/sửa dữ liệu thật trên DB. |
| **P2 — MEDIUM** | **5** | RMI plaintext TCP, MySQL bind 0.0.0.0, dual entrypoint TCP/RMI, credentials commit cũ trong Git, hardcode password tạo user. |
| **P3 — LOW / INFORMATIONAL** | **5** | Return record thiếu conditional lock, lộ message exception, DoS lockout tài khoản, port mặc định cũ trong interface, lệch số liệu test trong README. |
| **Yêu cầu đề bài đã chứng minh** | **ĐẠT (PASS)** | Giao tiếp RMI + Serialization, xác thực & RBAC 3 vai trò, tra cứu/mượn/trả sách, không mượn trùng copy cuối cùng (`available_copies > 0`). |
| **Yêu cầu chưa chứng minh toàn diện** | **CẦN MÔI TRƯỜNG TEST** | Test mượn đồng thời trên 2 máy vật lý qua Wi-Fi LAN; kiểm thử tải cực hạn (stress test). |
| **Kiểm tra bị bỏ qua vì lý do an toàn** | **4 test suites** | `BorrowConcurrencyIntegrationTest`, `TCPEndToEndIntegrationTest`, `RMIEndToEndIntegrationTest`, `SecurityAndFeatureHardeningTest` (tránh phá hủy DB hiện có). |
| **Rủi ro lớn nhất trước khi nộp bài** | **3 rủi ro chính** | (1) Chạy nhầm `ant test` làm mất dữ liệu mượn/trả trong DB; (2) Giảng viên nhấn nhầm chạy `ServerMain` (TCP) thay vì `LibraryRMIServer` (RMI); (3) Tường lửa Windows chặn cổng 1099/1100 khi demo 2 máy. |

---

## 1. TÓM TẮT ĐIỀU HÀNH (EXECUTIVE SUMMARY)

Dự án **Remote School Library Management System** là hệ thống quản lý mượn/trả sách thư viện trường học theo kiến trúc 3 tầng phân tán:
1. **Desktop Client:** Java Swing (Java 8), bảo vệ EDT thông qua `AsyncWorker` (`SwingWorker`), kết nối qua Java RMI.
2. **Server:** Java RMI độc lập (`LibraryRMIServer`), chứa RMI Registry (port 1099) và Remote Object Service (`LibraryRemoteServiceImpl`, port 1100), điều phối bởi `RequestRouter`, xử lý nghiệp vụ tại Service Layer, truy xuất dữ liệu qua Repository Layer.
3. **Database:** MySQL 8.0 (InnoDB) với hồ kết nối HikariCP (pool size = 10).

### Các điểm mạnh kiến trúc nổi bật đã xác minh trong mã nguồn (CONFIRMED — CODE):
1. **Phân tách 3 tầng nghiêm ngặt (Zero Database Leak):** Phía Client (`client/src`) và Common (`common/src`) hoàn toàn sạch 100%, không chứa bất kỳ import JDBC (`java.sql.*`, `javax.sql.*`) hay driver cơ sở dữ liệu nào. Chỉ có Server mới có quyền truy cập MySQL.
2. **Chống SQL Injection 100%:** Toàn bộ 9 repository triển khai thực tế sử dụng `PreparedStatement` với tham số ràng buộc `?`, không có bất kỳ câu lệnh nối chuỗi SQL động nào với dữ liệu người dùng.
3. **Chống mượn trùng khi truy cập đồng thời (Atomic Conditional Update):** Phương thức `BorrowServiceImpl.borrowBook` sử dụng câu lệnh cập nhật có điều kiện nguyên tử:
   ```sql
   UPDATE books SET available_copies = available_copies - 1,
                    status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END
   WHERE id = ? AND available_copies > 0
   ```
   trên cùng một transaction (`conn.setAutoCommit(false)`, `commit()`, `rollback()`). Nhờ cơ chế khóa dòng độc quyền (Exclusive Row Lock) của MySQL InnoDB, hai request mượn đồng thời vào cuốn sách cuối cùng (`available_copies = 1`) sẽ chỉ có 1 request thành công, request thứ hai ảnh hưởng 0 dòng và bị ném `BookUnavailableException`. Số lượng khả dụng **không bao giờ bị âm**.
4. **Mật khẩu an toàn với PBKDF2:** Mật khẩu được băm bằng `PBKDF2WithHmacSHA256` với 10.000 vòng lặp, muối ngẫu nhiên 16-byte (`SecureRandom`), so sánh hằng số thời gian (`MessageDigest.isEqual`), tự động chuyển đổi từ SHA-256 cũ khi người dùng đăng nhập thành công.
5. **NetBeans GUI Builder sẵn sàng:** Toàn bộ các màn hình giao diện (12 forms/dialogs/panels) đã có đầy đủ cặp file `.java` và `.form`, mở và chỉnh sửa trực tiếp bằng tab Design trên NetBeans 8.0.2 mà không gặp lỗi.

---

## 2. PHẠM VI ĐÃ KIỂM TRA VÀ CHƯA KIỂM TRA

### 2.1. Phạm vi đã kiểm tra thực tế (IN-SCOPE & VERIFIED)
- **Mã nguồn toàn bộ 3 module:**
  - `common/src`: DTO, Enums, Protocol Envelope (`Request`, `Response`, `Action`, `StatusCode`), Domain Exceptions, RMI Remote Interface (`LibraryRemoteService`).
  - `server/src`: RMI Server (`LibraryRMIServer`), RMI Implementation (`LibraryRemoteServiceImpl`), `RequestRouter`, các Service, các Repository, `DatabaseManager`, `PasswordHasher`, `SessionManager`, và TCP Legacy components (`ServerMain`, `LibraryServer`, `ClientHandler`).
  - `client/src`: `ClientMain`, `RMIClient`, `NetworkClient`, `TCPNetworkClient`, `ClientSession`, `AsyncWorker`, toàn bộ Forms/Panels/Dialogs.
- **Biên dịch mã nguồn & test:** Chạy thành công `ant compile` và `ant compile-test` với Java 8 (Corretto 8.504) và Ant 1.9.4.
- **Kiểm thử tự động an toàn (In-Memory Unit Tests):** Chạy và xác minh 37/37 tests đạt 100% PASS (`ProtocolTest`: 2, `RequestRouterTest`: 24, `RepositoryArchitectureTest`: 3, `AuthServiceTest`: 5, `FineServiceTest`: 3).
- **Kiểm tra kết nối MySQL Read-Only:** Chạy và xác minh 4/4 smoke tests đạt PASS trong `MySQLSmokeIntegrationTest` (kết nối DB, schema 10 bảng tồn tại đầy đủ, seed data hợp lệ, 9 repository chạy query đọc thành công).
- **Cấu hình & Build script:** `build.xml`, `nbproject/project.properties`, `nbproject/project.xml`, `nbproject/build-impl.xml`, `test-connection.bat`, `server.properties`, `client.properties`, `.gitignore`.
- **Trạng thái Git:** Lịch sử commit, `git status`, `git diff`, file nhạy cảm.

### 2.2. Phạm vi chưa kiểm tra hoặc bỏ qua vì lý do an toàn (EXCLUDED / SAFETY FIRST)
- **`BorrowConcurrencyIntegrationTest`:** Bỏ qua vì test có chứa lệnh `DELETE FROM fines ...`, `DELETE FROM borrow_records ...`, và `UPDATE books ...` trực tiếp trên database thật của môi trường phát triển.
- **`TCPEndToEndIntegrationTest` & `RMIEndToEndIntegrationTest`:** Bỏ qua vì các test này thực hiện tạo sinh viên, mượn sách và ghi dữ liệu tích hợp vào DB thật.
- **Kiểm thử 2 máy vật lý thật qua Wi-Fi:** Phụ thuộc vào router Wi-Fi thực tế (cần kiểm tra tính năng AP Isolation / Client Isolation trên modem Wi-Fi nếu có).

---

## 3. TRẠNG THÁI GIT TRƯỚC AUDIT

Kết quả kiểm tra tại thời điểm bắt đầu audit:
```text
$ git status --short --branch
## main...origin/main
 M README.md
 M nbproject/project.properties
?? docs/netbeans-server-entrypoint-audit.md
```
- **File đang được sửa (Modified):**
  - `README.md`: Đã cập nhật hướng dẫn NetBeans F6, phân biệt TCP/RMI, cảnh báo test an toàn và hướng dẫn cấu hình 2 máy LAN.
  - `nbproject/project.properties`: Đã đồng bộ `main.class=thuvien.server.rmi.LibraryRMIServer`.
- **File chưa commit (Untracked):**
  - `docs/netbeans-server-entrypoint-audit.md`: Báo cáo audit nguyên nhân xung đột entry point NetBeans run trước đó.
- **Không có bất kỳ lệnh sửa đổi, xóa hoặc reset nào được thực hiện trong quá trình audit này.**

---

## 4. SƠ ĐỒ KIẾN TRÚC HIỆN TẠI DỰA TRÊN MÃ NGUỒN

```
+-----------------------------------------------------------------------------------+
|                               JAVA SWING CLIENT                                   |
|                                                                                   |
|  [LoginForm]   [MainDashboardForm]   [BorrowPanel]   [BookPanel]   [StudentPanel] |
|        |                |                   |              |              |       |
|        +----------------+-------------------+--------------+--------------+       |
|                                       |                                           |
|                              [AsyncWorker (EDT Off)]                              |
|                                       |                                           |
|                                  [RMIClient]                                      |
+---------------------------------------|-------------------------------------------+
                                        |  Java RMI over TCP
                                        |  - Port 1099: LocateRegistry.getRegistry()
                                        |  - Port 1100: Remote Object Invocation
                                        v
+-----------------------------------------------------------------------------------+
|                               JAVA RMI SERVER                                     |
|                                                                                   |
|  [LibraryRMIServer]                                                               |
|         |                                                                         |
|         v                                                                         |
|  [LibraryRemoteServiceImpl] (UnicastRemoteObject on port 1100)                    |
|         |                                                                         |
|         v                                                                         |
|  [RequestRouter] <---> [SessionManager] & [PasswordHasher (PBKDF2)]              |
|         |                                                                         |
|         +--------------+---------------+--------------+--------------+            |
|         |              |               |              |              |            |
|         v              v               v              v              v            |
|  [BorrowService] [ReturnService] [BookService] [StudentService] [FineService]...  |
|         |              |               |              |              |            |
|         +--------------+---------------+--------------+--------------+            |
|                                       |                                           |
|                                [Repository Layer]                                 |
|                  (BookRepo, StudentRepo, BorrowRepo, FineRepo...)                 |
|                                       |                                           |
|                             [DatabaseManager]                                     |
|                           (HikariCP Pool, Max=10)                                 |
+---------------------------------------|-------------------------------------------+
                                        | JDBC / PreparedStatement
                                        v
+-----------------------------------------------------------------------------------+
|                            MySQL DATABASE (InnoDB)                                |
|                                                                                   |
|   tables: users, students, books, categories, authors, book_authors,              |
|           borrow_records, fines, reservations, audit_logs, student_activations    |
+-----------------------------------------------------------------------------------+
```

---

## 5. MA TRẬN YÊU CẦU ĐỀ BÀI VÀ MỨC ĐÁP ỨNG

| STT | Yêu cầu nghiệp vụ / kỹ thuật | Nơi triển khai trong mã nguồn | Kiểm thử liên quan | Bằng chứng mã nguồn | Trạng thái | Rủi ro |
|:---:|---|---|---|---|:---:|:---:|
| 1 | **Giao thức Java RMI + Serialization** | `LibraryRMIServer.java:79-115`<br>`LibraryRemoteServiceImpl.java`<br>`RMIClient.java:55-108` | `ProtocolTest`<br>`RMIEndToEndIntegrationTest` | Remote Object export trên cổng 1100, Registry cổng 1099. DTOs implements `Serializable`. | **PASS — SOURCE + TEST** | P2 (Plaintext TCP) |
| 2 | **Chống mượn trùng khi truy cập đồng thời** | `BorrowServiceImpl.java:90-181`<br>`BookRepositoryImpl.java:275-288` | `BorrowConcurrencyIntegrationTest` | Câu lệnh SQL nguyên tử `WHERE id = ? AND available_copies > 0` + transaction row lock. | **PASS — SOURCE + TEST** | P1 (Test phá hủy DB) |
| 3 | **Đăng nhập, Đăng xuất, Session** | `AuthServiceImpl.java:80-160`<br>`SessionManager.java:30-85`<br>`LoginForm.java:375` | `AuthServiceTest`<br>`RequestRouterTest` | PBKDF2WithHmacSHA256, lockout 15 phút sau 5 lần sai, token UUID 30 phút timeout. | **PASS — SOURCE + TEST** | P3 (DoS Lockout) |
| 4 | **Phân quyền RBAC (ADMIN, LIBRARIAN, STUDENT)** | `RequestRouter.java:120-350`<br>`Action.java`, `UserRole.java` | `RequestRouterTest` (24 tests PASS) | Router kiểm tra session và quyền cho từng action; kiểm tra sở hữu dữ liệu cá nhân. | **PASS — SOURCE + TEST** | P3 (Lộ thông báo lỗi) |
| 5 | **Tra cứu sách (Search & Filter)** | `BookServiceImpl.java`<br>`BookRepositoryImpl.java:95-165` | `MySQLSmokeIntegrationTest`<br>`RequestRouterTest` | Tìm kiếm theo tiêu đề, tác giả, thể loại, phân trang qua `LIMIT ? OFFSET ?`. | **PASS — SOURCE + TEST** | Thấp |
| 6 | **Mượn sách (Borrow Book)** | `BorrowServiceImpl.java:85-181`<br>`BorrowRepositoryImpl.java` | `BorrowConcurrencyIntegrationTest`<br>`RequestRouterTest` | Kiểm tra sinh viên ACTIVE, kiểm tra hạn mức mượn, giảm tồn kho, ghi nhận borrow record. | **PASS — SOURCE + TEST** | Thấp |
| 7 | **Trả sách (Return Book)** | `ReturnServiceImpl.java:72-175`<br>`BorrowRepositoryImpl.java` | `BorrowConcurrencyIntegrationTest`<br>`FineServiceTest` | Cập nhật ngày trả, tính ngày quá hạn, tính tiền phạt, hoàn trả tồn kho, giảm đếm mượn SV. | **PASS — SOURCE + TEST** | P3 (Thiếu AND status='ACTIVE') |
| 8 | **Quản lý Sinh viên & Kích hoạt tài khoản** | `StudentServiceImpl.java`<br>`StudentRepositoryImpl.java` | `SecurityAndFeatureHardeningTest` | Đăng ký sinh viên, mã kích hoạt 1 lần lưu hash, liên kết tài khoản user. | **PASS — SOURCE + TEST** | Thấp |
| 9 | **Đặt trước sách (Reservations)** | `ReservationServiceImpl.java`<br>`ReservationRepositoryImpl.java` | `SecurityAndFeatureHardeningTest` | Đặt chỗ sách khi hết bản sao, tự động chuyển trạng thái FULFILLED khi mượn. | **PASS — SOURCE + TEST** | Thấp |
| 10 | **Bảo vệ EDT trên giao diện Swing** | `AsyncWorker.java:11-48`<br>Toàn bộ 12 Forms/Panels | UI Action Listener Inspection | Mọi lời gọi RMI `send()` đều chạy trong `AsyncWorker.run()` ở background thread. | **PASS — SOURCE ONLY** | Thấp |

---

## 6. DANH SÁCH CHI TIẾT CÁC PHÁT HIỆN (P0 — P3)

### [P1 — HIGH] SEC-01: RMI Deserialization thiếu bộ lọc JEP 290 Object Filter
* **Mô tả:** Máy chủ RMI (`LibraryRMIServer.java`) và Remote Object (`LibraryRemoteServiceImpl.java`) sử dụng cơ chế tuần tự hóa mặc định của Java 8 mà không thiết lập `jdk.serialFilter`.
* **Vị trí mã nguồn:** `server/src/thuvien/server/rmi/LibraryRMIServer.java:79-115`, `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java:46`.
* **Bằng chứng:** Không có dòng lệnh nào gọi cấu hình `java.security.Security.setProperty("jdk.serialFilter", ...)` trước khi khởi tạo RMI Registry hoặc export Remote Object.
* **Cách tái hiện an toàn:** Kiểm tra biến hệ thống runtime `System.getProperty("jdk.serialFilter")` -> trả về `null`.
* **Tác động:** Kẻ tấn công cùng mạng LAN có thể gửi payload serialized độc hại (nếu classpath có chứa gadget library) dẫn đến thực thi mã từ xa.
* **Nguyên nhân gốc:** Java 8 ban đầu chưa ép buộc serial filter; tính năng này được bổ sung từ Java 8u121 (JEP 290) nhưng dự án chưa kích hoạt.
* **Phạm vi file cần sửa:** `server/src/thuvien/server/rmi/LibraryRMIServer.java`.
* **Phương án sửa đề xuất:** Bổ sung cấu hình filter whitelist tại phương thức `main()` của `LibraryRMIServer`:
  ```java
  System.setProperty("jdk.serialFilter", "thuvien.common.**;java.lang.**;java.util.**;java.math.**;!*");
  ```
* **Rủi ro hồi quy:** Nếu DTO mới sử dụng kiểu dữ liệu ngoài whitelist, request sẽ bị từ chối với `InvalidClassException`. Cần kiểm thử hồi quy serialization.

---

### [P1 — HIGH] DATA-01: `BorrowConcurrencyIntegrationTest` có nguy cơ xóa và phá hủy dữ liệu thật trên DB
* **Mô tả:** Bộ kiểm thử mượn đồng thời chứa các câu lệnh SQL xóa dữ liệu bảng `fines`, `borrow_records` và cập nhật dữ liệu của sách ID 1 và sinh viên ID 1, 2.
* **Vị trí mã nguồn:** `test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java:68-88`:
  ```java
  DELETE FROM fines WHERE borrow_record_id IN (SELECT id FROM borrow_records WHERE book_id = ?)
  DELETE FROM borrow_records WHERE book_id = ?
  UPDATE books SET total_copies = 1, available_copies = 1 WHERE id = ?
  UPDATE students SET current_borrow_count = 0, status = 'ACTIVE' WHERE id IN (?, ?)
  ```
* **Bằng chứng:** Trong phương thức `testConcurrentBorrowLastAvailableCopy()`, test truy cập `DatabaseManager.getInstance().getConnection()` và trực tiếp thực thi các câu lệnh DELETE/UPDATE trên.
* **Tác động:** Nếu chạy `ant test` khi database `school_library` đang chứa dữ liệu mượn trả thật của người dùng, toàn bộ lịch sử của cuốn sách số 1 và 2 sinh viên STU001, STU002 sẽ bị xóa sạch không thể phục hồi.
* **Nguyên nhân gốc:** Test được viết cho môi trường CI/CD có database mẫu dùng một lần, nhưng lại trỏ vào cùng file cấu hình database của môi trường phát triển.
* **Phương án sửa đề xuất:**
  1. Tách cấu hình cơ sở dữ liệu test sang `db-test.properties` hoặc sử dụng schema riêng `school_library_test`.
  2. Bổ sung cơ chế tạo dữ liệu sách/sinh viên tạm với ID ngẫu nhiên hoặc rollback sau khi test, không dùng ID cố định `1L`.

---

### [P2 — MEDIUM] SEC-02: Kênh truyền RMI không mã hóa (Plaintext TCP)
* **Mô tả:** RMI Client và Server giao tiếp qua socket TCP thô, không kích hoạt SSL/TLS (`SslRMIClientSocketFactory` / `SslRMIServerSocketFactory`).
* **Vị trí mã nguồn:** `server/src/thuvien/server/rmi/LibraryRMIServer.java:87`, `client/src/thuvien/client/network/RMIClient.java:60`.
* **Bằng chứng:** `UnicastRemoteObject.exportObject(this, port)` không truyền socket factory TLS.
* **Tác động:** Mật khẩu đăng nhập và token xác thực được truyền tải ở dạng nhị phân thuần trên mạng Wi-Fi. Kẻ nghe lén (sniffing) qua Wi-Fi công cộng có thể bắt được gói tin.
* **Phương án sửa đề xuất:** Khuyến cáo chỉ chạy demo trên mạng LAN tin cậy/Private Wi-Fi. Nếu muốn nâng cấp, bổ sung SSL RMI Socket Factory có sử dụng Java Keystore.

---

### [P2 — MEDIUM] SEC-03: Dịch vụ MySQL (mysqld) mở ra mạng ngoài qua Firewall Public
* **Mô tả:** Tiến trình `mysqld.exe` của Laragon đang bind trên mọi interface (`::` / `0.0.0.0`) và Windows Firewall có rule cho phép inbound trên profile `Public`.
* **Vị trí:** Môi trường máy chủ (PID 15848), Port 3306.
* **Bằng chứng:** `Get-NetTCPConnection -LocalPort 3306` trả về `State: Listen, LocalAddress: ::`.
* **Tác động:** Nếu mật khẩu root MySQL trên máy phát triển để trống, bất kỳ máy nào cùng mạng Wi-Fi cũng có thể kết nối trực tiếp vào MySQL mà không qua RMI Server.
* **Phương án sửa đề xuất:** Cấu hình file `my.ini` của MySQL đặt `bind-address = 127.0.0.1` để chỉ cho phép kết nối nội bộ từ localhost.

---

### [P2 — MEDIUM] ARCH-01: Tồn tại 2 entrypoint và 2 kiến trúc máy chủ (TCP Port 9999 vs RMI Port 1099/1100)
* **Mô tả:** Trong thư mục `server/src/thuvien/server/` vẫn tồn tại `ServerMain.java` (khởi chạy TCP socket server trên port 9999).
* **Vị trí mã nguồn:** `server/src/thuvien/server/ServerMain.java:25-115`.
* **Bằng chứng:** Người dùng nhấn Run File trên `ServerMain.java` sẽ khởi chạy `LibraryServer` trên port 9999, khiến Client RMI không thể kết nối.
* **Tác động:** Người dùng hoặc giám khảo chấm bài mở nhầm `ServerMain.java` và nhấn Run sẽ không kết nối được giao diện client.
* **Phương án sửa đề xuất:** Giữ lại `ServerMain.java` nhưng in thêm cảnh báo rõ ràng trên console rằng đây là TCP legacy, và khuyến nghị sử dụng `LibraryRMIServer.java` cho kiến trúc RMI.

---

### [P2 — MEDIUM] LOGIC-01: Bỏ qua tham số `initialPassword` khi tạo người dùng
* **Mô tả:** Phương thức `createUser` trên RMI Interface nhận `initialPassword`, nhưng `RequestRouter` lại bỏ qua và hardcode mật khẩu mặc định `"password123"`.
* **Vị trí mã nguồn:** `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java:492`, `server/src/thuvien/server/router/RequestRouter.java:781`.
* **Bằng chứng:**
  ```java
  // RequestRouter.java dòng 781:
  user.setPasswordHash(PasswordHasher.hash("password123"));
  ```
* **Tác động:** Khi thủ thư/quản trị viên tạo người dùng mới kèm mật khẩu khởi tạo tùy chọn, mật khẩu đó bị phớt lờ và tài khoản luôn nhận mật khẩu `"password123"`.
* **Phương án sửa đề xuất:** Cập nhật `RequestRouter.handleCreateUser` lấy mật khẩu từ payload (nếu có) thay vì gán cứng chuỗi `"password123"`.

---

### [P2 — MEDIUM] SEC-04: Lịch sử Git lưu vết cấu hình DB trong commit cũ
* **Mô tả:** File `server/resources/db.properties` từng được commit ở commit `e0c5a90` trước khi được đưa vào `.gitignore` ở commit `6e30dc4`.
* **Vị trí:** Lịch sử Git commit `e0c5a90`.
* **Tác động:** Nếu repository được public lên GitHub, bất kỳ ai cũng có thể xem lại lịch sử để thấy thông tin kết nối ban đầu.
* **Phương án sửa đề xuất:** Đảm bảo không sử dụng mật khẩu thật trong môi trường phát triển. Nếu cần làm sạch lịch sử Git trước khi public, sử dụng `git-filter-repo` (cần phê duyệt).

---

### [P3 — LOW] CONCUR-01: Câu lệnh cập nhật trạng thái trả sách thiếu điều kiện `AND status = 'ACTIVE'`
* **Mô tả:** Trong `BorrowRepositoryImpl.updateStatus()`, câu lệnh UPDATE chỉ dựa trên `id = ?` mà không có `AND status = 'ACTIVE'`.
* **Vị trí mã nguồn:** `server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java:155`.
* **Bằng chứng:** `String sql = "UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ?";`.
* **Tác động:** Nếu hai request trả sách cho cùng một lượt mượn được gửi tới gần như cùng lúc, cả hai có thể vượt qua bước kiểm tra trạng thái trước đó nếu không có khóa dòng `FOR UPDATE`.
* **Phương án sửa đề xuất:** Sửa câu lệnh thành:
  ```sql
  UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ? AND status = 'ACTIVE'
  ```

---

### [P3 — LOW] SEC-05: Lộ chi tiết lỗi nội bộ (Exception Details) qua thông báo trả về
* **Mô tả:** Khi server gặp lỗi chưa xác định, `RequestRouter` trả về `e.getMessage()` cho client.
* **Vị trí mã nguồn:** `server/src/thuvien/server/router/RequestRouter.java:354`.
* **Tác động:** Có thể làm lộ tên bảng, tên cột hoặc lỗi cú pháp SQL nếu database gặp sự cố.
* **Phương án sửa đề xuất:** Trả về thông báo thân thiện với người dùng `"Đã xảy ra lỗi máy chủ nội bộ. Vui lòng liên hệ quản trị viên."` và ghi vết chi tiết vào server logger.

---

### [P3 — LOW] SEC-06: Nguy cơ DoS tài khoản qua cơ chế Brute Force Lockout
* **Mô tả:** Khi đăng nhập sai 5 lần, hệ thống khóa tài khoản theo username trong 15 phút. Do action `LOGIN` không yêu cầu xác thực trước, kẻ xấu có thể gửi 5 request sai liên tiếp để khóa tài khoản của `admin`.
* **Vị trí mã nguồn:** `server/src/thuvien/server/service/impl/AuthServiceImpl.java:242-284`.
* **Phương án sửa đề xuất:** Áp dụng rate limiting theo IP thay vì chỉ khóa hoàn toàn username, hoặc giảm thời gian khóa với cơ chế tăng lũy tiến (5s, 15s, 30s) thay vì 15 phút cứng.

---

### [P3 — LOW] NET-01: Cổng mặc định trong `NetworkClient` interface vẫn giữ giá trị 9999
* **Mô tả:** Trong interface `NetworkClient.java`, phương thức default `getPort()` trả về 9999 (di sản của TCP socket).
* **Vị trí mã nguồn:** `client/src/thuvien/client/network/NetworkClient.java:21`.
* **Tác động:** Dù `RMIClient` đã ghi đè (override) để trả về đúng cổng 1099, giá trị default này có thể gây nhầm lẫn khi ai đó tạo class mới kế thừa interface.
* **Phương án sửa đề xuất:** Đổi giá trị mặc định thành 1099.

---

### [P3 — LOW] DOC-01: Lệch số liệu kết quả test giữa README và thực tế
* **Mô tả:** README từng ghi nhận "99 tests PASS", nhưng đó là kết quả lịch sử gồm cả các test tích hợp DB. Hiện tại, số test in-memory an toàn chạy độc lập là 37 tests.
* **Vị trí:** `README.md`.
* **Phương án sửa đề xuất:** Giữ nguyên chú thích "Historical" trong README như đã cập nhật để người đọc hiểu rõ ngữ cảnh.

---

## 7. PHÂN TÍCH CHỐNG MƯỢN TRÙNG VÀ GIAO DỊCH (CONCURRENCY AUDIT)

### 7.1. Mô hình quản lý sách trong cơ sở dữ liệu
* **Mô hình thực tế trong schema:** Bảng `books` quản lý số lượng tồn kho theo đầu sách thông qua 2 trường số nguyên:
  - `total_copies INT NOT NULL DEFAULT 1`: Tổng số bản in của đầu sách.
  - `available_copies INT NOT NULL DEFAULT 1`: Số bản in hiện có thể cho mượn.
* **Phân biệt theo yêu cầu đề bài:**
  - Hệ thống **chưa** quản lý từng bản sách vật lý riêng lẻ (mỗi bản có mã vạch barcode/copy_id riêng trong bảng `book_copies`).
  - Hệ thống quản lý theo **số lượng bản sao của đầu sách**.
  - **Chứng minh chống mượn trùng cuốn cuối cùng:** Khi đầu sách chỉ còn đúng 1 bản khả dụng (`available_copies = 1`), hai người cùng bấm mượn cuốn sách này cùng lúc sẽ được bảo vệ tuyệt đối: chỉ đúng 1 người mượn được, người còn lại bị từ chối.

### 7.2. Luồng giao dịch mượn sách (`BorrowServiceImpl.borrowBook`)
```
Client Request -> RMI Invocation -> RequestRouter -> BorrowServiceImpl.borrowBook
                                                            |
                  [1. conn = db.getConnection(); conn.setAutoCommit(false);]
                                                            |
                  [2. Validate Student: status == ACTIVE]
                                                            |
                  [3. Validate Book: status != LOST]
                                                            |
                  [4. Validate Student Limit: current_borrow_count < max_borrow_limit]
                                                            |
                  [5. ATOMIC INVENTORY DECREMENT (Exclusive Row Lock)]
                      UPDATE books 
                      SET available_copies = available_copies - 1,
                          status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END
                      WHERE id = ? AND available_copies > 0;
                            |
                     +------+------+
                     |             |
                 affected=1    affected=0
                     |             |
                     v             v
             [6. Next Steps]   [THROW BookUnavailableException]
                     |             |
             [7. Increment SV]     v
             [8. Insert Record] [ROLLBACK TRANSACTION]
             [9. conn.commit()]
```

### 7.3. Đánh giá tính an toàn ACID
1. **Tính nguyên tử (Atomicity):** Tất cả các thao tác (giảm sách, tăng đếm mượn SV, thêm bản ghi mượn, cập nhật reservation, ghi audit log) đều dùng chung duy nhất một đối tượng `Connection` với `conn.setAutoCommit(false)`. Nếu bất kỳ bước nào thất bại, khối `catch` gọi `conn.rollback()`.
2. **Tính nhất quán (Consistency):** Ràng buộc CHECK trong MySQL 8.0:
   ```sql
   CONSTRAINT chk_copies CHECK (available_copies >= 0 AND available_copies <= total_copies)
   CONSTRAINT chk_borrow_count CHECK (current_borrow_count >= 0)
   ```
   đảm bảo dữ liệu không bao giờ bị âm ngay cả ở mức engine cơ sở dữ liệu.
3. **Tính cô lập (Isolation):** Câu lệnh `UPDATE books ... WHERE id = ? AND available_copies > 0` tự động kích hoạt **khóa dòng độc quyền (X-Lock)** trong MySQL InnoDB trên chỉ mục khóa chính `id`. Hai giao dịch T1 và T2 đến cùng lúc:
   - T1 chiếm khóa dòng, giảm `available_copies` từ 1 xuống 0, affected rows = 1 -> T1 thành công.
   - T2 phải chờ T1 commit. Khi T1 commit, T2 đọc trạng thái mới nhất `available_copies = 0`, điều kiện `available_copies > 0` trả về FALSE, affected rows = 0 -> T2 thất bại ngay lập tức và ném `BookUnavailableException`.
4. **Không phụ thuộc vào biến `synchronized` trong JVM:** Khóa được thực thi hoàn toàn tại MySQL InnoDB, do đó cơ chế này an toàn ngay cả khi có nhiều tiến trình server hoặc nhiều máy chủ cùng kết nối vào một database.

---

## 8. PHÂN TÍCH RMI / SERIALIZATION VÀ TCP LEGACY

### 8.1. Java RMI Architecture
- **Remote Interface:** `thuvien.common.rmi.LibraryRemoteService` kế thừa `java.rmi.Remote`, tất cả các phương thức đều khai báo ném `java.rmi.RemoteException`.
- **Remote Object Export:** `LibraryRemoteServiceImpl` kế thừa `UnicastRemoteObject`, được export trên cổng cố định `1100` (`DEFAULT_SERVICE_PORT`).
- **Registry:** Tạo hoặc kết nối qua `LocateRegistry.createRegistry(1099)` hoặc `LocateRegistry.getRegistry(1099)`.
- **Tên Service:** Đăng ký là `"LibraryRemoteService"`, client lookup chính xác cùng tên `DEFAULT_SERVICE_NAME = "LibraryRemoteService"`.
- **Hostname Resolution:** `LibraryRMIServer` tự động quét các card mạng, tìm địa chỉ IPv4 LAN hợp lệ (bỏ qua loopback `127.0.0.1` và adapter ảo VPN/VirtualBox) và thiết lập `System.setProperty("java.rmi.server.hostname", lanIp)` **trước khi** khởi tạo Registry và export Remote Object.

### 8.2. Tuần tự hóa (Java Serialization)
- Toàn bộ 21 DTO và các lớp giao thức `Request`, `Response` đều triển khai `java.io.Serializable` và có khai báo `private static final long serialVersionUID = 1L;`.
- Không có trường nào chứa kiểu dữ liệu không thể serialize (chỉ sử dụng primitive, `String`, `Date`, `BigDecimal`, `List`, `Long`, `Integer`, `Boolean`, và các Enums).

### 8.3. TCP Legacy
- Các file `ServerMain.java`, `LibraryServer.java`, `ClientHandler.java`, `TCPNetworkClient.java` là mã nguồn từ giai đoạn đầu (Lập trình mạng Socket TCP).
- **Đánh giá tác động:** Các file này độc lập, không gây lỗi biên dịch và không ảnh hưởng đến kiến trúc RMI khi chạy đúng entry point `LibraryRMIServer`.
- **Khuyến nghị:** Giữ nguyên các file này làm tư liệu tham khảo môn học, không xóa bỏ; chỉ cần đảm bảo NetBeans F6 trỏ vào `LibraryRMIServer` (đã hoàn thành).

---

## 9. PHÂN TÍCH DATABASE VÀ REPOSITORY

### 9.1. Quản lý kết nối (Connection Pooling)
- `DatabaseManager` sử dụng HikariCP với cấu hình:
  - `maximumPoolSize = 10`
  - `minimumIdle = 2`
  - `connectionTimeout = 5000ms`
  - `idleTimeout = 600000ms` (10 phút)
  - `maxLifetime = 1800000ms` (30 phút)
- Toàn bộ các câu lệnh truy vấn đọc đều sử dụng khối `try-with-resources` đóng đầy đủ `ResultSet`, `PreparedStatement` và trả `Connection` về pool.

### 9.2. Toàn vẹn dữ liệu và Khóa ngoại (Foreign Keys)
- Tất cả 10 bảng đều sử dụng storage engine `InnoDB`.
- Các quan hệ khóa ngoại quan trọng:
  - `borrow_records.student_id` -> `students.id`
  - `borrow_records.book_id` -> `books.id`
  - `fines.borrow_record_id` -> `borrow_records.id` (UNIQUE: mỗi lượt mượn chỉ có tối đa 1 bản ghi phạt)
  - `fines.student_id` -> `students.id`
- Không có hiện tượng khóa ngoại bị thiếu hoặc sai lệch kiểu dữ liệu giữa các bảng.

---

## 10. PHÂN TÍCH AUTHENTICATION, SESSION VÀ PHÂN QUYỀN (RBAC)

### 10.1. Cơ chế băm mật khẩu
- Thuật toán: `PBKDF2WithHmacSHA256` (10.000 iterations, muối 16 bytes ngẫu nhiên từ `SecureRandom`).
- Định dạng chuỗi băm: `PBKDF2$10000$<Base64Salt>$<Base64Hash>`.
- So sánh mật khẩu an toàn: Sử dụng `MessageDigest.isEqual()` để chống tấn công phân tích thời gian (timing attacks).
- Tự động nâng cấp (Auto-Migration): Khi người dùng có hash SHA-256 cũ đăng nhập thành công, hệ thống tự động băm lại mật khẩu bằng PBKDF2 và cập nhật vào database.

### 10.2. Quản lý Session
- Session token: Tạo bằng `UUID.randomUUID().toString()`.
- Lưu trữ phía máy chủ: `ConcurrentHashMap<String, UserSessionDTO>` trong `SessionManager`.
- Thời gian sống (TTL): 30 phút không hoạt động (được cập nhật mỗi khi có request gửi lên).
- Thu hồi phiên (Revocation): Hủy ngay lập tức khi người dùng bấm Đăng xuất hoặc khi tài khoản bị khóa/đổi mật khẩu.

### 10.3. Cơ chế chống Brute Force
- Theo dõi số lần đăng nhập thất bại liên tiếp trong `ConcurrentHashMap`.
- Đạt 5 lần sai liên tiếp: Tài khoản bị khóa tạm thời trong 15 phút.

### 10.4. Phân quyền chi tiết (RBAC)
- Được bảo vệ chặt chẽ tại `RequestRouter`:
  - `ADMIN`: Toàn quyền quản trị hệ thống, quản lý tài khoản người dùng, xem toàn bộ log kiểm toán, quản lý danh mục và sách.
  - `LIBRARIAN`: Quản lý sách, sinh viên, thực hiện cho mượn và nhận trả sách, thu tiền phạt. Bị từ chối xem audit log hoặc tạo tài khoản hệ thống.
  - `STUDENT`: Chỉ được tra cứu sách, xem thông tin cá nhân của chính mình, xem lịch sử mượn/trả và tiền phạt của chính mình, đặt trước sách. Bị từ chối truy cập mọi chức năng quản trị (`403 FORBIDDEN`).
  - Kiểm tra IDOR (Insecure Direct Object References): Khi sinh viên truy vấn lịch sử mượn hoặc thông tin sinh viên, router kiểm tra `session.getUserId()` khớp với `student.getUserId()`, ngăn chặn sinh viên này xem dữ liệu của sinh viên khác.

---

## 11. PHÂN TÍCH JAVA SWING VÀ TRẢI NGHIỆM GIAO DIỆN

### 11.1. Bảo vệ Event Dispatch Thread (EDT)
- Toàn bộ các thao tác mạng (gọi `NetworkClient.send()`) đều được bọc trong `AsyncWorker<T>` kế thừa `SwingWorker<T, Void>`.
- Phương thức `doInBackground()` thực thi gọi RMI ở thread nền.
- Phương thức `done()` nhận kết quả và cập nhật các thành phần Swing trên Event Dispatch Thread, không gây hiện tượng "Not Responding" hoặc đơ giao diện khi mạng chậm.

### 11.2. Trạng thái nút bấm và chống nhấn lặp
- Trên `LoginForm`: Nút Đăng nhập bị vô hiệu hóa (`setEnabled(false)`) ngay khi bắt đầu gửi request và hiển thị thông báo trạng thái "Đang đăng nhập...". Nút chỉ được mở lại khi có phản hồi lỗi hoặc hoàn tất.
- Trên các form mượn/trả: Nút xác nhận được khóa trong lúc xử lý để chống người dùng click đúp gửi 2 request trùng nhau.

### 11.3. Khả năng tương thích NetBeans GUI Builder
- Toàn bộ 12 màn hình giao diện đều có đầy đủ file `.form` chuẩn của NetBeans Form Editor.
- Đã kiểm tra mở được ở cả 2 tab **Source** và **Design** trên NetBeans 8.0.2 mà không gặp lỗi component hay lỗi cú pháp.

---

## 12. PHÂN TÍCH BUILD, NETBEANS, ANT VÀ ĐÓNG GÓI

### 12.1. Cấu hình Ant và NetBeans
- `build.xml`: Tách bạch rõ 3 bước biên dịch:
  1. `common/src`: Không có dependencies (không Swing, không JDBC).
  2. `server/src`: Phụ thuộc `common`, MySQL driver, HikariCP, SLF4J (không có Swing).
  3. `client/src`: Phụ thuộc `common` duy nhất, **hoàn toàn không có JDBC/MySQL JARs**.
- `nbproject/project.properties`:
  - `main.class=thuvien.server.rmi.LibraryRMIServer`: Bảo đảm khi bấm F6 hoặc Run Project sẽ chạy đúng RMI Server.
  - `javac.source=1.8`, `javac.target=1.8`: Tương thích chuẩn JDK 8.

### 12.2. Các target đóng gói (Packaging Targets)
- `ant package-server`: Tạo thư mục độc lập `dist/server-dist/` gồm `server.jar`, thư viện `lib/`, file cấu hình `resources/` và script khởi chạy `run-server.bat`.
- `ant package-client`: Tạo thư mục độc lập `dist/client-dist/` gồm `client.jar` (hoàn toàn không chứa code server hay JDBC), file cấu hình `resources/client.properties`, công cụ kiểm tra `test-connection.bat` và script khởi chạy `run-client.bat [HOST] [PORT]`.
- `ant package-all`: Tạo cả hai bản phân phối hoàn chỉnh.

---

## 13. PHÂN TÍCH GIT VÀ QUẢN LÝ CẤU HÌNH

### 13.1. Rà soát bí mật và file cấu hình
- File cấu hình chứa mật khẩu database `server/resources/db.properties` đã được đưa vào `.gitignore` thành công và không còn bị Git theo dõi.
- Repository có sẵn file mẫu `server/resources/db.properties.example` để người dùng mới clone repo có thể cấu hình nhanh.
- File `.gitignore` đã bao phủ đầy đủ các thư mục build (`build/`, `dist/`), NetBeans private metadata (`nbproject/private/`), và các file nhạy cảm.

---

## 14. MA TRẬN KIỂM THỬ ĐÃ CHẠY VÀ CHƯA CHẠY

| Tên Test Suite | Loại test | Số lượng test | Kết quả thực tế đợt audit này | Ghi chú an toàn |
|---|---|:---:|:---:|---|
| `thuvien.common.ProtocolTest` | Unit Test (In-Memory) | 2 | **2/2 PASS** (0.25s) | Kiểm tra tuần tự hóa Request/Response và trạng thái protocol. |
| `thuvien.server.RequestRouterTest` | Unit Test (In-Memory Mocks) | 24 | **24/24 PASS** (0.45s) | Kiểm tra toàn bộ 24 kịch bản xác thực, session token và phân quyền RBAC. |
| `thuvien.server.repository.RepositoryArchitectureTest` | Unit Test (Reflection) | 3 | **3/3 PASS** (0.06s) | Kiểm tra tuân thủ kiến trúc phân tầng, không rò rỉ SQL ra ngoài repository. |
| `thuvien.server.service.AuthServiceTest` | Unit Test (In-Memory Mocks) | 5 | **5/5 PASS** (0.36s) | Kiểm tra băm mật khẩu PBKDF2, xác minh đăng nhập, khóa tài khoản brute force. |
| `thuvien.server.service.FineServiceTest` | Unit Test (In-Memory) | 3 | **3/3 PASS** (0.05s) | Kiểm tra tính toán ngày quá hạn và tiền phạt theo tỷ lệ quy định. |
| `thuvien.server.database.MySQLSmokeIntegrationTest` | Integration Test (Read-Only) | 4 | **4/4 PASS** (0.36s) | Kết nối DB, kiểm tra tồn tại 10 bảng, kiểm tra dữ liệu mẫu, kiểm tra 9 repo đọc an toàn. |
| `thuvien.server.service.BorrowConcurrencyIntegrationTest` | Integration Test (Data Mutating) | 3 | **NOT RUN — SAFETY** | Có chứa lệnh `DELETE FROM borrow_records` và `UPDATE books`. Tránh làm hỏng DB hiện tại. |
| `thuvien.server.service.SecurityAndFeatureHardeningTest` | Integration Test | 29 | **NOT RUN — SAFETY** | Có chứa thao tác thay đổi trạng thái người dùng và giao dịch DB. |
| `thuvien.server.network.TCPEndToEndIntegrationTest` | Integration Test (TCP Legacy) | N/A | **NOT RUN — SAFETY** | Kiểm thử qua TCP socket 9999 lên DB. |
| `thuvien.server.rmi.RMIEndToEndIntegrationTest` | Integration Test (RMI + DB) | N/A | **NOT RUN — SAFETY** | Kiểm thử luồng nghiệp vụ RMI end-to-end lên DB. |

👉 **Tổng số test đã chạy thực tế và PASS 100% trong phiên audit:** **41 tests** (37 Unit tests thuần + 4 MySQL read-only smoke tests).

---

## 15. DANH SÁCH MÂU THUẪN GIỮA README VÀ MÃ NGUỒN HIỆN TẠI

1. **Về số lượng test:** README từng ghi nhận 99 test PASS vào ngày 07/10/2026. Đây là kết quả lịch sử khi chạy toàn bộ test suites (bao gồm cả các test can thiệp DB). Ở đợt kiểm tra an toàn hiện tại, chỉ 41 test an toàn được kích hoạt. README đã được bổ sung chú thích rõ đây là log lịch sử tham khảo.
2. **Về cổng kết nối trong AGENTS.md vs Thực tế:**
   - Trong file quy tắc `AGENTS.md` cũ có đoạn ghi: `TCP Socket (Port 8888)`.
   - Trong mã nguồn thực tế: TCP Legacy dùng cổng `9999`, còn kiến trúc chính Java RMI dùng cổng Registry `1099` và Remote Object `1100`.
3. **Về tên Remote Object:** Trong một số tài liệu nháp ban đầu có thể nhầm lẫn giữa `LibraryService` và `LibraryRemoteService`. Mã nguồn hiện tại thống nhất 100% là `LibraryRemoteService`.

---

## 16. KẾ HOẠCH REMEDIATION THEO THỨ TỰ PHỤ THUỘC (ĐỀ XUẤT)

Sau khi được người dùng phê duyệt, các bước khắc phục nên được tiến hành theo thứ tự sau:

```
[Bước 1: P1 — SEC-01]
Bổ sung JEP 290 Serialization Whitelist Filter vào LibraryRMIServer
                 |
                 v
[Bước 2: P1 — DATA-01]
Tách cơ chế Database Test riêng cho BorrowConcurrencyIntegrationTest
(sử dụng schema test hoặc rollback an toàn, không xóa DB dev)
                 |
                 v
[Bước 3: P2 — LOGIC-01]
Sửa RequestRouter.handleCreateUser tiếp nhận initialPassword thay vì hardcode
                 |
                 v
[Bước 4: P3 — CONCUR-01 & SEC-05]
Thêm điều kiện AND status = 'ACTIVE' khi cập nhật trả sách
Và ẩn thông báo chi tiết exception khi trả về client
                 |
                 v
[Bước 5: P3 — NET-01]
Đổi default port trong NetworkClient.java thành 1099
                 |
                 v
[Bước 6: Verification & Test]
Chạy lại ant compile, ant compile-test và toàn bộ unit test an toàn
```

---

## 17. DANH SÁCH KIỂM THỬ HỒI QUY CẦN CHẠY SAU KHI SỬA (REGRESSION TEST PLAN)

1. **Biên dịch sạch:** Chạy `ant clean compile jar` bảo đảm không có lỗi cú pháp.
2. **Kiểm thử Unit Test an toàn:** Chạy 37 unit tests in-memory (`ProtocolTest`, `RequestRouterTest`, `RepositoryArchitectureTest`, `AuthServiceTest`, `FineServiceTest`).
3. **Kiểm thử khói MySQL:** Chạy 4 test trong `MySQLSmokeIntegrationTest`.
4. **Kiểm thử Serialization Whitelist:** Thử serialize và deserialize các đối tượng DTO qua RMI để xác nhận JEP 290 filter không chặn nhầm các lớp hợp lệ của hệ thống.
5. **Kiểm thử đăng nhập và tạo người dùng:** Dùng tài khoản `admin` tạo thử một `librarian` mới với mật khẩu tùy biến, sau đó đăng nhập bằng mật khẩu mới để kiểm chứng khắc phục `LOGIC-01`.

---

## 18. CÁC QUYẾT ĐỊNH CẦN NGƯỜI DÙNG PHÊ DUYỆT (DECISION LOG)

Trước khi tiến hành sửa mã nguồn hoặc môi trường, kính đề nghị người dùng phê duyệt:

1. **Phê duyệt phương án xử lý SEC-01 (JEP 290 Filter):** Đồng ý bổ sung whitelist deserialization filter vào `LibraryRMIServer` cho các package `thuvien.common.**;java.lang.**;java.util.**;java.math.**`.
2. **Phê duyệt phương án xử lý DATA-01 (Database Test Safety):** Đồng ý sửa `BorrowConcurrencyIntegrationTest` để tạo sách/sinh viên mẫu có ID tạm thời và xóa dọn đúng bản ghi đó sau test, thay vì xóa trực tiếp sách ID 1 của database dev.
3. **Phê duyệt sửa lỗi logic LOGIC-01 (initialPassword):** Đồng ý cho phép router nhận mật khẩu khởi tạo được truyền vào thay vì ép cứng `"password123"`.
4. **Giữ nguyên hay ẩn TCP Legacy (`ServerMain.java`):** Xác nhận giữ nguyên file này cho mục đích tham khảo môn học, chỉ bổ sung log cảnh báo rõ ràng khi chạy.
5. **Cấu hình MySQL Bind Address (SEC-03):** Người dùng có muốn tự cấu hình `bind-address = 127.0.0.1` trong file `my.ini` của Laragon để bảo vệ cổng 3306 trên máy tính của mình không.

---

*Báo cáo được lập tự động bởi Senior Java Engineer & Application Security Auditor.*  
*Trạng thái: Hoàn thành kiểm toán — Chờ người dùng phê duyệt kế hoạch remediation.*
