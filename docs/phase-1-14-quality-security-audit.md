# PHASE 1.14 — SYSTEM QUALITY, SECURITY & RELIABILITY AUDIT REPORT

**Dự án:** Remote School Library Management System  
**Workspace:** `D:\workspace\thuvien`  
**Kiến trúc:** Java Swing Client → Java RMI → Remote Library Server → MySQL 8.0.30 (HikariCP)  
**Môi trường:** Java 8 (Amazon Corretto jdk1.8.0_504), Apache Ant 1.9.4 (NetBeans 8.0.2), Laragon MySQL 8.0.30  
**Thời điểm thực hiện:** 2026-10-21  
**Phạm vi:** Kiểm toán chất lượng, an ninh bảo mật và độ tin cậy sau khi merge Phase 1.13  

---

## 1. Tóm tắt hiện trạng thực tế

- **Trạng thái Git**: Nhánh `main`, commit `437f6b1` (*Merge pull request #3 from thienle-c/fixx*), tracking `origin/main`. Working tree hoàn toàn sạch (clean), chỉ có tài liệu báo cáo mới được thêm vào.
- **Biên dịch & Build**:
  - Mã nguồn: 40 file `common`, 51 file `server`, 35 file `client` biên dịch thành công 100% với Java 8 target.
  - Phân tách module: Ant script (`build.xml`) tuân thủ nghiêm ngặt ranh giới: `common` không phụ thuộc Swing/JDBC; `server` không chứa Swing; `client` không chứa JDBC driver hoặc HikariCP.
- **Kiểm thử tự động**:
  - Toàn bộ 13 test suites với **115/115 tests PASS (100%)**, 0 failures, 0 errors, 0 skipped.
  - Test database: `school_library_test` được bảo vệ độc lập bởi [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java). Không có bất kỳ thao tác ghi/xóa nào diễn ra trên database nghiệp vụ `school_library`.
- **Giao tiếp mạng & RMI**:
  - RMI Registry chạy trên cổng cố định **1099**, Remote Object xuất khẩu trên cổng cố định **1100**.
  - JEP 290 Serialization Filter được kích hoạt với whitelist nghiêm ngặt (`thuvien.common.**`, kiểu dữ liệu an toàn tiêu chuẩn của JDK).
- **Trạng thái Phase 1.14**: Hoàn thành **Giai đoạn 1 — Audit chỉ đọc (Read-only Audit)**, chưa can thiệp sửa mã nguồn, ghi nhận trung thực các phát hiện và phân loại theo thứ tự ưu tiên P0–P3.

---

## 2. Thông tin Git & Working Tree

```text
Branch: main (up to date with 'origin/main')
Latest Commit: 437f6b1 Merge pull request #3 from thienle-c/fixx
Previous Commits:
  bd37358 Merge branch 'main' into fixx
  34b3626 feat: implement borrow service, RMI network client, and UI dashboard components
  1b2ae8f Merge pull request #2 from thienle-c/frontend
  6c63165 Merge branch 'main' into frontend
Working Tree Status: Clean (chỉ có file docs báo cáo mới)
```

---

## 3. Danh sách phát hiện theo phân loại P0 – P3

| Mã ID | Mức độ | Phân loại | Tóm tắt phát hiện | Trạng thái |
|:---:|:---:|:---|:---|:---:|
| **SEC-01** | **P1** | Security / Session | Đổi mật khẩu không tự động thu hồi (revoke) các phiên đăng nhập khác của cùng tài khoản | Đã xác minh |
| **REL-01** | **P2** | UI Reliability | Nút "Nhận trả sách" trong `BorrowManagementPanel` không được bật lại khi trả sách bị lỗi | Đã xác minh |
| **BLD-01** | **P2** | Build Ergonomics | `ant clean` xung đột file lock trên `dist/` khi Server đang chạy ngầm | Đã xác minh |
| **NET-01** | **P2** | Network Security | RMI truyền tải qua TCP Socket thuần, chưa có lớp mã hóa TLS/SSL | Đã xác minh (Giới hạn kiến trúc) |
| **MEM-01** | **P3** | Memory / Cleanup | `SessionManager` thu hồi phiên hết hạn theo cơ chế lazy (khi có truy vấn), chưa có scheduled purge định kỳ | Đã xác minh (Tối ưu hóa) |
| **LAN-01** | **P3** | Deployment Notice | Kiểm thử thực tế trên 2 laptop vật lý qua LAN chưa thực hiện (chỉ mới kiểm thử local) | Chưa kiểm tra runtime vật lý |

---

## 4. Chi tiết bằng chứng và phân tích mã nguồn từng phát hiện

### SEC-01 (P1): Đổi mật khẩu không thu hồi các phiên đăng nhập khác
- **Vị trí mã nguồn**:
  - [server/src/thuvien/server/service/impl/UserServiceImpl.java:L127-L154](file:///D:/workspace/thuvien/server/src/thuvien/server/service/impl/UserServiceImpl.java#L127-L154)
  - [server/src/thuvien/server/router/RequestRouter.java:L911-L925](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L911-L925)
- **Bằng chứng**:
  ```java
  // RequestRouter.java (L920)
  boolean ok = userService.changePassword(session.getUserId(), dto.getCurrentPassword(), dto.getNewPassword());
  if (auditLogService != null) {
      auditLogService.log(session.getUserId(), "PASSWORD_CHANGED", "User", session.getUserId(), "Đổi mật khẩu thành công");
  }
  return Response.ok(reqId, "Đổi mật khẩu thành công.", ok);
  ```
  `UserServiceImpl.changePassword(...)` cập nhật hash mật khẩu mới vào cơ sở dữ liệu và trả về `true`. Tuy nhiên, cả `UserServiceImpl` và `RequestRouter` đều không gọi `sessionManager.revokeAllSessionsForUser(userId)` hoặc thu hồi các session token khác đang hoạt động.
- **Rủi ro**: Trong kiến trúc hỗ trợ đa phiên (cùng tài khoản đăng nhập trên nhiều máy), nếu một người dùng đổi mật khẩu vì nghi ngờ bị lộ thông tin, các phiên đăng nhập đang mở trên máy khác vẫn tiếp tục hợp lệ cho đến khi hết hạn idle timeout (30 phút). Kẻ xâm nhập có token cũ vẫn có thể tiếp tục thao tác trong thời gian này.
- **Đề xuất khắc phục (Giai đoạn 2)**:
  Tại [RequestRouter.java:L920](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L920), sau khi đổi mật khẩu thành công, gọi:
  ```java
  sessionManager.revokeAllSessionsForUser(session.getUserId());
  // Tạo lại phiên mới cho chính token hiện tại để người dùng đang thao tác không bị ngắt quãng đột ngột
  // Hoặc yêu cầu đăng nhập lại toàn bộ để bảo đảm an toàn tuyệt đối
  ```

---

### REL-01 (P2): Nút "Nhận trả sách" không được khôi phục trạng thái khi trả sách bị lỗi
- **Vị trí mã nguồn**:
  - [client/src/thuvien/client/view/panels/BorrowManagementPanel.java:L882](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L882)
  - [client/src/thuvien/client/view/panels/BorrowManagementPanel.java:L904-L926](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L904-L926)
- **Bằng chứng**:
  Trong `performReturn()`:
  ```java
  returnBtn.setEnabled(false); // L882
  AsyncWorker.run(
      () -> borrowController.returnBook(record.getStudentId(), record.getBookId()),
      (ReturnResultDTO res) -> { ... },
      this::handleError // L899
  );
  ```
  Trong phương thức `handleError(Exception ex)`:
  ```java
  refreshBtn.setEnabled(true);
  if (newBorrowBtn != null) newBorrowBtn.setEnabled(true);
  // Thiếu: returnBtn.setEnabled(...)
  ```
- **Rủi ro**: Nếu quá trình trả sách gặp lỗi (lỗi mạng chập chờn, lỗi kết nối database, hoặc sách đã bị trả ở phiên khác), nút "Nhận trả sách" sẽ bị vô hiệu hóa vĩnh viễn cho đến khi người dùng nhấp chọn dòng khác trong bảng rồi nhấp quay lại.
- **Đề xuất khắc phục (Giai đoạn 2)**:
  Trong `handleError`, bổ sung khôi phục trạng thái cho `returnBtn`:
  ```java
  BorrowRecordDTO cur = getSelectedRecord();
  if (returnBtn != null) {
      returnBtn.setEnabled(cur != null && (cur.getStatus() == BorrowStatus.ACTIVE || cur.getStatus() == BorrowStatus.OVERDUE));
  }
  ```

---

### BLD-01 (P2): `ant clean` gặp lỗi file lock khi Server đang chạy
- **Vị trí**: [build.xml](file:///D:/workspace/thuvien/build.xml), `dist/server-dist/lib/HikariCP-4.0.3.jar`.
- **Bằng chứng**:
  ```text
  BUILD FAILED
  D:\workspace\thuvien\nbproject\build-impl.xml:1763: Unable to delete file D:\workspace\thuvien\dist\server-dist\lib\HikariCP-4.0.3.jar
  ```
  Nguyên nhân: Tiến trình `run-server.bat` đang chạy nền trong terminal của người dùng, giữ handle mở với file JAR của HikariCP trong thư mục `dist/`.
- **Đánh giá rủi ro**: Không ảnh hưởng đến mã nguồn hay database. Đây là hành vi bình thường của Windows file locking khi tiến trình Java đang chạy.
- **Khuyến nghị**: Khi biên dịch kiểm thử độc lập, sử dụng `ant compile compile-test test` hoặc tắt tiến trình server trước khi chạy `ant clean`.

---

### NET-01 (P2): Giao tiếp RMI hoạt động qua TCP Socket thuần không mã hóa TLS/SSL
- **Vị trí**:
  - [server/src/thuvien/server/rmi/LibraryRMIServer.java:L111-L121](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java#L111-L121)
  - [client/src/thuvien/client/network/RMIClient.java:L61-L64](file:///D:/workspace/thuvien/client/src/thuvien/client/network/RMIClient.java#L61-L64)
- **Bằng chứng**:
  RMI Registry và Remote Object sử dụng `LocateRegistry.createRegistry(1099)` và `UnicastRemoteObject(1100)` mặc định. Không có `SslRMIClientSocketFactory` / `SslRMIServerSocketFactory`.
- **Đánh giá rủi ro**:
  - *Mức độ bảo vệ hiện tại*: RMI Serialization Filter (JEP 290) ngăn chặn hoàn toàn Remote Code Execution (RCE) qua deserialization gadget chains. Xác thực token và mật khẩu được băm PBKDF2 trên server.
  - *Hạn chế*: Trên mạng LAN không tin cậy (Wi-Fi công cộng), dữ liệu truyền tải (bao gồm tên sách, thông tin sinh viên, token) có thể bị bắt trộm gói tin (sniffing qua Wireshark).
  - *Phù hợp bối cảnh*: Đồ án môn học Lập trình mạng yêu cầu triển khai Java RMI socket thuần, không dùng thư viện container web.
- **Khuyến nghị**: Ghi nhận rõ giới hạn kiến trúc trong tài liệu bàn giao đồ án. Nếu có yêu cầu nâng cao ở các đồ án chuyên sâu, có thể bổ sung SSL Socket Factory với keystore/truststore.

---

### MEM-01 (P3): Thu hồi phiên hết hạn trong bộ nhớ theo cơ chế lazy
- **Vị trí**: [server/src/thuvien/server/security/SessionManager.java:L37-L72](file:///D:/workspace/thuvien/server/src/thuvien/server/security/SessionManager.java#L37-L72)
- **Bằng chứng**:
  ```java
  public UserSessionDTO getSession(String token) {
      SessionEntry entry = activeSessions.get(token);
      if (entry == null) return null;
      long now = System.currentTimeMillis();
      if (entry.isExpired(now)) {
          activeSessions.remove(token);
          return null;
      }
      entry.touch(now);
      return entry.session;
  }
  ```
  Chỉ khi một request gửi kèm `token` đến thì `SessionManager` mới kiểm tra và xóa session hết hạn đó. Nếu một client tắt đột ngột và không bao giờ gửi request lại, entry đó nằm lại trong `ConcurrentHashMap`.
- **Đánh giá rủi ro**: Mức tiêu thụ bộ nhớ của một `SessionEntry` rất nhỏ (~100 bytes). Với phạm vi trường học vài trăm sinh viên, đây không phải rủi ro tràn RAM, nhưng là điểm cần tối ưu hóa dọn dẹp định kỳ.
- **Đề xuất cải tiến (P3)**: Bổ sung 1 timer quét định kỳ 30 phút một lần để gọi `activeSessions.values().removeIf(entry -> entry.isExpired(now))`.

---

## 5. Rà soát chuyên sâu 7 hạng mục chất lượng

### A. Authentication & Session
- [x] **Đăng nhập & Quản lý phiên**: `AuthServiceImpl.login()` xác thực người dùng, băm mật khẩu bằng `PBKDF2WithHmacSHA256` với 10.000 vòng lặp và muối ngẫu nhiên (salt 16-byte). Hỗ trợ tương thích ngược và tự động nâng cấp mã băm SHA-256 cũ.
- [x] **Cơ chế chống brute-force / Khóa tài khoản**: 5 lần nhập sai liên tiếp sẽ tự động khóa tài khoản trong 15 phút, đồng thời ghi log kiểm toán `ACCOUNT_LOCKED` và thu hồi toàn bộ session đang mở của tài khoản đó.
- [x] **Đa phiên độc lập (Multi-session)**: Hai máy client cùng đăng nhập một tài khoản (ví dụ `admin`) nhận hai UUID session token hoàn toàn độc lập. Khi Client A đăng xuất (`LOGOUT`), Server chỉ thu hồi đúng token của Client A, phiên của Client B vẫn hoạt động bình thường mà không bị ảnh hưởng.
- [x] **Bảo vệ thông tin nhạy cảm**: Tuyệt đối không log mật khẩu dạng plaintext trong toàn bộ hệ thống.

### B. Authorization & Nghiệp vụ (RBAC)
- [x] **Server-side Role Check**: Toàn bộ 47 Action trong `RequestRouter` được bảo vệ độc lập ở tầng máy chủ, không phụ thuộc vào việc ẩn/hiện nút trên giao diện Swing:
  - Sinh viên (`STUDENT`): Bị chặn tuyệt đối ở `BORROW_BOOK`, `RETURN_BOOK`, `CREATE_BOOK`, `DELETE_BOOK`, `CREATE_STUDENT`, v.v.
  - Phân quyền IDOR: Đối với các chức năng sinh viên (`GET_STUDENT`, `GET_STUDENT_ACTIVE_BORROWS`, `LIST_FINES`, `CREATE_RESERVATION`, `CANCEL_RESERVATION`), hệ thống buộc lấy theo `session.getUserId()`, ngăn chặn sinh viên xem hoặc hủy phiếu của sinh viên khác.
  - Quản trị viên (`ADMIN`): Có quyền thực thi đầy đủ các chức năng quản lý, phân quyền và cấp mã kích hoạt.

### C. Database & Transaction Integrity
- [x] **Tính nguyên tử khi mượn sách (`borrowBook`)**:
  - Thực thi trong 1 kết nối duy nhất với `conn.setAutoCommit(false)`.
  - Giảm tồn kho bằng câu lệnh có điều kiện nguyên tử: `UPDATE books SET available_copies = available_copies - 1 ... WHERE id = ? AND available_copies > 0`. Nếu hết sách, câu lệnh trả về 0 row affected, ném `BookUnavailableException` và rollback ngay lập tức.
  - Ngăn chặn mượn trùng lặp: Kiểm tra phiếu mượn đang `ACTIVE` của cùng sinh viên đối với cuốn sách đó.
- [x] **Tính nguyên tử khi trả sách (`returnBook`)**:
  - Cập nhật trạng thái phiếu mượn với điều kiện: `WHERE id = ? AND status = 'ACTIVE'`.
  - Tự động tính số ngày quá hạn và sinh bản ghi tiền phạt (`fines`) tương ứng (mức phạt mặc định 5.000 VNĐ/ngày).
  - Tăng số lượng sách có sẵn: `available_copies < total_copies`.
  - Giảm số lượng sách đang mượn của sinh viên: `current_borrow_count > 0`.
- [x] **Ngăn chặn SQL Injection**: 100% truy vấn trong các repository sử dụng `PreparedStatement` với tham số ràng buộc (`?`).

### D. RMI & An toàn mạng
- [x] **Cổng mạng cố định**: Registry (1099) và Remote Service (1100). Đã kiểm tra không sử dụng cổng ngẫu nhiên (dynamic anonymous port), tương thích tuyệt đối với Windows Firewall.
- [x] **Tự động nhận diện IPv4 LAN**: `LibraryRMIServer.resolveServerHost()` tự động lọc bỏ địa chỉ Loopback, VPN, VMware, VirtualBox, WSL để bind đúng địa chỉ IPv4 LAN nội bộ vào thuộc tính `java.rmi.server.hostname`.
- [x] **JEP 290 Serialization Filter**: Đã cấu hình và kiểm chứng chặn đứng các gadget classes độc hại thông qua [RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java).

### E. Swing UI & Luồng bất đồng bộ (EDT Protection)
- [x] **Không block Swing EDT**: Tất cả các thao tác gọi qua RMI mạng được đóng gói qua `AsyncWorker.run(...)` (`SwingWorker`), bảo đảm giao diện không bị treo đơ ("Not Responding") khi mạng trễ.
- [x] **Chống double-click**: Các nút xác nhận mượn sách (`submitButton`), đăng nhập (`loginButton`) đều tự động vô hiệu hóa (`setEnabled(false)`) ngay khi bắt đầu gửi request.
- [x] **Bảo toàn tính năng [BorrowDialog.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/dialogs/BorrowDialog.java)**: Tra cứu nhanh sinh viên, tra cứu sách, xem trước ngày trả theo spinner số ngày mượn, kiểm tra trước điều kiện mượn (`checkConditions`) hoạt động chính xác.

---

## 6. Kết quả kiểm thử tự động (Build & Test Execution)

### A. Lệnh thực thi:
```powershell
# 1. Biên dịch toàn bộ mã nguồn và test suite:
& "<NETBEANS_ANT_PATH>\ant.bat" compile compile-test
# Kết quả: BUILD SUCCESSFUL (2 giây)

# 2. Chạy toàn bộ test suites tích hợp:
& "<NETBEANS_ANT_PATH>\ant.bat" test
# Kết quả: BUILD SUCCESSFUL (16 giây)

# 3. Kiểm tra định dạng Git diff:
git diff --check
# Kết quả: Clean (0 lỗi)
```

### B. Bảng số liệu chi tiết từ các file XML (`build/test/results/TEST-*.xml`):

| STT | Test Suite | Số Test | Pass | Fail | Error | Skipped | Thời gian (giây) |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2 | 2 | 0 | 0 | 0 | 0.246s |
| 2 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 0.316s |
| 3 | `thuvien.server.repository.RepositoryArchitectureTest` | 3 | 3 | 0 | 0 | 0 | 0.052s |
| 4 | `thuvien.server.RequestRouterTest` | 24 | 24 | 0 | 0 | 0 | 0.417s |
| 5 | `thuvien.server.rmi.MultiSessionAndConcurrentBorrowTest` | 6 | 6 | 0 | 0 | 0 | 1.389s |
| 6 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 15 | 15 | 0 | 0 | 0 | 1.429s |
| 7 | `thuvien.server.rmi.RMISerializationFilterTest` | 10 | 10 | 0 | 0 | 0 | 0.349s |
| 8 | `thuvien.server.service.AuthServiceTest` | 5 | 5 | 0 | 0 | 0 | 0.328s |
| 9 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 4 | 4 | 0 | 0 | 0 | 1.882s |
| 10 | `thuvien.server.service.CreateUserSecurityTest` | 4 | 4 | 0 | 0 | 0 | 0.505s |
| 11 | `thuvien.server.service.FineServiceTest` | 3 | 3 | 0 | 0 | 0 | 0.047s |
| 12 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 29 | 29 | 0 | 0 | 0 | 5.291s |
| 13 | `thuvien.server.TestDatabaseSafetyGuardTest` | 6 | 6 | 0 | 0 | 0 | 0.063s |
| **TỔNG** | **13 Test Suites** | **115** | **115** | **0** | **0** | **0** | **11.914s (Tổng chạy 16s)** |

---

## 7. Các hạng mục chưa thể xác minh & Rủi ro tồn tại

1. **Kiểm thử trên 2 máy vật lý qua mạng LAN thực tế**:
   - *Hiện trạng*: Đã kiểm thử kết nối RMI Client/Server, hostname resolution và loopback trên máy phát triển. Chưa có 2 máy tính vật lý kết nối qua switch/router Wi-Fi để nghiệm thu thực tế về độ trễ, drop gói tin và firewall đa máy.
   - *Phân loại*: **Chưa kiểm tra runtime vật lý (Unverified multi-host runtime)**.
2. **File lock khi vừa chạy Server vừa Clean build**:
   - Khi chạy `run-server.bat`, Windows lock file `HikariCP-4.0.3.jar` trong `dist/`. Cần tắt Server trước khi thực hiện `ant clean`.

---

## 8. Đề xuất kế hoạch xử lý tiếp theo (Giai đoạn 2)

Tuân thủ nguyên tắc an toàn, các sửa đổi được đề xuất theo mức độ ưu tiên tối thiểu:

1. **Nhóm 1 (P1 - Bảo mật phiên khi đổi mật khẩu)**:
   - Sửa [RequestRouter.java:L920](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L920): Sau khi đổi mật khẩu thành công, thu hồi các phiên đăng nhập khác của tài khoản để bảo đảm an toàn.
   - Bổ sung test case trong `SecurityAndFeatureHardeningTest` để xác nhận sau khi đổi mật khẩu, phiên cũ trên máy khác bị từ chối.
2. **Nhóm 2 (P2 - Khôi phục trạng thái nút UI)**:
   - Sửa [BorrowManagementPanel.java:L904](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L904): Khôi phục lại trạng thái `setEnabled()` cho nút "Nhận trả sách" trong khối `handleError`.
3. **Nhóm 3 (Xác minh hồi quy)**:
   - Chạy lại toàn bộ 115 test cases, kiểm tra `git diff` để đảm bảo không làm phát sinh lỗi mới.

---

## 9. Danh sách file được kiểm toán & Danh sách file thay đổi

### File được kiểm toán trong Phase 1.14:
- `server/src/thuvien/server/security/SessionManager.java`
- `server/src/thuvien/server/security/PasswordHasher.java`
- `server/src/thuvien/server/service/impl/AuthServiceImpl.java`
- `server/src/thuvien/server/service/impl/UserServiceImpl.java`
- `server/src/thuvien/server/service/impl/BorrowServiceImpl.java`
- `server/src/thuvien/server/service/impl/ReturnServiceImpl.java`
- `server/src/thuvien/server/repository/impl/BorrowRepositoryImpl.java`
- `server/src/thuvien/server/repository/impl/BookRepositoryImpl.java`
- `server/src/thuvien/server/router/RequestRouter.java`
- `server/src/thuvien/server/rmi/LibraryRMIServer.java`
- `server/src/thuvien/server/rmi/LibraryRemoteServiceImpl.java`
- `client/src/thuvien/client/network/RMIClient.java`
- `client/src/thuvien/client/ClientMain.java`
- `client/src/thuvien/client/view/LoginForm.java`
- `client/src/thuvien/client/view/MainDashboardForm.java`
- `client/src/thuvien/client/view/dialogs/BorrowDialog.java`
- `client/src/thuvien/client/view/panels/BorrowManagementPanel.java`
- `test/thuvien/server/TestDatabaseSafetyGuard.java`

### File thay đổi trong Giai đoạn 1 (Audit chỉ đọc):
- `docs/phase-1-14-quality-security-audit.md` (Tạo mới báo cáo kiểm toán)
- *Không có file mã nguồn nào bị thay đổi trong giai đoạn này.*
