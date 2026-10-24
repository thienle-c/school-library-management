# BÁO CÁO RÀ SOÁT AN TOÀN BẢO MẬT VÀ QUY TRÌNH VẬN HÀNH AN TOÀN
## Remote School Library Management System

**Dự án:** Hệ thống quản lý thư viện trường học từ xa (Remote School Library Management System)  
**Workspace:** `d:/workspace/thuvien`  
**Ngày thực hiện:** 14/10/2026  
**Chuyên viên đánh giá:** Senior Java Security Engineer & Build/Release Engineer  
**Trạng thái kết luận:** `AUDIT COMPLETE — NO REMEDIATION APPLIED` (Giai đoạn A — Chỉ kiểm tra, bảo toàn nguyên trạng mã nguồn và môi trường)

---

## MỤC LỤC
1. [Tóm tắt điều hành (Executive Summary)](#1-tóm-tắt-điều-hành)
2. [Phạm vi kiểm tra và các giới hạn kiểm thử](#2-phạm-vi-kiểm-tra-và-các-giới-hạn-kiểm-thử)
3. [Bảng tổng hợp phát hiện bảo mật (Security Findings Table)](#3-bảng-tổng-hợp-phát-hiện-bảo-mật)
4. [Ma trận phân quyền chi tiết (RBAC Matrix)](#4-ma-trận-phân-quyền-chi-tiết-rbac-matrix)
5. [Rà soát Java RMI và Java Serialization](#5-rà-soát-java-rmi-và-java-serialization)
6. [Rà soát MySQL, JDBC, HikariCP và cấu hình cục bộ](#6-rà-soát-mysql-jdbc-hikaricp-và-cấu-hình-cục-bộ)
7. [Rà soát Xác thực, Mật khẩu và Quản lý Phiên (Session)](#7-rà-soát-xác-thực-mật-khẩu-và-quản-lý-phiên-session)
8. [Rà soát Windows Firewall và Triển khai LAN/Wi-Fi](#8-rà-soát-windows-firewall-và-triển-khai-lanwi-fi)
9. [Rà soát Git, .gitignore và nguy cơ lộ lọt bí mật](#9-rà-soát-git-gitignore-và-nguy-cơ-lộ-lọt-bí-mật)
10. [Kết quả kiểm thử và phân loại an toàn](#10-kết-quả-kiểm-thử-và-phân-loại-an-toàn)
11. [Quy trình chạy và vận hành an toàn (Safe Runbook)](#11-quy-trình-chạy-và-vận-hành-an-toàn-safe-runbook)
12. [Danh sách đề xuất xử lý theo thứ tự ưu tiên (Prioritized Remediation Roadmap)](#12-danh-sách-đề-xuất-xử-lý-theo-thứ-tự-ưu-tiên)
13. [Các mục bắt buộc người dùng phê duyệt trước khi thay đổi](#13-các-mục-bắt-buộc-người-dùng-phê-duyệt-trước-khi-thay-đổi)

---

## 1. Tóm tắt điều hành

Dự án **Remote School Library Management System** được thiết kế theo mô hình 3 tầng phân tán phục vụ môn học Lập trình mạng:
- **Tầng 1 (Client):** Ứng dụng Desktop Java Swing chạy trên Java 8, giao tiếp qua Java RMI.
- **Tầng 2 (Server):** Máy chủ Java RMI độc lập (`LibraryRMIServer`), chứa RMI Registry (cổng TCP 1099) và Remote Object Service (`LibraryRemoteServiceImpl`, cổng cố định TCP 1100), điều phối nghiệp vụ qua `RequestRouter`, xử lý dịch vụ tại Service layer và tương tác dữ liệu qua Repository layer.
- **Tầng 3 (Database):** MySQL (InnoDB engine) với hồ kết nối HikariCP.

### Điểm mạnh cốt lõi đã xác minh trong mã nguồn (CONFIRMED — CODE):
1. **Phân tách kiến trúc 3 tầng tuyệt đối:** Mã nguồn Client (`client/src`) và Common (`common/src`) hoàn toàn **không** chứa bất kỳ import JDBC (`java.sql.*`, `javax.sql.*`), driver MySQL hay cấu hình cơ sở dữ liệu nào. Chỉ có Server kết nối trực tiếp với MySQL.
2. **Phòng chống SQL Injection triệt để:** Toàn bộ 9 repository triển khai thực tế sử dụng 100% `PreparedStatement` với tham số ràng buộc `?`, không tồn tại truy vấn ghép chuỗi (concatenation) với dữ liệu người dùng.
3. **An toàn giao dịch đồng thời (Concurrency Safety):** Giao dịch mượn sách (`BorrowServiceImpl.borrowBook`) sử dụng atomic decrement có điều kiện (`UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`) trên cùng một Connection duy nhất với `setAutoCommit(false)`, `commit()`, và `rollback()` chặt chẽ, ngăn chặn tuyệt đối hiện tượng âm sách khi nhiều người mượn đồng thời.
4. **Bảo mật mật khẩu hiện đại:** Sử dụng thuật toán chuẩn `PBKDF2WithHmacSHA256` với 10.000 vòng lặp, muối ngẫu nhiên 16-byte (`SecureRandom`), so sánh hằng số thời gian (`MessageDigest.isEqual`), và có cơ chế tự động nâng cấp từ SHA-256 cũ khi đăng nhập thành công.
5. **Kiểm soát brute force và session an toàn:** Khóa tạm thời 15 phút sau 5 lần đăng nhập sai, session token định danh bằng UUID ngẫu nhiên phía server, timeout tự động sau 30 phút không hoạt động, và thu hồi session tức thì khi khóa tài khoản hoặc đăng xuất.

### 5 Rủi ro an ninh quan trọng nhất cần lưu ý:
1. **Thiếu bộ lọc Java Deserialization Filter (JEP 290):** [HIGH] RMI Registry (1099) và Remote Object (1100) chấp nhận đối tượng Java tuần tự hóa mà không có bộ lọc whitelist. Kẻ tấn công trong mạng LAN có thể gửi payload serialized độc hại.
2. **Kênh truyền RMI không mã hóa (Plaintext TCP):** [MEDIUM] Lưu lượng RMI không sử dụng TLS/SSL (`SslRMIClientSocketFactory`/`SslRMIServerSocketFactory`). Mật khẩu và session token có thể bị bắt lén (sniffing) nếu kết nối qua mạng Wi-Fi công cộng hoặc LAN không tin cậy.
3. **Dịch vụ MySQL (mysqld) mở ra mạng ngoài qua Firewall Public:** [HIGH] Quá trình MySQL đang lắng nghe trên cổng `::` (mọi card mạng) và Windows Firewall có rule cho phép inbound trên profile `Public`. Nếu mật khẩu root MySQL trên máy phát triển để trống, bất kỳ máy nào cùng mạng Wi-Fi cũng có thể kết nối trực tiếp vào MySQL.
4. **Lịch sử Git lưu vết cấu hình trong commit cũ:** [MEDIUM] Tệp `server/resources/db.properties` từng được commit ở commit `e0c5a90` trước khi bị đưa vào `.gitignore` ở commit `6e30dc4`. Nếu đẩy repo lên GitHub công khai, commit cũ vẫn chứa thông tin cấu hình ban đầu.
5. **Nguy cơ từ chối dịch vụ tài khoản qua Brute Force Lockout:** [LOW] Do action `LOGIN` không yêu cầu xác thực, kẻ tấn công có thể cố tình gửi sai mật khẩu 5 lần cho tài khoản `admin` để khóa tài khoản này trong 15 phút.

---

## 2. Phạm vi kiểm tra và các giới hạn kiểm thử

### 2.1. Phạm vi đã kiểm tra thực tế
- Mã nguồn toàn bộ 3 module: `common` (DTO, Enums, Protocol, Exceptions, RMI Interface), `server` (RMI, Router, Service, Repository, Database, Security), `client` (Network, Session, View, Controllers).
- Cấu hình runtime: `server.properties`, `client.properties`, `db.properties.example`, `.gitignore`, `build.xml`, `nbproject/project.properties`.
- Trạng thái Git: `git status`, `git log`, `git diff`, danh sách commit lịch sử.
- Trạng thái cổng mạng và Windows Defender Firewall: Truy vấn LISTEN qua `Get-NetTCPConnection` và các quy tắc firewall liên quan đến Java, mysqld, RMI.
- Kiểm tra tính tương thích biên dịch: Chạy thành công `ant compile` với Java 8 (OpenJDK Corretto 8.504.01.1) và Apache Ant 1.9.4.

### 2.2. Phạm vi chưa kiểm tra hoặc có giới hạn
- **Kiểm thử 2 máy vật lý qua Wi-Fi/LAN thật:** Trong phiên làm việc hiện tại, chỉ có thể kiểm tra ràng buộc cấu hình và binding cục bộ; việc thông mạng giữa 2 thiết bị thực tế phụ thuộc vào cấu hình AP Isolation (chặn client-to-client) của router Wi-Fi của người dùng.
- **Không chạy test ghi dữ liệu phá hủy:** Các test như `BorrowConcurrencyIntegrationTest` có lệnh `DELETE FROM borrow_records` và `UPDATE books` trực tiếp trên database thật; tuân thủ nguyên tắc an toàn, các test này **chưa** được tự ý kích hoạt để bảo toàn dữ liệu database của người dùng.
- **Không thay đổi Firewall hoặc MySQL:** Không thực hiện bất kỳ lệnh sửa đổi cấu hình hệ thống máy tính của người dùng trong giai đoạn Audit.

---

## 3. Bảng tổng hợp phát hiện bảo mật

| ID | Phân loại | Mức độ | Trạng thái bằng chứng | Vị trí tệp / Mã nguồn | Tác động | Khuyến nghị xử lý |
|---|---|---|---|---|---|---|
| **SEC-01** | RMI / Deserialization | **HIGH** | CONFIRMED — CODE | `server/.../LibraryRMIServer.java:79-88`<br>`server/.../LibraryRemoteServiceImpl.java:46` | RMI sử dụng `ObjectInputStream` mặc định của Java 8, không có `jdk.serialFilter`. Có nguy cơ deserialization attack nếu có gadget chain trên classpath. | Cấu hình whitelist deserialization filter (JEP 290) chỉ cho phép các package `thuvien.common.*` và kiểu dữ liệu Java chuẩn. |
| **SEC-02** | RMI / Transport Security | **MEDIUM** | CONFIRMED — CODE | `server/.../LibraryRMIServer.java:87`<br>`client/.../RMIClient.java:60` | Lưu lượng RMI truyền ở dạng nhị phân không mã hóa (no TLS/SSL). Kẻ nghe lén cùng Wi-Fi/LAN có thể bắt được token hoặc mật khẩu plaintext khi đăng nhập. | Hạn chế triển khai trong mạng LAN nội bộ an toàn. Đề xuất bổ sung SSL Socket Factories (`SslRMIClientSocketFactory` / `SslRMIServerSocketFactory`) nếu cần an toàn tuyệt đối. |
| **SEC-03** | Database / Network Exposure | **HIGH** | CONFIRMED — RUNTIME | Process `mysqld.exe` (PID 15848)<br>Windows Firewall Rule: `mysqld` (Public Inbound) | MySQL bind trên mọi interface (`::`/`0.0.0.0`) và Firewall cho phép kết nối từ mạng Public. Nếu mật khẩu root để trống, người ngoài mạng có thể truy cập DB. | Sửa `my.ini` của MySQL/Laragon đặt `bind-address = 127.0.0.1`. Xóa hoặc vô hiệu hóa rule firewall Public của `mysqld`. |
| **SEC-04** | Git / Secret History | **MEDIUM** | CONFIRMED — GIT | Commit `e0c5a90`<br>`server/resources/db.properties` | Tệp cấu hình chứa thông tin DB đã được commit vào lịch sử Git ở commit đầu tiên trước khi được untrack ở commit `6e30dc4`. | Đảm bảo không sử dụng mật khẩu thật của môi trường thực tế trong file này. Nếu public mã nguồn, cân nhắc xóa lịch sử file nhạy cảm bằng `git-filter-repo` (cần phê duyệt). |
| **SEC-05** | Information Disclosure | **LOW** | CONFIRMED — CODE | `server/.../RequestRouter.java:354`<br>`server/.../LibraryRemoteServiceImpl.java:76-88` | Khi server gặp Exception chưa xác định, trả về `"Server error: " + e.getMessage()`, có thể làm lộ tên bảng, tên cột hoặc lỗi cú pháp SQL. | Trả về thông báo lỗi chung chung `"Lỗi máy chủ nội bộ. Vui lòng thử lại sau."` và ghi vết chi tiết vào server log. |
| **SEC-06** | Authentication / DoS Lockout | **LOW** | CONFIRMED — CODE | `server/.../AuthServiceImpl.java:242-284` | Khóa tài khoản 15 phút theo `username` sau 5 lần gõ sai. Do `LOGIN` là công khai, kẻ xấu có thể cố tình gửi 5 request sai để khóa người dùng hợp lệ (đặc biệt là tài khoản admin). | Bổ sung rate-limit theo địa chỉ IP gọi đến hoặc giảm thời gian khóa với cơ chế lũy tiến (5s, 15s, 30s) thay vì 15 phút cứng. |
| **SEC-07** | Logic Inconsistency | **INFORMATIONAL** | CONFIRMED — CODE | `server/.../LibraryRemoteServiceImpl.java:492`<br>`server/.../RequestRouter.java:781` | Phương thức `createUser` trên RMI interface nhận `initialPassword` nhưng router lại bỏ qua và hardcode mật khẩu mặc định `"password123"`. | Cập nhật router để tiếp nhận và sử dụng đúng mật khẩu khởi tạo được truyền vào. |
| **SEC-08** | Legacy Hash Fallback | **LOW** | CONFIRMED — CODE | `server/.../PasswordHasher.java:61`<br>`database/seed.sql:9-18` | Hệ thống vẫn chấp nhận SHA-256 không muối cho tài khoản mẫu từ `seed.sql` trước khi người dùng đăng nhập lần đầu để được nâng cấp lên PBKDF2. | Cập nhật sẵn các hash trong `seed.sql` sang định dạng PBKDF2 để loại bỏ hoàn toàn SHA-256 không muối. |

---

## 4. Ma trận phân quyền chi tiết (RBAC Matrix)

Được đối chiếu trực tiếp từ `server/src/thuvien/server/router/RequestRouter.java` và `common/src/thuvien/common/protocol/Action.java`:

| Hành động (Action) | Khách (Guest / Chưa đăng nhập) | Sinh viên (STUDENT) | Thủ thư (LIBRARIAN) | Quản trị viên (ADMIN) | Cơ chế kiểm tra an toàn tại Server |
|---|:---:|:---:|:---:|:---:|---|
| `PING` | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Bỏ qua kiểm tra token. Chỉ kiểm tra kết nối mạng. |
| `LOGIN` | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Bỏ qua kiểm tra token. Xác thực mật khẩu qua PBKDF2 + Brute force lockout. |
| `REGISTER_STUDENT` | **CHO PHÉP** | Bị từ chối (Đã có tk) | Bị từ chối | Bị từ chối | Bỏ qua token. Kiểm tra Mã SV + Mã kích hoạt 1 lần + Trạng thái chưa liên kết. |
| `LOGOUT` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu Session Token. Thu hồi phiên ngay lập tức khỏi `SessionManager`. |
| `GET_CURRENT_USER` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu Session Token. Trả về thông tin phiên người dùng hiện tại. |
| `CHANGE_PASSWORD` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Chỉ đổi mật khẩu của chính user ID gắn với session token. |
| `LIST_BOOKS` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu đăng nhập. Xem danh mục sách. |
| `GET_BOOK` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu đăng nhập. Xem chi tiết sách theo ID hoặc ISBN. |
| `SEARCH_BOOKS` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu đăng nhập. Tìm kiếm sách phân trang. |
| `CREATE_BOOK` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `UPDATE_BOOK` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `DELETE_BOOK` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `LIST_CATEGORIES` / `AUTHORS` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Yêu cầu đăng nhập. Xem thể loại / tác giả. |
| `CREATE`/`UPDATE`/`DELETE_CATEGORY` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `CREATE`/`UPDATE`/`DELETE_AUTHOR` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `LIST_STUDENTS` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `GET_STUDENT` | Bị từ chối | **CHỈ XEM HỒ SƠ CỦA MÌNH** | **CHO PHÉP** | **CHO PHÉP** | **Chống IDOR:** Nếu role STUDENT, ép buộc kiểm tra `student.getUserId() == session.getUserId()`. |
| `CREATE_STUDENT` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `UPDATE_STUDENT` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)`. |
| `DELETE_STUDENT` | Bị từ chối | Bị từ chối (403) | Bị từ chối (403) | **CHO PHÉP** | `requireRole(session, ADMIN)` — Chỉ Admin mới có quyền tạm khóa/xóa. |
| `GENERATE_STUDENT_ACTIVATION` | Bị từ chối | Bị từ chối (403) | Bị từ chối (403) | **CHO PHÉP** | `requireRole(session, ADMIN)` — Chỉ Admin mới được cấp mã kích hoạt. |
| `BORROW_BOOK` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)` — Thủ thư/Admin thực hiện tại quầy. |
| `RETURN_BOOK` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)` — Thủ thư/Admin nhận sách trả. |
| `LIST_BORROW_RECORDS` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)` — Xem toàn bộ phiếu mượn. |
| `GET_STUDENT_ACTIVE_BORROWS`| Bị từ chối | **CHỈ XEM CỦA MÌNH** | **CHO PHÉP** | **CHO PHÉP** | **Chống IDOR:** Sinh viên bị cưỡng chế gán `studentId` của chính mình. |
| `LIST_FINES` | Bị từ chối | **CHỈ XEM CỦA MÌNH** | **CHO PHÉP** | **CHO PHÉP** | **Chống IDOR:** Sinh viên chỉ xem phạt của chính mình; Thủ thư xem toàn bộ. |
| `PAY_FINE` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | `requireRole(session, ADMIN, LIBRARIAN)` — Thu tiền phạt tại quầy. |
| `CREATE_RESERVATION` | Bị từ chối | **CHO PHÉP (ĐẶT CHO MÌNH)** | **CHO PHÉP** | **CHO PHÉP** | Sinh viên tự đặt trước; Thủ thư/Admin có thể đặt hộ sinh viên. |
| `CANCEL_RESERVATION` | Bị từ chối | **CHỈ HỦY CỦA MÌNH** | **CHO PHÉP** | **CHO PHÉP** | **Chống IDOR:** Kiểm tra sinh viên chỉ được hủy yêu cầu đặt của chính mình. |
| `LIST_RESERVATIONS` | Bị từ chối | **CHỈ XEM CỦA MÌNH** | **CHO PHÉP** | **CHO PHÉP** | Sinh viên xem danh sách đặt của mình; Thủ thư/Admin xem toàn bộ. |
| `LIST_USERS` / `GET_USER` | Bị từ chối | Bị từ chối (403) | Bị từ chối (403) | **CHO PHÉP** | `requireRole(session, ADMIN)` — Quản lý tài khoản hệ thống. |
| `CREATE`/`UPDATE`/`DELETE_USER` | Bị từ chối | Bị từ chối (403) | Bị từ chối (403) | **CHO PHÉP** | `requireRole(session, ADMIN)`. |
| `GET_DASHBOARD_METRICS` | Bị từ chối | Bị từ chối (403) | **CHO PHÉP** | **CHO PHÉP** | Thống kê số lượng sách, độc giả, phiếu mượn toàn trường. |
| `GET_STUDENT_DASHBOARD` | Bị từ chối | **CHO PHÉP** | Bị từ chối (403) | Bị từ chối (403) | `requireRole(session, STUDENT)` — Dashboard cá nhân của sinh viên. |
| `LIST_AUDIT_LOGS` | Bị từ chối | Bị từ chối (403) | Bị từ chối (403) | **CHO PHÉP** | `requireRole(session, ADMIN)` — Xem nhật ký kiểm toán hệ thống. |
| `SEARCH_SUGGESTIONS` | Bị từ chối | **CHO PHÉP** | **CHO PHÉP** | **CHO PHÉP** | Gợi ý tự động tìm kiếm sách/độc giả theo ngữ cảnh vai trò. |

> **Đánh giá RBAC:** Cơ chế phân quyền được thực thi **100% tại máy chủ (`RequestRouter`)**, hoàn toàn không phụ thuộc vào giao diện Swing của Client. Mọi hành vi sửa payload ở client (như sinh viên gửi ID của sinh viên khác) đều bị chặn đứng nhờ kiểm tra quyền sở hữu (ownership verification).

---

## 5. Rà soát Java RMI và Java Serialization

### 5.1. Cấu hình Cổng và Binding
- **RMI Registry Port:** Cố định tại cổng TCP `1099` (`LibraryRMIServer.java:26`).
- **RMI Remote Object Port:** Cố định tại cổng TCP `1100` (`LibraryRMIServer.java:27`, `LibraryRemoteServiceImpl.java:49`). Hoàn toàn không sử dụng cổng ngẫu nhiên (dynamic anonymous port), giúp việc cấu hình Firewall cố định và minh bạch.
- **Tên dịch vụ RMI:** `"LibraryRemoteService"` đồng nhất giữa Server (`LibraryRMIServer.java:28`) và Client (`RMIClient.java:33`).
- **Định cấu hình Hostname (`java.rmi.server.hostname`):**
  - Trong `LibraryRMIServer.java:73-75`, hệ thống tự động dò tìm card mạng LAN vật lý thực tế (bỏ qua `127.0.0.1`, loopback, virtual adapters như VirtualBox/VMware/WSL) để gán vào `java.rmi.server.hostname` trước khi export Remote Object.
  - Điều này giải quyết triệt để lỗi kinh điển của RMI khi chạy 2 máy: Stub gửi về Client bị dính IP `127.0.0.1` khiến máy khách không kết nối được vào Remote Object cổng 1100.

### 5.2. Tuần tự hóa đối tượng (Java Serialization)
- **Kiểm tra `serialVersionUID`:** Toàn bộ 21 DTO trong `thuvien.common.dto.*` cùng với `Request.java` và `Response.java` đều khai báo tường minh `private static final long serialVersionUID = 1L;`. Điều này đảm bảo tính tương thích serialization giữa các bản build.
- **Lỗ hổng Deserialization:** Hiện tại Server chưa kích hoạt bộ lọc `jdk.serialFilter`. Do Java RMI bản chất giải mã nhị phân mọi `Serializable` object được gửi đến, việc kết nối từ mạng không tin cậy có thể tiềm ẩn rủi ro nạp gadget chain. Khuyến nghị cấu hình whitelist filter cho JVM khi chạy server:
  `-Djdk.serialFilter="thuvien.common.**;java.lang.**;java.util.**;java.math.**;!*"`

### 5.3. Bảo mật kênh truyền (Transport Security)
- Hệ thống sử dụng Socket TCP thô cho RMI, **chưa tích hợp TLS/SSL**.
- Đây là giới hạn thường thấy của đồ án môn học mạng khi ưu tiên tính tương thích và đơn giản trong cài đặt.
- **Rủi ro:** Kẻ xấu cùng mạng Wi-Fi có thể bắt gói tin thấy thông tin sách, sinh viên và mật khẩu gửi đi khi đăng nhập.
- **Khuyến nghị vận hành:** Chỉ chạy hệ thống trong mạng LAN gia đình, phòng lab trường học, hoặc mạng nội bộ tin cậy; tuyệt đối không mở port ra Internet hoặc dùng trên Wi-Fi quán cafe công cộng khi chưa có TLS.

---

## 6. Rà soát MySQL, JDBC, HikariCP và cấu hình cục bộ

### 6.1. Cách ly tầng dữ liệu (Database Isolation)
- **Client hoàn toàn không chạm vào MySQL:** Đã rà soát toàn bộ thư mục `client/` và `common/`: không có bất kỳ dòng mã nào import `java.sql.*`, `javax.sql.*` hay `com.mysql.*`. Target Ant `compile` biên dịch `client/src` với classpath tách biệt hoàn toàn khỏi JAR JDBC.
- **Đóng gói phân phối riêng biệt:** Target `package-client` trong `build.xml` tạo ra `dist/client-dist/client.jar` chỉ chứa `thuvien/common/**` và `thuvien/client/**`, loại trừ toàn bộ mã server, `server.properties`, `db.properties` và các thư viện JDBC.

### 6.2. SQL Injection và Quản lý tài nguyên JDBC
- Toàn bộ câu lệnh SQL trong `server/src/thuvien/server/repository/impl/` đều sử dụng `PreparedStatement`.
- Các câu lệnh tìm kiếm động (`BookRepositoryImpl.search`) nối chuỗi câu lệnh SQL có điều kiện nhưng toàn bộ giá trị tìm kiếm (keyword, categoryId, status, authorId) đều được đưa vào danh sách tham số `?` qua `setObject`, `setInt`, loại bỏ 100% nguy cơ SQL Injection.
- Mọi tài nguyên JDBC (`Connection`, `PreparedStatement`, `ResultSet`) đều được giải phóng bằng cấu trúc `try-with-resources`.

### 6.3. Hồ kết nối HikariCP
- `DatabaseManager.java` khởi tạo HikariCP với cấu hình chuẩn:
  - Tối đa 10 kết nối (`maxSize=10`), tối thiểu 2 kết nối rảnh (`minIdle=2`).
  - Timeout kết nối: 10 giây (`connectionTimeout=10000`).
  - Kích hoạt bộ nhớ đệm Prepared Statement phía server MySQL:
    `cachePrepStmts=true`, `prepStmtCacheSize=250`, `prepStmtCacheSqlLimit=2048`, `useServerPrepStmts=true`.
  - Có hook `DatabaseManager.getInstance().shutdown()` được đăng ký trong JVM Shutdown Hook của `LibraryRMIServer` để đóng hồ kết nối an toàn khi tắt server.

### 6.4. Đánh giá chuỗi kết nối MySQL
- Chuỗi kết nối mẫu: `jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8`
- `useSSL=false` và `allowPublicKeyRetrieval=true` là cấu hình đặc thù dành cho môi trường phát triển cục bộ MySQL 8.0 (Laragon/XAMPP) không có chứng chỉ SSL. Khi triển khai production, cần cấu hình chứng chỉ CA và bật `useSSL=true`.

---

## 7. Rà soát Xác thực, Mật khẩu và Quản lý Phiên (Session)

### 7.1. Thuật toán băm và so khớp mật khẩu (`PasswordHasher.java`)
- **Thuật toán chính:** `PBKDF2WithHmacSHA256`.
- **Số vòng lặp (Iterations):** 10.000 vòng (đáp ứng tiêu chuẩn chống bẻ khóa tốc độ cao).
- **Muối (Salt):** 16 byte ngẫu nhiên sinh bằng `java.security.SecureRandom`.
- **Độ dài khóa băm:** 256 bits.
- **Định dạng lưu trữ:** `PBKDF2$<iterations>$<saltHex>$<hashHex>`.
- **So sánh an toàn:** Sử dụng `MessageDigest.isEqual(expectedHash, computedHash)` — thuật toán so sánh hằng số thời gian (constant-time comparison), chống tấn công Timing Attack.

### 7.2. Tương thích ngược và Tự động nâng cấp mật khẩu
- Đối với các tài khoản tạo từ dữ liệu mẫu cũ (`seed.sql`), mật khẩu lưu dưới dạng SHA-256 không muối.
- Trong `AuthServiceImpl.java:211-218`: Khi người dùng đăng nhập bằng mật khẩu đúng, hệ thống kiểm tra qua `PasswordHasher.isLegacyHash(storedHash)`. Nếu phát hiện là hash SHA-256 cũ, hệ thống tự động băm lại mật khẩu bằng PBKDF2 với muối ngẫu nhiên mới và cập nhật ngay vào bảng `users`.

### 7.3. Chống Brute Force (Account Lockout)
- `AuthServiceImpl.java` theo dõi số lần đăng nhập thất bại trong bộ nhớ (`ConcurrentHashMap`).
- **Quy tắc:** Đăng nhập sai 5 lần liên tiếp (`MAX_FAILED_ATTEMPTS = 5`) sẽ kích hoạt tạm khóa tài khoản trong 15 phút (`LOCKOUT_DURATION_MS = 15 * 60 * 1000L`).
- **Thu hồi phiên khi khóa:** Ngay khi bị khóa, toàn bộ session token đang hoạt động của user đó bị thu hồi lập tức (`sessionManager.revokeAllSessionsForUser`).
- **Ghi log kiểm toán:** Mọi lần đăng nhập sai, đăng nhập thành công, và sự kiện khóa tài khoản đều được ghi nhận vào bảng `audit_logs`.

### 7.4. Quản lý Phiên (`SessionManager.java`)
- Token phiên được sinh bằng `UUID.randomUUID().toString()`, lưu trong bộ nhớ máy chủ.
- **Thời gian hết hạn:** 30 phút không hoạt động (`IDLE_TIMEOUT_MS = 30 * 60 * 1000L`).
- Mỗi yêu cầu hợp lệ sẽ cập nhật `lastAccessedAt` qua phương thức `touch()`.
- Hỗ trợ hủy phiên toàn diện: Theo token đơn lẻ (`removeSession`), theo User ID (`revokeAllSessionsForUser`), hoặc theo username (`revokeAllSessionsForUsername`).

---

## 8. Rà soát Windows Firewall và Triển khai LAN/Wi-Fi

### 8.1. Kiểm tra trạng thái cổng thực tế trên máy
- **Cổng 3306 (MySQL):** Trạng thái `LISTEN` trên mọi interface (`::`), sở hữu bởi tiến trình `mysqld.exe` (PID 15848) thuộc `D:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysqld.exe`.
- **Cổng 1099 (RMI Registry) & 1100 (Remote Object):** Không có tiến trình nào chiếm giữ (Server đang tắt).

### 8.2. Rà soát quy tắc Windows Defender Firewall
Kết quả kiểm tra cấu hình tường lửa hiện tại:
- Có rule `mysqld` cho phép **Inbound** trên profile **Public**!
- Có rule `School Library Server TCP 9999` cho phép Inbound trên Any profile (cổng TCP socket cũ).
- **Chưa có quy tắc tường lửa riêng biệt cho cổng RMI 1099 và 1100**. Mặc dù có các rule cho `java.exe` và `javaw.exe`, nhưng trong mạng LAN giữa 2 máy, Windows Firewall thường chặn các kết nối lạ vào cổng 1099 và 1100 nếu chưa có rule tường minh.

### 8.3. Khuyến nghị thiết lập Firewall an toàn (ĐỀ XUẤT — CẦN DUYỆT TRƯỚC KHI CHẠY):
Không nên mở cổng công khai cho toàn mạng. Nếu mở cho đồ án mạng LAN, chỉ áp dụng cho profile **Private** (mạng riêng/mạng nhà) và hạn chế dải IP:

```powershell
# CHỈ CHẠY KHI ĐÃ ĐƯỢC DUYỆT - MỞ CỔNG RMI CHO MẠNG NỘI BỘ (PRIVATE)
New-NetFirewallRule -DisplayName "School Library RMI Registry (LAN Only)" `
    -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow -Profile Private

New-NetFirewallRule -DisplayName "School Library RMI Object (LAN Only)" `
    -Direction Inbound -LocalPort 1100 -Protocol TCP -Action Allow -Profile Private
```

> **Lưu ý đặc biệt:** Tuyệt đối **không mở cổng 3306** cho máy Client vì kiến trúc 3 tầng quy định Client chỉ nói chuyện với RMI Server, không bao giờ kết nối trực tiếp vào MySQL.

---

## 9. Rà soát Git, .gitignore và nguy cơ lộ lọt bí mật

### 9.1. Tệp `.gitignore` hiện tại
- Tệp `.gitignore` tại gốc dự án được cấu hình rất chuẩn mực:
  - Bỏ qua build artifacts: `build/`, `dist/`, `*.class`, `*.log`.
  - Bỏ qua metadata người dùng NetBeans: `nbproject/private/`.
  - Bỏ qua cấu hình database cục bộ: `server/resources/db.properties`, `local.properties`.
  - Bảo lưu tệp cấu hình mẫu: `!server/resources/db.properties.example`.

### 9.2. Kiểm tra vết bí mật trong lịch sử Git
- **Trạng thái hiện tại (`git ls-files`):** `server/resources/db.properties` **không** bị Git theo dõi. Chỉ có `db.properties.example` và `server.properties` được theo dõi.
- **Rà soát lịch sử commit:**
  - Commit `e0c5a90` (Initial release): Tệp `server/resources/db.properties` đã từng được commit. Nội dung chứa `db.username=root` và `db.password=` (mật khẩu trống của Laragon).
  - Commit `6e30dc4` (Chore update gitignore): Đã thực hiện xóa file này khỏi Git tracking.
- **Đánh giá rủi ro:** Mặc dù không lộ mật khẩu quan trọng (chỉ là user root không pass của local Laragon), nhưng việc file tồn tại trong lịch sử Git có thể kích hoạt các công cụ quét mã bảo mật tự động (Secret Scanners) trên GitHub.
- **Khuyến nghị:** Đối với đồ án môn học, trạng thái này chấp nhận được. Nếu muốn hoàn toàn sạch vết trước khi nộp hoặc public, cần sự cho phép của người dùng để làm sạch lịch sử commit trước khi push.

---

## 10. Kết quả kiểm thử và phân loại an toàn

### 10.1. Phân loại mức độ an toàn của các bộ kiểm thử tự động
Trước khi chạy bất kỳ test nào, hệ thống kiểm thử được phân loại như sau:

| Bộ kiểm thử (Test Suite) | Loại kiểm thử | Tác động dữ liệu (Side Effects) | Khuyến nghị kích hoạt |
|---|---|---|---|
| `ProtocolTest` | Unit Test | Hoàn toàn trong bộ nhớ (In-memory) | **AN TOÀN TUYỆT ĐỐI — Chạy bất kỳ lúc nào** |
| `AuthServiceTest` | Unit Test | Giả lập (Mock Repository) | **AN TOÀN TUYỆT ĐỐI — Chạy bất kỳ lúc nào** |
| `FineServiceTest` | Unit Test | Giả lập (Mock Repository) | **AN TOÀN TUYỆT ĐỐI — Chạy bất kỳ lúc nào** |
| `RepositoryArchitectureTest` | Unit Test | Kiểm tra phản xạ cấu trúc code (Reflection) | **AN TOÀN TUYỆT ĐỐI — Chạy bất kỳ lúc nào** |
| `RequestRouterTest` | Unit Test | Giả lập toàn bộ Service layer | **AN TOÀN TUYỆT ĐỐI — Chạy bất kỳ lúc nào** |
| `SecurityAndFeatureHardeningTest` | Integration | Sử dụng DB thật nếu kết nối được | **CẦN CẨN TRỌNG — Tạo/xóa tài khoản test** |
| `RMIEndToEndIntegrationTest` | Integration | Khởi động RMI Server và Client test | **CẦN CẨN TRỌNG — Tạo dữ liệu mượn/trả** |
| `TCPEndToEndIntegrationTest` | Legacy Integration | Chạy trên socket TCP cũ | **KHÔNG CẦN THIẾT — Đã chuyển sang RMI** |
| `BorrowConcurrencyIntegrationTest` | Stress / Integration | **CÓ TÁC ĐỘNG XÓA DỮ LIỆU THẬT** (`DELETE FROM fines`, `DELETE FROM borrow_records`, cập nhật sách ID 1) | **NGUY HIỂM NẾU CHƯA SAO LƯU DB** |

### 10.2. Kết quả kiểm tra biên dịch trong lần audit này
- **Lệnh thực hiện:** `& "C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\netbeans\extide\ant\bin\ant.bat" compile`
- **Môi trường:** JDK Corretto 1.8.0_504, Ant 1.9.4.
- **Kết quả:** `BUILD SUCCESSFUL` (0 lỗi, biên dịch thành công toàn bộ 3 module).
- **Trạng thái Git sau biên dịch:** Sạch hoàn toàn (`## main...origin/main`).

---

## 11. Quy trình chạy và vận hành an toàn (Safe Runbook)

Đây là quy trình chuẩn xác đã được đối chiếu 100% với cấu trúc thư mục, tệp cấu hình và target Ant thực tế trong repository.

### Bước 1: Chuẩn bị môi trường
- Đảm bảo Java 8 đã có trong PATH:
  ```cmd
  java -version
  ```
  *(Kết quả phải hiển thị `1.8.0_xxx`)*.

### Bước 2: Khởi động Cơ sở dữ liệu MySQL
- Mở Laragon, bấm **Start All** (đảm bảo MySQL đang chạy ở cổng 3306).
- Kiểm tra database `school_library` đã tồn tại chưa:
  - Nếu chưa có, mở HeidiSQL/phpMyAdmin tạo database `school_library` (utf8mb4).
  - Chạy lần lượt các script trong thư mục `database/`:
    1. `database/schema.sql` (Khởi tạo bảng).
    2. `database/indexes.sql` (Tạo chỉ mục tối ưu).
    3. `database/seed.sql` (Nạp dữ liệu mẫu ban đầu).

### Bước 3: Cấu hình thông tin kết nối Database
- Kiểm tra tệp `server/resources/db.properties`. Nếu chưa có, sao chép từ `server/resources/db.properties.example`:
  ```properties
  db.driver=com.mysql.cj.jdbc.Driver
  db.url=jdbc:mysql://localhost:3306/school_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
  db.username=root
  db.password=
  ```
  *(Điền đúng mật khẩu MySQL của bạn trên máy Server)*.

### Bước 4: Biên dịch dự án bằng NetBeans Ant
Sử dụng Ant đi kèm NetBeans:
```cmd
"C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\netbeans\extide\ant\bin\ant.bat" compile
```

### Bước 5: Khởi động Java RMI Server
Chạy lệnh Ant:
```cmd
"C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\netbeans\extide\ant\bin\ant.bat" run-server
```
**Dấu hiệu thành công trên màn hình console Server:**
```text
RMI Server started
RMI Registry port: 1099
RMI Remote Object port: 1100
RMI hostname: 192.168.x.x
Service: LibraryRemoteService
Available IPv4 addresses:
- 192.168.x.x (Wi-Fi / Ethernet adapter)
```

### Bước 6: Chạy Client trên cùng máy (Localhost)
Mở một cửa sổ dòng lệnh mới và chạy:
```cmd
"C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\netbeans\extide\ant\bin\ant.bat" run-client
```
- Màn hình đăng nhập Swing sẽ xuất hiện.
- Đăng nhập thử với các tài khoản mẫu:
  - Quản trị viên: `admin` / `admin123`
  - Thủ thư: `librarian1` / `lib123`
  - Sinh viên: `student1` / `student123`

### Bước 7: Chạy Client trên máy khác trong mạng LAN/Wi-Fi
1. Đóng gói bộ Client độc lập trên máy chủ:
   ```cmd
   "C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\netbeans\extide\ant\bin\ant.bat" package-client
   ```
2. Copy toàn bộ thư mục `dist/client-dist` sang máy khách (máy thứ hai).
3. Đảm bảo trên máy chủ đã mở cổng 1099 và 1100 (xem mục 8.3).
4. Trên máy khách, mở thư mục `client-dist` và chạy:
   ```cmd
   run-client.bat <IP_MAY_SERVER> 1099
   ```
   *(Ví dụ: `run-client.bat 192.168.1.15 1099`)*.
5. Kiểm tra kết nối: Client sẽ kết nối tới RMI Registry của máy chủ và gọi Remote Object cổng 1100.

---

## 12. Danh sách đề xuất xử lý theo thứ tự ưu tiên

### Mức ưu tiên 1: Cần thiết cho vận hành mạng an toàn
1. **Khóa cổng MySQL cục bộ:** Thêm `bind-address = 127.0.0.1` vào cấu hình MySQL (`my.ini`) của Laragon để ngăn máy ngoài truy cập trực tiếp MySQL port 3306.
2. **Cấu hình Firewall chọn lọc cho RMI:** Chỉ mở cổng 1099 và 1100 trên profile `Private` cho dải mạng LAN gia đình/lớp học, không mở trên `Public`.

### Mức ưu tiên 2: Củng cố tính bền vững của mã nguồn
3. **Thêm Deserialization Filter (JEP 290):** Đặt System property `jdk.serialFilter` trong `LibraryRMIServer.java` để bảo vệ RMI khỏi các tấn công deserialization.
4. **Chuẩn hóa thông báo lỗi nội bộ:** Thay đổi dòng 354 trong `RequestRouter.java` để không trả nguyên văn `e.getMessage()` của các Exception hệ thống về phía client, tránh lộ lọt cấu trúc cơ sở dữ liệu.
5. **Đồng bộ tham số mật khẩu ban đầu:** Cập nhật `RequestRouter.handleCreateUser` để tôn trọng tham số `initialPassword` truyền từ RMI interface thay vì luôn ép về `"password123"`.

### Mức ưu tiên 3: Tối ưu hóa lâu dài
6. **Nâng cấp toàn bộ hash trong `seed.sql` lên PBKDF2:** Loại bỏ hoàn toàn chuỗi hash SHA-256 không muối khỏi file khởi tạo dữ liệu ban đầu.
7. **Làm sạch lịch sử Git (nếu cần public mã nguồn):** Nếu người dùng muốn public dự án lên GitHub và xóa sạch dấu vết commit `e0c5a90`, có thể chạy quy trình dọn lịch sử Git chuyên dụng.

---

## 13. Các mục bắt buộc người dùng phê duyệt trước khi thay đổi

Theo quy tắc an toàn đã đề ra, các hành động sau **chưa được thực hiện** trong giai đoạn Audit này và **chỉ được tiến hành khi có sự đồng ý rõ ràng của người dùng**:

1. **Sửa mã nguồn Java:** Không tự ý sửa `RequestRouter.java`, `LibraryRMIServer.java`, `PasswordHasher.java` hoặc các DTO.
2. **Chạy các Integration Test có tác động cơ sở dữ liệu:** Không chạy `BorrowConcurrencyIntegrationTest` trên database thực tế vì có nguy cơ xóa các bản ghi phiếu mượn và tiền phạt hiện có.
3. **Thao tác trên Windows Firewall:** Không chạy các lệnh `New-NetFirewallRule` hay thay đổi quy tắc tường lửa trên máy tính của người dùng.
4. **Thao tác viết lại lịch sử Git:** Không chạy `git filter-repo`, `git rebase` hay force-push làm thay đổi cây commit hiện tại.

---

## KẾT LUẬN

```text
================================================================================
               AUDIT COMPLETE — NO REMEDIATION APPLIED
================================================================================
Tất cả các thành phần kiến trúc, RMI, phân quyền RBAC, SQL, mật khẩu, phiên,
Firewall và Git đã được rà soát toàn diện và đối chiếu với mã nguồn thực tế.
Không có mã nguồn, cấu hình hay dữ liệu nào bị thay đổi trong đợt kiểm tra này.
================================================================================
```
