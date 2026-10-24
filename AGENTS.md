# Quy tắc phát triển — School Library Management System

## Mục tiêu dự án
Ứng dụng quản lý thư viện trường học theo mô hình client–server, dùng Java Swing ở máy khách, Java RMI cho giao tiếp mạng và MySQL ở tầng dữ liệu. Dự án dùng cho học tập môn Lập trình mạng máy tính.

## Công nghệ và ràng buộc
- Java 8; giữ tương thích source/target 1.8.
- Apache Ant và cấu hình dự án NetBeans hiện có. Không chuyển sang Maven/Gradle nếu chưa được yêu cầu.
- Java Swing cho giao diện desktop.
- Java RMI và Java Object Serialization cho giao tiếp client–server. Không viết lại hệ thống thành TCP Socket thuần nếu chưa có yêu cầu cụ thể.
- MySQL 8/InnoDB, JDBC và HikariCP ở phía máy chủ.
- JUnit 4 cho kiểm thử.

## Kiến trúc và ranh giới module
- `client/`: giao diện Swing, controller phía client, phiên đăng nhập và adapter RMI. Không được kết nối trực tiếp tới MySQL hoặc chứa thông tin xác thực cơ sở dữ liệu.
- `common/`: DTO, giao diện remote, protocol và kiểu dữ liệu dùng chung. Tránh phụ thuộc Swing hoặc JDBC.
- `server/`: RMI bootstrap/service, router, xác thực, phân quyền, nghiệp vụ, repository và kết nối cơ sở dữ liệu. Không phụ thuộc Swing.
- `database/`: schema, seed, index và migration SQL.
- `test/`: kiểm thử tự động.
- `docs/`: tài liệu đang dùng; `docs/archive/` chứa báo cáo lịch sử.

Mọi yêu cầu nghiệp vụ phải được xác thực và phân quyền ở phía máy chủ. Ẩn nút trên giao diện chỉ là hỗ trợ trải nghiệm, không phải biện pháp bảo vệ truy cập.

## Quy tắc bảo mật
1. Không commit mật khẩu, token, dữ liệu cá nhân hoặc file cấu hình cơ sở dữ liệu thực tế.
2. Giữ cấu hình cục bộ như `server/resources/db.properties` ngoài Git; cập nhật file mẫu/tài liệu khi cần.
3. Dùng `PreparedStatement` cho truy vấn có dữ liệu đầu vào; đóng tài nguyên bằng try-with-resources.
4. Không ghi mật khẩu, password hash, session token hay bí mật vào log.
5. Xác minh vai trò và trạng thái phiên trên máy chủ cho từng thao tác được bảo vệ.
6. Hệ thống hiện dùng Java RMI; không tuyên bố có TLS nếu chưa triển khai và kiểm thử TLS. Chỉ thử nghiệm trong mạng tin cậy.
7. Không chạy migration, seed hoặc thao tác ghi lên database thật khi chưa có xác nhận rõ ràng và bản sao lưu phù hợp.

## Quy tắc nghiệp vụ và đồng thời
- Mượn/trả sách và các thao tác nhiều bảng cần dùng transaction, rollback khi lỗi.
- Không để số bản sao khả dụng âm hoặc vượt tổng số bản sao.
- Kiểm tra trạng thái sinh viên, hạn mức mượn và quyền truy cập ở máy chủ.
- Khi sửa cơ chế đồng thời, bổ sung kiểm thử cho trường hợp nhiều client tranh bản sao cuối cùng.

## Quy tắc mã nguồn
- Tuân thủ Java 8 và phong cách hiện có của dự án.
- Tránh tạo class/DTO trùng chức năng; tìm kiếm mã nguồn trước khi thêm mới.
- Không tái cấu trúc hoặc định dạng lại các tệp không liên quan đến nhiệm vụ.
- Không thêm thư viện mới nếu chưa có lý do kỹ thuật rõ ràng.
- Không xóa tệp chỉ vì tên có vẻ cũ; kiểm tra tham chiếu, tác dụng trong build, tài liệu và Git trước.

## Build và kiểm thử
Từ thư mục gốc, dùng Apache Ant:

```bash
ant clean
ant compile
ant test
ant package-all
```

Chỉ báo cáo kết quả build/test thực tế sau khi chạy lệnh. Không suy diễn số lượng test hoặc mức độ sẵn sàng production từ tài liệu cũ.

## Quy tắc với tác nhân AI
- Đọc README và tài liệu liên quan trước khi sửa.
- Ưu tiên thay đổi nhỏ, có thể rà soát.
- Bảo toàn thay đổi hiện có của người dùng; không reset, checkout đè hoặc xóa thay đổi chưa được xác nhận.
- Sau khi sửa, kiểm tra diff, chạy build/test phù hợp và báo rõ tệp thay đổi, kết quả kiểm tra, rủi ro còn lại.
