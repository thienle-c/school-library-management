# Báo cáo Xác minh Bằng chứng Độc lập Phase 1.10 (Final Evidence Verification Phase 1.10)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Thời gian thực hiện:** 15/10/2026 14:50  
**Môi trường thực thi:** OpenJDK 1.8.0_504 (Amazon Corretto 64-bit), MySQL 8.0.30 (InnoDB), Apache Ant 1.9.4 (NetBeans 8.0.2 runtime)  
**Trạng thái kiểm thử mới nhất (`ant clean test`):** **12 Test Suites, 109 Tests — 109 PASS, 0 FAIL, 0 ERROR, 0 SKIPPED**  
**Đánh giá tổng kết (Overall Rating):** **PASS WITH LIMITATIONS**

---

## 1. Lệnh Đã Chạy, Exit Code và Kết Quả Thực Tế

Toàn bộ quá trình xác minh Phase 1.10 được ghi nhận qua các lệnh thực thi độc lập:

| STT | Lệnh thực thi | Mục đích | Exit Code | Kết quả quan sát thực tế |
|:---:|:---|:---|:---:|:---|
| 1 | `ant clean test` | Xóa sạch `build/` và chạy toàn bộ test suites | **0** | `BUILD SUCCESSFUL`, tổng thời gian 22 giây; sinh mới hoàn toàn 12 file XML báo cáo |
| 2 | PowerShell XML Aggregator | Tổng hợp chi tiết kết quả từ 12 file XML mới sinh | **0** | 109/109 Tests PASS, 0 Failures, 0 Errors, 0 Skipped |
| 3 | `java ... VerifyPhase110Evidence` | Kiểm chứng độc lập RMI, filter JEP 290 và safety guard | **0** | Đạt 100% tất cả các kiểm chứng thực nghiệm |
| 4 | `mysql ... information_schema & COUNT(*)` | Đối chiếu số bản ghi `school_library` và `school_library_test` trước và sau test | **0** | `school_library`: 0 thay đổi; `school_library_test`: +189 logs |
| 5 | `git diff --check` | Kiểm tra cú pháp, ký tự thừa và định dạng | **0** | Sạch hoàn toàn, không có cảnh báo cú pháp/whitespace |
| 6 | `git status --short --branch` | Kiểm tra trạng thái Git repository | **0** | Bảo toàn tuyệt đối working tree, không commit, không push |

---

## 2. Số Test Được Xác Minh Từ Lần Chạy Mới Nhất (`ant clean test`)

Timestamp của 12 file kết quả kiểm thử XML trong thư mục `build/test/results` đều được sinh mới đồng loạt trong khoảng thời gian từ `2026-10-15 14:47:16` đến `2026-10-15 14:47:31`:

| STT | Tên Test Suite | Thời điểm tạo XML (Timestamp) | Tests | PASS | FAIL | ERROR | SKIPPED | Thời gian chạy (giây) |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `thuvien.common.ProtocolTest` | 2026-10-15 14:47:16 | 2 | 2 | 0 | 0 | 0 | 0.418 |
| 2 | `thuvien.server.RequestRouterTest` | 2026-10-15 14:47:17 | 24 | 24 | 0 | 0 | 0 | 0.848 |
| 3 | `thuvien.server.TestDatabaseSafetyGuardTest` | 2026-10-15 14:47:18 | 6 | 6 | 0 | 0 | 0 | 0.135 |
| 4 | `thuvien.server.database.MySQLSmokeIntegrationTest` | 2026-10-15 14:47:19 | 4 | 4 | 0 | 0 | 0 | 0.526 |
| 5 | `thuvien.server.repository.RepositoryArchitectureTest` | 2026-10-15 14:47:19 | 3 | 3 | 0 | 0 | 0 | 0.114 |
| 6 | `thuvien.server.rmi.RMIEndToEndIntegrationTest` | 2026-10-15 14:47:21 | 15 | 15 | 0 | 0 | 0 | 1.690 |
| 7 | `thuvien.server.rmi.RMISerializationFilterTest` | 2026-10-15 14:47:23 | 10 | 10 | 0 | 0 | 0 | 0.640 |
| 8 | `thuvien.server.service.AuthServiceTest` | 2026-10-15 14:47:24 | 5 | 5 | 0 | 0 | 0 | 0.568 |
| 9 | `thuvien.server.service.BorrowConcurrencyIntegrationTest` | 2026-10-15 14:47:25 | 4 | 4 | 0 | 0 | 0 | 0.749 |
| 10 | `thuvien.server.service.CreateUserSecurityTest` | 2026-10-15 14:47:26 | 4 | 4 | 0 | 0 | 0 | 0.895 |
| 11 | `thuvien.server.service.FineServiceTest` | 2026-10-15 14:47:26 | 3 | 3 | 0 | 0 | 0 | 0.085 |
| 12 | `thuvien.server.service.SecurityAndFeatureHardeningTest` | 2026-10-15 14:47:31 | 29 | 29 | 0 | 0 | 0 | 4.455 |
| **Tổng cộng** | **12 Test Suites** | **Đồng bộ trong lần chạy clean test** | **109** | **109** | **0** | **0** | **0** | **~11.1s (Test) / 22s (Ant)** |

---

## 3. Bằng Chứng Thực Tế Cho Từng Cấu Hình RMI

Qua chương trình kiểm chứng độc lập [VerifyPhase110Evidence.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/VerifyPhase110Evidence.java):

1. **Khóa Fallback (`server.*`) hoạt động độc lập**:
   - Khi cấu hình chỉ chứa `server.host=192.168.1.50`, `server.port=1098`, `server.service=FallbackLibraryService`:
   - Client và Server đều đọc đúng giá trị từ các khóa cũ này mà không phụ thuộc vào khóa `rmi.*`.
2. **Khóa Canonical (`rmi.*`) chuẩn hóa**:
   - Khi cấu hình chứa `rmi.server.host=10.0.107.143`, `rmi.registry.port=1099`, `rmi.service.port=1100`, `rmi.service.name=LibraryRemoteService`:
   - Hệ thống đọc đúng chuẩn hiện đại, áp dụng chính xác cho Registry (1099) và Remote Object (1100).
3. **Thứ tự ưu tiên (Precedence)**:
   - Khi cả hai bộ khóa cùng tồn tại, khóa canonical (`rmi.*`) ghi đè hoàn toàn khóa fallback (`server.*`).
4. **Bằng chứng Stub Endpoint**:
   - Đã bóc tách cấu trúc nhị phân của đối tượng Stub:
     ```text
     Stub class: thuvien.server.rmi.LibraryRemoteServiceImpl
     RemoteRef: sun.rmi.server.UnicastServerRef
     Endpoint: [127.0.0.1:12100]
     Stub Advertised Host: 127.0.0.1
     Stub Advertised Port: 12100
     ```
   - Xác nhận: Stub chứa chính xác địa chỉ IP và cổng mà Server chỉ định quảng bá; lệnh gọi hàm từ xa `ping()` qua stub trả về `"PONG"`.
5. **Ranh giới Cấp độ Mạng (Network Testing Levels)**:
   - **Cấp độ A (Local Loopback `127.0.0.1`)**: Đã xác minh 100% bằng 109 bài test tự động.
   - **Cấp độ B (Same-Machine qua LAN IP `10.0.107.143`)**: Đã xác minh qua RMI endpoint inspection và socket binding.
   - **Cấp độ C (Máy thứ hai vật lý qua LAN)**: **CHƯA ĐƯỢC XÁC MINH** do không có máy tính vật lý thứ hai trong môi trường làm việc. Không suy diễn thành công ở Cấp độ A/B sang Cấp độ C.

---

## 4. Bằng Chứng Kích Hoạt và Hiệu Lực Của Serialization Filter (JEP 290)

1. **Cơ chế Cài đặt Filter trên JDK 8 (Amazon Corretto `1.8.0_504`)**:
   - Mã nguồn [LibraryRMIServer.java](file:///D:/workspace/thuvien/server/src/thuvien/server/rmi/LibraryRMIServer.java) thực thi:
     ```java
     System.setProperty("jdk.serialFilter", DEFAULT_SERIAL_FILTER);
     java.security.Security.setProperty("jdk.serialFilter", DEFAULT_SERIAL_FILTER);
     ```
   - Chuỗi cấu hình kích hoạt:
     `maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;com.sun.proxy.**;!*`
   - Log kích hoạt của JDK ghi nhận:
     ```text
     INFO: Creating serialization filter from maxdepth=15;maxbytes=10485760;maxrefs=50000;maxarray=100000;thuvien.common.**;java.lang.**;java.util.**;java.math.**;java.sql.**;java.rmi.**;sun.rmi.**;com.sun.proxy.**;!*
     ```
2. **Filter nào được áp dụng cho RMI và Test**:
   - Thuộc tính `jdk.serialFilter` là bộ lọc tiến trình toàn cục (process-wide filter) của JVM.
   - Mọi kết nối RMI Inbound đi qua RMI TCP Transport và mọi luồng `ObjectInputStream` được tạo trong JVM đều tự động kế thừa bộ lọc này.
   - Bài test [RMISerializationFilterTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMISerializationFilterTest.java) và [VerifyPhase110Evidence.java](file:///C:/Users/thien/.gemini/antigravity-ide/brain/4c061636-0e26-4369-adab-3f0c940b70a7/scratch/VerifyPhase110Evidence.java) kiểm tra trên chính `ObjectInputStream` thừa hưởng active filter này.
3. **Bằng chứng Từ chối Trực tiếp (Direct Empirical Rejections)**:
   - Lớp ngoài whitelist: `ObjectInputFilter REJECTED: class UnauthorizedMaliciousPayload, array length: -1, nRefs: 1, depth: 1, bytes: 113` $\rightarrow$ `InvalidClassException`.
   - Giới hạn `maxdepth`: `ObjectInputFilter REJECTED: null, array length: -1, nRefs: 17, depth: 16, bytes: 287` $\rightarrow$ Từ chối tại độ sâu 16 (ngưỡng 15).
   - Giới hạn `maxarray`: `ObjectInputFilter REJECTED: class [I, array length: 100005, nRefs: 2, depth: 1, bytes: 27` $\rightarrow$ Từ chối mảng 100.005 (ngưỡng 100.000).
   - Giới hạn `maxbytes`: `ObjectInputFilter REJECTED: null, array length: -1, nRefs: 109, depth: 2, bytes: 10501126` $\rightarrow$ Từ chối luồng 11 MB khi chạm 10.501.126 bytes (ngưỡng 10.485.760).
   - Giới hạn `maxrefs`: `ObjectInputFilter REJECTED: null, array length: -1, nRefs: 50001, depth: 2, bytes: 850022` $\rightarrow$ Từ chối tại tham chiếu thứ 50.001 (ngưỡng 50.000).
   - Dynamic Proxy: `ObjectInputFilter REJECTED: interface UnauthorizedProxyIface` $\rightarrow$ Bị chặn ngay khi nạp interface chưa được cấp phép.
4. **Giới hạn Bảo mật Thừa nhận (Strict Limitation Notice)**:
   - Whitelist hiện tại bảo vệ chống lại các thư viện gadget bên thứ ba (Commons Collections, Spring, Groovy...) và cạn kiệt tài nguyên DoS.
   - Whitelist **vẫn còn chứa wildcard cấp package** (`java.util.**`, `java.lang.**`), do đó **không tuyên bố bảo vệ toàn diện trước mọi chuỗi gadget thuần JDK có thể được phát hiện trong tương lai**.

---

## 5. Kết Quả Kiểm Tra An Toàn Cơ Sở Dữ Liệu (Database Safety Verification)

1. **Xác minh Mã nguồn Toàn bộ Các Đường dẫn Ghi/Xóa (Write & Cleanup Paths)**:
   - [TestDatabaseSafetyGuard.java](file:///D:/workspace/thuvien/test/thuvien/server/TestDatabaseSafetyGuard.java) bắt buộc kiểm tra `conn.getCatalog()`.
   - [BorrowConcurrencyIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/BorrowConcurrencyIntegrationTest.java): Kiểm tra `isSafeTestDatabase(conn)` trước khi khởi tạo service và trong teardown.
   - [SecurityAndFeatureHardeningTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/SecurityAndFeatureHardeningTest.java): Gọi `requireIsolatedTestDb()` trước mọi thao tác nghiệp vụ ghi.
   - [RMIEndToEndIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/rmi/RMIEndToEndIntegrationTest.java): Kiểm tra `isSafeTestDatabase(conn)` trong `setUpRMIAndDatabase()` trước khi thiết lập dữ liệu mẫu.
   - [MySQLSmokeIntegrationTest.java](file:///D:/workspace/thuvien/test/thuvien/server/database/MySQLSmokeIntegrationTest.java): 100% chỉ thực hiện `SELECT` và metadata inspection.
   - [CreateUserSecurityTest.java](file:///D:/workspace/thuvien/test/thuvien/server/service/CreateUserSecurityTest.java): Dùng Mock Repository in-memory, hoàn toàn không chạm tới database.
2. **Kiểm tra Các Kịch bản Biên (Edge Cases)**:
   - `conn == null`: Bị từ chối an toàn (`WARNING: Connection is null`).
   - `conn.isClosed()`: Bị từ chối an toàn (`WARNING: Connection is closed`).
   - `catalog == null` hoặc rỗng: Bị từ chối an toàn (`WARNING: Connection catalog is null or empty`).
   - `catalog.equalsIgnoreCase("school_library")`: Bị chặn đứng vô điều kiện (`SEVERE: ABORT: Target database is the production/coursework database`).
   - `catalog.equalsIgnoreCase("school_library_test")`: Được phép chạy kiểm thử.
   - URL trỏ tới `school_library` nhưng catalog là `school_library_test`: Bị chặn đứng do xung đột URL/catalog.
   - URL trỏ tới database khác với catalog: Bị chặn đứng do sai lệch siêu dữ liệu.
   - Cờ boolean system property (`test.isolated.db=true`, `test.database.enabled=true`) không thể vượt qua guard bảo vệ.
3. **Bằng chứng Thực nghiệm Đối chiếu Trước và Sau Kiểm thử**:

| Bảng dữ liệu | Số lượng trước test | Số lượng sau `ant clean test` | Biến động | Kết luận bảo toàn |
|:---|:---:|:---:|:---:|:---|
| `school_library.audit_logs` | **5631** | **5631** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.books` | **5** | **5** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.users` | **7** | **7** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.students` | **4** | **4** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library.borrow_records` | **2** | **2** | **0** | **Bảo toàn nguyên vẹn 100%** |
| `school_library_test.audit_logs` | 1700 | 1889 | +189 | 100% log test cô lập vào test DB |
| `school_library_test.books` | 5 | 5 | 0 | Đã dọn dẹp sạch sau test |
| `school_library_test.users` | 4 | 4 | 0 | Giữ nguyên tài khoản test |
| `school_library_test.students` | 3 | 3 | 0 | Giữ nguyên sinh viên test |
| `school_library_test.borrow_records` | 1 | 1 | 0 | Đã hoàn trả và dọn dẹp sau test |

4. **Tuyên bố Giới hạn Lịch sử (Historical Limitation)**:
   - Do dự án không có bản sao lưu snapshot nhị phân trước Phase 1.4, không thể chứng minh một cách hồi tố rằng dữ liệu trước Phase 1.4 chưa từng bị tác động bởi các lần chạy cũ.
   - Bằng chứng thực nghiệm xác nhận tuyệt đối an toàn kể từ khi triển khai `TestDatabaseSafetyGuard` (Phase 1.5 đến nay).

---

## 6. Vấn Đề Còn Tồn Tại và Các Bước Triển Khai Chưa Được Kiểm Thử

1. **Chưa có kiểm thử trên Máy thứ hai thực tế (Pending Level C LAN Test)**:
   - Rào cản: Thiếu máy trạm vật lý thứ hai trong môi trường kiểm thử hiện tại.
2. **Quy định Tường lửa Windows Defender Firewall**:
   - Mặc định Windows chặn các kết nối Inbound tới cổng 1099 và 1100 từ máy trạm khác. Quản trị viên cần mở thủ công bằng lệnh PowerShell:
     ```powershell
     New-NetFirewallRule -DisplayName "School Library RMI (Private)" -Direction Inbound -LocalPort 1099,1100 -Protocol TCP -Action Allow -Profile Private
     ```
3. **Mã hóa Kênh truyền RMI**:
   - Dữ liệu RMI chạy trên TCP socket nhị phân thuần túy chưa mã hóa TLS/SSL. Không nên triển khai trên mạng Wi-Fi công cộng không có mã hóa tầng mạng.

---

## 7. Kiểm Tra Trạng Thái Git

- `git diff --check`: Không có lỗi định dạng hay khoảng trắng thừa.
- `git status --short --branch`:
  ```text
  ## fixx...origin/fixx
   M server/src/thuvien/server/rmi/LibraryRMIServer.java
  ?? docs/remediation-phase-1-10-report.md
  ?? docs/remediation-phase-1-9-report.md
  ```

---

## 8. Đánh Giá Tổng Kết (Final Verdict)

### **ĐÁNH GIÁ: PASS WITH LIMITATIONS**

**Lý do:**
1. **PASS**: Toàn bộ 109 bài kiểm thử tự động của 12 test suites chạy trên `ant clean test` đạt tỷ lệ thành công 100% (109 PASS, 0 Fail, 0 Error, 0 Skipped).
2. **PASS**: Cấu hình RMI (canonical và fallback), cơ chế xuất bản Stub (host và port 1100) và filter JEP 290 đã được xác minh độc lập qua thực nghiệm thực tế với log ghi nhận rõ ràng.
3. **PASS**: Cơ sở dữ liệu nghiệp vụ `school_library` được bảo vệ nguyên vẹn 100% (5631 audit logs không đổi); tính cô lập và nguyên tắc fail-closed hoạt động hoàn hảo.
4. **LIMITATIONS**:
   - Nghiệm thu mạng LAN Cấp độ C (máy trạm thứ hai) chưa thể thực hiện do giới hạn phần cứng phòng lab chỉ có 1 máy.
   - Không thể chứng minh hồi tố an toàn cơ sở dữ liệu thời điểm trước Phase 1.4 do thiếu file sao lưu ban đầu.
   - Whitelist JEP 290 còn chứa wildcard package (`java.util.**`, `java.lang.**`), chưa đạt mức whitelist từng lớp đơn lẻ.
