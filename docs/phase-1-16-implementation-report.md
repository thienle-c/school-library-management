# BÁO CÁO THỰC THI & CHUẨN BỊ PHÁT HÀNH PUBLIC GITHUB (PHASE 1.16)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Kiến trúc:** 3-Tier Distributed Architecture (Java Swing $\rightarrow$ Java RMI $\rightarrow$ MySQL 8)  
**Môi trường:** Java 8 (Amazon Corretto 1.8.0_504), Apache Ant (NetBeans 8.0.2 bundled runtime), MySQL 8.0.30 (Laragon)  
**Thời gian thực hiện:** 21/10/2026  
**Trạng thái kiểm thử:** 116/116 tests **PASS (100%)** — 0 Failures, 0 Errors, 0 Skipped  

---

## 1. Tổng quan & Mục tiêu Phase 1.16

Mục tiêu của Phase 1.16 không dừng lại ở việc đánh giá hay lập báo cáo, mà tập trung giải quyết triệt để các tồn đọng kỹ thuật từ Phase 1.14 và Phase 1.15, đồng thời chuẩn bị repository ở trạng thái sẵn sàng cao nhất cho việc phát hành mã nguồn mở (Public GitHub):

1. **Sửa dứt điểm lỗ hổng bảo mật P1 (SEC-01)**: Thực thi cơ chế thu hồi phiên đăng nhập đồng thời khi người dùng đổi mật khẩu.
2. **Sửa lỗi độ tin cậy giao diện P2 (REL-01)**: Khôi phục chính xác trạng thái nút "Trả sách" (`returnBtn`) trong `BorrowManagementPanel.handleError()`.
3. **Dọn dẹp rác & chuẩn hóa tài nguyên**: Xóa các file `.class` thử nghiệm rác ở thư mục gốc; bổ sung tệp giấy phép bản quyền `LICENSE` (MIT License) với thông cáo đầy đủ các thư viện phụ thuộc của bên thứ ba.
4. **Chuẩn hóa tài liệu công khai**: Loại bỏ toàn bộ đường dẫn cục bộ máy cá nhân (`C:\Users\thien\...`) trong `docs/` và thay thế bằng các biến/chỉ dẫn tổng quát (`<NETBEANS_ANT_PATH>`, `<workspace_root>`).
5. **Xác minh bí mật & lịch sử Git**: Kiểm tra vết lịch sử commit `e0c5a90`, kiểm tra an toàn cấu hình và thiết lập checklist đánh giá trước khi chuyển repository sang Public.
6. **Thẩm định chất lượng toàn diện**: Biên dịch sạch và chạy thành công 100% bộ kiểm thử tự động (116 tests) bằng Apache Ant.

---

## 2. Chi tiết các lỗi đã sửa & Giải pháp kỹ thuật

### 2.1. Lỗ hổng Bảo mật P1: SEC-01 — Thu hồi phiên đăng nhập khi đổi mật khẩu

- **Hiện trạng trước sửa đổi**:
  - Tại [RequestRouter.java](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L913-L926), phương thức `handleChangePassword()` sau khi gọi `userService.changePassword()` thành công chỉ ghi nhật ký kiểm toán (`auditLogService.log`), không phát tín hiệu thông báo cho [SessionManager.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/SessionManager.java) để hủy các phiên đăng nhập khác của tài khoản đó.
  - Nếu tài khoản bị lộ mật khẩu và kẻ xấu đang duy trì một session token trên máy khác, khi chủ tài khoản đổi mật khẩu thì phiên của kẻ xấu vẫn có thể tiếp tục hoạt động cho đến khi hết hạn (30 phút).
- **Quyết định thiết kế chính sách phiên (Policy Decision)**:
  - **Lựa chọn**: Duy trì phiên hiện tại (session token của máy gửi yêu cầu đổi mật khẩu) và **thu hồi ngay lập tức toàn bộ các phiên khác** (`revokeOtherSessionsForUser(userId, currentToken)`).
  - **Lý do**:
    1. Giao diện đổi mật khẩu (`ChangePasswordDialog`) là một hộp thoại con mở từ thanh công cụ của `MainDashboardForm`. Nếu hủy luôn cả phiên hiện tại, giao diện chính ngay lập tức bị từ chối quyền truy cập (`UNAUTHORIZED`), gây lỗi bất ngờ cho người dùng đang thao tác.
    2. Về mặt an ninh, hành động đổi mật khẩu đã xác thực thành công mật khẩu cũ và được thực hiện bởi chính chủ thể sở hữu phiên hiện tại. Việc giữ phiên hiện tại và vô hiệu hóa tất cả các phiên còn lại trên các máy/trình duyệt khác là chuẩn mực an toàn phổ biến (tương tự Google, GitHub).
    3. Nếu giao dịch đổi mật khẩu thất bại (ví dụ sai mật khẩu cũ, mật khẩu mới không hợp lệ), **không có bất kỳ phiên nào bị hủy**.
    4. Phiên đăng nhập của các người dùng khác trong hệ thống hoàn toàn không bị ảnh hưởng.
- **Mã nguồn đã chỉnh sửa**:
  1. Trong [SessionManager.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/SessionManager.java#L92-L99):
     ```java
     public void revokeOtherSessionsForUser(Long userId, String exceptToken) {
         if (userId == null) return;
         activeSessions.entrySet().removeIf(entry ->
                 entry.getValue().session.getUserId() != null &&
                 entry.getValue().session.getUserId().equals(userId) &&
                 (exceptToken == null || !entry.getKey().equals(exceptToken))
         );
     }
     ```
  2. Trong [RequestRouter.java](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L920-L928):
     ```java
     boolean ok = userService.changePassword(session.getUserId(), dto.getCurrentPassword(), dto.getNewPassword());
     if (ok) {
         // SEC-01: Invalidate all other active sessions of this user across all devices/terminals
         sessionManager.revokeOtherSessionsForUser(session.getUserId(), request.getToken());
         if (auditLogService != null) {
             auditLogService.log(session.getUserId(), "PASSWORD_CHANGED", "User", session.getUserId(),
                     "Đổi mật khẩu thành công (đã thu hồi các phiên đăng nhập khác)");
         }
     }
     return Response.ok(reqId, "Đổi mật khẩu thành công.", ok);
     ```
  3. Bổ sung test tự động: `test24_ChangePasswordRevokesOtherSessionsForSameUser` trong [SecurityAndFeatureHardeningTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/SecurityAndFeatureHardeningTest.java#L730-L791). Test xác minh đa chiều:
     - Tạo 2 phiên đăng nhập cho `student1` (Client A và Client B) và 1 phiên cho `admin`.
     - Đổi mật khẩu thất bại $\rightarrow$ Cả 3 phiên đều giữ nguyên.
     - Đổi mật khẩu thành công từ Client A $\rightarrow$ Phiên Client A tiếp tục hợp lệ; phiên Client B bị thu hồi ngay (bị từ chối `401 UNAUTHORIZED`); phiên của `admin` không bị tác động.
     - Phục hồi lại mật khẩu mặc định sau test.

---

### 2.2. Lỗi Độ tin cậy Giao diện P2: REL-01 — Khôi phục trạng thái nút Trả sách

- **Hiện trạng trước sửa đổi**:
  - Tại [BorrowManagementPanel.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L904-L908), phương thức `handleError(Exception ex)` chỉ bật lại `refreshBtn` và `newBorrowBtn`. Nút `returnBtn` bị vô hiệu hóa (`setEnabled(false)`) ở đầu hàm `performReturn()`, nhưng nếu quá trình xử lý ngầm gặp lỗi (mạng chập chờn, ngoại lệ RMI, lỗi nghiệp vụ), `returnBtn` vẫn bị kẹt ở trạng thái `disabled`, buộc người dùng phải click sang dòng khác hoặc bấm refresh mới thao tác lại được.
- **Quyết định thiết kế**:
  - Không bật lại `returnBtn` một cách vô điều kiện (vì nếu người dùng chưa chọn dòng nào, hoặc dòng đang chọn có trạng thái `RETURNED`, nút trả sách phải ở trạng thái disable theo đúng quy tắc nghiệp vụ).
  - Phục hồi trạng thái `enabled` của `returnBtn` dựa trên bản ghi đang được chọn trong bảng (`getSelectedRecord()`) và quyền hạn nhân viên (`isStaff`):
    ```java
    if (returnBtn != null && isStaff) {
        BorrowRecordDTO cur = getSelectedRecord();
        returnBtn.setEnabled(cur != null && (cur.getStatus() == BorrowStatus.ACTIVE || cur.getStatus() == BorrowStatus.OVERDUE));
    }
    ```
- **Mã nguồn đã chỉnh sửa**:
  - Tệp [BorrowManagementPanel.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L904-L913).

---

### 2.3. Dọn dẹp rác & Tệp Giấy phép Bản quyền (LICENSE)

1. **Xóa 4 file `.class` rác thử nghiệm ở thư mục gốc**:
   - Các file: `'ScratchProxyFilterTest$AllowedHandler.class'`, `'ScratchProxyFilterTest$MyTestInterface.class'`, `'ScratchProxyFilterTest$UnallowedHandler.class'`, `'ScratchTestRefs$Item.class'`.
   - Đã xác minh: Đây là các tệp bytecode sinh ra do chạy lệnh `javac` thử nghiệm trực tiếp tại thư mục gốc trong quá trình nghiên cứu JEP 290 Serialization Filter, không thuộc bất kỳ gói nào trong `src/` và không được tham chiếu bởi `build.xml`. Đã xóa an toàn khỏi thư mục gốc.
2. **Khởi tạo tệp [LICENSE](file:///D:/workspace/thuvien/LICENSE)**:
   - Áp dụng giấy phép mã nguồn mở phổ biến và chuẩn mực: **MIT License**.
   - Bổ sung mục **Third-Party Notices & Licenses** liệt kê rõ ràng quyền sở hữu trí tuệ của các thư viện bên thứ ba đang sử dụng trong dự án:
     - MySQL Connector/J (GPL v2 with FOSS License Exception)
     - HikariCP (Apache License 2.0)
     - SLF4J (MIT License)
     - JUnit 4 (Eclipse Public License 1.0)
     - Hamcrest (BSD 3-Clause License)

---

### 2.4. Chuẩn hóa đường dẫn máy cá nhân trong tài liệu

- Đã rà soát và thay thế các đường dẫn tuyệt đối dạng `C:\Users\thien\Downloads\netbeans-8.0.2-...` và `D:\workspace\thuvien`:
  - [docs/security-audit-report.md](file:///D:/workspace/thuvien/docs/security-audit-report.md): Thay bằng `"<NETBEANS_ANT_PATH>\ant.bat"`.
  - [docs/phase-1-14-quality-security-audit.md](file:///D:/workspace/thuvien/docs/phase-1-14-quality-security-audit.md): Thay bằng `& "<NETBEANS_ANT_PATH>\ant.bat"`.
  - [docs/phase-1-13-merge-audit-report.md](file:///D:/workspace/thuvien/docs/phase-1-13-merge-audit-report.md): Thay bằng `& "<NETBEANS_ANT_PATH>\ant.bat"` và đường dẫn tương đối `.\dist\...`.
  - [docs/phase-1-11-lan-test-runbook.md](file:///D:/workspace/thuvien/docs/phase-1-11-lan-test-runbook.md): Thay bằng `<workspace_root>`.
- Tệp [README.md](file:///D:/workspace/thuvien/README.md) đã được rà soát: hoàn toàn sạch, sử dụng placeholder `<NETBEANS_ANT_PATH>`, `<SERVER_IP>` và hướng dẫn portable cho mọi máy Windows khác.

---

## 3. Xác minh Bí mật & Lịch sử Git

### 3.1. Xác minh Commit `e0c5a90`
- Báo cáo Phase 1.15 ghi nhận tệp cấu hình `server/resources/db.properties` từng xuất hiện trong commit `e0c5a90`.
- **Kết quả điều tra thực tế bằng Git**:
  - Lệnh kiểm tra: `git show e0c5a90:server/resources/db.properties`
  - Nội dung tại commit đó:
    ```properties
    db.driver=com.mysql.cj.jdbc.Driver
    db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
    db.username=root
    db.password=
    ```
  - **Đánh giá rủi ro**: Giá trị `db.password` để **rỗng** (cấu hình mặc định của Laragon/XAMPP local). **Không phát hiện mật khẩu thật của cá nhân hay môi trường production bị lộ.**
  - **Cơ chế phòng ngừa hiện tại**: `.gitignore` đã có mục `server/resources/db.properties` và chỉ cho phép theo dõi `!server/resources/db.properties.example`. Tệp cục bộ trên máy phát triển không bao giờ bị Git theo dõi lại.

### 3.2. Đánh giá về việc làm sạch lịch sử Git (Git History Rewrite)
- **Phương án A: Giữ nguyên lịch sử commit hiện tại (Khuyến nghị cho nộp đồ án / đồ án nhóm)**:
  - Commit `e0c5a90` chỉ chứa password rỗng và user `root` local; các commit khác hoàn toàn không chứa API key, private key hay secret thật.
  - Giữ nguyên lịch sử bảo toàn toàn bộ commit log, commit hash của các thành viên trong nhóm, thuận tiện cho giảng viên đối chiếu tiến độ đồ án và không gây lỗi phân kỳ (diverged branch) cho các thành viên khác đang clone repo.
- **Phương án B: Sử dụng `git filter-repo` hoặc BFG Repo-Cleaner**:
  - Chỉ nên áp dụng nếu người dùng có yêu cầu bảo mật cấp doanh nghiệp khắt khe muốn xoá hoàn toàn dấu vết tệp `db.properties` khỏi lịch sử trước khi đưa lên Public.
  - *Lưu ý*: Việc này sẽ thay đổi toàn bộ commit hash từ commit `e0c5a90` trở đi và cần `git push --force`. Theo nguyên tắc an toàn, tác vụ này không được tự động thực hiện và cần người dùng phê duyệt riêng.

---

## 4. Danh sách các tệp đã thay đổi và lý do

| STT | Tệp tin | Trạng thái Git | Mục đích / Lý do thay đổi |
|:---:|:---|:---:|:---|
| 1 | `server/src/thuvien/server/security/SessionManager.java` | Modified | Thêm `revokeOtherSessionsForUser(userId, exceptToken)` để thu hồi các phiên khác khi đổi mật khẩu (SEC-01). |
| 2 | `server/src/thuvien/server/router/RequestRouter.java` | Modified | Gọi thu hồi các phiên khác sau khi `userService.changePassword()` thành công (SEC-01). |
| 3 | `client/src/thuvien/client/view/panels/BorrowManagementPanel.java` | Modified | Khôi phục trạng thái `returnBtn` trong `handleError()` theo đúng dòng chọn và vai trò người dùng (REL-01). |
| 4 | `test/thuvien/server/service/SecurityAndFeatureHardeningTest.java` | Modified | Bổ sung kiểm thử tự động `test24_ChangePasswordRevokesOtherSessionsForSameUser` (SEC-01). |
| 5 | `docs/security-audit-report.md` | Modified | Chuẩn hóa đường dẫn Ant cá nhân thành `<NETBEANS_ANT_PATH>\ant.bat`. |
| 6 | `docs/phase-1-11-lan-test-runbook.md` | Modified | Chuẩn hóa đường dẫn thư mục cá nhân thành `<workspace_root>`. |
| 7 | `docs/phase-1-14-quality-security-audit.md` | Untracked / Modified | Chuẩn hóa đường dẫn Ant trong tài liệu kiểm toán Phase 1.14. |
| 8 | `docs/phase-1-13-merge-audit-report.md` | Untracked / Modified | Chuẩn hóa đường dẫn Ant và đường dẫn chạy bat trong báo cáo Phase 1.13. |
| 9 | `LICENSE` | Untracked (Mới) | Giấy phép MIT License và thông cáo bản quyền bên thứ ba chuẩn bị cho public release. |
| 10 | `docs/phase-1-15-repository-readiness-audit.md` | Untracked (Mới) | Báo cáo rà soát chuẩn bị repository từ Phase 1.15. |
| 11 | `docs/phase-1-16-implementation-report.md` | Untracked (Mới) | Báo cáo thực thi hoàn thành Phase 1.16. |
| 12 | 4 tệp `Scratch*.class` tại thư mục gốc | Deleted | Đã xóa an toàn các bytecode rác không thuộc source code. |

---

## 5. Kết quả Biên dịch & Kiểm thử Tự động Thực tế

### 5.1. Lệnh Biên dịch (Compilation)
- **Lệnh thực hiện**: `ant compile compile-test`
- **Kết quả**: `BUILD SUCCESSFUL` (Thời gian: 0 giây - toàn bộ bytecode cập nhật chính xác).
- **Tuân thủ chuẩn**: Mã nguồn biên dịch hoàn toàn tương thích Java 8 (`source="1.8"`, `target="1.8"`), không sử dụng bất kỳ API nào từ Java 9 trở lên.

### 5.2. Lệnh Kiểm thử (Automated Test Execution)
- **Lệnh thực hiện**: `ant test`
- **Kết quả**: `BUILD SUCCESSFUL` (Tổng thời gian: 18 giây).
- **Cơ chế an toàn CSDL**: Toàn bộ các test tích hợp được bảo vệ nghiêm ngặt bởi `TestDatabaseSafetyGuard`, tự động trỏ sang `school_library_test` và chặn tuyệt đối tác động lên database chính `school_library`.

### 5.3. Bảng tổng hợp chi tiết kết quả 13 Test Suite

| STT | Test Suite | Số Test | Pass | Fail | Error | Skipped | Thời gian (giây) | Trạng thái |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.250s | **PASS** |
| 2 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.445s | **PASS** |
| 3 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.070s | **PASS** |
| 4 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.316s | **PASS** |
| 5 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.057s | **PASS** |
| 6 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 6 | 0 | 0 | 0 | 2.470s | **PASS** |
| 7 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 1.464s | **PASS** |
| 8 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 10 | 0 | 0 | 0 | 0.412s | **PASS** |
| 9 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.367s | **PASS** |
| 10 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 1.450s | **PASS** |
| 11 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.556s | **PASS** |
| 12 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.048s | **PASS** |
| 13 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 30 | 30 | 0 | 0 | 0 | 6.304s | **PASS** |
| **TỔNG** | **13 Test Suites** | **116** | **116** | **0** | **0** | **0** | **18.0s** | **PASS 100%** |

---

## 6. Kết quả Kiểm tra Diff & Git Status

1. **`git diff --check`**:
   - Hoàn toàn sạch, không có lỗi định dạng thừa dấu cách (trailing whitespace) hay xung đột merge marker.
2. **`git status`**:
   - Nhánh: `main`, đồng bộ với `origin/main`.
   - Các tệp theo dõi được chỉnh sửa: đúng 6 tệp trọng tâm, không có thay đổi ngoài phạm vi.
   - Các tệp untracked: chỉ gồm các tài liệu kiểm toán hoàn thiện (`docs/phase-1-*.md`) và tệp `LICENSE`.

---

## 7. Phân loại Mức độ Kiểm chứng Thực tế

Nhằm đảm bảo tính trung thực và minh bạch kỹ thuật cao nhất:

- **ĐÃ KIỂM TRA (VERIFIED)**:
  - Cấu trúc thư mục, tệp `.gitignore`, file cấu hình mẫu `.example`.
  - Toàn bộ nội dung commit `e0c5a90` trong lịch sử Git (xác nhận mật khẩu rỗng).
  - Không còn bất kỳ đường dẫn cá nhân `C:\Users\thien\...` nào trong các hướng dẫn sử dụng và runbook.
  - Phân vùng kiến trúc nghiêm ngặt (Client không có JDBC, MySQL hay HikariCP).
- **ĐÃ SỬA (FIXED)**:
  - SEC-01: Thu hồi toàn bộ phiên khác của tài khoản khi đổi mật khẩu thành công.
  - REL-01: Khôi phục trạng thái nút `returnBtn` trong `handleError()` theo đúng nghiệp vụ.
  - Xóa 4 tệp `.class` rác ở thư mục gốc.
  - Bổ sung tệp `LICENSE` (MIT License) với Third-Party Notices.
- **ĐÃ TEST (TESTED)**:
  - 116/116 unit & integration tests chạy thành công qua Ant trên môi trường Java 8 + MySQL 8.
  - Test case mới `test24_ChangePasswordRevokesOtherSessionsForSameUser` chạy thực tế và pass 100%.
  - Cơ chế row lock và atomic decrement chống mượn quá tồn kho (`BorrowConcurrencyIntegrationTest`).
  - Bộ lọc Serialization Filter bảo vệ chống RCE qua RMI (`RMISerializationFilterTest`).
- **CHƯA XÁC MINH (UNVERIFIED / CẦN NGƯỜI DÙNG QUYẾT ĐỊNH)**:
  - *Kiểm thử thực tế trên 2 máy vật lý qua Wi-Fi LAN mới*: Mặc dù RMI logic, dynamic IP detection và firewall runbook đã hoàn thiện và kiểm thử đạt trên localhost, việc triển khai thực tế trên một môi trường mạng LAN cụ thể vẫn cần cấu hình tường lửa Windows Defender Firewall trên máy chủ thực tế.
  - *Quyết định về lịch sử Git*: Người dùng lựa chọn giữa việc giữ nguyên commit history hiện tại (khuyến nghị) hoặc chạy công cụ làm sạch lịch sử commit trước khi Public.

---

## 8. Checklist Sẵn sàng trước khi Public GitHub

Người dùng có thể thực hiện theo checklist cuối cùng này trước khi bấm nút chuyển chế độ Public trên GitHub:

- [x] 1. Mã nguồn biên dịch thành công 100% bằng Java 8 và Apache Ant chuẩn (`ant compile`).
- [x] 2. Toàn bộ 116 bài kiểm thử tự động vượt qua (`ant test`).
- [x] 3. Không có credential, password thật hay API key trong mã nguồn và tài liệu.
- [x] 4. Tệp cấu hình thật `server/resources/db.properties` đã nằm trong `.gitignore`; tệp `db.properties.example` đã có sẵn làm mẫu.
- [x] 5. Không còn đường dẫn cá nhân (`C:\Users\thien\...`) trong tài liệu hướng dẫn và mã nguồn.
- [x] 6. Đã có tệp `LICENSE` (MIT) và thông cáo bản quyền bên thứ ba.
- [x] 7. Tài liệu `README.md` đầy đủ sơ đồ kiến trúc, tài khoản demo, hướng dẫn cài đặt, build và chạy cho người khác.
- [ ] 8. Người dùng xác nhận quyết định giữ nguyên lịch sử Git (Phương án A) hoặc yêu cầu rewrite commit `e0c5a90` (Phương án B).
- [ ] 9. Người dùng thực hiện commit các thay đổi của Phase 1.16:
  ```powershell
  git add client/ server/ test/ docs/ LICENSE
  git commit -m "feat: complete Phase 1.16 fixes (SEC-01, REL-01, license, docs standardization)"
  ```
- [ ] 10. Chuyển repository sang chế độ **Public** trên giao diện GitHub Settings.
