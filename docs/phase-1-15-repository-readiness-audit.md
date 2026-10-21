# PHASE 1.15 — REPOSITORY READINESS, FULL FILE AUDIT & PUBLICATION SAFETY REPORT

**Dự án:** Remote School Library Management System  
**Workspace:** `D:\workspace\thuvien`  
**Kiến trúc:** Java Swing Client → Java RMI → Remote Library Server → MySQL 8.0.30 (HikariCP)  
**Môi trường:** Java 8 (Amazon Corretto jdk1.8.0_504), Apache Ant 1.9.4 (NetBeans 8.0.2), Laragon MySQL 8.0.30  
**Thời điểm thực hiện:** 2026-10-21  
**Mục tiêu:** Rà soát toàn bộ tệp tin, dữ liệu nhạy cảm, lịch sử Git, tài liệu kỹ thuật và độ sẵn sàng trước khi công khai (public) repository lên GitHub.  

---

## 1. Tóm tắt hiện trạng Repository

- **Trạng thái Git**:
  - Nhánh hiện tại: `main` (đồng bộ với `origin/main` tại commit `437f6b1`).
  - Remote: `origin -> https://github.com/thienle-c/school-library-management.git` (Private repository hiện tại).
  - Working tree: Sạch, không có uncommitted source code thay đổi.
- **Cấu trúc thư mục cốt lõi**:
  - `common/src/`: 40 tệp Java (DTO, Enum, Exception, Protocol Envelope, Remote Interface).
  - `server/src/` & `server/resources/`: 51 tệp Java, `server.properties`, template `db.properties.example`.
  - `client/src/` & `client/resources/`: 35 tệp Java, `client.properties`, ảnh banner `images/library_hero.jpg`.
  - `database/`: 3 tệp SQL chuẩn mực (`schema.sql`, `indexes.sql`, `seed.sql`).
  - `lib/`: 6 tệp JAR mã nguồn mở tiêu chuẩn (Connector/J, HikariCP, JUnit, Hamcrest, SLF4J).
  - `test/`: 14 tệp kiểm thử tự động và safety guard.
  - `docs/`: 26 tài liệu kiến trúc, báo cáo kiểm toán và runbook.
  - `nbproject/` & `build.xml`: Cấu hình NetBeans 8.0.2 và Apache Ant độc lập môi trường.
- **Tình trạng bí mật & Thông tin nhạy cảm**:
  - Không có khóa riêng tư (`*.pem`, `*.key`), chứng chỉ (`*.jks`, `*.p12`) hoặc token bí mật nào bị theo dõi trong Git.
  - Không có mật khẩu production thực tế nào tồn tại trong mã nguồn hoặc lịch sử commit.

---

## 2. Bảng phân loại toàn bộ tệp tin (File Inventory & Classification)

Phân loại theo 5 nhóm trạng thái:
1. **PUBLIC**: Tệp sạch, chuẩn hóa, an toàn để công khai trên GitHub.
2. **CLEANUP**: Tệp công khai nhưng cần chuẩn hóa đường dẫn cá nhân (`C:\Users\...`) hoặc dọn rác trước khi đẩy lên.
3. **PRIVATE**: Tệp cục bộ chứa cấu hình môi trường máy phát triển, bắt buộc phải nằm trong `.gitignore` và không được đẩy lên Git.
4. **GENERATED**: Tệp sinh tự động từ quá trình build/test, có thể tái tạo bất kỳ lúc nào.
5. **REVIEW**: Tệp cần quyết định chiến lược quản lý tài liệu (giữ lại hay lưu trữ archive).

| Nhóm đường dẫn / Tệp tin | Phân loại | Mục đích & Trạng thái | Đánh giá an toàn & Hướng xử lý |
|---|:---:|---|---|
| `README.md` | **PUBLIC** | Tài liệu giới thiệu tổng quan, kiến trúc, cài đặt và chạy ứng dụng | Rất sạch, không chứa đường dẫn cá nhân, sơ đồ Mermaid chuẩn mực. Sẵn sàng công khai. |
| `AGENTS.md` | **PUBLIC** | Quy tắc kiến trúc và tiêu chuẩn lập trình dành cho AI agent | Sạch, mô tả đầy đủ ràng buộc Java 8, Ant, NetBeans. Sẵn sàng công khai. |
| `build.xml` | **PUBLIC** | Kịch bản build Apache Ant biên dịch phân tầng module | Sử dụng đường dẫn tương đối, phân tách module chặt chẽ. Sẵn sàng công khai. |
| `manifest.mf` | **PUBLIC** | Manifest chuẩn cho JAR | Sạch. Sẵn sàng công khai. |
| `nbproject/project.xml`, `project.properties`, `build-impl.xml`, `genfiles.properties` | **PUBLIC** | Cấu hình dự án Apache NetBeans 8.x | Sử dụng đường dẫn tương đối `${file.reference...}`, không gắn cứng máy người dùng. Sẵn sàng công khai. |
| `.gitignore` | **PUBLIC** | Quy tắc loại trừ tệp tự động của Git | Rất đầy đủ (loại bỏ `.class`, `build/`, `dist/`, `db.properties`, `.vscode/`, `.agent/`). Sẵn sàng công khai. |
| `test-connection.bat`, `scripts/test-rmi-ports.ps1` | **PUBLIC** | Script kiểm tra mở cổng 1099 và 1100 qua mạng LAN | Độc lập môi trường, hỗ trợ tham số IP. Sẵn sàng công khai. |
| `common/src/**` (40 files) | **PUBLIC** | DTOs, Enums, Exceptions, Protocol | Độc lập Swing/JDBC, có `serialVersionUID = 1L`. Sẵn sàng công khai. |
| `server/src/**` (51 files) | **PUBLIC** | Server business logic, RMI, Security | Độc lập Swing, PreparedStatement 100%, bảo mật session. Sẵn sàng công khai. |
| `server/resources/server.properties` | **PUBLIC** | Cấu hình cổng RMI 1099/1100 | Giá trị mặc định an toàn. Sẵn sàng công khai. |
| `server/resources/db.properties.example` | **PUBLIC** | Mẫu cấu hình database | Chứa placeholder (`your_db_username`, `your_db_password`). Sẵn sàng công khai. |
| `client/src/**` (35 files) | **PUBLIC** | Swing UI, Client Controllers, RMIClient | Không chứa JDBC/SQL, xử lý qua AsyncWorker. Sẵn sàng công khai. |
| `client/resources/client.properties` | **PUBLIC** | Cấu hình kết nối mặc định của Client | Mặc định `127.0.0.1:1099`. Sẵn sàng công khai. |
| `client/resources/images/library_hero.jpg` | **PUBLIC** | Ảnh banner giao diện đăng nhập | Tài nguyên đồ họa ứng dụng. Sẵn sàng công khai. |
| `database/schema.sql`, `indexes.sql` | **PUBLIC** | Cấu trúc bảng MySQL và chỉ mục hiệu năng | DDL chuẩn, không có dữ liệu nhạy cảm. Sẵn sàng công khai. |
| `database/seed.sql` | **PUBLIC** | Dữ liệu mẫu demo học thuật | Chứa tài khoản mẫu (`admin123`, `lib123`) có ghi chú cảnh báo rõ ràng là dữ liệu test. Sẵn sàng công khai. |
| `lib/*.jar` (6 files) | **PUBLIC** | Thư viện bên thứ ba (MySQL Connector, HikariCP, JUnit, Hamcrest, SLF4J) | Thư viện runtime và testing bắt buộc cho NetBeans Ant build. Sẵn sàng công khai. |
| `test/**` (14 files) | **PUBLIC** | Bộ test tự động và TestDatabaseSafetyGuard | Bảo vệ database cô lập, an toàn khi chạy. Sẵn sàng công khai. |
| `docs/API.md`, `ARCHITECTURE.md`, `DATABASE.md`, `NETWORK.md`, `PERFORMANCE.md`, `RMI_MIGRATION_REPORT.md` | **PUBLIC** | Tài liệu kiến trúc và hướng dẫn kỹ thuật cốt lõi | Tài liệu chính thống của hệ thống, chuẩn hóa kỹ thuật. Sẵn sàng công khai. |
| `docs/phase-1-11-lan-test-runbook.md`, `phase-1-11-demo-checklist.md` | **CLEANUP** | Kịch bản demo và chạy thử LAN 2 máy | Cần thay thế một số đường dẫn cứng `D:\workspace\thuvien` thành `<workspace_root>`. |
| `docs/phase-1-13-merge-audit-report.md`, `phase-1-14-quality-security-audit.md` | **CLEANUP** | Báo cáo kiểm toán Phase 1.13 và 1.14 | Có chứa đường dẫn NetBeans Ant trên máy cá nhân `C:\Users\thien\Downloads\...`. Cần chuẩn hóa thành lệnh tổng quát `ant package-all`. |
| `docs/remediation-phase-1-*.md`, `security-audit-report.md`, `tcp-rmi-dependency-audit.md` | **REVIEW** | Các báo cáo khắc phục lỗi từ Phase 1.1 đến 1.10 | Chứa vết lịch sử phát triển đồ án. Đề xuất: Gom vào thư mục `docs/history/` để giữ repository gọn gàng và chuyên nghiệp. |
| `server/resources/db.properties` | **PRIVATE** | Cấu hình kết nối MySQL cục bộ thực tế | Đã nằm trong `.gitignore`. Tuyệt đối không đưa lên Git. |
| `test.local.properties` | **PRIVATE** | Cấu hình database test cục bộ `school_library_test` | Đã nằm trong `.gitignore`. Tuyệt đối không đưa lên Git. |
| `nbproject/private/` | **PRIVATE** | Cấu hình giao diện và vị trí cửa sổ NetBeans của user | Đã nằm trong `.gitignore`. Không đưa lên Git. |
| `.vscode/`, `.agent/` | **PRIVATE** | Thiết lập môi trường soạn thảo VS Code và agent AI | Đã nằm trong `.gitignore`. Không đưa lên Git. |
| `build/`, `dist/`, `.vscode/bin/` | **GENERATED** | Thư mục sinh ra khi biên dịch | Đã nằm trong `.gitignore`. Không đưa lên Git. |
| `ScratchProxyFilterTest*.class`, `ScratchTestRefs*.class` | **GENERATED** | 4 file bytecode `.class` thử nghiệm còn sót ở thư mục gốc | Đã bị bỏ qua bởi `*.class` trong `.gitignore`, nhưng cần xóa sạch khỏi working tree. |

---

## 3. Phát hiện bảo mật, chất lượng & Tính sẵn sàng công khai mới

### 1. Phân tích lịch sử Git (Git Commit History Secret Audit)
- **Kiểm tra**: Quét toàn bộ commit từ commit đầu tiên `e0c5a90` đến commit hiện tại `437f6b1`.
- **Kết quả**:
  - Trong commit đầu tiên `e0c5a90` (*Initial release*), file `server/resources/db.properties` đã từng được commit với nội dung: `db.username=root`, `db.password=` (mật khẩu rỗng của Laragon local).
  - Ngay tại commit `6e30dc4`, tệp này đã được xóa khỏi Git tracking và chuyển sang `.gitignore`, thay thế bằng `db.properties.example`.
- **Đánh giá rủi ro**:
  - Không có mật khẩu sản xuất hay secret thực sự nào bị lộ.
  - Tuy nhiên, khi chuyển repo từ Private sang Public, một số công cụ quét bảo mật tự động của GitHub (GitHub Secret Scanning / Dependabot) có thể phát hiện tên file `db.properties` trong commit cũ.
  - **Khuyến nghị**: Nếu người dùng muốn lịch sử Git tuyệt đối sạch 100%, có thể sử dụng công cụ `git-filter-repo` để thanh lọc commit cũ, hoặc giữ nguyên vì giá trị mật khẩu hoàn toàn rỗng.

### 2. Thiếu tệp LICENSE mã nguồn mở (LIC-01 — P2)
- **Hiện trạng**: Thư mục gốc chưa có tệp `LICENSE` (MIT, Apache 2.0 hoặc GNU GPLv3).
- **Ý nghĩa**: Một repository công khai trên GitHub nếu không có file `LICENSE` mặc định sẽ áp dụng bản quyền độc quyền ("All rights reserved"), khiến người khác hoặc giảng viên chấm bài không rõ quyền sử dụng/sao chép mã nguồn phục vụ học thuật.
- **Đề xuất**: Bổ sung file `LICENSE` (khuyến nghị giấy phép **MIT License** phù hợp với đồ án sinh viên/học thuật).

### 3. Vết đường dẫn cá nhân trong tài liệu (PATH-01 — P3)
- **Hiện trạng**: Có 5 file tài liệu markdown trong `docs/` chứa đường dẫn cục bộ dạng `C:\Users\thien\Downloads\netbeans-8.0.2-netbeans-8.0.2-201411181905-javase\...` và `D:\workspace\thuvien`.
- **Đề xuất**: Thay thế bằng cú pháp lệnh độc lập: `<workspace_root>` và `<netbeans_path>/extide/ant/bin/ant.bat` hoặc lệnh `ant` tiêu chuẩn.

### 4. Bốn tệp bytecode rác ở thư mục gốc (CLUTTER-01 — P3)
- `ScratchProxyFilterTest$AllowedHandler.class`
- `ScratchProxyFilterTest$MyTestInterface.class`
- `ScratchProxyFilterTest$UnallowedHandler.class`
- `ScratchTestRefs$Item.class`
- **Đề xuất**: Xóa 4 tệp này khỏi thư mục gốc để giữ workspace sạch sẽ.

---

## 4. Đối chiếu các phát hiện từ Phase 1.14

| Mã ID | Phân loại | Tóm tắt từ Phase 1.14 | Trạng thái hiện tại | Kế hoạch xử lý |
|:---:|:---:|:---|:---:|:---|
| **SEC-01** | P1 | Đổi mật khẩu không thu hồi các phiên đăng nhập khác của cùng user | **Còn tồn tại** (Đã xác minh mã nguồn) | Cần cập nhật [RequestRouter.java:L920](file:///D:/workspace/thuvien/server/src/thuvien/server/router/RequestRouter.java#L920) để gọi `revokeAllSessionsForUser` |
| **REL-01** | P2 | Nút "Nhận trả sách" không được bật lại nếu `returnBook()` lỗi | **Còn tồn tại** (Đã xác minh mã nguồn) | Cần cập nhật `handleError` trong [BorrowManagementPanel.java:L904](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java#L904) |
| **BLD-01** | P2 | `ant clean` gặp lỗi file lock khi server đang chạy nền | **Đặc tính Windows** | Khuyến nghị tắt server trước khi clean hoặc dùng `ant compile compile-test test` |
| **NET-01** | P2 | RMI hoạt động qua TCP Socket thuần không mã hóa TLS/SSL | **Giới hạn kiến trúc** | Ghi nhận trong tài liệu đồ án; phù hợp yêu cầu môn học |
| **MEM-01** | P3 | Lazy session purge trong `SessionManager` | **Cải tiến tối ưu** | Thêm scheduled cleanup nếu cần mở rộng quy mô lớn |
| **LAN-01** | P3 | Kiểm thử thực tế trên 2 laptop vật lý | **Chưa kiểm tra runtime vật lý** | Đã có hướng dẫn chi tiết trong runbook, chờ nghiệm thu phần cứng |

---

## 5. Danh sách tài liệu kỹ thuật cần chuẩn hóa trước khi Public

1. **`README.md`**: Đã rất tốt, hoàn chỉnh, chuyên nghiệp. Không cần sửa.
2. **`docs/phase-1-11-lan-test-runbook.md`**: Chuẩn hóa các đường dẫn `D:\workspace\thuvien` thành `<workspace_root>`.
3. **`docs/phase-1-13-merge-audit-report.md`** & **`docs/phase-1-14-quality-security-audit.md`**: Chuẩn hóa đường dẫn NetBeans Ant cá nhân.
4. **Cơ cấu thư mục `docs/`**: Gom các báo cáo remediation cũ (`remediation-phase-1-*.md`) vào thư mục con `docs/history/` để tăng tính mạch lạc cho người đọc repository công khai.

---

## 6. Tổng hợp các việc cần làm theo mức độ ưu tiên (P0 – P3)

### Mức P0 (Ngăn chặn khẩn cấp):
*Không có lỗi P0 nào được phát hiện. Hệ thống không có lỗ hổng nghiêm trọng làm lộ dữ liệu hay crash máy chủ.*

### Mức P1 (Bảo mật & Nghiệp vụ quan trọng):
- [ ] **SEC-01**: Sửa `RequestRouter.handleChangePassword` để tự động thu hồi các phiên đăng nhập khác của cùng tài khoản khi đổi mật khẩu thành công.

### Mức P2 (Chất lượng & Chuẩn bị công khai GitHub):
- [ ] **LIC-01**: Tạo tệp `LICENSE` (MIT License) ở thư mục gốc.
- [ ] **REL-01**: Sửa [BorrowManagementPanel.java](file:///D:/workspace/thuvien/client/src/thuvien/client/view/panels/BorrowManagementPanel.java) để khôi phục trạng thái nút "Nhận trả sách" khi thao tác gặp ngoại lệ.

### Mức P3 (Chuẩn hóa tài liệu & Dọn dẹp):
- [ ] **CLUTTER-01**: Xóa 4 file `.class` rác còn sót ở thư mục gốc.
- [ ] **PATH-01**: Chuẩn hóa các đường dẫn cá nhân trong `docs/`.
- [ ] **ORG-01**: Gom các báo cáo giai đoạn cũ vào `docs/history/`.

---

## 7. Checklist kiểm tra cuối cùng trước khi Public GitHub

- [ ] 1. Toàn bộ 115 test cases chạy thành công (`ant test` -> 100% PASS).
- [ ] 2. Không có file nhạy cảm (`db.properties`, `test.local.properties`) bị commit lên Git (`git status` clean).
- [ ] 3. Đã có file `LICENSE` ở thư mục gốc.
- [ ] 4. Đã xóa các file `.class` rác ở thư mục gốc.
- [ ] 5. Đã xử lý bản sửa lỗi P1 (SEC-01) và P2 (REL-01).
- [ ] 6. Tài liệu không còn để lộ đường dẫn cá nhân `C:\Users\thien\...`.
- [ ] 7. Cơ chế bảo vệ database `TestDatabaseSafetyGuard` hoạt động trơn tru.
- [ ] 8. Người dùng xem xét và xác nhận quyết định chuyển quyền Private -> Public trên GitHub.
