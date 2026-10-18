# Bảng Kiểm Thử Nghiệm Thu & Kịch Bản Trình Diễn (Phase 1.11 Demo Checklist)

**Dự án:** Remote School Library Management System  
**Phiên bản:** Phase 1.11  
**Mục đích:** Hướng dẫn từng bước kiểm thử chức năng và trình diễn báo cáo đồ án trên giao diện Java Swing qua mạng LAN RMI.  
**Tài khoản thử nghiệm sẵn có (Seed Accounts):**
- **Thủ thư (Librarian):** `librarian1` / `lib123` (Toàn quyền quản lý sách, độc giả, mượn trả)
- **Độc giả (Student):** `student1` / `student123` (Xem thông tin cá nhân, tra cứu sách, đặt trước)
- **Quản trị viên (Admin):** `admin` / `admin123` (Quản trị người dùng, hệ thống, xem toàn bộ nhật ký)

---

## 1. Điều Kiện Tiên Quyết Trước Khi Demo (Pre-Demo Setup)

| STT | Hạng mục kiểm tra | Thao tác thực hiện | Kết quả mong đợi (Expected) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 1 | Khởi động Database | Mở Laragon, khởi động MySQL | MySQL hoạt động trên cổng 3306 (nội bộ Máy A) | [ ] PASS<br>[ ] FAIL |
| 2 | Khởi động Server RMI | Trên Máy A: `ant run-server` | Server bind port 1099, 1100 và quảng bá đúng IP LAN | [ ] PASS<br>[ ] FAIL |
| 3 | Cấu hình Client Máy B | Sửa `client.properties`: `rmi.server.host=<IP_LAN_A>` | File lưu đúng địa chỉ IP LAN của Máy A | [ ] PASS<br>[ ] FAIL |
| 4 | Kiểm tra cổng từ Máy B | `Test-NetConnection <IP_LAN_A> -Port 1099, 1100` | Cả 2 cổng đều trả về `TcpTestSucceeded : True` | [ ] PASS<br>[ ] FAIL |
| 5 | Khởi động Client Máy B | Trên Máy B: `ant run-client` | Màn hình đăng nhập Swing hiển thị đầy đủ, sắc nét | [ ] PASS<br>[ ] FAIL |

---

## 2. Kịch Bản Trình Diễn Nghiệp Vụ (Live Demo Scenarios)

### Kịch bản 1: Xác thực Người dùng & Phân quyền Đăng nhập (Authentication & RBAC)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 1.1 | Đăng nhập sai mật khẩu | User: `librarian1`<br>Pass: `wrongpass` | Hệ thống từ chối, hiển thị hộp thoại cảnh báo: "Tên đăng nhập hoặc mật khẩu không chính xác". | [ ] PASS<br>[ ] FAIL |
| 1.2 | Kiểm tra Rate-Limiting | Thử sai liên tiếp 5 lần | Tài khoản bị tạm khóa trong 15 phút để chống Brute-Force; hiển thị thông báo khóa tài khoản. | [ ] PASS<br>[ ] FAIL |
| 1.3 | Đăng nhập hợp lệ Thủ thư | User: `librarian1`<br>Pass: `lib123` | Đăng nhập thành công; chuyển sang màn hình chính `MainDashboardForm` với các tab: Quản lý sách, Độc giả, Mượn trả, Tiền phạt. | [ ] PASS<br>[ ] FAIL |

---

### Kịch bản 2: Tra cứu & Quản lý Danh mục Sách (Book Catalog)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 2.1 | Tìm kiếm sách theo từ khóa | Từ khóa: `Computer` | Bảng hiển thị sách "Computer Networks", đầy đủ ISBN, tác giả, thể loại, tổng số bản và số bản khả dụng. | [ ] PASS<br>[ ] FAIL |
| 2.2 | Lọc theo thể loại | Chọn danh mục: `Computer Science` | Danh sách lọc chính xác các đầu sách thuộc lĩnh vực CNTT. | [ ] PASS<br>[ ] FAIL |
| 2.3 | Xem chi tiết sách | Chọn một dòng sách | Panel chi tiết hiển thị đầy đủ thông tin xuất bản, số lượng sách còn trong kho. | [ ] PASS<br>[ ] FAIL |

---

### Kịch bản 3: Quản lý Thông tin Độc giả (Student Management)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 3.1 | Tra cứu sinh viên | Mã SV: `STU001` | Hiển thị thông tin sinh viên Nguyễn Văn A, trạng thái `ACTIVE`, số sách đang mượn hiện tại. | [ ] PASS<br>[ ] FAIL |
| 3.2 | Kiểm tra giới hạn mượn | Xem trường giới hạn mượn | Hệ thống hiển thị hạn mức mượn tối đa (ví dụ: 5 cuốn). | [ ] PASS<br>[ ] FAIL |

---

### Kịch bản 4: Nghiệp vụ Mượn Sách Giao dịch Nguyên tử (Atomic Borrow Flow)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 4.1 | Kiểm tra số lượng trước mượn | Ghi nhận số bản khả dụng của Sách ID 1 | Bản sao khả dụng = $K$ ($K > 0$). | [ ] PASS<br>[ ] FAIL |
| 4.2 | Thực hiện mượn sách | Mã SV: `STU001`<br>Sách ID: `1`<br>Hạn mượn: 14 ngày | Giao dịch RMI thành công; sinh mã phiếu mượn mới; bảng thông báo mượn sách thành công. | [ ] PASS<br>[ ] FAIL |
| 4.3 | Xác nhận cập nhật tồn kho | Tải lại danh sách sách | Số bản khả dụng của sách tự động giảm đúng 1 đơn vị ($K - 1$). | [ ] PASS<br>[ ] FAIL |
| 4.4 | Kiểm tra mượn khi hết sách | Mượn cuốn sách có số bản khả dụng = 0 | Server ném ngoại lệ `BookUnavailableException`; client hiển thị: "Sách đã hết bản sao khả dụng". | [ ] PASS<br>[ ] FAIL |

---

### Kịch bản 5: Nghiệp vụ Trả Sách & Tính Tiền Phạt (Atomic Return Flow)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 5.1 | Thực hiện trả sách đúng hạn | Mã SV: `STU001`<br>Sách ID: `1` | Hệ thống cập nhật trạng thái phiếu mượn thành `RETURNED`, ghi nhận ngày trả thực tế; tiền phạt = 0 VNĐ. | [ ] PASS<br>[ ] FAIL |
| 5.2 | Phục hồi tồn kho | Tải lại danh sách sách | Số bản khả dụng của sách tự động khôi phục về ban đầu ($K$). Số sách đang mượn của SV giảm 1. | [ ] PASS<br>[ ] FAIL |
| 5.3 | Trả sách quá hạn (nếu có) | Phiếu mượn quá ngày hạn trả | Server tự động tính số ngày quá hạn và phát sinh bản ghi phạt trong bảng `fines` (ví dụ: 5.000 VNĐ/ngày). | [ ] PASS<br>[ ] FAIL |

---

### Kịch bản 6: Phân quyền Độc giả (Student Role Boundary)

| Bước | Thao tác | Dữ liệu đầu vào | Kết quả mong đợi (Expected Result) | Đánh dấu |
|:---:|:---|:---|:---|:---:|
| 6.1 | Đăng xuất Thủ thư | Nhấn nút "Đăng xuất" | Phiên làm việc bị hủy; quay trở lại màn hình đăng nhập. | [ ] PASS<br>[ ] FAIL |
| 6.2 | Đăng nhập Độc giả | User: `student1`<br>Pass: `student123` | Đăng nhập thành công với vai trò `STUDENT`; giao diện Dashboard chỉ hiển thị thông tin độc giả. | [ ] PASS<br>[ ] FAIL |
| 6.3 | Kiểm tra ranh giới bảo mật | Kiểm tra các menu quản trị | Độc giả **không** có quyền thêm/sửa/xóa sách, không quản lý người dùng khác; chỉ được tra cứu và đặt trước sách. | [ ] PASS<br>[ ] FAIL |

---

## 3. Tổng Kết Đánh Giá Buổi Trình Diễn

- **Người thực hiện kiểm thử:** ................................................................
- **Máy Server (Máy A) IP LAN:** ................................................................
- **Máy Client (Máy B) IP LAN:** ................................................................
- **Thời gian thực hiện:** ................................................................
- **Số kịch bản đạt (PASS):** ........ / 6 kịch bản.
- **Đánh giá tổng thể:** [ ] XUẤT SẮC (100% PASS)  [ ] ĐẠT CÓ LƯU Ý  [ ] KHÔNG ĐẠT
