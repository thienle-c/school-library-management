# Phase 1.12 Acceptance Report — Hoàn Thiện Lập Phiếu Mượn và Đăng Nhập Nhiều Phiên

**Dự án:** Remote School Library Management  
**Workspace:** `D:\workspace\thuvien`  
**Ngày thực hiện:** 15/10/2026  
**Kiến trúc:** Java Swing Client → Java RMI → Server → MySQL (InnoDB)  
**Môi trường thử nghiệm:** Windows 11, OpenJDK / Amazon Corretto 1.8.0_504, NetBeans 8.0.2 / Ant, MySQL 8.0.30 (Laragon)

---

## 1. Nguyên nhân lỗi ban đầu và bằng chứng từ code

### 1.1. Luồng lập phiếu mượn (Borrow Record Creation)
* **Thực trạng giao diện:** Màn hình `BorrowManagementPanel` đã có nút "Tạo Phiếu Mượn", mở ra `BorrowDialog`. Tuy nhiên, `BorrowDialog` trước đây chỉ là biểu mẫu nhập thô sơ bằng `JTextField` cho mã ID số (`studentIdField`, `bookIdField`), thiếu cơ chế tra cứu/tìm kiếm sinh viên (theo mã số sinh viên, họ tên) và sách (theo ISBN, nhan đề), không có bộ kiểm tra điều kiện trước (pre-validation preview) và không hiển thị thông tin trực quan để nhân viên thủ thư xác nhận trước khi lập phiếu.
* **Nguy cơ trùng lặp phiếu mượn (Duplicate Active Loan):** Trước khi sửa, `BorrowServiceImpl` kiểm tra số lượng sách tồn (`available_copies > 0`), trạng thái sinh viên (`ACTIVE`) và hạn mức mượn (`current_borrow_count < max_borrow_limit`). Tuy nhiên, thiếu bước kiểm tra xem sinh viên này hiện tại có đang giữ một phiếu mượn `ACTIVE` cho chính cuốn sách đó hay không. Nếu thủ thư bấm gửi đúp hoặc gửi nhầm, hệ thống sẽ tạo hai phiếu mượn trùng lặp cho cùng một sinh viên và cuốn sách.
* **Cạnh tranh tồn kho (Concurrency):** `BorrowServiceImpl` và `BookRepositoryImpl` đã áp dụng điều kiện trừ nguyên tử:
  ```sql
  UPDATE books SET available_copies = available_copies - 1,
                   status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END
  WHERE id = ? AND available_copies > 0
  ```
  kết hợp với cơ chế Transaction Rollback khi số dòng cập nhật `= 0` (ném `BookUnavailableException`). Nhờ đó, bảo đảm được tính bất biến không bị âm kho dưới tầng database.

### 1.2. Cơ chế phiên làm việc (Session Management)
* **Phía Server:** `SessionManager` trên server bản chất sử dụng `ConcurrentHashMap<String, SessionEntry>` đánh chỉ mục theo `token` (chuỗi UUID ngẫu nhiên). Do đó, server vốn đã hỗ trợ lưu trữ nhiều token session độc lập.
* **Phía Client:** Điểm nghẽn hạn chế nhiều phiên nằm hoàn toàn ở tầng Client:
  1. `ClientSession` được thiết kế dưới dạng singleton toàn cục tĩnh duy nhất (`private static ClientSession instance = new ClientSession()`).
  2. `RMIClient` khi gửi request qua hàm `send(Request request)` tự động trích xuất token từ `ClientSession.getInstance().getToken()`.
  3. Khi hai cửa sổ/instance Client chạy trên cùng máy hoặc trong cùng JVM (hoặc chạy đa luồng), client thứ hai đăng nhập sẽ ghi đè `ClientSession.getInstance()`. Khi một client gọi `logout()`, `ClientSession.getInstance().clear()` khiến toàn bộ client khác mất session.

---

## 2. Các file đã thay đổi

| STT | Tệp tin | Vị trí | Mục đích thay đổi |
|---|---|---|---|
| 1 | `BorrowServiceImpl.java` | `server/src/thuvien/server/service/impl/` | Bổ sung bước 3.5: Kiểm tra `borrowRepository.findActiveBorrow(studentId, bookId)` để từ chối tạo phiếu mượn trùng lặp với thông báo rõ ràng. |
| 2 | `ClientSession.java` | `client/src/thuvien/client/session/` | Thêm constructor `public ClientSession()` và factory method `createIsolatedSession()` hỗ trợ phiên cô lập cho từng client instance bên cạnh singleton tương thích ngược. |
| 3 | `NetworkClient.java` | `client/src/thuvien/client/network/` | Bổ sung các phương thức default `setSession(ClientSession)`, `getSession()`, `setSessionToken(String)`. |
| 4 | `RMIClient.java` | `client/src/thuvien/client/network/` | Bổ sung instance field `clientSession`, cho phép mỗi kết nối RMI gắn liền với một phiên client độc lập; ưu tiên token phiên riêng khi tiêm vào `Request`. |
| 5 | `ClientAuthController.java` | `client/src/thuvien/client/controller/` | Bổ sung constructor nhận `ClientSession` riêng; cập nhật `login()` và `logout()` thao tác trực tiếp trên phiên được gán thay vì ép ghi đè singleton tĩnh. |
| 6 | `MainDashboardForm.java` | `client/src/thuvien/client/view/` | Gán session độc lập vào `networkClient.setSession(cs)` khi khởi tạo dashboard để bảo toàn phiên suốt vòng đời giao diện. |
| 7 | `BorrowManagementPanel.java` | `client/src/thuvien/client/view/panels/` | Ưu tiên lấy session từ `networkClient.getSession()` để kiểm tra phân quyền hiển thị chức năng lập phiếu mượn cho Admin/Librarian. |
| 8 | `BorrowDialog.java` | `client/src/thuvien/client/view/dialogs/` | Hoàn thiện giao diện lập phiếu mượn: bổ sung tra cứu sinh viên, tra cứu sách qua dialog tìm kiếm trực quan; badge xem trước thông tin; nút "Kiểm Tra Điều Kiện"; tính trước hạn trả; xử lý kết quả thông báo chi tiết. |
| 9 | `MultiSessionAndConcurrentBorrowTest.java` | `test/thuvien/server/rmi/` | Test suite tự động hóa cho Phase 1.12 kiểm thử: đa phiên cùng tài khoản, đăng xuất độc lập, phân quyền RBAC và cạnh tranh lập phiếu mượn đồng thời. |

---

## 3. Luồng lập phiếu mượn trước và sau khi sửa

### 3.1. Trước khi sửa
1. Thủ thư mở `BorrowDialog`, phải nhớ và nhập thủ công ID dạng số của sinh viên và sách.
2. Không có nút tra cứu tìm kiếm tên hoặc mã sinh viên / ISBN sách.
3. Không kiểm tra trước điều kiện mượn (đang mượn bao nhiêu cuốn, có bị khóa không, sách còn bao nhiêu bản tồn).
4. Server không chặn trường hợp sinh viên đang mượn chính cuốn sách đó, dẫn đến nguy cơ tạo phiếu trùng lặp nếu thao tác lặp.

### 3.2. Sau khi sửa
1. **Tra cứu sinh viên:** Hỗ trợ nhập trực tiếp hoặc nhấn nút **"Tìm / Chọn SV"** để mở bảng tra cứu sinh viên theo Mã SV, Họ tên. Khi chọn, hệ thống cập nhật badge thông tin hiển thị trạng thái sinh viên (`ACTIVE`), số sách đang mượn và hạn mức (`X/Y cuốn`).
2. **Tra cứu sách:** Hỗ trợ nhập trực tiếp hoặc nhấn nút **"Tìm / Chọn Sách"** để mở bảng tra cứu sách theo ISBN, Tựa đề, Tác giả. Khi chọn, hệ thống cập nhật badge hiển thị số lượng bản sách còn trong kho (`Còn Z bản`).
3. **Kiểm tra trước điều kiện ("Kiểm Tra Điều Kiện"):**
   - Kiểm tra sinh viên có tồn tại và đang `ACTIVE` không.
   - Kiểm tra sinh viên đã chạm hạn mức mượn sách chưa (`current_borrow_count >= max_borrow_limit`).
   - Kiểm tra sách còn bản khả dụng không (`available_copies > 0`).
   - Kiểm tra sinh viên có đang mượn chính cuốn sách này không.
   - Hiển thị ngày dự kiến trả sách (mặc định 14 ngày).
4. **Xác nhận lập phiếu:**
   - Client gửi `Request(Action.BORROW_BOOK, BorrowRequestDTO)` kèm token session của Client.
   - Server `RequestRouter` xác minh role `ADMIN` hoặc `LIBRARIAN` (từ chối `STUDENT` với `403 FORBIDDEN`).
   - `BorrowServiceImpl` thực hiện transaction nguyên tử:
     - Khóa/trừ tồn kho: `UPDATE books SET available_copies = available_copies - 1 ... WHERE id = ? AND available_copies > 0`.
     - Tăng số sách sinh viên đang giữ: `UPDATE students SET current_borrow_count = current_borrow_count + 1 WHERE id = ? AND current_borrow_count < max_borrow_limit`.
     - Tạo bản ghi `borrow_records`.
     - Commit transaction (hoặc Rollback toàn bộ nếu bất kỳ điều kiện nào thất bại).
5. **Cập nhật giao diện:** Hiển thị thông báo thành công kèm mã phiếu mượn; tự động làm mới danh sách phiếu mượn trên `BorrowManagementPanel`.

---

## 4. Cách quản lý nhiều session sau khi sửa

* **Định danh phiên phía Server:**
  - Mỗi lần gọi `Action.LOGIN`, `AuthService` sinh một token UUID mới (`UUID.randomUUID().toString()`).
  - Đối tượng `UserSessionDTO` được lưu vào `SessionManager` với key là token UUID.
  - Server hỗ trợ không giới hạn số session đồng thời cho cùng một tài khoản (`librarian1` có thể đăng nhập trên N client, mỗi client sở hữu một token riêng biệt).
* **Quản lý phiên độc lập phía Client:**
  - `ClientSession` hỗ trợ khởi tạo phiên cô lập (`new ClientSession()`).
  - `RMIClient` giữ instance `ClientSession` riêng của kết nối đó (`client.setSession(cs)`).
  - Khi Client A đăng xuất (`Action.LOGOUT` với `tokenA`), server chỉ thu hồi `tokenA` khỏi `SessionManager`.
  - Phiên của Client B (`tokenB`) hoàn toàn không bị ảnh hưởng, tiếp tục thực thi các nghiệp vụ bình thường.
  - Request từ client tự động mang theo token của client đó; server hoàn toàn không tin role do client tự khai mà tra cứu role thực tế gắn với token hợp lệ trên server.

---

## 5. Kết quả kiểm thử tự động

### 5.1. Kết quả kiểm thử Part C1 (Đa phiên & Phân quyền)
*Tập kiểm thử:* `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest`
- `testC1_MultiSessionLoginAndIndependentLogout`:
  - Client A đăng nhập `librarian1` → Nhận Token A.
  - Client B đăng nhập `librarian1` → Nhận Token B.
  - Xác nhận `Token A != Token B`.
  - Cả hai client gửi yêu cầu `Action.SEARCH_BOOKS` → Cả hai nhận kết quả `200 OK`.
  - Client A đăng xuất → Client A bị thu hồi phiên.
  - Gửi request bằng Token A → Server từ chối `401 UNAUTHORIZED`.
  - Gửi request bằng Token B → Server chấp nhận `200 OK` (Phiên B hoạt động độc lập bình thường).
- `testC1_StudentDirectBorrowActionForbidden`:
  - Đăng nhập tài khoản `student1` (vai trò `STUDENT`).
  - Gửi trực tiếp `Request(Action.BORROW_BOOK, ...)` lên server.
  - Server từ chối với mã lỗi `403 FORBIDDEN` do vi phạm chính sách RBAC.
- `testC1_InvalidOrForgedSessionTokenRejected`:
  - Gửi request với token giả mạo `forged-unregistered-token-99999`.
  - Server từ chối với mã lỗi `401 UNAUTHORIZED`.

### 5.2. Kết quả kiểm thử Part C2 (Cạnh tranh đồng thời trên database test)
*Cơ sở dữ liệu:* Thực hiện trên `school_library_test` dưới sự kiểm soát của `TestDatabaseSafetyGuard`.
- `testC2_ConcurrentBorrowSingleCopyCompetition`:
  - Tạo sách thử nghiệm với `total_copies = 1`, `available_copies = 1`.
  - Tạo 2 sinh viên thử nghiệm (`Alpha` và `Beta`).
  - 2 luồng độc lập của Client A và Client B kích hoạt đồng thời qua `CountDownLatch`.
  - **Kết quả:** Chính xác 1 client thành công (`200 OK`, tạo phiếu mượn #89), client còn lại nhận lỗi `409 CONFLICT` ("No copies available").
  - **Bảo toàn bất biến MySQL:**
    - `available_copies` của cuốn sách bằng chính xác `0` (không âm).
    - Số lượng phiếu mượn được tạo cho cuốn sách là chính xác `1`.
- `testC2_ConcurrentBorrowTwoCopiesBothSucceed`:
  - Tạo sách thử nghiệm với `total_copies = 2`, `available_copies = 2`.
  - 2 client đồng thời mượn cho 2 sinh viên khác nhau.
  - **Kết quả:** Cả 2 request đều thành công (`200 OK`, tạo phiếu #86 và #87).
  - `available_copies` giảm về `0`, số phiếu mượn là `2`.
- `testC2_DuplicateActiveBorrowRejected`:
  - Sinh viên mượn cuốn sách lần thứ nhất → Thành công (`200 OK`, phiếu #88).
  - Tiếp tục gửi yêu cầu mượn chính cuốn sách đó khi phiếu trước đang `ACTIVE`.
  - **Kết quả:** Bị chặn với lỗi `400 BAD_REQUEST`, thông báo: *"Sinh viên hiện đang mượn cuốn sách này... Không thể tạo phiếu mượn trùng lặp."*

### 5.3. Kết quả chạy toàn bộ test suite (`ant clean test`)
Tổng hợp kết quả đọc từ các file XML trong `build/test/results/`:

| Test Suite | Số test | Failures | Errors | Skipped | Thời gian (s) |
|---|---|---|---|---|---|
| `thuvien.common.ProtocolTest` | 2 | 0 | 0 | 0 | 0.264 |
| `thuvien.server.RequestRouterTest` | 24 | 0 | 0 | 0 | 0.479 |
| `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 0 | 0 | 0 | 0.073 |
| `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 0 | 0 | 0 | 0.333 |
| `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 0 | 0 | 0 | 0.060 |
| `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 0 | 0 | 0 | 1.069 |
| `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 0 | 0 | 0 | 1.051 |
| `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 0 | 0 | 0 | 0.405 |
| `thuvien.server.service.AuthServiceTest` | 5 | 0 | 0 | 0 | 0.384 |
| `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 0 | 0 | 0 | 0.457 |
| `thuvien.server.service.CreateUserSecurityTest` | 4 | 0 | 0 | 0 | 0.552 |
| `thuvien.server.service.FineServiceTest` | 3 | 0 | 0 | 0 | 0.053 |
| `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 0 | 0 | 0 | 2.915 |
| **TỔNG CỘNG** | **115** | **0** | **0** | **0** | **7.745 s** |

*Trạng thái build:* **BUILD SUCCESSFUL** (Exit code: `0`).

### 5.4. Kết quả kiểm tra `git diff --check`
- Lệnh: `git diff --check`
- **Kết quả:** Không có lỗi whitespace hay conflict marker nào (Exit code: `0`).

---

## 6. Những hạn chế chưa xử lý và kiểm thử chưa thực hiện

1. **Kiểm thử trên 2 máy vật lý độc lập:** Các bài kiểm tra đa client và cạnh tranh đồng thời ở trên được thực hiện bằng 2 client instance độc lập (2 luồng RMI client riêng biệt) trên cùng một máy chủ kết nối qua loopback / IP nội bộ. Chưa kiểm thử tương tác thực tế từ 2 thiết bị phần cứng vật lý khác nhau trong mạng LAN thực tế.
2. **Cơ chế gia hạn phiếu mượn (Renew Borrow):** Hiện tại hệ thống hỗ trợ Tạo phiếu mượn và Trả sách. Nghiệp vụ gia hạn hạn mượn (Renew) chưa có Action riêng, thủ thư cần thực hiện trả và lập phiếu mượn mới.
3. **Giới hạn số phiên đồng thời tối đa (Max Concurrent Sessions per Account):** Hiện tại hệ thống cho phép không giới hạn số phiên đồng thời cho một tài khoản. Nếu nhà trường có chính sách khống chế tối đa (ví dụ: tối đa 3 phiên/tài khoản), cần bổ sung cấu hình và logic dọn dẹp phiên cũ nhất khi vượt ngưỡng.
