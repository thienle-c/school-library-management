# BÁO CÁO PHASE 1.22 — UI CLEANUP, ICON GLYPH REPAIR & ACCESS CONTROL AUDIT

**Dự án**: Remote School Library Management System  
**Đường dẫn**: `D:\workspace\thuvien`  
**Môi trường**: Java 8 (Amazon Corretto 1.8.0_504), Java Swing, Java RMI, MySQL 8 / Laragon, Apache Ant 1.9.4  
**Thời gian thực hiện**: 2026-10-22  
**Trạng thái**: Hoàn thành thực hiện trực tiếp trong mã nguồn, kiểm thử tự động đạt 118/118 PASS.

---

## 1. Tóm tắt điều hành

Phase 1.22 đã rà soát toàn diện mã nguồn Client Swing và Server RMI để giải quyết triệt để 4 nhóm vấn đề trọng tâm:
1. **Khắc phục lỗi hiển thị icon biến thành ký tự hình vuông (`▯` / Tofu glyphs)**: Xác định nguyên nhân gốc rễ và xử lý tại tầng view, thay thế các ký tự Unicode/emoji không tương thích bằng text/vector rendering chuẩn.
2. **Loại bỏ các thành phần thử nghiệm khỏi giao diện người dùng**: Gỡ bỏ thanh “Tài khoản mẫu thử nghiệm” cùng các chip điền nhanh (Admin, Thủ thư, Sinh viên) tại màn hình đăng nhập, bảo toàn trọn vẹn các tính năng người dùng thực tế.
3. **Kiểm toán bảo mật và phân quyền máy chủ (Server-side RBAC)**: Rà soát cơ chế xác thực phiên và phân quyền trong `RequestRouter` và các Service/DAO; bổ sung test case tự động kiểm chứng việc server từ chối các request trái quyền khi client cố tình gọi trực tiếp qua RMI.
4. **Chuẩn hóa giao diện & Build/Package Distribution**: Tái đóng gói thành công `dist/client-dist/client.jar` đồng bộ các sửa đổi mới nhất mà không gây xung đột khóa file.

---

## 2. Nguyên nhân thực tế của lỗi icon hình vuông và giải pháp khắc phục

### 2.1. Phân tích nguyên nhân gốc rễ
Qua kiểm tra toàn bộ cây thư mục `client/src/thuvien/client/view`:
1. **Sử dụng ký tự Emoji & Unicode đặc biệt**: Trước đây các file giao diện sử dụng các ký tự như `👋` (U+1F44B), `🟢` (U+1F7E2), `⚙️` (U+2699), `👁️` (U+1F441), `🔒` (U+1F512), `⚡` (U+26A1), `👑` (U+1F451), `📖` (U+1F4D6), `🎓` (U+1F393), `❌` (U+274C), `✓` (U+2713), `•` (U+2022). Trên môi trường Windows tiêu chuẩn chạy Java 8 (Oracle JDK/Corretto), engine render font mặc định của Swing (với font Segoe UI / Dialog) không có cơ chế font-fallback cho các khối emoji/symbol này, dẫn đến việc Swing hiển thị ký tự thay thế hình chữ nhật rỗng (`▯` - tofu glyph).
2. **Ký tự trong hộp thoại mượn trả**: Tại `BorrowDialog.java`, các nhãn HTML và thông báo kết quả kiểm tra điều kiện mượn sách chứa trực tiếp `✓` và `❌` cùng dấu chấm bullet `•`, gây vỡ hiển thị trên các máy trạm Windows.
3. **File đóng gói client bị cũ**: Bản phân phối `dist/client-dist/client.jar` trước đó được build từ thời điểm chưa đồng bộ mã nguồn giao diện vector mới do tiến trình đóng gói `package-all` gặp lỗi khóa file `HikariCP-4.0.3.jar` khi server đang chạy nền.

### 2.2. Giải pháp thực hiện trực tiếp
- **`ModernUIHelper.java`**: Sử dụng `ModernUIHelper.VectorIcon` vẽ bằng Java 2D Graphics (`Graphics2D`, `BasicStroke`, anti-aliasing) trên lưới tọa độ vector 24x24 độc lập hoàn toàn với font hệ thống, bảo đảm 100% hiển thị sắc nét trên mọi phiên bản Windows/Linux/macOS mà không phụ thuộc font emoji ngoài.
- **`BorrowDialog.java`**:
  - Dòng 451, 590: Thay thế `✓ <b>%s</b>` thành `[HỢP LỆ] <b>%s</b>`.
  - Dòng 456, 595: Thay thế `❌ Không tìm thấy sinh viên/sách...` thành `[!] Không tìm thấy sinh viên/sách...`.
  - Dòng 782: Thay thế `✓ Đủ điều kiện mượn sách!` thành `Đủ điều kiện mượn sách!`.
  - Dòng 788, 790: Thay thế `✓ Sinh viên / Sách` thành `- Sinh viên / Sách`.
  - Dòng 826–831: Thay thế ký tự bullet `•` thành `-`.
- Rà soát toàn bộ `client/src`: Quét regex toàn bộ ký tự ngoài bảng mã chuẩn và xác nhận **không còn bất kỳ ký tự symbol/emoji nào** gây lỗi hình vuông.

---

## 3. Loại bỏ các mục thử nghiệm khỏi giao diện người dùng

### 3.1. Thành phần đã loại bỏ
Tại `LoginForm.java` (dòng 327–375):
- Đã loại bỏ panel `demoPanel` chứa tiêu đề `"Tài khoản mẫu thử nghiệm:"`.
- Đã loại bỏ 3 nút chip điền nhanh:
  - `adminChip` (tự điền `admin / admin123`)
  - `libChip` (tự điền `librarian1 / lib123`)
  - `studentChip` (tự điền `student1 / student123`)
- **Lý do**: Đây là các điều khiển phục vụ phát triển/thử nghiệm nội bộ (debug shortcut), làm lộ cấu trúc tài khoản mẫu và không an toàn cho môi trường triển khai thực tế của người dùng cuối.

### 3.2. Thành phần nghiệp vụ được giữ nguyên và chuẩn hóa
- Trường nhập Server IP và Port máy chủ (hỗ trợ cấu hình thu gọn/mở rộng).
- Nút `"Kiểm tra"` (`testConnectionButton`): Gửi lệnh `PING` tới server RMI để kiểm tra trạng thái mạng trước khi đăng nhập.
- Trường nhập tên đăng nhập (`usernameField`).
- Trường nhập mật khẩu (`passwordField`) kèm nút ẩn/hiện mật khẩu sử dụng vector icon chuẩn (`EYE` / `EYE_OFF`).
- Nút bấm chính `"Đăng Nhập"` (`loginButton`) với hiệu ứng hover và phím tắt `Enter`.
- Nút liên kết `"Chưa có tài khoản sinh viên? Đăng ký tại đây"` (`registerButton`) mở hộp thoại đăng ký sinh viên chính thức.
- Bố cục hai cột (heroPanel bên trái với đồ họa AI Grid học thuật, authPanel bên phải) được căn chỉnh tỷ lệ cân đối, thoáng đãng và trực quan.

---

## 4. Kiểm toán bảo mật và phân quyền (Access Control Audit)

### 4.1. Phân tích kiến trúc bảo vệ tại Server
Trong hệ thống, Client giao tiếp với Server qua `LibraryRemoteService` (Java RMI). Tầng RMI Server (`LibraryRemoteServiceImpl`) đóng gói mọi lời gọi thành đối tượng `Request(action, token, payload)` và chuyển tiếp trực tiếp vào `RequestRouter.route(request)`.

Mọi kiểm tra bảo mật đều được thực thi tại `RequestRouter` ở server:
1. **Xác thực phiên (Authentication Guard)**:
   - Tất cả các hành động (ngoại trừ `LOGIN`, `REGISTER_STUDENT`, `PING`) bắt buộc phải có `token` hợp lệ trong `SessionManager`.
   - Kiểm tra trạng thái khóa tài khoản (`isAccountLocked`); nếu tài khoản bị khóa do đăng nhập sai, token lập tức bị hủy và trả về lỗi `UNAUTHORIZED`.
2. **Kiểm tra quyền theo vai trò (Role-Based Access Control - RBAC)**:
   - Các hành động quản trị cao cấp chỉ dành cho `ADMIN`: `LIST_USERS`, `GET_USER`, `CREATE_USER`, `UPDATE_USER`, `DELETE_USER`, `DELETE_STUDENT`, `GENERATE_STUDENT_ACTIVATION`, `LIST_AUDIT_LOGS`.
   - Các hành động thủ thư & quản trị (`ADMIN`, `LIBRARIAN`): `CREATE_BOOK`, `UPDATE_BOOK`, `DELETE_BOOK`, `CREATE_STUDENT`, `UPDATE_STUDENT`, `LIST_STUDENTS`, `BORROW_BOOK`, `RETURN_BOOK`, `LIST_BORROW_RECORDS`, `PAY_FINE`, `CREATE_CATEGORY`, `UPDATE_CATEGORY`, `DELETE_CATEGORY`, `CREATE_AUTHOR`, `UPDATE_AUTHOR`, `DELETE_AUTHOR`, `GET_DASHBOARD_METRICS`.
   - Sinh viên (`STUDENT`): Bị từ chối triệt để (`403 FORBIDDEN`) nếu cố gắng gọi các hành động trên.
3. **Bảo vệ quyền sở hữu dữ liệu (Resource Ownership / IDOR Prevention)**:
   - `GET_STUDENT`: Khi `STUDENT` gửi request, server kiểm tra `student.getUserId().equals(session.getUserId())`. Sinh viên không thể xem hồ sơ sinh viên khác.
   - `GET_STUDENT_ACTIVE_BORROWS`: Với vai trò `STUDENT`, server bỏ qua ID do client gửi và tự động truy vấn theo `studentId` lấy từ phiên đăng nhập tin cậy.
   - `LIST_FINES`: Sinh viên chỉ nhận danh sách tiền phạt của chính mình.
   - `CREATE_RESERVATION`: Tự động gắn ID của sinh viên hiện tại, ngăn việc đặt trước thay người khác.
   - `CANCEL_RESERVATION`: Kiểm tra ID đặt trước thuộc sở hữu của sinh viên hiện tại trước khi hủy.
   - `SEARCH_SUGGESTIONS`: Sinh viên bị cấm tìm kiếm gợi ý danh sách sinh viên khác (`type = "STUDENT"`).

### 4.2. Bổ sung Unit Test xác minh bảo mật
Đã bổ sung 2 phương thức test mới trong `test/thuvien/server/RequestRouterTest.java`:
- `testStudentCannotPerformPrivilegedOperations()`:
  - Sinh viên cố tình gọi `DELETE_BOOK` -> nhận mã `403 FORBIDDEN`.
  - Sinh viên cố tình gọi `BORROW_BOOK` trực tiếp -> nhận mã `403 FORBIDDEN`.
  - Sinh viên cố tình gọi `PAY_FINE` trực tiếp -> nhận mã `403 FORBIDDEN`.
  - Sinh viên cố tình gọi `LIST_AUDIT_LOGS` -> nhận mã `403 FORBIDDEN`.
  - Sinh viên cố tình gọi `GENERATE_STUDENT_ACTIVATION` -> nhận mã `403 FORBIDDEN`.
- `testLibrarianCannotPerformAdminOnlyOperations()`:
  - Thủ thư cố tình gọi `DELETE_USER` -> nhận mã `403 FORBIDDEN`.
  - Thủ thư cố tình gọi `DELETE_STUDENT` -> nhận mã `403 FORBIDDEN`.
  - Thủ thư cố tình gọi `GENERATE_STUDENT_ACTIVATION` -> nhận mã `403 FORBIDDEN`.
  - Thủ thư cố tình gọi `LIST_AUDIT_LOGS` -> nhận mã `403 FORBIDDEN`.

---

## 5. Danh sách các file thay đổi

| STT | Tệp tin | Loại thay đổi | Chi tiết |
|:---:|:---|:---:|:---|
| 1 | `client/src/thuvien/client/view/LoginForm.java` | Sửa đổi | Xóa bỏ `demoPanel` và các nút tài khoản mẫu; tinh chỉnh spacing giao diện đăng nhập |
| 2 | `client/src/thuvien/client/view/dialogs/BorrowDialog.java` | Sửa đổi | Thay thế các ký tự `✓`, `❌`, `•` bằng text và ký hiệu an toàn |
| 3 | `client/src/thuvien/client/view/common/ModernUIHelper.java` | Sửa đổi | Dọn dẹp ký tự tofu trong chú thích |
| 4 | `test/thuvien/server/RequestRouterTest.java` | Sửa đổi | Bổ sung các test case kiểm tra RBAC máy chủ cho Sinh viên và Thủ thư |
| 5 | `docs/phase-1-22-ui-cleanup-security-audit.md` | Tạo mới | Báo cáo kiểm toán chất lượng giao diện và bảo mật |

---

## 6. Kết quả thực thi kiểm thử và đóng gói

### 6.1. Biên dịch và Kiểm thử tự động (`ant test`)
- Lệnh: `ant compile compile-test` -> **BUILD SUCCESSFUL**
- Lệnh: `ant test` -> **BUILD SUCCESSFUL**
- **Thống kê chi tiết**:
  - `ProtocolTest`: 2 tests PASS
  - `RequestRouterTest`: 26 tests PASS (tăng 2 test bảo mật so với trước)
  - `TestDatabaseSafetyGuardTest`: 6 tests PASS
  - `MySQLSmokeIntegrationTest`: 4 tests PASS
  - `RepositoryArchitectureTest`: 3 tests PASS
  - `MultiSessionAndConcurrentBorrowTest`: 6 tests PASS
  - `RMIEndToEndIntegrationTest`: 15 tests PASS
  - `RMISerializationFilterTest`: 10 tests PASS
  - `AuthServiceTest`: 5 tests PASS
  - `BorrowConcurrencyIntegrationTest`: 4 tests PASS
  - `CreateUserSecurityTest`: 4 tests PASS
  - `FineServiceTest`: 3 tests PASS
  - `SecurityAndFeatureHardeningTest`: 30 tests PASS
- **Tổng cộng**: **118 / 118 tests PASS (100%)**, 0 failures, 0 errors, 0 skipped.

### 6.2. Đóng gói phân phối Client (`ant package-client`)
- Lệnh: `ant package-client` -> **BUILD SUCCESSFUL**
- Tệp sinh ra: `dist/client-dist/client.jar` (dung lượng: ~377 KB)
- Toàn bộ thay đổi về giao diện và icon glyphs đã được biên dịch và đóng gói hoàn chỉnh vào thư mục phân phối client.

---

## 7. Các hạng mục ghi chú & xác minh môi trường

1. **Khóa tệp thư viện HikariCP trên Windows**: Target `package-all` phụ thuộc vào `package-server`. Khi server đang chạy từ `dist/server-dist/run-server.bat`, Windows áp dụng cơ chế khóa độc quyền trên tệp `HikariCP-4.0.3.jar`. Để đóng gói server mới, cần dừng tiến trình server trước khi chạy `ant package-server`. Việc chạy `ant package-client` độc lập hoạt động bình thường mà không bị ảnh hưởng bởi server.
2. **Bảo toàn trạng thái Git**:
   - Toàn bộ commit trước đó được giữ nguyên.
   - Không thực hiện bất kỳ lệnh `git commit`, `git push`, `git reset` hoặc `git clean`.
   - Không có file ngoài phạm vi bị chỉnh sửa.

---

## 8. Kết luận

Phase 1.22 đã giải quyết triệt để vấn đề ký tự icon hình vuông, gỡ bỏ toàn bộ thành phần thử nghiệm khỏi giao diện đăng nhập, củng cố bằng chứng kiểm thử bảo mật phân quyền server và đóng gói client thành công. Hệ thống sẵn sàng cho việc nghiệm thu giao diện người dùng và vận hành.
