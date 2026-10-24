# Báo cáo Nghiệm thu Mạng LAN và Mức độ Sẵn sàng Trình diễn Phase 1.11 (Phase 1.11 LAN Acceptance & Demo Readiness Report)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Thời gian thực hiện:** 15/10/2026 15:05  
**Môi trường thực thi:** OpenJDK 1.8.0_504 (Amazon Corretto 64-bit), MySQL 8.0.30 (Laragon), Apache Ant 1.9.4 (NetBeans 8.0.2 runtime)  
**Địa chỉ IP LAN Server thực tế:** `192.168.1.232` (Intel Wi-Fi 6 AX201)  
**Đánh giá tổng thể (Overall Verdict):** **PASS WITH LIMITATIONS**

---

## 1. Phân Định Rõ Ràng Các Cấp Độ Kiểm Thử (Testing Scope Categorization)

Để đảm bảo tính trung thực kỹ thuật tuyệt đối, toàn bộ các hạng mục kiểm thử được phân định rạch ròi theo 4 mức độ:

| Cấp độ kiểm thử | Nội dung kỹ thuật | Phạm vi thực hiện | Kết quả thực tế | Ghi chú & Giới hạn |
|:---|:---|:---:|:---:|:---|
| **Cấp độ 1: Kiểm thử Tự động (Automated Tests)** | Toàn bộ 12 test suites kiểm tra protocol, logic nghiệp vụ mượn trả, kiểm soát concurrency, password hasher, safety guard và JEP 290 filter. | Máy phát triển (Máy A) | **109/109 PASS (100%)** | Chạy qua `ant clean test`, 0 Fail, 0 Error, 0 Skipped; 12 file XML sinh mới đồng loạt lúc 15:02:16. |
| **Cấp độ 2: Kiểm thử Thủ công trên Server (Server Manual Validation)** | Khởi động `LibraryRMIServer`, xác nhận tự động phân giải IP LAN vật lý (`192.168.1.232`), tạo Registry (1099), export Remote Object (1100), nạp JEP 290 filter. | Máy phát triển (Máy A) | **PASS** | Server bind thành công cả 2 cổng 1099 và 1100; log ghi nhận quảng bá đúng IP LAN; bỏ qua adapter ảo VPN. |
| **Cấp độ 3: Kiểm thử Stub & Gọi hàm Từ xa nội bộ (In-Process RMI Invocation)** | Bóc tách cấu trúc Stub (`sun.rmi.server.UnicastServerRef` $\rightarrow$ `TCPEndpoint`), xác minh stub chứa IP LAN và port 1100, gọi phương thức `ping()`. | Máy phát triển (Máy A) | **PASS** | Stub trả về đúng host và port; lệnh gọi remote `ping()` trả về `"PONG"`. |
| **Cấp độ 4: Kiểm thử Thực tế từ Máy thứ hai qua Mạng LAN (Physical 2-Machine LAN Acceptance)** | Kết nối máy trạm thứ hai qua cùng router Wi-Fi, kiểm tra thông mạch cổng 1099/1100, cấu hình `client.properties` và chạy giao diện Swing Client. | Máy A (Server) $\leftrightarrow$ Máy B (Client) | **CHƯA KIỂM THỬ (PENDING)** | Môi trường phòng lab hiện tại chỉ có 1 máy vật lý; đã bàn giao đầy đủ Runbook và Demo Checklist để người dùng thực hiện khi có máy thứ hai. |

---

## 2. Kết Quả Kiểm Tra Baseline Trước Khi Thử LAN (Task A)

- **Lệnh thực thi**: `ant clean test` sử dụng runtime Ant 1.9.4 đi kèm NetBeans 8.0.2.
- **Exit Code**: `0` (`BUILD SUCCESSFUL`, thời gian thực thi: 15 giây).
- **Chi tiết kết quả 12 Test Suites (Timestamp: 15:02:05 - 15:02:16 ngày 15/10/2026)**:
  1. `thuvien.common.ProtocolTest`: 2 tests — PASS
  2. `thuvien.server.RequestRouterTest`: 24 tests — PASS
  3. `thuvien.server.TestDatabaseSafetyGuardTest`: 6 tests — PASS
  4. `thuvien.server.database.MySQLSmokeIntegrationTest`: 4 tests — PASS
  5. `thuvien.server.repository.RepositoryArchitectureTest`: 3 tests — PASS
  6. `thuvien.server.rmi.RMIEndToEndIntegrationTest`: 15 tests — PASS
  7. `thuvien.server.rmi.RMISerializationFilterTest`: 10 tests — PASS
  8. `thuvien.server.service.AuthServiceTest`: 5 tests — PASS
  9. `thuvien.server.service.BorrowConcurrencyIntegrationTest`: 4 tests — PASS
  10. `thuvien.server.service.CreateUserSecurityTest`: 4 tests — PASS
  11. `thuvien.server.service.FineServiceTest`: 3 tests — PASS
  12. `thuvien.server.service.SecurityAndFeatureHardeningTest`: 29 tests — PASS
  - **Tổng cộng: 109 Tests — 109 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**.

---

## 3. Xác Định Cấu Hình Thực Tế Của Server và Client (Task B)

Đối chiếu mã nguồn tại [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java), [ClientMain.java](file:///D:/workspace/thuvien/client/src/thuvien/client/ClientMain.java), [server.properties](file:///D:/workspace/thuvien/server/resources/server.properties) và [client.properties](file:///D:/workspace/thuvien/client/resources/client.properties):

1. **Cổng Registry Server**: `1099` (Khởi tạo qua `LocateRegistry.createRegistry(1099)`).
2. **Cổng Remote Object**: `1100` (Export cố định qua `super(servicePort)` trong `LibraryRemoteServiceImpl`).
3. **Tên dịch vụ RMI**: `LibraryRemoteService` (Đồng bộ tuyệt đối giữa Server, Client và Registry).
4. **Khóa cấu hình & Thứ tự ưu tiên**:
   - Client và Server đều đọc theo thứ tự ưu tiên: `Canonical Key (rmi.*)` $\rightarrow$ `Fallback Key (server.*)` $\rightarrow$ `Default Value`.
   - `rmi.server.host` / `server.host`: IP máy chủ (Server tự động chọn IP LAN, Client cấu hình trỏ tới Server).
   - `rmi.registry.port` / `server.port`: Cổng Registry (mặc định 1099).
   - `rmi.service.name` / `server.service`: Tên dịch vụ (mặc định `LibraryRemoteService`).
5. **Giá trị Host Quảng bá trong Stub**:
   - Khi chạy trên Máy A, hàm `resolveServerHost()` tự động phát hiện IP Wi-Fi vật lý: `192.168.1.232` (bỏ qua adapter ảo Radmin VPN `26.122.195.188`).
   - Stub RMI được đóng gói với địa chỉ `192.168.1.232:1100`, bảo đảm khi Client nhận stub sẽ kết nối ngược lại đúng interface LAN vật lý của Server.
6. **Kiến trúc Cơ sở Dữ liệu Phân tán**:
   - Client được biên dịch với quy tắc **ZERO DATABASE DEPENDENCY** (không có MySQL JDBC driver, không HikariCP, không import `java.sql.*`).
   - Client chỉ trao đổi qua giao thức Java RMI tới cổng 1099 và 1100 của Server.
   - Cổng MySQL 3306 chỉ mở nội bộ trên Server (`localhost:3306`), tuyệt đối không mở ra mạng LAN.

---

## 4. Xác Minh An Toàn Cơ Sở Dữ Liệu Nghiệp Vụ (Task D & Safety)

Số liệu kiểm tra thực tế đối chiếu trước và sau lần chạy `ant clean test`:

| Bảng dữ liệu | Trước `ant clean test` | Sau `ant clean test` | Biến động | Kết luận bảo toàn |
|:---|:---:|:---:|:---:|:---|
| `school_library.audit_logs` | **5631** | **5631** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.books` | **5** | **5** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.users` | **7** | **7** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.students` | **4** | **4** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.borrow_records` | **2** | **2** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library_test.audit_logs` | 1889 | 2078 | +189 | 100% log test cô lập vào test DB |

- **Giới hạn bằng chứng lịch sử**: Không thể chứng minh hồi tố thời điểm trước Phase 1.4 do không có bản dump ban đầu. Toàn bộ các đợt chạy từ Phase 1.4 đến nay đều được bảo vệ nguyên vẹn bởi [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java).

---

## 5. Tài Liệu Bàn Giao Đã Hoàn Thành (Task C, D, E)

1. [docs/phase-1-11-lan-test-runbook.md](file:///D:/workspace/thuvien/docs/phase-1-11-lan-test-runbook.md):
   - Hướng dẫn chi tiết thiết lập Máy A (Server) và Máy B (Client).
   - Sơ đồ kết nối mạng RMI 3 tầng.
   - Hướng dẫn lệnh PowerShell mở tường lửa cho Private profile (người dùng tự quyết định).
   - Bảng tra cứu chẩn đoán 9 cấp độ lỗi (Subnet, Port 1099, Port 1100, Stub advertising, v.v.).
2. [docs/phase-1-11-demo-checklist.md](file:///D:/workspace/thuvien/docs/phase-1-11-demo-checklist.md):
   - Bảng kiểm thử 6 kịch bản demo sống động: Đăng nhập/Rate-limiting, Tra cứu sách, Quản lý độc giả, Mượn sách nguyên tử, Trả sách & phạt, Phân quyền độc giả.
   - Tài khoản mẫu sẵn có: `librarian1` / `lib123`, `student1` / `student123`.

---

## 6. Kiểm Tra Trạng Thái Git (Task F)

- `git diff --check`: Không có lỗi định dạng hay khoảng trắng thừa.
- `git status --short --branch`:
  ```text
  ## fixx...origin/fixx
   M server/src/thuvien/server/rmi/LibraryRMIServer.java
  ?? docs/phase-1-11-acceptance-report.md
  ?? docs/phase-1-11-demo-checklist.md
  ?? docs/phase-1-11-lan-test-runbook.md
  ?? docs/remediation-phase-1-10-report.md
  ?? docs/remediation-phase-1-9-report.md
  ```
  *(Toàn bộ thay đổi và file tài liệu mới được bảo toàn, không commit, không push, không reset, không stash)*.

---

## 7. Đánh Giá Tổng Kết & Hướng Dẫn Thực Hiện Tiếp Theo

### **KẾT LUẬN: PASS WITH LIMITATIONS**

**Lý do:**
1. **ĐẠT (PASS)**:
   - 109/109 bài kiểm thử tự động đạt kết quả xuất sắc (0 Fail, 0 Error, 0 Skipped).
   - Server khởi động và bind thành công trên IP LAN vật lý `192.168.1.232`, mở đúng Registry port 1099 và Remote Object port 1100.
   - Cơ sở dữ liệu nghiệp vụ `school_library` được bảo vệ nguyên vẹn 100% (5631 logs không đổi).
   - Bộ tài liệu Runbook và Demo Checklist hoàn thiện chi tiết, chuyên nghiệp.
2. **GIỚI HẠN (LIMITATIONS)**:
   - Cần thực hiện bước kết nối thực tế từ Máy B khi có máy trạm thứ hai trong phòng lab theo hướng dẫn tại [docs/phase-1-11-lan-test-runbook.md](file:///D:/workspace/thuvien/docs/phase-1-11-lan-test-runbook.md).
   - Quản trị viên cần chạy lệnh mở firewall Private trên Máy A nếu Windows Defender chặn các cổng 1099 và 1100.
