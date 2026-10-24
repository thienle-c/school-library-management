# BÁO CÁO KIỂM TOÁN BẢO MẬT, TÍNH TOÀN VẸN DỮ LIỆU & AN TOÀN TRIỂN KHAI (PHASE 1.24)

> **Dự án**: Remote School Library Management System  
> **Repository**: `D:\workspace\thuvien`  
> **Công nghệ**: Java 8 (OpenJDK 1.8), Java Swing, Java RMI, MySQL 8 (Laragon / InnoDB), HikariCP, Apache Ant, NetBeans 8.0.2  
> **Thời điểm kiểm toán**: Tháng 10/2026  
> **Trạng thái thực thi**: Hoàn thành kiểm toán toàn diện & xác minh bằng mã nguồn và kiểm thử tự động  

---

## 1. BẢO VỆ GIT VÀ DỮ LIỆU

### 1.1. Ghi nhận trạng thái trước khi kiểm toán
Trước khi tiến hành kiểm toán, trạng thái Git được ghi nhận chi tiết:
- **`git status --short`**: Hoàn toàn sạch (clean working tree).
- **`git log -5 --oneline`**:
  ```text
  f174c39 feat: implement modern UI views and network components for client authentication and borrowing
  2f7a0d2 Merge branch 'main' of https://github.com/thienle-c/school-library-management
  54ed3c6 feat: implement session management, request routing, security tests, and documentation
  31c9a2f Merge pull request #4 from thienle-c/frontend
  802fc13 Merge branch 'origin/frontend' into frontend
  ```
- **`git diff --stat`**: Rỗng (không có thay đổi unstaged trước kiểm toán).
- **`git diff --cached --stat`**: Rỗng (không có thay đổi staged trước kiểm toán).
- **Các báo cáo từ phase trước**: Toàn bộ 10 tệp tài liệu và báo cáo kiểm toán trước đó (`phase-1-13` đến `phase-1-17`, `LICENSE`, `phase-1-22`, `phase-1-23`) được bảo toàn nguyên vẹn.
- **Tiến trình đang chạy**: Tiến trình server nền (`dist\server-dist\run-server.bat`, PID 10008) đang hoạt động bình thường, không bị can thiệp hay buộc dừng. Không xóa hoặc sửa dữ liệu database thật.

---

## 2. KIỂM TRA XÁC THỰC VÀ PHÂN QUYỀN (RBAC & ANTI-IDOR)

### 2.1. Xác thực và quản lý phiên (Authentication & Session Management)
- **Cơ chế token**: Session token được sinh ngẫu nhiên bằng `UUID.randomUUID().toString()`, lưu trong `SessionManager` dưới cấu trúc `ConcurrentHashMap<String, SessionEntry>`.
- **Vòng đời phiên**: Hết hạn sau 30 phút không hoạt động (`IDLE_TIMEOUT_MS = 30 * 60 * 1000L`). Mỗi request hợp lệ sẽ gọi `touch(now)` để làm mới thời gian truy cập.
- **Xác thực tập trung ở Server**: `RequestRouter.java` (dòng 169–185) thực hiện chặn mọi action (ngoại trừ `LOGIN`, `REGISTER_STUDENT`, `PING`). Token rỗng hoặc không tồn tại sẽ lập tức trả về `401 UNAUTHORIZED`.
- **Server-Managed Role**: Đối tượng `UserSessionDTO` được nạp trực tiếp từ `SessionManager` trên server dựa vào token. Client hoàn toàn không thể tự khai báo `userId` hoặc `role` để leo thang đặc quyền.
- **Chính sách đổi mật khẩu (SEC-01)**: Khi đổi mật khẩu thành công qua action `CHANGE_PASSWORD`, server kích hoạt `sessionManager.revokeOtherSessionsForUser(userId, token)`. Phiên làm việc hiện tại của người dùng được giữ lại, toàn bộ các phiên làm việc khác trên các máy/terminal khác của cùng tài khoản bị thu hồi ngay lập tức. Đổi mật khẩu thất bại không làm mất phiên.
- **Chống brute-force & khóa tài khoản**: `AuthServiceImpl` ghi nhận số lần đăng nhập sai (`failedAttempts`). Khi vượt quá 5 lần liên tiếp, tài khoản bị tạm khóa 15 phút. `RequestRouter` kiểm tra trạng thái khóa ở mỗi request và lập tức thu hồi token nếu tài khoản rơi vào trạng thái khóa.
- **Chống User Enumeration**: Khi đăng nhập thất bại do sai tài khoản hoặc sai mật khẩu, `AuthServiceImpl` trả về cùng một thông báo lỗi chuẩn: `"Tên đăng nhập hoặc mật khẩu không chính xác."`.

### 2.2. Ma trận phân quyền chi tiết (Action Permission Matrix)

Hệ thống hỗ trợ 3 vai trò người dùng chính: `ADMIN` (Quản trị viên hệ thống), `LIBRARIAN` (Thủ thư), `STUDENT` (Sinh viên / Bạn đọc). Dưới đây là ma trận kiểm toán quyền thực tế trên `RequestRouter.java`:

| STT | Action | Quyền được phép | Xác thực yêu cầu | Cơ chế kiểm tra & Chống IDOR |
|:---:|:---|:---|:---:|:---|
| 1 | `PING` | Tất cả (Public) | Không | Kiểm tra trạng thái sống của dịch vụ RMI |
| 2 | `LOGIN` | Tất cả (Public) | Không | Xác thực credentials, chống brute-force (khóa 15 phút sau 5 lần sai), tự động upgrade PBKDF2 |
| 3 | `LOGOUT` | Mọi user có phiên | Có | Thu hồi token khỏi `SessionManager` |
| 4 | `GET_CURRENT_USER` | Mọi user có phiên | Có | Trả về thông tin phiên gắn liền với token (server-resolved) |
| 5 | `REGISTER_STUDENT` | Tất cả (Public) | Không | Đăng ký tài khoản sinh viên dựa trên mã SV và mã kích hoạt |
| 6 | `CHANGE_PASSWORD` | Mọi user có phiên | Có | Tự đổi mật khẩu bản thân; thu hồi các phiên đăng nhập khác của cùng user (SEC-01) |
| 7 | `LIST_BOOKS` | Mọi user có phiên | Có | Tra cứu danh mục sách toàn trường |
| 8 | `GET_BOOK` | Mọi user có phiên | Có | Xem chi tiết sách theo ID hoặc ISBN |
| 9 | `SEARCH_BOOKS` | Mọi user có phiên | Có | Tìm kiếm sách có phân trang và bộ lọc an toàn |
| 10 | `CREATE_BOOK` | `ADMIN`, `LIBRARIAN` | Có | Thêm sách mới vào kho (`requireRole`) |
| 11 | `UPDATE_BOOK` | `ADMIN`, `LIBRARIAN` | Có | Cập nhật thông tin sách (`requireRole`) |
| 12 | `DELETE_BOOK` | `ADMIN`, `LIBRARIAN` | Có | Xóa sách khỏi hệ thống (`requireRole`) |
| 13 | `LIST_STUDENTS` | `ADMIN`, `LIBRARIAN` | Có | Xem danh sách toàn bộ sinh viên |
| 14 | `GET_STUDENT` | `STUDENT` (chính mình), `ADMIN`, `LIBRARIAN` | Có | **Anti-IDOR**: Sinh viên chỉ được xem hồ sơ của chính mình (`student.getUserId().equals(session.getUserId())`), nếu truy cập ID khác bị từ chối 403 FORBIDDEN |
| 15 | `CREATE_STUDENT` | `ADMIN`, `LIBRARIAN` | Có | Tạo hồ sơ sinh viên mới |
| 16 | `UPDATE_STUDENT` | `ADMIN`, `LIBRARIAN` | Có | Chỉnh sửa thông tin sinh viên |
| 17 | `DELETE_STUDENT` | `ADMIN` | Có | Chỉ Admin được tạm khóa hoặc xóa vĩnh viễn sinh viên |
| 18 | `GENERATE_STUDENT_ACTIVATION` | `ADMIN` | Có | Chỉ Admin được cấp mã kích hoạt tài khoản |
| 19 | `BORROW_BOOK` | `ADMIN`, `LIBRARIAN` | Có | Thực hiện giao dịch mượn sách tại quầy thủ thư |
| 20 | `RETURN_BOOK` | `ADMIN`, `LIBRARIAN` | Có | Thực hiện giao dịch trả sách tại quầy thủ thư |
| 21 | `LIST_BORROW_RECORDS` | `ADMIN`, `LIBRARIAN` | Có | Xem toàn bộ lịch sử mượn trả thư viện |
| 22 | `GET_STUDENT_ACTIVE_BORROWS` | `STUDENT` (chính mình), `ADMIN`, `LIBRARIAN` | Có | **Anti-IDOR**: Khi role là `STUDENT`, server bỏ qua payload và tự động trích xuất `studentId` từ session của người dùng đăng nhập |
| 23 | `LIST_FINES` | `STUDENT` (chính mình), `ADMIN`, `LIBRARIAN` | Có | **Anti-IDOR**: Sinh viên chỉ nhận được danh sách tiền phạt của chính mình; Thủ thư/Admin có thể lọc theo sinh viên bất kỳ hoặc xem toàn bộ |
| 24 | `CALCULATE_FINE` | Mọi user có phiên | Có | Tiện ích tính tiền phạt quá hạn theo số ngày (read-only) |
| 25 | `PAY_FINE` | `ADMIN`, `LIBRARIAN` | Có | Xác nhận thu tiền phạt tại quầy thư viện |
| 26 | `CREATE_RESERVATION` | Mọi user có phiên | Có | **Anti-IDOR**: Sinh viên đặt trước tự động gán `studentId` từ session; Thủ thư có thể đặt hộ cho sinh viên |
| 27 | `CANCEL_RESERVATION` | Mọi user có phiên | Có | **Anti-IDOR**: Sinh viên chỉ được phép hủy phiếu đặt trước của chính mình, cố tình hủy phiếu của người khác bị chặn 403 FORBIDDEN |
| 28 | `LIST_RESERVATIONS` | Mọi user có phiên | Có | **Anti-IDOR**: Sinh viên chỉ xem danh sách đặt trước của bản thân |
| 29 | `LIST_CATEGORIES` | Mọi user có phiên | Có | Tra cứu thể loại sách |
| 30 | `GET_CATEGORY` | Mọi user có phiên | Có | Chi tiết thể loại |
| 31 | `CREATE_CATEGORY` | `ADMIN`, `LIBRARIAN` | Có | Thêm thể loại mới |
| 32 | `UPDATE_CATEGORY` | `ADMIN`, `LIBRARIAN` | Có | Sửa thể loại |
| 33 | `DELETE_CATEGORY` | `ADMIN`, `LIBRARIAN` | Có | Xóa thể loại |
| 34 | `LIST_AUTHORS` | Mọi user có phiên | Có | Tra cứu tác giả |
| 35 | `GET_AUTHOR` | Mọi user có phiên | Có | Chi tiết tác giả |
| 36 | `CREATE_AUTHOR` | `ADMIN`, `LIBRARIAN` | Có | Thêm tác giả mới |
| 37 | `UPDATE_AUTHOR` | `ADMIN`, `LIBRARIAN` | Có | Sửa thông tin tác giả |
| 38 | `DELETE_AUTHOR` | `ADMIN`, `LIBRARIAN` | Có | Xóa tác giả |
| 39 | `LIST_USERS` | `ADMIN` | Có | Xem danh sách tài khoản người dùng hệ thống |
| 40 | `GET_USER` | `ADMIN` | Có | Xem thông tin chi tiết người dùng |
| 41 | `CREATE_USER` | `ADMIN` | Có | Tạo tài khoản thủ thư/admin/sinh viên |
| 42 | `UPDATE_USER` | `ADMIN` | Có | Cập nhật thông tin tài khoản |
| 43 | `DELETE_USER` | `ADMIN` | Có | Xóa tài khoản người dùng |
| 44 | `GET_DASHBOARD_METRICS` | `ADMIN`, `LIBRARIAN` | Có | Xem thống kê tổng quan thư viện |
| 45 | `GET_STUDENT_DASHBOARD` | `STUDENT` | Có | **Anti-IDOR**: Xem thống kê cá nhân của sinh viên đang đăng nhập |
| 46 | `LIST_AUDIT_LOGS` | `ADMIN` | Có | Xem nhật ký kiểm toán hệ thống |
| 47 | `SEARCH_SUGGESTIONS` | Mọi user có phiên | Có | **Anti-IDOR**: Sinh viên bị cấm tìm gợi ý danh sách sinh viên khác; gợi ý mượn/đặt trước bị giới hạn trong dữ liệu của chính mình |

---

## 3. SQL INJECTION VÀ TÍNH TOÀN VẸN DỮ LIỆU

### 3.1. Rà soát SQL Injection
Đã kiểm tra toàn bộ 9 repository implementations và các service thực thi truy vấn:
- `UserRepositoryImpl.java`
- `StudentRepositoryImpl.java`
- `BookRepositoryImpl.java`
- `BorrowRepositoryImpl.java`
- `FineRepositoryImpl.java`
- `ReservationRepositoryImpl.java`
- `CategoryRepositoryImpl.java`
- `AuthorRepositoryImpl.java`
- `AuditLogRepositoryImpl.java`
- `SearchSuggestionServiceImpl.java`

**Kết quả kiểm tra**:
1. **100% sử dụng `PreparedStatement`**: Mọi tham số từ phía client (chuỗi tìm kiếm, ID, mã sinh viên, ISBN, số trang, kích thước trang, trạng thái) đều được truyền qua dấu chấm hỏi `?`.
2. **Không nối chuỗi trực tiếp**: Không phát hiện bất kỳ câu truy vấn nào ghép trực tiếp chuỗi do người dùng nhập (`String concatenation`) vào mệnh đề SQL.
3. **Phân trang an toàn**: Phân trang trong `BookRepositoryImpl` sử dụng `LIMIT ? OFFSET ?` với tham số là số nguyên đã được kiểm tra tính hợp lệ (`page > 0`, `pageSize > 0`).

### 3.2. Tính toàn vẹn dữ liệu trong giao dịch nghiệp vụ (ACID Transactions)

#### Giao dịch mượn sách (`BorrowServiceImpl.borrowBook`)
1. **Xác thực**:
   - Kiểm tra sinh viên tồn tại và có `status == StudentStatus.ACTIVE`.
   - Kiểm tra sách tồn tại và `status != BookStatus.LOST`.
   - Kiểm tra giới hạn mượn: `currentBorrowCount < maxBorrowLimit`.
   - Ngăn mượn trùng: Gọi `borrowRepository.findActiveBorrow(studentId, bookId)` để đảm bảo sinh viên không mượn cùng lúc 2 cuốn sách giống nhau khi chưa trả cuốn trước.
2. **Đảm bảo tồn kho chống Race Condition (Atomic Inventory Decrement)**:
   - Thực thi cập nhật có điều kiện trực tiếp ở mức hàng database:
     ```sql
     UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0
     ```
   - Nếu `affected_rows == 0`, ném `BookUnavailableException` và rollback ngay lập tức. Đảm bảo dù nhiều luồng đồng thời cố gắng mượn cuốn sách cuối cùng, số lượng tồn kho `available_copies` không bao giờ bị âm.
3. **Cập nhật số sách đang mượn**:
   - `studentRepository.incrementBorrowCount(studentId, conn)`: Tăng `current_borrow_count` thêm 1 trên cùng một Connection.
4. **Tạo phiếu mượn & Ghi Audit Log**:
   - Thêm bản ghi vào bảng `borrow_records`.
   - Tự động chuyển đổi phiếu đặt trước (nếu có) thành `FULFILLED`.
   - Thêm bản ghi vào `audit_logs`.
5. **Commit / Rollback**:
   - Gọi `conn.commit()`. Nếu xảy ra bất kỳ ngoại lệ nào ở các bước trên, khối `catch` thực hiện `conn.rollback()` hoàn toàn, khôi phục trạng thái ban đầu của database.

#### Giao dịch trả sách (`ReturnServiceImpl.returnBook`)
1. **Xác định phiếu mượn**:
   - Tìm kiếm phiếu mượn đang hoạt động bằng `findActiveBorrow(studentId, bookId)`.
2. **Tính phạt quá hạn**:
   - So sánh ngày trả thực tế (`returnDate`) với hạn trả (`dueDate`).
   - Nếu trễ hạn, tính số ngày quá hạn và tạo bản ghi `fines` trên cùng Connection giao dịch.
3. **Cập nhật trạng thái phiếu mượn**:
   - Thực thi câu lệnh có điều kiện:
     ```sql
     UPDATE borrow_records SET status = 'RETURNED', return_date = ? WHERE id = ? AND status = 'ACTIVE'
     ```
   - Nếu bản ghi đã bị trả trước đó hoặc không ở trạng thái `ACTIVE`, câu lệnh trả về `0 rows affected` và giao dịch bị rollback ngay lập tức, ngăn ngừa hoàn toàn lỗi trả sách hai lần (double return).
4. **Khôi phục tồn kho & Số lượng mượn**:
   - `UPDATE books SET available_copies = available_copies + 1 WHERE id = ? AND available_copies < total_copies` (ngăn tồn kho vượt quá tổng số sách).
   - `UPDATE students SET current_borrow_count = current_borrow_count - 1 WHERE id = ? AND current_borrow_count > 0` (ngăn số lượng mượn âm).
5. **Ghi Audit Log & Commit**:
   - Ghi nhật ký trả sách vào `audit_logs` và gọi `conn.commit()`.

#### Thanh toán tiền phạt (`FineServiceImpl.payFine`)
- Kiểm tra tính lũy kế/idempotency: Kiểm tra `fine.isPaid()`. Nếu tiền phạt đã được thanh toán, hàm trả về `true` ngay lập tức mà không thực hiện ghi đè hoặc trừ tiền trùng lặp.
- Đánh dấu đã thanh toán bằng `UPDATE fines SET is_paid = TRUE, paid_date = CURRENT_TIMESTAMP, collected_by_user_id = ? WHERE id = ?`.

---

## 4. MẬT KHẨ, SESSION VÀ THÔNG TIN NHẠY CẢM

### 4.1. Thuật toán băm mật khẩu (Password Hashing)
- **Thuật toán chính**: `PBKDF2WithHmacSHA256` trong lớp [PasswordHasher.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/PasswordHasher.java).
- **Tham số an toàn**:
  - Salt: 16 bytes ngẫu nhiên từ `java.security.SecureRandom`.
  - Số vòng lặp (Iterations): `10,000`.
  - Độ dài khóa sinh: `256 bits`.
  - Định dạng lưu trữ: `PBKDF2$<iterations>$<saltHex>$<hashHex>`.
- **Chống Timing Attack**: So sánh hàm băm bằng `MessageDigest.isEqual(expectedHash, computedHash)` (so sánh mảng byte với thời gian không đổi).
- **Tương thích ngược & Nâng cấp tự động (Backward Compatibility & In-flight Migration)**:
  - Khi xác minh, hệ thống kiểm tra tiền tố `PBKDF2$`. Nếu là mã băm SHA-256 cũ (từ dữ liệu ban đầu trước Phase 1.16), hệ thống dùng fallback SHA-256 để so sánh.
  - Khi người dùng đăng nhập thành công với mật khẩu SHA-256 cũ, `AuthServiceImpl` (dòng 211–219) tự động tạo băm mới chuẩn `PBKDF2` và cập nhật vào MySQL mà không yêu cầu người dùng phải đổi mật khẩu thủ công.

### 4.2. Bảo vệ thông tin nhạy cảm trong Log và Ngoại lệ
- Không in mật khẩu hoặc session token ra server logger.
- Không đưa thông tin mật khẩu vào `AuditLogDTO`.

---

## 5. CẤU HÌNH DATABASE, RMI VÀ AN TOÀN MẠNG

### 5.1. Cơ chế ưu tiên nạp cấu hình cơ sở dữ liệu
Trong [DatabaseConfig.java](file:///D:/workspace/thuvien/server/src/thuvien/server/database/DatabaseConfig.java), thứ tự nạp cấu hình được thiết kế theo các tầng ưu tiên:
1. `db.properties` trên classpath (mặc định cho ứng dụng).
2. Tệp ghi đè cục bộ `test.local.properties` (nếu tồn tại) hoặc qua cờ JVM `-Ddb.config.file=...`.
3. Biến môi trường hệ thống (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
4. Thuộc tính hệ thống JVM (`-Ddb.url=...`, `-Ddb.username=...`, `-Ddb.password=...`).

### 5.2. Đánh giá Git History và `.gitignore`
- `.gitignore` đã cấu hình loại trừ:
  - `server/resources/db.properties`
  - `local.properties`
  - `*.local.properties`
  - `.env`, `.env.*`
  - Các tệp khóa, chứng chỉ (`*.key`, `*.pem`, `*.jks`, `*.keystore`).
- **Kiểm tra lịch sử Git**: Tệp `server/resources/db.properties` trong commit cũ `e0c5a90` chỉ chứa tài khoản dev mặc định của Laragon (`username=root`, `password=` rỗng), không chứa bất kỳ secret môi trường thật nào. Tệp này đã được xóa khỏi Git tracking từ commit `6e30dc4` và chỉ duy trì `db.properties.example` với các giá trị mẫu.

### 5.3. An toàn mạng & Java RMI Deserialization Filter (JEP 290)
- **Cấu hình Serialization Filter**: Lớp [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java) thiết lập bộ lọc JEP 290 ngay tại thời điểm khởi động máy ảo:
  ```java
  public static final String DEFAULT_SERIAL_FILTER =
          "maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;"
          + "thuvien.common.**;"
          + "java.lang.**;"
          + "java.util.**;"
          + "java.math.**;"
          + "java.sql.**;"
          + "java.rmi.**;"
          + "sun.rmi.**;"
          + "com.sun.proxy.**;"
          + "!*";
  ```
  - **Tác dụng**: Giới hạn độ sâu phân cấp đối tượng không quá 15, kích thước gói tin tuần tự hóa tối đa 10 MB, và danh sách trắng (whitelist) nghiêm ngặt chỉ chấp nhận các lớp nghiệp vụ thuộc `thuvien.common` cùng các kiểu dữ liệu chuẩn của JDK. Ký tự phủ định `!*` chặn đứng toàn bộ các payload gadget exploits phổ biến (như Apache Commons Collections, Spring Beans, Groovy, Jython).
- **RMI Port Binding**: RMI Registry chạy trên cổng cố định `1099`, RMI Remote Object được xuất trên cổng cố định `1100` (`LibraryRemoteServiceImpl`), không mở cổng ngẫu nhiên.
- **Loại bỏ hoàn toàn Socket TCP cũ**: Toàn bộ các lớp giao tiếp TCP cũ (`TCPNetworkClient`, `LibraryServer`, `ClientHandler`) đã được loại bỏ; kiến trúc mạng đã thống nhất 100% trên giao thức RMI.

---

## 6. DANH SÁCH CÁC PHÁT HIỆN KIỂM TOÁN VÀ XỬ LÝ (AUDIT FINDINGS)

### Phát hiện SEC-1.24-01: Thiếu thu hồi phiên khi tài khoản bị khóa, tạm ngưng hoặc xóa (Session Invalidation Gap)
- **Mức độ**: **HIGH**
- **Vị trí**:
  - `server/src/thuvien/server/router/RequestRouter.java:518-524` (`handleDeleteStudent`)
  - `server/src/thuvien/server/router/RequestRouter.java:810-822` (`handleUpdateUser`, `handleDeleteUser`)
- **Bằng chứng**: Khi Quản trị viên xóa một tài khoản người dùng (`DELETE_USER`), vô hiệu hóa tài khoản (`UPDATE_USER` với `isActive = false`), hoặc tạm khóa sinh viên (`DELETE_STUDENT`), token phiên của người dùng đó trong `SessionManager` không bị xóa ngay lập tức. Người dùng bị khóa vẫn có thể tiếp tục thực hiện các thao tác trong tối đa 30 phút cho đến khi phiên hết hạn.
- **Tác động**: Tài khoản bị thu hồi quyền hoặc tạm khóa vẫn truy cập được hệ thống trái phép trong phiên làm việc hiện tại.
- **Xử lý (Fixed)**:
  - Bổ sung `sessionManager.invalidateUserSessions(id)` khi xóa user thành công trong `handleDeleteUser`.
  - Bổ sung `sessionManager.invalidateUserSessions(user.getId())` khi cập nhật trạng thái `!user.isActive()` trong `handleUpdateUser`.
  - Bổ sung tra cứu `student.getUserId()` và gọi `sessionManager.invalidateUserSessions(studentUserId)` khi tạm khóa hoặc xóa vĩnh viễn sinh viên trong `handleDeleteStudent`.
- **Trạng thái**: **FIXED** (Xác minh bằng test tự động `test25_SessionInvalidationOnUserDeactivation`).

---

### Phát hiện SEC-1.24-02: Rò rỉ thông báo ngoại lệ nội bộ ra phía Client (Information Disclosure in Exceptions)
- **Mức độ**: **LOW**
- **Vị trí**:
  - `server/src/thuvien/server/router/RequestRouter.java:355`
  - `server/src/thuvien/server/service/impl/AuthServiceImpl.java:238`
- **Bằng chứng**:
  - Trong `RequestRouter.java`: Khối `catch (Exception e)` bắt các lỗi không xác định và trả về:
    `Response.error(reqId, StatusCode.SERVER_ERROR, "Server error: " + e.getMessage());`
  - Trong `AuthServiceImpl.java`: Khi có lỗi kết nối database lúc đăng nhập:
    `throw new AuthenticationException("Dịch vụ xác thực tạm thời không khả dụng: " + e.getMessage());`
  - `e.getMessage()` có thể chứa tên cột, tên bảng, cú pháp SQL bị lỗi hoặc thông tin kết nối máy chủ MySQL.
- **Tác động**: Tiết lộ thông tin cấu trúc cơ sở dữ liệu nội bộ cho người dùng hoặc kẻ tấn công.
- **Xử lý (Fixed)**:
  - Chuẩn hóa thông báo lỗi trả về cho client thành thông báo an toàn, thân thiện: `"Lỗi máy chủ nội bộ. Vui lòng liên hệ quản trị viên."` và `"Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại sau."`.
  - Chi tiết ngoại lệ kỹ thuật và stack trace được ghi đầy đủ vào `LOGGER.log(Level.SEVERE, ...)` tại phía server để phục vụ kiểm tra lỗi kỹ thuật.
- **Trạng thái**: **FIXED** (Xác minh bằng test tự động `test26_UnexpectedExceptionHidesInternalDetailsFromClient`).

---

### Phát hiện SEC-1.24-03: Kênh truyền Java RMI chưa kích hoạt mã hóa TLS (Plaintext Transport over Network)
- **Mức độ**: **MEDIUM**
- **Vị trí**: `server/src/thuvien/server/rmi/LibraryRMIServer.java:111-120`
- **Bằng chứng**: `LocateRegistry.createRegistry(registryPort)` và `UnicastRemoteObject` sử dụng socket TCP mặc định không qua mã hóa TLS/SSL.
- **Tác động**: Trong môi trường mạng LAN mở, mạng Wi-Fi công cộng không có phân đoạn mạng, các gói tin RMI có thể bị bắt lén (sniffing) qua các công cụ như Wireshark. Mặc dù mật khẩu khi đăng nhập không lộ dạng rõ trên database, nhưng session token gửi qua mạng plaintext có thể bị nghe lén nếu kết nối trên hạ tầng mạng không an toàn.
- **Đánh giá & Kế hoạch giảm thiểu (Mitigation Plan)**:
  - Đây là thiết kế chuẩn phục vụ học phần Lập trình Mạng và Mạng máy tính trong môi trường Lab/LAN cục bộ có kiểm soát.
  - Khi triển khai sản xuất (Production Deployment) trên môi trường mạng diện rộng hoặc Wi-Fi không bảo mật: Cần cấu hình `SslRMIClientSocketFactory` và `SslRMIServerSocketFactory` kèm chứng chỉ X.509/Keystore hoặc triển khai trên mạng riêng ảo VPN nội bộ.
- **Trạng thái**: **VERIFIED / MITIGATION DOCUMENTED** (Đã đánh giá rủi ro và lập phương án giảm thiểu, không thay đổi mã nguồn đột ngột để bảo toàn tương thích Java 8/NetBeans lab).

---

### Phát hiện SEC-1.24-04: Cấu hình mặc định Database sử dụng quyền `root` khi thiếu tệp cấu hình
- **Mức độ**: **LOW**
- **Vị trí**: `server/src/thuvien/server/database/DatabaseConfig.java:34, 45-46`
- **Bằng chứng**: Khi tệp `db.properties` vắng mặt trên classpath, hệ thống fallback về tài khoản `username=root`, `password=root` hoặc `password=""`.
- **Tác động**: Vi phạm nguyên tắc đặc quyền tối thiểu (Principle of Least Privilege) trong môi trường sản xuất.
- **Kế hoạch giảm thiểu (Mitigation Plan)**:
  - Trong môi trường phát triển (Laragon/XAMPP trên Windows), tài khoản `root` với mật khẩu rỗng là mặc định tiêu chuẩn của công cụ.
  - Đối với môi trường triển khai thực tế: Hướng dẫn quản trị viên tạo tài khoản chuyên dụng cho ứng dụng (ví dụ: `thuvien_app`) với quyền hạn tối thiểu:
    ```sql
    CREATE USER 'thuvien_app'@'localhost' IDENTIFIED BY 'StrongPasswordHere!';
    GRANT SELECT, INSERT, UPDATE, DELETE ON school_library.* TO 'thuvien_app'@'localhost';
    FLUSH PRIVILEGES;
    ```
- **Trạng thái**: **VERIFIED / DOCUMENTED** (Khuyến cáo triển khai sản xuất).

---

## 7. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG SAU KIỂM TOÁN VÀ KHẮC PHỤC

Đã thực hiện biên dịch và chạy toàn bộ bộ kiểm thử tự động bằng Apache Ant thông qua NetBeans Ant runner:
- Lệnh biên dịch: `& "<NETBEANS_ANT_PATH>\ant.bat" compile compile-test`
- Lệnh chạy kiểm thử: `& "<NETBEANS_ANT_PATH>\ant.bat" test`

### Bảng kết quả thực tế từ XML Test Results (`build/test/results/TEST-*.xml`)

| STT | Suite Kiểm thử | Số Test | Thất bại (Failures) | Lỗi (Errors) | Bỏ qua (Skipped) | Thời gian thực thi |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 0 | 0 | 0 | 0.252s |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 0 | 0 | 0 | 0.303s |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 0 | 0 | 0 | 0.056s |
| 4 | `thuvien.server.RequestRouterTest` | 26 | 0 | 0 | 0 | 0.463s |
| 5 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 0 | 0 | 0 | 0.926s |
| 6 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 0 | 0 | 0 | 0.933s |
| 7 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 0 | 0 | 0 | 0.396s |
| 8 | `thuvien.server.service.AuthServiceTest` | 5 | 0 | 0 | 0 | 0.355s |
| 9 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 0 | 0 | 0 | 0.398s |
| 10 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 0 | 0 | 0 | 0.541s |
| 11 | `thuvien.server.service.FineServiceTest` | 3 | 0 | 0 | 0 | 0.050s |
| 12 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 33 | 0 | 0 | 0 | 4.383s |
| 13 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 0 | 0 | 0 | 0.066s |
| **TỔNG CỘNG** | **13 Test Suites** | **121** | **0** | **0** | **0** | **8.722s** |

**Đánh giá**:
- Tăng từ 118 tests lên **121 tests** nhờ bổ sung 3 test case chuyên sâu cho kiểm toán bảo mật:
  - `test25_SessionInvalidationOnUserDeactivation`: Xác nhận thu hồi phiên tức thời khi user bị xóa/khóa.
  - `test26_UnexpectedExceptionHidesInternalDetailsFromClient`: Xác nhận lỗi máy chủ nội bộ không rò rỉ SQL/Stacktrace.
  - `test27_StudentCannotAccessOtherStudentActiveBorrows`: Xác nhận chống IDOR khi sinh viên tra cứu phiếu mượn của tài khoản khác.
- **Tỷ lệ vượt qua**: **121/121 tests PASS (100%)**, 0 failures, 0 errors, 0 skipped.

---

## 8. KẾT LUẬN VÀ KHUYẾN NGHỊ PHÁT HÀNH

1. **Tính sẵn sàng phát hành**: Hệ thống đã đạt mức bảo mật vững chắc cho môi trường bài tập lớn / đồ án môn học Lập trình mạng. Không còn bất kỳ lỗ hổng mức **CRITICAL** hoặc **HIGH** nào chưa được xử lý.
2. **Không có rò rỉ secret**: Toàn bộ cấu hình nhạy cảm đã được loại trừ khỏi Git và bảo vệ bằng biến môi trường hoặc tệp unversioned.
3. **Tính toàn vẹn dữ liệu**: Các luồng nghiệp vụ cốt lõi (Mượn sách, Trả sách, Nộp phạt) được bọc trong các giao dịch ACID với khóa điều kiện ở mức hàng, chống hiện tượng âm tồn kho và trả sách trùng lặp khi chạy đa luồng.
4. **Không vi phạm quy tắc an toàn**: Không thực hiện `git commit` hay `git push`, không sửa đổi cơ sở dữ liệu thật, không phá vỡ khả năng tương thích Java 8 và NetBeans 8.0.2.
