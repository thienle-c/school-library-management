# Sổ tay Hướng dẫn Kiểm thử Nghiệm thu Mạng LAN Hai Máy (Phase 1.11 LAN Test Runbook)

**Dự án:** Remote School Library Management System  
**Không gian làm việc:** `D:\workspace\thuvien`  
**Phiên bản:** Phase 1.11 — LAN Acceptance & Demo Readiness  
**Môi trường:** Java 8 (Corretto 1.8.0_504), MySQL 8.0.30 qua Laragon, Apache Ant (NetBeans 8.0.2 runtime)

---

## 1. Sơ đồ Kiến trúc Kết nối Mạng LAN

Hệ thống được thiết kế theo mô hình 3 tầng phân tán nghiêm ngặt. **Máy Client không bao giờ kết nối trực tiếp vào MySQL**:

```
[ MÁY B — SWING CLIENT ] (IP LAN: ví dụ 192.168.1.x)
       |
       |--- Bước 1: Tra cứu dịch vụ (RMI Registry Lookup) ---> Cổng TCP 1099
       |    [ Nhận đối tượng Stub chứa IP Server và Cổng 1100 ]
       |
       |--- Bước 2: Gọi phương thức từ xa (Remote Method Invocation) ---> Cổng TCP 1100
       |
       v
[ MÁY A — RMI SERVER ] (IP LAN: ví dụ 192.168.1.232)
       |
       +---> [ RMI Registry (Cổng 1099) ]
       |
       +---> [ Remote Object: LibraryRemoteServiceImpl (Cổng 1100) ]
       |         |
       |         v
       |     [ JEP 290 Serialization Filter (Bảo vệ luồng Inbound) ]
       |         |
       |         v
       |     [ RequestRouter & Session Security (RBAC, Rate-Limiting) ]
       |         |
       |         v
       |     [ Service Layer (Borrow, Return, User, Book, Fine) ]
       |         |
       |         v
       |     [ Repository Layer (HikariCP Connection Pool) ]
       |         |
       |         v (Nội bộ máy A: localhost:3306)
       +---> [ MySQL 8.0.30 Database (school_library) ] (KHÔNG MỞ RA LAN)
```

---

## 2. Chuẩn bị trên Máy A (Server)

### Bước A.1: Kết nối Mạng và Xác định IPv4 LAN
1. Kết nối Máy A vào cùng mạng Wi-Fi hoặc cáp LAN với Máy B.
2. Mở PowerShell hoặc Command Prompt trên Máy A, chạy lệnh:
   ```powershell
   ipconfig
   ```
3. Tìm bộ điều hợp mạng đang hoạt động (ví dụ: `Wireless LAN adapter Wi-Fi` hoặc `Ethernet adapter`).
4. Ghi lại giá trị **IPv4 Address** (ví dụ hiện tại của máy: `192.168.1.232`).
   > [!IMPORTANT]
   > Ghi nhớ địa chỉ IPv4 LAN này để cấu hình cho Máy B ở các bước sau.

### Bước A.2: Khởi động Cơ sở Dữ liệu MySQL (Laragon)
1. Đảm bảo MySQL đang chạy thông qua Laragon (Start All) hoặc kiểm tra bằng lệnh:
   ```powershell
   Get-Process mysqld
   ```
2. Kiểm tra database sẵn sàng trên cổng nội bộ 3306:
   ```powershell
   Get-NetTCPConnection -LocalPort 3306 -State Listen
   ```

### Bước A.3: Khởi động RMI Server
1. Mở PowerShell tại thư mục dự án `D:\workspace\thuvien`.
2. Khởi động server bằng lệnh Ant chính thức của dự án:
   ```powershell
   ant run-server
   ```
   *(Hoặc chạy qua NetBeans: Right-click dự án $\rightarrow$ Run Targets $\rightarrow$ `run-server`)*.
3. Quan sát log xuất hiện trên màn hình:
   ```text
   INFO: Configured java.rmi.server.hostname = 192.168.1.232
   INFO: Created new RMI Registry on port 1099
   INFO: LibraryRMIServer started successfully
   INFO: RMI Registry port: 1099
   INFO: RMI Remote Object port: 1100
   INFO: RMI hostname: 192.168.1.232
   INFO: Service: LibraryRemoteService
   ```

### Bước A.4: Xác nhận Trạng thái Lắng nghe Cổng (Read-Only Check)
Mở một cửa sổ PowerShell khác trên Máy A để xác nhận Server đang lắng nghe đúng cả 2 cổng:
```powershell
Get-NetTCPConnection -LocalPort 1099, 1100 -State Listen | Select-Object LocalAddress, LocalPort, State, OwningProcess
```
*Kết quả hợp lệ*: Cả 2 cổng 1099 và 1100 đều phải ở trạng thái `Listen`.

---

## 3. Chuẩn bị và Thực thi trên Máy B (Client)

### Bước B.1: Kiểm tra Thông mạch Mạng (Network Reachability Check)
Trên Máy B, mở PowerShell và chạy kiểm tra kết nối TCP tới Máy A (thay `<SERVER_LAN_IP>` bằng IP thực tế của Máy A, ví dụ `192.168.1.232`):
```powershell
Test-NetConnection -ComputerName 192.168.1.232 -Port 1099
Test-NetConnection -ComputerName 192.168.1.232 -Port 1100
```
*Yêu cầu bắt buộc*: Cả 2 lệnh đều phải trả về:
```text
TcpTestSucceeded : True
```

> [!WARNING]
> - Lệnh `ping` chỉ kiểm tra giao thức ICMP, không phản ánh việc cổng ứng dụng RMI có mở hay không (nhiều hệ thống chặn ping nhưng vẫn thông port, hoặc ngược lại).
> - Nếu cổng 1099 trả về `True` nhưng cổng 1100 trả về `False`, Client sẽ tra cứu được Registry nhưng **sẽ bị treo hoặc ngắt kết nối** ngay khi gọi hàm đăng nhập hoặc tìm kiếm sách.

### Bước B.2: Cấu hình Client Trỏ tới Máy A
1. Sao chép mã nguồn client hoặc bản build `dist/client-dist` sang Máy B (đảm bảo Máy B có Java Runtime 8+).
2. Mở file cấu hình `client/resources/client.properties` (hoặc `resources/client.properties` trong thư mục client distribution).
3. Sửa địa chỉ IP `127.0.0.1` thành IP LAN của Máy A:
   ```properties
   # Java RMI Client Configuration
   rmi.server.host=192.168.1.232
   rmi.registry.port=1099
   rmi.service.name=LibraryRemoteService
   network.timeout=10000
   ```
   *(Tuyệt đối không để `127.0.0.1` hay `localhost` trên máy B)*.

### Bước B.3: Khởi động Ứng dụng Client
Trên Máy B, mở PowerShell tại thư mục dự án và khởi chạy giao diện Swing Client:
```powershell
ant run-client
```
*(Hoặc chạy lệnh Java trực tiếp nếu sử dụng bản đóng gói: `java -jar thuvien-client.jar`)*.

### Bước B.4: Thực hiện Nghiệm thu Nghiệp vụ Thực tế
1. **Đăng nhập**: Nhập tài khoản thủ thư `librarian1` / `lib123` hoặc độc giả `student1` / `student123`.
2. **Tìm kiếm sách**: Gõ từ khóa tìm kiếm (ví dụ: `Computer Networks`), nhấn nút tìm kiếm và kiểm tra danh sách sách hiển thị trên bảng.
3. **Mượn / Trả sách**: Thực hiện một giao dịch mượn sách mẫu và kiểm tra số lượng bản sao khả dụng cập nhật tức thì trên giao diện.

---

## 4. Hướng dẫn Mở Tường Lửa (Windows Defender Firewall) Khi Cần Thiết

Theo quy tắc an toàn bảo mật, hệ thống không tự động thay đổi tường lửa của hệ điều hành. Nếu lệnh `Test-NetConnection` từ Máy B ở Bước B.1 trả về `False`, Quản trị viên cần thực hiện mở cổng thủ công trên Máy A:

### Lệnh PowerShell Administrator trên Máy A:
Mở PowerShell bằng quyền **Run as Administrator** trên Máy A và chạy lệnh (chỉ cấp phép cho profile mạng **Private** nội bộ, không mở ra Public Internet):
```powershell
New-NetFirewallRule -DisplayName "School Library RMI Service (Private)" -Direction Inbound -LocalPort 1099,1100 -Protocol TCP -Action Allow -Profile Private
```

*Để kiểm tra quy tắc đã tạo*:
```powershell
Get-NetFirewallRule -DisplayName "School Library RMI Service (Private)"
```

*Để xóa quy tắc sau khi demo xong (nếu muốn khôi phục)*:
```powershell
Remove-NetFirewallRule -DisplayName "School Library RMI Service (Private)"
```

---

## 5. Quy trình Chẩn đoán và Xử lý Lỗi (9-Tier Troubleshooting Guide)

Nếu Máy B không kết nối được hoặc gặp lỗi trong quá trình nghiệm thu, hãy tra cứu theo 9 cấp độ sau:

| Cấp độ | Hiện tượng / Thông báo lỗi | Nguyên nhân gốc rễ | Lệnh chẩn đoán & Cách khắc phục |
|:---:|:---|:---|:---|
| **1** | `Test-NetConnection` báo `Name resolution failed` hoặc `DestinationHostUnreachable` | Sai địa chỉ IP LAN hoặc Máy A và Máy B đang ở 2 dải mạng con (subnet) khác nhau. | Chạy `ipconfig` trên cả 2 máy, so sánh 3 octet đầu của IP (ví dụ: cả 2 máy phải cùng dải `192.168.1.xxx`). |
| **2** | `Test-NetConnection` cổng 1099 báo `TcpTestSucceeded : False` | Server trên Máy A chưa chạy hoặc chưa khởi tạo Registry thành công. | Trên Máy A, kiểm tra log xem có dòng `Created new RMI Registry on port 1099` không; kiểm tra lệnh `Get-NetTCPConnection -LocalPort 1099`. |
| **3** | Cổng 1099 `TcpTestSucceeded : True` nhưng cổng 1100 `False` | Remote Object chưa được export hoặc Windows Firewall chặn cổng 1100. | Xem log Server xác nhận `RMI Remote Object port: 1100`. Chạy lệnh mở tường lửa cho cả 2 cổng 1099 và 1100. |
| **4** | Client kết nối được nhưng bị lỗi `ConnectException: Connection refused to host: 127.0.0.1` | Máy A quảng bá sai địa chỉ trong RMI Stub (`java.rmi.server.hostname` bị gán là loopback thay vì IP LAN). | Trên Máy A, kiểm tra log `Configured java.rmi.server.hostname`. Nếu nhận sai adapter, khởi động server kèm tham số: `ant run-server -Drmi.server.host=192.168.1.232`. |
| **5** | Lỗi `NotBoundException: LibraryRemoteService` | Tên dịch vụ đăng ký tại Registry không khớp với tên Client tìm kiếm. | Kiểm tra `rmi.service.name` trong `client.properties` và `server.properties` (phải cùng là `LibraryRemoteService`). |
| **6** | Lỗi `InvalidClassException: filter status: REJECTED` | JEP 290 Serialization Filter trên Server từ chối đối tượng không thuộc whitelist. | Kiểm tra đối tượng truyền nhận có thuộc package `thuvien.common.**` hay không; kiểm tra version JDK của Client và Server có tương thích không. |
| **7** | Client khởi động báo `AuthenticationException` hoặc `Invalid username or password` | Sai thông tin đăng nhập hoặc tài khoản bị khóa do thử sai quá 5 lần. | Nhập đúng tài khoản demo (`librarian1` / `lib123`). Chờ 15 phút nếu tài khoản bị khóa tự động theo cơ chế rate-limiting. |
| **8** | Lỗi `LibraryException: Database connection error` | Server không kết nối được tới MySQL trên Máy A. | Kiểm tra dịch vụ MySQL/Laragon trên Máy A; kiểm tra `db.properties` hoặc `test.local.properties` trên Máy A trỏ đúng database. |
| **9** | Client bị ngắt kết nối giữa chừng (SocketTimeoutException) | Quá thời gian timeout mạng (10 giây) do sóng Wi-Fi chập chờn. | Kiểm tra độ trễ mạng bằng lệnh `Test-NetConnection -InformationLevel Detailed`; tăng giá trị `network.timeout=20000` trong `client.properties`. |
