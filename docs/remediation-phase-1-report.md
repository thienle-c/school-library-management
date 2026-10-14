# BÁO CÁO REMEDIATION GIAI ĐOẠN 1: AN TOÀN DỮ LIỆU, BẢO MẬT RMI VÀ LOGIC XÁC THỰC
## Remote School Library Management System — Workspace: `D:\workspace\thuvien`

---

* **Ngày thực hiện:** 14/10/2026
* **Môi trường:** JDK 8 (Amazon Corretto 8.504.01.1), Apache Ant 1.9.4 (NetBeans 8.0.2), MySQL 8.0 (Laragon)
* **Kỹ sư thực hiện:** Senior Java Engineer & Application Security Auditor
* **Phạm vi xử lý:** DATA-01, SEC-01, LOGIC-01 và kiểm tra mở rộng CONCUR-01

---

## 1. NGUYÊN NHÂN GỐC ĐÃ XÁC MINH (VERIFIED ROOT CAUSES)

| Mã vấn đề | Phân loại | Nguyên nhân gốc trong mã nguồn |
|---|---|---|
| **DATA-01** | Test Safety / Data Loss | `test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java` hardcode `book_id = 1L`, `student_id IN (1, 2, 3)`. Test chạy các câu lệnh `DELETE FROM fines ...`, `DELETE FROM borrow_records ...` và `UPDATE books/students` trực tiếp trên database đang kết nối. Nếu chạy `ant test` trên database phát triển, toàn bộ lịch sử của sách 1 và sinh viên mẫu sẽ bị xóa vĩnh viễn. |
| **SEC-01** | RMI Security / Deserialization | `LibraryRMIServer` và `LibraryRemoteServiceImpl` sử dụng cơ chế tuần tự hóa mặc định của Java RMI mà không thiết lập cấu hình JEP 290 `jdk.serialFilter`. Bất kỳ đối tượng nào trên đường truyền cũng được deserialize tự do, tiềm ẩn nguy cơ Remote Code Execution qua deserialization gadget. |
| **LOGIC-01** | Authentication Logic / Hardcoded Secret | Trong `RequestRouter.handleCreateUser()`, tham số mật khẩu bị phớt lờ và gán cứng chuỗi `"password123"`. Trong `LibraryRemoteServiceImpl.createUser(token, user, initialPassword)`, tham số `initialPassword` bị bỏ qua không đưa vào `Request` payload. Trong `UserServiceImpl.createUser()`, nếu mật khẩu rỗng cũng tự động fallback về `"password123"`. |
| **CONCUR-01** | Concurrency / Double Increment | `BorrowRepositoryImpl.updateStatus()` thực thi câu lệnh SQL `UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ?` thiếu điều kiện `AND status = 'ACTIVE'`. Khi 2 request trả cùng một lượt mượn đến đồng thời, cả 2 đều có thể update bản ghi và tiến hành tăng tồn kho sách (`available_copies`) 2 lần. |

---

## 2. DANH SÁCH FILE THAY ĐỔI VÀ LÝ DO CHI TIẾT

### 2.1. File mới tạo (Untracked)
1. **`common/src/thuvien/common/dto/CreateUserRequestDTO.java`**
   - *Lý do:* Đóng gói an toàn cặp thông tin `UserDTO` và `initialPassword` truyền qua giao thức RMI. Có phương thức `toString()` che dấu mật khẩu `[PROTECTED]` để không bao giờ lộ mật khẩu trong log hệ thống.
2. **`test/thuvien/server/rmi/RMISerializationFilterTest.java`**
   - *Lý do:* Bộ unit test tự động (5 tests) chứng minh JEP 290 filter hoạt động: cho phép toàn bộ DTOs và các kiểu dữ liệu hợp lệ của ứng dụng, đồng thời từ chối lớp trái phép bằng `InvalidClassException`.
3. **`test/thuvien/server/service/CreateUserSecurityTest.java`**
   - *Lý do:* Bộ unit test tự động (4 tests) chứng minh khắc phục LOGIC-01: mật khẩu khởi tạo được truyền đúng, băm bằng PBKDF2 (không plaintext), không fallback `"password123"`, từ chối mật khẩu rỗng/ngắn (< 6 ký tự), và ép buộc quyền `ADMIN` tại máy chủ.

### 2.2. File mã nguồn sửa đổi (Modified)
1. **`server/src/thuvien/server/rmi/LibraryRMIServer.java`**
   - *Thay đổi:* Bổ sung cấu hình filter `DEFAULT_SERIAL_FILTER` theo chuẩn JEP 290:
     ```text
     maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;!*
     ```
   - Thêm phương thức `public static void configureSerializationFilter()`, gọi trong `static {}`, `start()` và `main()`.
2. **`server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java`**
   - *Thay đổi:* Kích hoạt `configureSerializationFilter()` trong `static {}`. Sửa phương thức `createUser(token, user, initialPassword)` đóng gói dữ liệu vào `CreateUserRequestDTO` trước khi gửi tới Router.
3. **`server/src/thuvien/server/router/RequestRouter.java`**
   - *Thay đổi:* `handleCreateUser()` giải nén `CreateUserRequestDTO`, kiểm tra username không rỗng, kiểm tra mật khẩu khởi tạo không rỗng và độ dài tối thiểu 6 ký tự, loại bỏ hoàn toàn chuỗi gán cứng `"password123"`.
4. **`server/src/thuvien/server/service/impl/UserServiceImpl.java`**
   - *Thay đổi:* `createUser()` loại bỏ fallback `"password123"`, ném `ValidationException` nếu mật khẩu rỗng hoặc < 6 ký tự, băm mật khẩu bằng PBKDF2 qua `PasswordHasher.hash()`.
5. **`server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java`**
   - *Thay đổi:* Sửa `updateStatus()` thành:
     ```sql
     UPDATE borrow_records SET status = ?, return_date = ? WHERE id = ? AND status = 'ACTIVE'
     ```
     Đảm bảo chỉ cập nhật thành công nếu lượt mượn đang thực sự `ACTIVE`.
6. **`server/src/thuvien/server/service/impl/ReturnServiceImpl.java`**
   - *Thay đổi:* Kiểm tra kết quả `recordUpdated`. Nếu cập nhật trả về 0 dòng, ném `LibraryException` với thông báo rõ ràng: `"Failed to update borrowing record status to RETURNED. The record may have already been returned or is not ACTIVE."` và rollback toàn bộ transaction.
7. **`test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java`**
   - *Thay đổi:*
     - Tự động phát hiện database đang kết nối. Nếu là database mặc định `school_library` và không có cờ an toàn (`-Dtest.database.enabled=true`), tự động bỏ qua (`SKIPPED`) với lý do an toàn bảo vệ dữ liệu.
     - Khi chạy trong môi trường test: Tự động sinh cuốn sách test riêng biệt (ISBN ngẫu nhiên) và sinh viên test riêng biệt (mã SV ngẫu nhiên), chạy test mượn đồng thời trên đúng bản ghi này.
     - Bổ sung test `testConcurrentReturnSameBorrowRecord`: Giả lập 2 thread cùng trả 1 cuốn sách đồng thời, chứng minh chỉ 1 thread thành công, tồn kho tăng đúng 1 lần (không tăng gấp đôi), số lượt mượn SV giảm đúng 1 lần (không bị âm).
     - Khối `tearDown()` dọn dẹp chính xác các bản ghi do test tạo ra theo khóa chính, **tuyệt đối không xóa bất kỳ bản ghi có sẵn nào của hệ thống**.
8. **`build.xml`**
   - *Thay đổi:* Thêm hook `-pre-compile-test` và `-pre-compile-test-single` dọn dẹp các file class test nếu vô tình bị các tool IDE ngoài compile vào thư mục `build/classes`.

---

## 3. KẾT QUẢ KIỂM THỬ THỰC TẾ (TEST EXECUTION RESULTS)

### 3.1. Các test đã chạy và kết quả

| STT | Tên Test Suite | Loại kiểm thử | Số test | Kết quả | Thời gian |
|:---:|---|:---:|:---:|:---:|:---:|
| 1 | `thuvien.server.rmi.RMISerializationFilterTest` | Unit Test (JEP 290 Filter) | 5 | **5/5 PASS** | 0.28s |
| 2 | `thuvien.server.service.CreateUserSecurityTest` | Unit Test (LOGIC-01 & RBAC) | 4 | **4/4 PASS** | 0.57s |
| 3 | `thuvien.common.ProtocolTest` | Unit Test (In-Memory) | 2 | **2/2 PASS** | 0.25s |
| 4 | `thuvien.server.RequestRouterTest` | Unit Test (RBAC & Session) | 24 | **24/24 PASS** | 0.46s |
| 5 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` *(chế độ mặc định an toàn)* | Integration Test | 4 | **4/4 SKIPPED (SAFE)** | 0.25s |
| 6 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` *(với cờ test cô lập)* | Integration Test (MySQL Concurrency) | 4 | **4/4 PASS** | 0.46s |

👉 **Chi tiết kết quả 4 test mượn/trả đồng thời (Test suite 6):**
* `testConcurrentBorrowLastAvailableCopy`: 2 threads tranh mượn cuốn sách còn 1 bản sao duy nhất -> 1 thành công (`commit`), 1 thất bại (`BookUnavailableException`), tồn kho còn đúng 0. **PASS**.
* `testConcurrentReturnSameBorrowRecord`: 2 threads cùng gọi trả sách trên 1 lượt mượn -> 1 thành công (`status=RETURNED`), 1 thất bại (`LibraryException: record may have already been returned or is not ACTIVE`), tồn kho tăng đúng 1 (không tăng 2), đếm mượn SV về 0. **PASS**.
* `testNormalBorrowFollowedByReturn`: Mượn và trả tuần tự, cân đối chính xác tồn kho và đếm mượn. **PASS**.
* `testOverdueReturnGeneratesFine`: Trả sách quá hạn 5 ngày -> tự động tạo bản ghi phạt 25.000 VND (5 x 5000 VND/ngày). **PASS**.

### 3.2. Các test chưa chạy trong lượt này và lý do
* `TCPEndToEndIntegrationTest`: Test legacy socket TCP 9999 lên database.
* `RMIEndToEndIntegrationTest` & `SecurityAndFeatureHardeningTest`: Các test tích hợp này yêu cầu chuẩn bị tài khoản và dữ liệu người dùng mẫu trên database test riêng.

---

## 4. HƯỚNG DẪN CẤU HÌNH DATABASE TEST (DEDICATED TEST DATABASE RUNBOOK)

Để chạy trọn vẹn toàn bộ các integration test mà không chạm vào database phát triển:

### Cách 1: Sử dụng cờ an toàn trên môi trường phát triển hiện tại
Nhờ cơ chế cô lập mới trong `BorrowConcurrencyIntegrationTest`, test chỉ tạo sách/sinh viên tạm và tự dọn dẹp sạch sẽ sau khi chạy xong. Để cho phép test thực thi:
```bat
"<NETBEANS_ANT_PATH>\ant.bat" -Dtest-sys-prop.test.database.enabled=true -Djavac.includes=thuvien/server/service/BorrowConcurrencyIntegrationTest.java -Dtest.includes=thuvien/server/service/BorrowConcurrencyIntegrationTest.java test-single
```

### Cách 2: Tạo database kiểm thử riêng biệt `school_library_test` (Khuyến nghị chuẩn)
1. Mở MySQL Console trên Laragon:
   ```sql
   CREATE DATABASE school_library_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE school_library_test;
   SOURCE D:/workspace/thuvien/database/schema.sql;
   SOURCE D:/workspace/thuvien/database/seed.sql;
   ```
2. Trong `server/resources/db.properties` (hoặc tạo file cấu hình test):
   ```properties
   db.url=jdbc:mysql://localhost:3306/school_library_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
   ```
3. Khi catalog kết nối chứa từ khóa `test`, `BorrowConcurrencyIntegrationTest` sẽ tự động nhận diện và chạy mà không cần truyền cờ thủ công.

---

## 5. CÁC RỦI RÒ CÒN TỒN TẠI VÀ GIỚI HẠN BẢO MẬT

1. **RMI chưa sử dụng TLS/SSL:** Lưu lượng RMI Registry (1099) và Remote Object (1100) truyền qua TCP thuần không mã hóa. Cần giữ phạm vi thử nghiệm trong mạng LAN nội bộ hoặc Wi-Fi riêng tin cậy.
2. **Cổng MySQL 3306 bind `0.0.0.0`:** Tiến trình `mysqld.exe` trên máy phát triển vẫn lắng nghe trên mọi card mạng; người dùng nên cấu hình `bind-address = 127.0.0.1` trong file `my.ini` của MySQL để tăng độ an toàn.

---

## 6. HƯỚNG DẪN KIỂM TRA THỦ CÔNG TRÊN NETBEANS 8.0.2

1. **Mở dự án:** Khởi động NetBeans 8.0.2 -> `File` -> `Open Project` -> Chọn thư mục `D:\workspace\thuvien`.
2. **Biên dịch dự án:** Nhấp chuột phải vào dự án `thuvien` -> chọn **Clean and Build**. Xác nhận thanh trạng thái báo `BUILD SUCCESSFUL`.
3. **Chạy Server RMI:**
   - Nhấn phím **F6** (hoặc nút **Run Project** màu xanh trên toolbar).
   - Kiểm tra Output console hiển thị:
     ```text
     Configured JEP 290 Serialization Filter: maxdepth=15;...;thuvien.common.**;...;!*
     RMI Server started
     RMI Registry port: 1099
     RMI Remote Object port: 1100
     ```
4. **Chạy Client Swing:**
   - Mở cây thư mục `client/src` -> `thuvien.client` -> nhấp chuột phải vào `ClientMain.java` -> chọn **Run File** (hoặc `Shift+F6`).
   - Cửa sổ đăng nhập Swing xuất hiện.
   - Nhấn **Kiểm tra kết nối** -> báo `Server connected` màu xanh.
   - Đăng nhập bằng `admin` / `admin123` -> Giao diện Dashboard mở thành công.
5. **Chạy Unit Test an toàn:**
   - Mở `test/src` -> `thuvien.server.rmi` -> Chuột phải vào `RMISerializationFilterTest.java` -> chọn **Test File** (`Ctrl+F6`). Kết quả: **5/5 PASS**.
   - Mở `test/src` -> `thuvien.server.service` -> Chuột phải vào `CreateUserSecurityTest.java` -> chọn **Test File** (`Ctrl+F6`). Kết quả: **4/4 PASS**.

---

## 7. TRẠNG THÁI GIT HIỆN TẠI

```text
$ git status --short --branch
## main...origin/main
 M README.md
 M build.xml
 M nbproject/project.properties
 M server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java
 M server/src/thuvien/server/rmi/LibraryRMIServer.java
 M server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java
 M server/src/thuvien/server/router/RequestRouter.java
 M server/src/thuvien/server/service/impl/ReturnServiceImpl.java
 M server/src/thuvien/server/service/impl/UserServiceImpl.java
 M test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java
?? common/src/thuvien/common/dto/CreateUserRequestDTO.java
?? docs/full-project-audit.md
?? docs/netbeans-server-entrypoint-audit.md
?? docs/remediation-phase-1-report.md
?? test/thuvien/server/rmi/RMISerializationFilterTest.java
?? test/thuvien/server/service/CreateUserSecurityTest.java
```

*Toàn bộ các thay đổi được giữ nguyên trong working tree, không có commit, push hay thay đổi cấu hình firewall/Git history.*
