# Hệ thống quản lý thư viện trường học

Ứng dụng quản lý thư viện theo mô hình client–server, xây dựng bằng Java Swing, Java RMI và MySQL. Dự án phục vụ mục đích học tập môn Lập trình mạng máy tính, đồng thời minh họa cách tách giao diện người dùng, giao tiếp mạng và xử lý nghiệp vụ phía máy chủ.

## Chức năng chính

- Đăng nhập, đổi mật khẩu, quản lý phiên và phân quyền theo vai trò `ADMIN`, `LIBRARIAN`, `STUDENT`.
- Quản lý sách, thể loại, tác giả và thông tin bản sao.
- Quản lý bạn đọc/sinh viên, trạng thái tài khoản và đăng ký bằng mã kích hoạt.
- Lập phiếu mượn, trả sách, đặt trước sách và xử lý tiền phạt.
- Tìm kiếm danh mục, gợi ý tìm kiếm và xem bảng điều khiển theo quyền.
- Ghi nhật ký kiểm toán cho các thao tác quan trọng.
- Kiểm tra quyền ở phía máy chủ; thao tác mượn sách dùng giao dịch cơ sở dữ liệu để hạn chế tranh chấp khi nhiều máy khách cùng yêu cầu bản sao cuối cùng.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 8 |
| Giao diện | Java Swing |
| Giao tiếp client–server | Java RMI, Java Object Serialization |
| Cơ sở dữ liệu | MySQL 8 |
| Build | Apache Ant |
| IDE tương thích với cấu hình dự án | NetBeans |

## Kiến trúc tổng quan

```text
Java Swing Client
      |
      | Java RMI
      v
RMI Registry / Remote Service
      |
      v
Server: xác thực, phân quyền, nghiệp vụ, giao dịch
      |
      v
MySQL
```

Máy khách xử lý giao diện và gửi yêu cầu qua RMI. Máy chủ chịu trách nhiệm xác thực phiên, kiểm tra quyền, thực hiện nghiệp vụ và truy cập cơ sở dữ liệu. Không nên xem việc ẩn nút trên giao diện là biện pháp phân quyền thay thế cho kiểm tra ở máy chủ.

## Cấu trúc thư mục

| Đường dẫn | Nội dung |
|---|---|
| `client/` | Mã nguồn giao diện Swing và thành phần kết nối RMI |
| `server/` | Máy chủ RMI, dịch vụ nghiệp vụ, bảo mật và truy cập dữ liệu |
| `common/` | DTO, giao diện remote và các lớp dùng chung |
| `test/` | Kiểm thử tự động |
| `database/` | Schema, dữ liệu mẫu, chỉ mục và script migration |
| `docs/` | Tài liệu kiến trúc, cài đặt, API, mạng và triển khai |
| `docs/archive/` | Báo cáo kiểm tra và tài liệu lịch sử đã lưu trữ |
| `scripts/` | Script hỗ trợ kiểm tra kết nối |
| `lib/` | Thư viện phụ thuộc của dự án |
| `build.xml` | Các tác vụ build, test, chạy và đóng gói bằng Ant |

## Yêu cầu môi trường

- JDK 8.
- Apache Ant (có thể dùng Ant đi kèm NetBeans).
- MySQL 8 và công cụ quản lý cơ sở dữ liệu phù hợp.
- NetBeans nếu muốn mở và chạy dự án bằng IDE.

Cần bảo đảm lệnh `java`, `javac` và `ant` sử dụng đúng phiên bản JDK. Tên database, tài khoản và mật khẩu phải được cấu hình theo môi trường của bạn; không dùng thông tin đăng nhập thực tế trong tài liệu công khai.

## Cài đặt cơ sở dữ liệu

1. Khởi động MySQL.
2. Tạo cơ sở dữ liệu thử nghiệm riêng, sau đó chạy các script trong `database/` theo thứ tự phù hợp với hướng dẫn tại [docs/DATABASE.md](docs/DATABASE.md).
3. Nếu cần khởi tạo dữ liệu minh họa, xem [database/seed.sql](database/seed.sql).
4. Đọc [docs/INSTALLATION.md](docs/INSTALLATION.md) và [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) trước khi cấu hình ứng dụng.

**Lưu ý:** kiểm tra kỹ tên database trước khi chạy script. Không chạy migration hoặc script seed lên cơ sở dữ liệu đang sử dụng thật nếu chưa sao lưu và chưa xác nhận tác động.

## Cấu hình kết nối

Cấu hình máy chủ và máy khách được mô tả trong các file mẫu/tài liệu của dự án:

- Máy chủ RMI: `server/resources/server.properties`.
- Máy khách: `client/resources/client.properties`.
- Kết nối MySQL: sử dụng cấu hình cục bộ `server/resources/db.properties` hoặc biến hệ thống được hỗ trợ trong tài liệu.

Không commit mật khẩu, token, file cấu hình chứa thông tin xác thực hoặc dữ liệu cá nhân. File cấu hình cục bộ `db.properties` được Git bỏ qua; hãy dùng file mẫu nếu có và điền thông tin phù hợp trên máy của bạn.

## Build và kiểm thử

Mở terminal tại thư mục gốc dự án:

```bash
ant clean
ant compile
ant test
```

Đóng gói các bản phân phối:

```bash
ant package-all
```

Các gói đầu ra được tạo trong `dist/` và không được quản lý bằng Git. Các target chạy ứng dụng được khai báo trong `build.xml`; có thể xem thêm hướng dẫn ở [docs/INSTALLATION.md](docs/INSTALLATION.md).

## Chạy ứng dụng qua mạng LAN

1. Cấu hình database và khởi chạy máy chủ trên máy có MySQL.
2. Xác định địa chỉ IP LAN của máy chủ và cấu hình máy khách trỏ đến địa chỉ đó.
3. Đảm bảo firewall cho phép các cổng RMI mà dự án cấu hình; mặc định dự án sử dụng Registry cổng `1099` và remote service cổng `1100`.
4. Chạy máy khách trên máy còn lại và kiểm tra đăng nhập/thao tác cơ bản.

Hướng dẫn chi tiết và chẩn đoán lỗi: [docs/NETWORK.md](docs/NETWORK.md), [docs/INSTALLATION.md](docs/INSTALLATION.md). RMI trong cấu hình hiện tại chưa sử dụng TLS, vì vậy chỉ nên thử nghiệm trong mạng tin cậy; không đưa trực tiếp lên Internet.

## Tài liệu

- [Kiến trúc hệ thống](docs/ARCHITECTURE.md)
- [API](docs/API.md)
- [Cơ sở dữ liệu](docs/DATABASE.md)
- [Cài đặt](docs/INSTALLATION.md)
- [Triển khai](docs/DEPLOYMENT.md)
- [Sao lưu và khôi phục](docs/BACKUP-RESTORE.md)
- [Hiệu năng](docs/PERFORMANCE.md)
- [Kiểm tra bàn giao staging](docs/STAGING-HANDOFF-CHECKLIST.md)
- [Lịch sử báo cáo kỹ thuật](docs/archive/README.md)

## Phạm vi và lưu ý bảo mật

Đây là dự án học tập, không mặc nhiên được xem là sẵn sàng cho môi trường production. Trước khi triển khai thực tế cần đánh giá bảo mật mạng, cấu hình TLS, quản lý bí mật, sao lưu/khôi phục và quy trình quản trị tài khoản. Không dùng tài khoản demo hoặc mật khẩu mặc định trên dữ liệu thật.

## Giấy phép

Xem file [LICENSE](LICENSE).
