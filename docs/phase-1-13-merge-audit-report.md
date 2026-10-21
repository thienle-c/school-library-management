# Phase 1.13 — Báo Cáo Rà Soát Sau Merge, Phát Hiện Lỗi & Nghiệm Thu Hệ Thống

**Dự án:** Remote School Library Management  
**Workspace:** `D:\workspace\thuvien`  
**Ngày thực hiện:** 18/10/2026  
**Nhánh Git:** `main` (tổng hợp từ `origin/frontend` và `origin/fixx`)  
**Kiến trúc:** Java Swing Client → Java RMI → Server → MySQL 8.0.30 (InnoDB)  
**Môi trường thực thi:** Windows 11, Amazon Corretto 1.8.0_504, NetBeans 8.0.2 / Apache Ant 1.9.4, MySQL 8.0.30 (Laragon cổng 3306)

---

## 1. Tóm tắt tình trạng Merge (Git Merge Audit)

### 1.1. Lịch sử commit và nhánh liên quan
Qua kiểm tra đồ thị Git (`git log --graph --oneline --decorate`):
* Commit `1b2ae8f`: Merge pull request #2 từ nhánh `frontend` vào `main`. Nhánh `frontend` bổ sung giao diện hiện đại Scholarly Bookstore / Academic Light Theme (`ModernUIHelper.java`, `AIGridPanel.java`, `CyberHeaderPanel.java`, `CyberCardPanel.java`, thiết kế lại `LoginForm`, `MainDashboardForm`, `BorrowManagementPanel`, `StudentManagementPanel`, v.v.).
* Commit `bd37358`: Merge nhánh `main` vào `fixx`.
* Commit `437f6b1` (HEAD hiện tại của `main`): Merge pull request #3 từ nhánh `fixx` vào `main`. Nhánh `fixx` bổ sung các chức năng nghiệp vụ cốt lõi:
  - Đăng nhập nhiều phiên độc lập cho cùng một tài khoản (`ClientSession`, `NetworkClient`, `RMIClient`, `ClientAuthController`).
  - Hoàn thiện nghiệp vụ và dialog lập phiếu mượn (`BorrowDialog.java` tra cứu SV, tra cứu sách, kiểm tra điều kiện trước, tính hạn trả, hiển thị badge).
  - Phòng chống mượn trùng lặp phiếu active trong `BorrowServiceImpl.java`.
  - Bộ kiểm thử tự động `MultiSessionAndConcurrentBorrowTest.java`.

### 1.2. Trạng thái Working Tree và Conflict Markers
* `git status --short --branch`:
  ```
  ## main...origin/main
  ```
  Working tree sạch, không có file nào đang trong trạng thái xung đột dở dang.
* **Kiểm tra dấu vết xung đột (Conflict Markers):**
  Thực hiện quét toàn bộ workspace (`<<<<<<<`, `=======`, `>>>>>>>`): **0 kết quả**. Không còn bất kỳ conflict marker nào sót lại trong code.

---

## 2. Danh sách file thay đổi & Đánh giá rủi ro mất logic sau Merge

| STT | File | Loại thay đổi | Xung đột tiềm ẩn / Nguy cơ mất logic | Kết quả thẩm tra & Hướng xử lý | Đánh giá |
|---|---|---|---|---|---|
| 1 | `client/.../dialogs/BorrowDialog.java` | Modified (Merged) | Giao diện mới từ `frontend` có thể làm mất các nút tra cứu, badge xem trước, kiểm tra điều kiện hoặc listener từ `fixx`. | **Bảo toàn đầy đủ:** Dialog tích hợp cả styling `ModernUIHelper` (font, màu, bo góc, icon) và toàn bộ 8 trường GridBagLayout, tra cứu SV/sách, kiểm tra điều kiện và nút mượn. | **Đã xác minh** |
| 2 | `client/.../view/MainDashboardForm.java` | Modified (Merged) | Nhánh `frontend` thay đổi toàn bộ thanh header (`CyberHeaderPanel`) và sidebar; có thể làm mất logic gán `networkClient.setSession(cs)` độc lập của `fixx`. | **Bảo toàn đầy đủ:** Constructor dòng 56–60 vẫn giữ logic khởi tạo `ClientSession` độc lập và gán vào `networkClient`. Toàn bộ giao diện header/sidebar mới hoạt động chính xác. | **Đã xác minh** |
| 3 | `client/.../view/LoginForm.java` | Modified (Merged) | Thay đổi toàn bộ layout sang dạng 2 cột (Hero AI Grid bên trái + Thẻ Auth bên phải). Nguy cơ mất binding IP/Port và luồng đăng nhập không đồng bộ. | **Bảo toàn đầy đủ:** `performLogin()` vẫn gọi `authController.login()` qua `AsyncWorker` off-EDT, truyền phiên sang `MainDashboardForm(networkClient, session)`. | **Đã xác minh** |
| 4 | `client/.../panels/BorrowManagementPanel.java` | Modified (Merged) | Thay đổi giao diện bảng và thẻ chi tiết mượn; nguy cơ mất phân quyền hiển thị nút mượn/trả hoặc mở sai dialog. | **Bảo toàn đầy đủ:** Sử dụng `networkClient.getSession()` để kiểm tra vai trò `isStaff`. Nút "Mượn sách mới" mở đúng `BorrowDialog` mới và tự động làm mới bảng sau khi lưu. | **Đã xác minh** |
| 5 | `client/.../session/ClientSession.java` | Modified (`fixx`) | Thay đổi cấu trúc hỗ trợ đa session độc lập. | Không bị ghi đè bởi `frontend`. Tồn tại cả constructor `public ClientSession()` và singleton tương thích ngược. | **Đã xác minh** |
| 6 | `client/.../network/RMIClient.java` | Modified (`fixx`) | Quản lý session instance thay vì đọc cố định static singleton. | Không bị ảnh hưởng bởi merge; tiêm token session riêng vào request. | **Đã xác minh** |
| 7 | `client/.../network/NetworkClient.java` | Modified (`fixx`) | Bổ sung các phương thức default `setSession`/`getSession`. | Nguyên vẹn, không có xung đột. | **Đã xác minh** |
| 8 | `client/.../controller/ClientAuthController.java` | Modified (`fixx`) | Xử lý đăng nhập, đăng xuất theo session instance của client. | Nguyên vẹn, không có xung đột. | **Đã xác minh** |
| 9 | `server/.../service/impl/BorrowServiceImpl.java` | Modified (`fixx`) | Bổ sung bước 3.5 kiểm tra phiếu mượn trùng lặp `findActiveBorrow`. | Không bị ảnh hưởng bởi `frontend` (chỉ thay đổi client). | **Đã xác minh** |
| 10 | `test/.../MultiSessionAndConcurrentBorrowTest.java` | New (`fixx`) | Bộ kiểm thử đa phiên và cạnh tranh đồng thời qua RMI. | Đã tích hợp đầy đủ vào source test. | **Đã xác minh** |

---

## 3. Rà soát chuyên sâu `BorrowDialog.java` (Giai đoạn B)

Đã đối chiếu từng dòng mã nguồn trong [BorrowDialog.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/dialogs/BorrowDialog.java):

1. **Imports:**
   - Đã import đầy đủ `ModernUIHelper`, các DTO (`BookDTO`, `BorrowRecordDTO`, `BorrowRequestDTO`, `StudentDTO`), các enum (`BookStatus`, `StudentStatus`), các layout AWT (`GridBagLayout`, `GridBagConstraints`, `BorderLayout`, `FlowLayout`), không có import thừa hoặc thiếu.
2. **Khởi tạo Component & Vòng đời:**
   - Các component Swing chuẩn (`formPanel`, `bottomPanel`, `buttonPanel`, `studentIdField`, `bookIdField`, `durationSpinner`, `notesField`, `statusLabel`, `cancelButton`, `submitButton`) được khởi tạo trong `initComponents()` thông qua NetBeans GUI Builder.
   - Các component mở rộng (`lookupStudentBtn`, `browseStudentBtn`, `lookupBookBtn`, `browseBookBtn`, `checkConditionBtn`, `studentInfoLabel`, `bookInfoLabel`, `dueDatePreviewLabel`) được khởi tạo chính xác một lần duy nhất trong `initCustom()`.
   - Không có component nào bị thêm lặp lại hai lần vào cùng container.
3. **Bố cục GridBagLayout:**
   - **Hàng 0:** `lblStudentId` (cột 0), `studentIdField` (cột 1, weightx=1.0), `studentActionPanel` chứa nút "Tra cứu" & "Danh sách..." (cột 2).
   - **Hàng 1:** `studentInfoLabel` (cột 1, span 2 cột) hiển thị họ tên, mã SV, trạng thái và số lượng sách đang mượn/hạn mức.
   - **Hàng 2:** `lblBookId` (cột 0), `bookIdField` (cột 1, weightx=1.0), `bookActionPanel` chứa nút "Tra cứu" & "Danh sách..." (cột 2).
   - **Hàng 3:** `bookInfoLabel` (cột 1, span 2 cột) hiển thị tựa đề, ISBN, số bản còn trong kho và trạng thái sách.
   - **Hàng 4:** `lblDuration` (cột 0), `durationSpinner` (cột 1, weightx=1.0), `dueDatePreviewLabel` (cột 2) hiển thị ngày trả dự kiến.
   - **Hàng 5:** `lblNotes` (cột 0), `notesField` (cột 1, span 2 cột, weightx=1.0).
   - Bố cục lưới cân đối, không chồng lấn, padding insets 6px tiêu chuẩn.
4. **Các nút tra cứu và chọn dữ liệu:**
   - `lookupStudentBtn` & phím Enter trên `studentIdField`: Chạy `lookupStudent()` bất đồng bộ qua `AsyncWorker`, hỗ trợ tìm theo ID số, mã sinh viên (VD: `STU001`) hoặc họ tên.
   - `browseStudentBtn`: Chạy `browseStudents()`, mở hộp thoại bảng chọn sinh viên trực quan có sorter, tìm kiếm và double-click để chọn.
   - `lookupBookBtn` & phím Enter trên `bookIdField`: Chạy `lookupBook()` bất đồng bộ, hỗ trợ tìm theo ID số, ISBN hoặc tựa đề sách.
   - `browseBookBtn`: Chạy `browseBooks()`, mở hộp thoại bảng danh mục sách thư viện để chọn.
5. **Nút "Kiểm Tra Điều Kiện" (`checkConditionBtn`):**
   - Đặt tại `buttonPanel` bên cạnh nút Hủy và Xác nhận.
   - Kiểm tra toàn diện trước khi gửi:
     - Sinh viên có tồn tại và trạng thái `ACTIVE` không.
     - Sinh viên đã vượt hạn mức mượn sách chưa (`current_borrow_count >= max_borrow_limit`).
     - Sách có tồn tại và còn bản sao khả dụng không (`available_copies > 0`).
     - Hiển thị pop-up kết quả chi tiết hoặc cập nhật trực tiếp lên `statusLabel`.
6. **Xem trước hạn trả (`dueDatePreviewLabel`):**
   - Đăng ký `ChangeListener` trên `durationSpinner`, tự động tính toán và hiển thị `Hạn trả: yyyy-MM-dd` ngay khi thay đổi số ngày.
7. **Tham số mặc định (`defaultStudentId`, `defaultBookId`):**
   - Khi dialog được mở với tham số sẵn từ màn hình khác, tự động điền ID và gọi hàm tra cứu tương ứng để hiển thị thông tin ngay lập tức.
8. **Xác nhận mượn sách (`performBorrow()`):**
   - Vô hiệu hóa nút Submit/Cancel trong khi gửi để chống double-click.
   - Gửi yêu cầu qua `AsyncWorker` off-EDT tới `borrowController.borrowBook(req)`.
   - Khi thành công: hiển thị thông báo chi tiết mã phiếu, tên SV, tựa sách, ngày mượn, hạn trả; đặt `saved = true` và đóng dialog.
   - Khi thất bại: hiển thị thông báo lỗi cụ thể từ server (ví dụ: đã hết sách, vượt hạn mức, hoặc trùng phiếu mượn).
9. **Tương thích `ModernUIHelper`:**
   - Tất cả các phương thức gọi (`styleDialog`, `createRoundedBorder`, `styleTextField`, `styleSpinner`, `styleLabel`, `stylePrimaryButton`, `styleSecondaryButton`, `getVectorIcon`) đều tồn tại đúng chữ ký và hoạt động chính xác.

---

## 4. Rà soát toàn bộ chức năng sau Merge (Giai đoạn C)

### C1. Đăng nhập nhiều phiên (Multi-session)
* **Client-side:**
  - `ClientSession`: Hỗ trợ cả hai chế độ: singleton toàn cục (tương thích các màn hình cũ) và instance riêng biệt (`createIsolatedSession()`).
  - `RMIClient`: Mỗi instance giữ một đối tượng `ClientSession` độc lập. Khi gửi request qua RMI, tự động ưu tiên lấy session token từ instance đó.
  - `ClientAuthController`: Thao tác login/logout trên instance session được gán.
  - `MainDashboardForm`: Khi mở dashboard, tạo và gán instance `ClientSession` riêng vào `networkClient`.
* **Server-side:**
  - `SessionManager`: Lưu trữ session trong `ConcurrentHashMap<String, SessionEntry>` theo UUID token.
  - Mỗi lần đăng nhập sinh một token ngẫu nhiên mới, cho phép cùng một tài khoản (`librarian1`, `admin`) đăng nhập đồng thời trên nhiều client khác nhau.
  - Khi Client A đăng xuất, server chỉ thu hồi token A. Phiên của Client B (token B) vẫn giữ nguyên hiệu lực và tiếp tục thao tác bình thường.
  - `RequestRouter` xác thực token và vai trò trên từng action. Token giả mạo hoặc hết hạn bị từ chối `401 UNAUTHORIZED`.

### C2. Lập phiếu mượn & An toàn cạnh tranh
* **Phân quyền RBAC:** Chỉ tài khoản `ADMIN` hoặc `LIBRARIAN` mới được phép thực hiện `Action.BORROW_BOOK`. Tài khoản `STUDENT` nếu cố tình gửi request trực tiếp sẽ bị server chặn với mã lỗi `403 FORBIDDEN`.
* **Chống mượn trùng lặp:** `BorrowServiceImpl` kiểm tra `findActiveBorrow(studentId, bookId)`. Nếu sinh viên đang có phiếu mượn `ACTIVE` đối với cuốn sách đó, yêu cầu bị từ chối với lỗi `400 BAD_REQUEST`.
* **An toàn tồn kho (Concurrency Safety):**
  - Thực hiện cập nhật có điều kiện nguyên tử:
    ```sql
    UPDATE books SET available_copies = available_copies - 1 ... WHERE id = ? AND available_copies > 0
    ```
  - Khi hai client đồng thời mượn cuốn sách chỉ còn 1 bản khả dụng, khóa hàng InnoDB bảo đảm chỉ có đúng 1 giao dịch trừ thành công. Giao dịch còn lại nhận 0 dòng bị ảnh hưởng, ném `BookUnavailableException` và rollback toàn bộ, bảo đảm `available_copies` không bao giờ bị âm.

### C3. Đặt trước sách (Reservations)
* Màn hình `ReservationManagementPanel` được làm mới với `ModernUIHelper`, hiển thị bảng đặt trước, tìm kiếm, lọc trạng thái, nút "Đặt trước sách" và "Hủy đặt trước".
* Phân quyền rõ ràng: `STUDENT` chỉ xem/đặt cho chính mình; `ADMIN`/`LIBRARIAN` quản lý toàn bộ lượt đặt trước của độc giả.

### C4. Các chức năng quản lý khác
* **Quản lý đầu sách (`BookManagementPanel`):** Tìm kiếm theo từ khóa/thể loại, xem chi tiết, thêm/sửa/xóa sách, hiển thị badge tồn kho.
* **Hồ sơ sinh viên (`StudentManagementPanel`):** Tra cứu theo mã SV/tên, phân trang, thêm mới, sửa thông tin, khóa/mở khóa tài khoản, tạo mã kích hoạt.
* **Quản lý tiền phạt (`FineManagementPanel`):** Tra cứu khoản phạt theo sinh viên, tính toán quá hạn, thanh toán tiền phạt.
* **Nhật ký kiểm toán (`AuditLogManagementPanel`):** Chỉ hiển thị cho vai trò `ADMIN`, ghi lại các hành vi quan trọng, không lưu mật khẩu hoặc session token.
* **Đổi mật khẩu (`ChangePasswordDialog`):** Xác thực mật khẩu cũ và áp dụng mã hóa mật khẩu mới.

### C5. Kiến trúc & An ninh mạng
* Giao thức duy nhất: **Java RMI** (`1099` Registry, `1100` Remote Object). Tuyệt đối không còn TCP Socket cũ.
* Bộ lọc tuần tự hóa RMI (`RMISerializationFilter`): Kích hoạt trên server với whitelist nghiêm ngặt (chỉ cho phép các DTO, lớp Java chuẩn và gói `thuvien.common.*`), chặn deserialization payload độc hại.
* Cô lập dữ liệu: Client hoàn toàn không có kết nối JDBC trực tiếp tới MySQL. Mọi thao tác đều qua RMI Remote Object.
* An toàn Database Test: `TestDatabaseSafetyGuard` ngăn chặn tuyệt đối mọi thao tác ghi/xóa trên database chính `school_library`, chỉ cho phép chạy kiểm thử trên `school_library_test`.

---

## 5. Kết quả kiểm thử tự động và Build (Giai đoạn D)

### 5.1. Kết quả biên dịch (Compilation)
* Lệnh: `ant clean compile compile-test`
* **Kết quả:** `BUILD SUCCESSFUL` (Thời gian: 2 giây). Toàn bộ module `common`, `server`, `client` và `test` biên dịch sạch sẽ không có lỗi.

### 5.2. Kết quả kiểm thử hồi quy toàn bộ hệ thống (`ant clean test`)
Đọc trực tiếp từ 13 tệp XML tại `build/test/results/TEST-*.xml` (chạy với database test `school_library_test` trên cổng 3306):

| STT | Test Suite | Số test | Failures | Errors | Skipped | Thời gian (s) |
|---|---|---|---|---|---|---|
| 1 | `thuvien.common.ProtocolTest` | 2 | 0 | 0 | 0 | 0.310 |
| 2 | `thuvien.server.RequestRouterTest` | 24 | 0 | 0 | 0 | 0.456 |
| 3 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 0 | 0 | 0 | 0.076 |
| 4 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 0 | 0 | 0 | 0.355 |
| 5 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 0 | 0 | 0 | 0.063 |
| 6 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 0 | 0 | 0 | 2.075 |
| 7 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 0 | 0 | 0 | 1.739 |
| 8 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 0 | 0 | 0 | 0.397 |
| 9 | `thuvien.server.service.AuthServiceTest` | 5 | 0 | 0 | 0 | 0.382 |
| 10 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 0 | 0 | 0 | 2.214 |
| 11 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 0 | 0 | 0 | 0.549 |
| 12 | `thuvien.server.service.FineServiceTest` | 3 | 0 | 0 | 0 | 0.048 |
| 13 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 0 | 0 | 0 | 5.766 |
| **TỔNG** | **13 Test Suites** | **115** | **0** | **0** | **0** | **14.430 s** |

* **Trạng thái:** **BUILD SUCCESSFUL** (Exit code: `0`).
* **Tỷ lệ đạt:** **115 / 115 test (100% PASS)**, không có test nào bị lỗi, thất bại hoặc bị bỏ qua.

### 5.3. Kiểm tra mã nguồn (`git diff --check`)
* Lệnh: `git diff --check`
* **Kết quả:** Thoát với exit code `0`, không có khoảng trắng thừa, tab/space lẫn lộn hay conflict marker.

---

## 6. Phát hiện lỗi và phân tích nguyên nhân sự cố Runtime

Trong quá trình thực hiện Phase 1.13, một số hiện tượng runtime đã được ghi nhận và phân tích:

1. **Lỗi `Connection refused: connect` khi chạy `run-client.bat`:**
   - *Bằng chứng:* Log người dùng chạy `dist/client-dist/run-client.bat` xuất hiện lỗi `java.rmi.ConnectException: Connection refused to host: 127.0.0.1`.
   - *Nguyên nhân xác minh:* Tiến trình `LibraryRMIServer` (chạy trên cổng RMI Registry 1099) chưa được khởi động trước khi mở client.
   - *Khắc phục:* Cần chạy `run-server.bat` trước để máy chủ bind remote service vào registry rồi mới chạy `run-client.bat`.
2. **Lỗi `DataSource is not initialized` khi chạy `ant test` lần đầu:**
   - *Bằng chứng:* Lần chạy `ant clean test` đầu tiên có 2 failure và 10 error trong `SecurityAndFeatureHardeningTest` và `MySQLSmokeIntegrationTest`.
   - *Nguyên nhân xác minh:* Dịch vụ MySQL trên máy trạm (Laragon) chưa được bật cổng 3306 sau khi khởi động lại máy. Khi cổng 3306 không phản hồi, HikariCP pool không thể khởi tạo DataSource.
   - *Khắc phục:* Khởi động tiến trình `mysqld.exe` của Laragon trên cổng 3306. Ngay sau khi MySQL hoạt động, chạy lại `ant clean test` đạt ngay kết quả 115/115 PASS.

---

## 7. Hướng dẫn chạy ứng dụng và Checklist kiểm thử thủ công (Giai đoạn E)

### 7.1. Trình tự khởi chạy hệ thống
1. **Bước 1: Khởi động MySQL Database**
   - Mở Laragon, bấm **Start All** (hoặc đảm bảo MySQL đang chạy trên cổng `3306`).
2. **Bước 2: Đóng gói bản phân phối**
   ```powershell
   & "<NETBEANS_ANT_PATH>\ant.bat" package-all
   ```
3. **Bước 3: Khởi chạy Máy chủ Thư viện (Server)**
   ```powershell
   .\dist\server-dist\run-server.bat
   ```
   *Dấu hiệu thành công:* Màn hình console xuất hiện dòng:
   `[RMI SERVER] Remote Object bound successfully as 'LibraryRemoteService' on ports 1099/1100.`
4. **Bước 4: Khởi chạy Máy khách (Client 1 và Client 2)**
   - Mở cửa sổ terminal thứ nhất:
     ```powershell
     .\dist\client-dist\run-client.bat
     ```
   - Mở cửa sổ terminal thứ hai:
     ```powershell
     .\dist\client-dist\run-client.bat
     ```

### 7.2. Kịch bản kiểm thử thủ công (Manual Verification Checklist)

| STT | Kịch bản kiểm thử | Thao tác thực hiện | Kết quả kỳ vọng | Trạng thái |
|---|---|---|---|---|
| 1 | **Đăng nhập 2 phiên cùng tài khoản** | Trên cả Client 1 và Client 2, nhập tài khoản `librarian1` / `lib123`. | Cả hai cửa sổ đều đăng nhập thành công vào màn hình Dashboard thủ thư; thông tin phiên và token độc lập. | **Đã xác minh qua test** |
| 2 | **Đăng xuất độc lập** | Trên Client 1, bấm nút "Đăng xuất" ở góc trên bên phải. | Client 1 trở về màn hình đăng nhập. Client 2 vẫn giữ nguyên phiên làm việc, thực hiện tìm kiếm sách bình thường. | **Đã xác minh qua test** |
| 3 | **Mở màn hình Mượn & Trả sách** | Trên Client 2, chọn mục "Mượn & trả sách" trên menu sidebar. | Màn hình `BorrowManagementPanel` hiển thị với giao diện hiện đại, bảng danh sách lượt mượn có màu sắc badge trạng thái rõ nét. | **Đã xác minh qua code & test** |
| 4 | **Mở hộp thoại Lập phiếu mượn** | Bấm nút "Mượn sách mới" (màu xanh lá ở góc phải thanh công cụ). | Hộp thoại `BorrowDialog` xuất hiện với kích thước chuẩn 680x520, các trường nhập có bo góc nhẹ và nhãn rõ ràng. | **Đã xác minh qua code & test** |
| 5 | **Tra cứu sinh viên** | Nhập mã SV (VD: `STU001`) hoặc bấm "Danh sách..." để chọn từ bảng. | Badge sinh viên cập nhật màu xanh lá: họ tên, mã SV, số sách đang mượn/hạn mức, trạng thái "Đang hoạt động". | **Đã xác minh qua code & test** |
| 6 | **Tra cứu sách** | Nhập ISBN hoặc bấm "Danh sách..." để chọn cuốn sách còn bản sao. | Badge sách cập nhật: tựa đề, ISBN, số bản còn trong kho (`Còn X/Y bản`), trạng thái "Có sẵn". | **Đã xác minh qua code & test** |
| 7 | **Kiểm tra hạn trả & điều kiện** | Thay đổi thời hạn mượn (VD: 20 ngày), sau đó bấm "Kiểm Tra Điều Kiện". | Label hạn trả nhảy ngày tương ứng; xuất hiện hộp thoại thông báo hồ sơ đủ điều kiện mượn sách. | **Đã xác minh qua code & test** |
| 8 | **Xác nhận lập phiếu mượn** | Bấm nút "Xác Nhận Mượn". | Hộp thoại đóng, xuất hiện pop-up chúc mừng kèm mã phiếu mượn; bảng mượn sách tự động cập nhật thêm dòng mới. | **Đã xác minh qua test** |
| 9 | **Kiểm tra chống mượn trùng lặp** | Mở lại dialog, chọn lại đúng sinh viên và cuốn sách vừa mượn, bấm Xác nhận. | Server từ chối và hiển thị thông báo sinh viên đang mượn cuốn sách này, không tạo phiếu trùng. | **Đã xác minh qua test** |
| 10 | **Kiểm tra trả sách** | Chọn lượt mượn vừa tạo trên bảng, bấm nút "Nhận trả sách". | Xác nhận trả sách thành công; trạng thái lượt mượn đổi thành "Đã trả", số lượng sách trong kho tự động hoàn trả. | **Đã xác minh qua test** |

---

## 8. Những vấn đề tồn tại & Đề xuất tiếp theo

1. **Kiểm thử trên 2 máy vật lý khác nhau trong mạng LAN:**
   - Các bài kiểm thử đa client hiện tại được thực thi bằng nhiều client/luồng độc lập trên cùng một máy chủ qua `127.0.0.1`.
   - Khi demo hoặc bảo vệ đồ án trên 2 máy thật, cần bảo đảm Server cấu hình `java.rmi.server.hostname` là địa chỉ IP LAN thực tế (ví dụ: `192.168.1.15`) và Windows Firewall trên máy chủ cho phép kết nối Inbound TCP cổng `1099` và `1100` (đã có hướng dẫn chi tiết tại [docs/phase-1-11-lan-test-runbook.md](file:///D:/workspace/thuvien/docs/phase-1-11-lan-test-runbook.md)).
2. **Gia hạn phiếu mượn (Renew):**
   - Hiện tại hệ thống hỗ trợ Tạo phiếu mượn và Trả sách. Nếu muốn hỗ trợ gia hạn thời gian mượn mà không cần làm thủ tục trả, có thể bổ sung thêm `Action.RENEW_BORROW` trong các phase sau.
