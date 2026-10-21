# BÁO CÁO XÁC MINH ĐỘC LẬP SAU SỬA LỖI (PHASE 1.17 POST-FIX VERIFICATION)

**Dự án:** Remote School Library Management System  
**Workspace:** `D:\workspace\thuvien`  
**Kiến trúc:** 3-Tier Distributed Architecture (Java Swing $\rightarrow$ Java RMI $\rightarrow$ MySQL 8)  
**Thời điểm thực hiện:** 21/10/2026  
**Trạng thái kiểm thử:** 116/116 tests **PASS (100%)** — 0 Failures, 0 Errors, 0 Skipped  

---

## TỔNG HỢP KẾT QUẢ ĐÁNH GIÁ

| Hạng mục kiểm tra | Trạng thái | Bằng chứng kỹ thuật chính |
|---|:---:|---|
| **1. Xác minh SEC-01 (Thu hồi phiên khi đổi mật khẩu)** | **PASS** | Kiểm tra luồng `RequestRouter` (L911-L930), `SessionManager` (L92-L99), và test case 6 giai đoạn trong `SecurityAndFeatureHardeningTest` (L730-L791). |
| **2. Xác minh REL-01 (Khôi phục trạng thái nút Trả sách)** | **PASS** | Kiểm tra `BorrowManagementPanel` (L864-L913), sự kiện chọn dòng (L211-L219), và đảm bảo thực thi trên EDT qua `AsyncWorker` (L32-L48). |
| **3. Xác minh kết quả kiểm thử tự động** | **PASS** | 13/13 test suites sinh mới lúc 13:04 - 13:05, 116/116 tests PASS 100%. Xác định rõ nguyên nhân lệnh `ant package-all` của người dùng gặp lỗi khóa file do tiến trình server đang chạy. |
| **4. Xác minh LICENSE & Chuẩn hóa tài liệu** | **PASS** | `LICENSE` hợp lệ MIT kèm Third-Party Notices. Toàn bộ file tracked sạch đường dẫn cá nhân (chỉ còn placeholder trong `README.md`). |
| **5. Kiểm tra lịch sử Git & Bí mật nhạy cảm** | **PASS** | Commit `e0c5a90` xác minh có mật khẩu rỗng (`password=`). Không có token, certificate hay private key trong lịch sử. |
| **6. Triển khai mạng LAN 2 máy thực tế** | **NOT VERIFIED** | RMI dynamic IP detection và firewall script đã sẵn sàng; chưa kiểm thử trên 2 máy vật lý độc lập qua Wi-Fi ngoài môi trường lab. |

---

## 1. Xác minh Lỗ hổng Bảo mật P1: SEC-01

### 1.1. Bằng chứng mã nguồn thực tế
1. **Thời điểm thu hồi phiên**:
   - Trong [RequestRouter.java](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L920-L928):
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
     ```
   - *Phân tích logic:* Hàm `userService.changePassword(...)` được thực thi trước. Nếu việc đổi mật khẩu thất bại (sai mật khẩu cũ, mật khẩu không khớp, hoặc lỗi DB), phương thức ném `AuthenticationException`/`ValidationException` hoặc trả về `false`. Khối `if (ok)` hoàn toàn **không** được thực thi, do đó **không thu hồi phiên nếu đổi mật khẩu thất bại**.
2. **Chính sách giữ phiên hiện tại và thu hồi các phiên khác**:
   - Trong [SessionManager.java](file:///D:/workspace/thuvien/server/src/thuvien/server/security/SessionManager.java#L92-L99):
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
   - *Phân tích logic:* Điều kiện `(exceptToken == null || !entry.getKey().equals(exceptToken))` bảo toàn mục có khóa bằng `request.getToken()`. Chỉ các mục thỏa mãn `userId.equals(...)` VÀ có token khác `request.getToken()` mới bị xóa khỏi map.
3. **An toàn đa luồng & Race condition**:
   - `activeSessions` được khai báo là `ConcurrentHashMap<String, SessionEntry>` (dòng 37).
   - Trong Java 8, `ConcurrentHashMap.entrySet().removeIf(...)` sử dụng weakly-consistent iterator an toàn đa luồng, loại bỏ phần tử thông qua iterator nội bộ của map, không ném `ConcurrentModificationException`, và không làm nghẽn các yêu cầu đọc/ghi đồng thời khác.
4. **Cô lập giữa các người dùng**:
   - Bộ lọc kiểm tra nghiêm ngặt `entry.getValue().session.getUserId().equals(userId)`. Phiên của người dùng khác có `userId` khác nên hoàn toàn không bị ảnh hưởng.

### 1.2. Bằng chứng kiểm thử tự động
Đọc chi tiết phương thức `test24_ChangePasswordRevokesOtherSessionsForSameUser()` trong [SecurityAndFeatureHardeningTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/SecurityAndFeatureHardeningTest.java#L730-L791):
- **Giai đoạn 1 (L734-L740)**: Khởi tạo 2 phiên độc lập cho `student1` (`sessionClientA`, `sessionClientB`) và xác nhận 2 token khác nhau (`assertNotEquals`).
- **Giai đoạn 2 (L741-L744)**: Khởi tạo phiên thứ ba cho `admin` (`sessionAdmin`).
- **Giai đoạn 3 (Negative test - L745-L754)**: Gửi request đổi mật khẩu với `wrong_current_password` qua Client A:
  - Khẳng định `respFail.getStatusCode() != StatusCode.OK`.
  - Khẳng định cả 3 token (`sessionClientA`, `sessionClientB`, `sessionAdmin`) vẫn hợp lệ (`sessionManager.isValid() == true`).
- **Giai đoạn 4 (Positive test - L756-L762)**: Gửi request đổi mật khẩu hợp lệ qua Client A:
  - Khẳng định trả về `StatusCode.OK` (200).
- **Giai đoạn 5 (Kiểm chứng hành vi thực tế - L763-L784)**:
  - Khẳng định `sessionManager.isValid(sessionClientA.getToken()) == true`. Gửi request `GET_CURRENT_USER` qua router trả về `StatusCode.OK`.
  - Khẳng định `sessionManager.isValid(sessionClientB.getToken()) == false`. Gửi request `GET_CURRENT_USER` qua router bằng token của Client B bị từ chối với `StatusCode.UNAUTHORIZED` (401).
  - Khẳng định `sessionManager.isValid(sessionAdmin.getToken()) == true`. Gửi request `GET_CURRENT_USER` của Admin trả về `StatusCode.OK`.
- **Giai đoạn 6 (Dọn dẹp - L785-L790)**: Đổi lại mật khẩu cũ `student123` để tránh làm sai lệch trạng thái CSDL.

**Kết luận mục 1: PASS**

---

## 2. Xác minh Lỗi Độ tin cậy Giao diện P2: REL-01

### 2.1. Luồng thực thi trong `BorrowManagementPanel.java`
1. **Khi nhấn Trả sách (`performReturn` - L864-L902)**:
   - Kiểm tra `getSelectedRecord()`. Nếu chưa chọn bản ghi, hiển thị thông báo yêu cầu chọn và kết thúc sớm (L866-L869).
   - Khi người dùng xác nhận `YES_OPTION`: Vô hiệu hóa `refreshBtn` và `returnBtn` (`setEnabled(false)` - L881-L882) để ngăn chặn double-click gửi trùng yêu cầu.
   - Gọi `AsyncWorker.run(...)` đẩy công việc gọi RMI `borrowController.returnBook(...)` sang worker thread ngầm.
2. **Khi trả sách thành công (`onSuccess` - L887-L898)**:
   - Bật lại `refreshBtn.setEnabled(true)`.
   - Hiển thị thông báo (kèm thông tin quá hạn nếu có).
   - Gọi `loadRecords()`. `loadRecords()` sau khi tải lại danh sách sẽ gọi `applyFilter(false)`.
   - Trong `applyFilter`: nếu bản ghi đang chọn trước đó được khôi phục hàng chọn (`restoreRow != -1`), sự kiện `ListSelectionListener` (L211-L219) được kích hoạt:
     ```java
     BorrowRecordDTO r = getSelectedRecord();
     if (returnBtn != null) {
         returnBtn.setEnabled(r != null && (r.getStatus() == BorrowStatus.ACTIVE || r.getStatus() == BorrowStatus.OVERDUE));
     }
     ```
   - Do bản ghi vừa trả đã chuyển sang trạng thái `BorrowStatus.RETURNED`, điều kiện trên trả về `false`, nút `returnBtn` chuyển sang **disabled** đúng nghiệp vụ.
3. **Khi gọi trả sách thất bại (`handleError` - L904-L913)**:
   - Mã nguồn thực tế:
     ```java
     private void handleError(Exception ex) {
         refreshBtn.setEnabled(true);
         if (newBorrowBtn != null) newBorrowBtn.setEnabled(true);
         if (returnBtn != null && isStaff) {
             BorrowRecordDTO cur = getSelectedRecord();
             returnBtn.setEnabled(cur != null && (cur.getStatus() == BorrowStatus.ACTIVE || cur.getStatus() == BorrowStatus.OVERDUE));
         }
         statusLabel.setText("Lỗi: " + ex.getMessage());
         LOGGER.log(Level.WARNING, "Borrow/Return error", ex);
         ...
     ```
   - *Đánh giá các trường hợp:*
     - **Gọi trả sách thất bại nhưng bản ghi đang chọn vẫn là `ACTIVE` hoặc `OVERDUE`**: `cur != null` và trạng thái hợp lệ $\rightarrow$ `returnBtn.setEnabled(true)`, người dùng có thể thao tác lại mà không bị khóa nút.
     - **Không có dòng được chọn** (`cur == null`): `returnBtn.setEnabled(false)`.
     - **Dòng được chọn có trạng thái `RETURNED` hoặc `LOST`**: `returnBtn.setEnabled(false)`.
     - **Vai trò không phải nhân viên** (`isStaff == false`): Khối lệnh không thực thi; nút `returnBtn` đã bị ẩn từ constructor (`setVisible(false)` - L190) và không bao giờ bị kích hoạt nhầm.
4. **Bảo vệ Swing Event Dispatch Thread (EDT)**:
   - Đọc [AsyncWorker.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/common/AsyncWorker.java#L32-L48):
     Phương thức `done()` của `javax.swing.SwingWorker` luôn được gọi trên **EDT**. Cả `onSuccess.accept(result)` và `onError.accept(cause)` đều được gọi bên trong `done()`. Do đó, `this::handleError` được đảm bảo 100% chạy trên EDT.

**Kết luận mục 2: PASS**

---

## 3. Xác minh Kết quả Kiểm thử & Sự cố Lệnh `ant package-all`

### 3.1. Phân tích lỗi lệnh `ant package-all` người dùng vừa gặp
- **Hiện tượng**: Người dùng chạy lệnh:
  `& "<NETBEANS_ANT_PATH>\ant.bat" package-all`
  và gặp lỗi:
  ```text
  ariCP-4.0.3.jar
  Total time: 0 seconds
  BUILD FAILED: Unable to delete file D:\workspace\thuvien\dist\server-dist\lib\HikariCP-4.0.3.jar
  ```
- **Bằng chứng nguyên nhân**:
  - Tại [build.xml](file:///D:/workspace/thuvien/build.xml#L122-L126), target `package-server` (được gọi bởi `package-all`) có lệnh:
    `<delete dir="${server.dist.dir}"/>`
  - Trên hệ điều hành Windows, một tiến trình Java đang chạy `dist\server-dist\run-server.bat` (đã chạy hơn 50 phút theo tiến trình nền) đang load và giữ file lock trên `dist\server-dist\lib\HikariCP-4.0.3.jar`. Windows cấm tuyệt đối việc xóa tệp đang bị khóa.
  - Các target `compile`, `compile-test`, và `test` **chỉ thao tác trên thư mục `build/` và `build/test/`**, hoàn toàn độc lập với `dist/`, do đó biên dịch và chạy test hoàn toàn bình thường.
  - **Khuyến nghị**: Để chạy `ant package-all` thành công, chỉ cần tắt cửa sổ tiến trình server đang chạy trước khi đóng gói.

### 3.2. Bằng chứng kết quả kiểm thử mới nhất
Lần chạy `ant test` hoàn thành lúc 13:05:07 ngày 21/10/2026. File XML trong `build/test/results/` có LastWriteTime: `10/21/2026 1:04:50 PM - 1:05:07 PM`.

| STT | Test Suite | Số Test | Pass | Fail | Error | Skipped | Thời gian (giây) | Trạng thái |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.260s | **PASS** |
| 2 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.451s | **PASS** |
| 3 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.075s | **PASS** |
| 4 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.333s | **PASS** |
| 5 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.059s | **PASS** |
| 6 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 6 | 0 | 0 | 0 | 2.499s | **PASS** |
| 7 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 1.569s | **PASS** |
| 8 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 10 | 0 | 0 | 0 | 0.407s | **PASS** |
| 9 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.370s | **PASS** |
| 10 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 1.203s | **PASS** |
| 11 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.553s | **PASS** |
| 12 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.051s | **PASS** |
| 13 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 30 | 30 | 0 | 0 | 0 | 5.840s | **PASS** |
| **TỔNG** | **13 Test Suites** | **116** | **116** | **0** | **0** | **0** | **18.0s** | **PASS 100%** |

**Kết luận mục 3: PASS**

---

## 4. Xác minh LICENSE & Chuẩn hóa Tài liệu

### 4.1. Tệp LICENSE
- [LICENSE](file:///D:/workspace/thuvien/LICENSE) áp dụng giấy phép chuẩn MIT License (bản quyền năm 2026 cho Contributors của dự án).
- Bổ sung mục **Third-Party Licenses Notice** nêu rõ bản quyền của các thư viện mã nguồn mở đang sử dụng: MySQL Connector/J (GPLv2 with FOSS Exception), HikariCP (Apache 2.0), SLF4J (MIT), JUnit (EPL-1.0), Hamcrest (BSD-3-Clause). Không tự nhận bản quyền trái phép.

### 4.2. Rà soát đường dẫn máy cá nhân & Thông tin nhạy cảm trong Git Tracked Files
1. **Tìm kiếm `C:\Users\`**:
   - Lệnh: `git grep -i "C:\\Users\\"`
   - Kết quả: Duy nhất 1 vị trí trong `README.md` dòng 284 dưới dạng placeholder minh họa: `"C:\Users\<USERNAME>\Downloads\netbeans-8.0.2-javase\netbeans\extide\ant\bin\ant.bat"`. Hoàn toàn không còn đường dẫn máy cá nhân thật.
2. **Tìm kiếm `D:\workspace\thuvien`**:
   - Lệnh: `git grep -i "D:\\workspace\\thuvien"`
   - Kết quả: Không xuất hiện trong mã nguồn `src/` hay `README.md`. Chỉ xuất hiện trong phần tiêu đề metadata của các báo cáo audit lịch sử (`docs/remediation-phase-1-*.md`, `docs/full-project-audit.md`), mang tính lưu trữ nội bộ không ảnh hưởng đến người dùng clone repo.
3. **Phân biệt Secret thật vs. Demo/Placeholder**:
   - `server/resources/db.properties.example`: Chứa `db.password=your_db_password` (placeholder).
   - `server/resources/db.properties` (tệp thật trên máy): Đã được `.gitignore` chặn, không nằm trong Git tracking.
   - `CreateUserRequestDTO.java`: Phương thức `toString()` hiển thị `initialPassword=[PROTECTED]`, bảo vệ dữ liệu log.
   - Tài khoản `admin123`, `lib123`, `student123`: Là dữ liệu mẫu phục vụ chấm điểm đồ án trong `database/seed.sql` và đã được cảnh báo trong `README.md`. Không phải mật khẩu thật.

### 4.3. Xác minh liên kết tương đối trong tài liệu
Các liên kết trong `README.md` (`server/src/thuvien/server/rmi/LibraryRMIServer.java`, `client/src/thuvien/client/ClientMain.java`, `database/seed.sql`) đã được xác minh tồn tại chính xác.

**Kết luận mục 4: PASS**

---

## 5. Kiểm tra Lịch sử Git & Bí mật

### 5.1. Xác minh Commit `e0c5a90`
- Lệnh: `git show e0c5a90:server/resources/db.properties`
- Nội dung xác nhận:
  ```properties
  db.driver=com.mysql.cj.jdbc.Driver
  db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
  db.username=root
  db.password=
  ```
- **Kết quả**: `db.password` để rỗng (cấu hình mặc định của Laragon local). Không có credential môi trường thật.
- Tệp này đã được xóa khỏi Git tracking tại commit `6e30dc4` và đưa vào `.gitignore`.

### 5.2. Rà soát toàn bộ lịch sử commit đối với các file nhạy cảm
- Lệnh kiểm tra các định dạng `.properties`, `.env`, `.pem`, `.key`, `.jks`:
  Chỉ có 5 tệp `.properties` từng xuất hiện: `nbproject/genfiles.properties`, `nbproject/project.properties`, `client/resources/client.properties`, `server/resources/server.properties`, `server/resources/db.properties`.
- **Kết luận**: Lịch sử Git không chứa khóa riêng tư, chứng chỉ bảo mật hay API key bí mật. Không cần thiết phải viết lại lịch sử (git filter-repo), khuyến nghị giữ nguyên commit history để bảo toàn tiến độ đồ án.

**Kết luận mục 5: PASS**

---

## 6. Trạng thái Git & Danh sách tệp chuẩn bị Commit

### 6.1. `git diff --check`
```text
warning: in the working copy of 'docs/security-audit-report.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'server/src/thuvien/server/security/SessionManager.java', LF will be replaced by CRLF the next time Git touches it
```
*(0 lỗi định dạng, không có trailing whitespace hay conflict markers).*

### 6.2. `git status --short`
```text
 M client/src/thuvien/client/view/panels/BorrowManagementPanel.java
 M docs/phase-1-11-lan-test-runbook.md
 M docs/security-audit-report.md
 M server/src/thuvien/server/router/RequestRouter.java
 M server/src/thuvien/server/security/SessionManager.java
 M test/thuvien/server/service/SecurityAndFeatureHardeningTest.java
?? LICENSE
?? docs/phase-1-13-merge-audit-report.md
?? docs/phase-1-14-quality-security-audit.md
?? docs/phase-1-15-repository-readiness-audit.md
?? docs/phase-1-16-implementation-report.md
?? docs/phase-1-17-post-fix-verification.md
```

### 6.3. Tóm tắt các tệp sẽ được đưa vào commit
1. **Source Code**:
   - `server/src/thuvien/server/security/SessionManager.java` (Sửa SEC-01)
   - `server/src/thuvien/server/router/RequestRouter.java` (Sửa SEC-01)
   - `client/src/thuvien/client/view/panels/BorrowManagementPanel.java` (Sửa REL-01)
2. **Automated Test**:
   - `test/thuvien/server/service/SecurityAndFeatureHardeningTest.java` (Test SEC-01)
3. **Legal & Licensing**:
   - `LICENSE` (Giấy phép MIT + Third-Party Notices)
4. **Documentation**:
   - `docs/security-audit-report.md` (Chuẩn hóa lệnh Ant)
   - `docs/phase-1-11-lan-test-runbook.md` (Chuẩn hóa path)
   - `docs/phase-1-13-merge-audit-report.md` (Báo cáo audit 1.13)
   - `docs/phase-1-14-quality-security-audit.md` (Báo cáo audit 1.14)
   - `docs/phase-1-15-repository-readiness-audit.md` (Báo cáo audit 1.15)
   - `docs/phase-1-16-implementation-report.md` (Báo cáo thực thi 1.16)
   - `docs/phase-1-17-post-fix-verification.md` (Báo cáo xác minh 1.17)

---

## 7. Khuyến nghị & Kết luận cuối cùng

1. **Khả năng commit**: **ĐỦ ĐIỀU KIỆN ĐỂ COMMIT**. Toàn bộ mã nguồn đã được xác minh độc lập, biên dịch sạch bằng Ant và vượt qua 116/116 bài kiểm thử tự động.
2. **Lệnh commit đề xuất**:
   ```powershell
   git add client/ server/ test/ docs/ LICENSE
   git commit -m "feat: complete Phase 1.16 & 1.17 (fix SEC-01, REL-01, add license, verify 116 tests)"
   ```
3. **Lưu ý khi đóng gói `package-all`**: Hãy dừng cửa sổ terminal đang chạy server (`dist\server-dist\run-server.bat`) trước khi thực hiện đóng gói để giải phóng khóa tệp trên `HikariCP-4.0.3.jar`.
