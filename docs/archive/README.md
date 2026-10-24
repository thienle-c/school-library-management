# THƯ MỤC LƯU TRỮ TÀI LIỆU KIỂM TOÁN LỊCH SỬ (HISTORICAL AUDIT ARCHIVE)
## Remote School Library Management System — Archival Index

> **Mục đích**: Thư mục này lưu trữ các báo cáo kiểm toán, đánh giá kỹ thuật và nhật ký khắc phục từ các giai đoạn nâng cấp trước (Phase 1.1 đến Phase 1.33).  
> **Nguyên tắc bảo tồn**: Toàn bộ dữ liệu, bằng chứng kiểm thử và nhật ký lỗi lịch sử được giữ nguyên trạng 100% để phục vụ tra cứu hồi quy khi cần thiết, không làm loãng cấu trúc tài liệu chính tại thư mục `docs/`.

---

### BỘ TÀI LIỆU CHÍNH HIỆN HÀNH (ACTIVE DOCUMENTATION SET)

Các tài liệu chính thức đang được sử dụng nằm tại thư mục gốc `docs/`:
1. [README.md](../../README.md): Hướng dẫn tổng quan dự án, tài khoản demo và xử lý sự cố.
2. [ARCHITECTURE.md](../ARCHITECTURE.md): Kiến trúc phân tầng 3-tier, phân quyền RBAC và nguyên tắc phân tách module.
3. [NETWORK.md](../NETWORK.md): Giao thức mạng, cấu hình cổng RMI 1099/1100 và phát hiện IP LAN.
4. [DATABASE.md](../DATABASE.md): Thiết kế cơ sở dữ liệu, quan hệ bảng và khóa ngoại InnoDB.
5. [API.md](../API.md): Danh mục Action, Request/Response envelope và mã trạng thái.
6. [INSTALLATION.md](../INSTALLATION.md): Hướng dẫn môi trường, biên dịch Ant và đóng gói phân phối.
7. [DEPLOYMENT.md](../DEPLOYMENT.md): Quy trình triển khai, kiểm tra môi trường và chạy migration an toàn.
8. [BACKUP-RESTORE.md](../BACKUP-RESTORE.md): Quy trình sao lưu và phục hồi với `--result-file`.
9. [PERFORMANCE.md](../PERFORMANCE.md): Tiêu chuẩn hiệu năng, bảo vệ EDT và kết quả benchmark.
10. [master-finalization-and-staging-readiness.md](../master-finalization-and-staging-readiness.md): Báo cáo tổng thể hiện trạng kỹ thuật và các cổng kiểm soát chất lượng.
11. [STAGING-HANDOFF-CHECKLIST.md](../STAGING-HANDOFF-CHECKLIST.md): Danh mục bàn giao hạ tầng và các tiêu chí nghiệm thu.
12. [PROJECT-CLEANUP-REPORT.md](../PROJECT-CLEANUP-REPORT.md): Báo cáo dọn dẹp và chuẩn hóa cấu trúc dự án.

---

### MỤC LỤC TÀI LIỆU LỊCH SỬ ĐÃ LƯU TRỮ (ARCHIVED REPORTS)

#### 1. Kiểm toán Tổng thể & Khảo sát Ban đầu:
* `full-project-audit.md`: Khảo sát hiện trạng toàn diện trước khi tái cấu trúc RMI.
* `security-audit-report.md`: Báo cáo rà soát an ninh mạng và lỗ hổng bảo mật giai đoạn đầu.
* `tcp-rmi-dependency-audit.md`: Rà soát phân tách phụ thuộc giữa Socket TCP thô và Java RMI.
* `netbeans-server-entrypoint-audit.md`: Kiểm tra điểm vào khởi động máy chủ trong NetBeans IDE.
* `RMI_MIGRATION_REPORT.md`: Báo cáo chuyển đổi giao thức từ TCP Socket sang Java RMI.

#### 2. Nhật ký Khắc phục Lỗi RMI (Remediation Phases 1.1 - 1.10):
* `remediation-phase-1-report.md`: Khắc phục lỗi kết nối RMI ban đầu.
* `remediation-phase-1-verification.md`: Bằng chứng xác minh sau khắc phục Phase 1.
* `remediation-phase-1-3-report.md` đến `remediation-phase-1-10-report.md`: Chuỗi báo cáo khắc phục chi tiết các lỗi timeout, hostname quảng bá LAN IP (`10.0.107.143`), giải phóng tài nguyên registry và đồng bộ hóa session.

#### 3. Báo cáo Nghiệm thu & Tối ưu hóa (Phases 1.11 - 1.23):
* `phase-1-11-acceptance-report.md`, `phase-1-11-demo-checklist.md`, `phase-1-11-lan-test-runbook.md`: Nghiệm thu kết nối LAN nội bộ và kịch bản demo.
* `phase-1-12-acceptance-report.md`: Nghiệm thu luồng nghiệp vụ mượn/trả sách.
* `phase-1-13-merge-audit-report.md`: Kiểm toán tích hợp các nhánh mã nguồn.
* `phase-1-14-quality-security-audit.md`: Đánh giá chất lượng mã nguồn và bảo mật.
* `phase-1-15-repository-readiness-audit.md`: Kiểm tra tính sẵn sàng của kho mã nguồn.
* `phase-1-16-implementation-report.md`: Báo cáo hoàn thiện các tính năng cốt lõi.
* `phase-1-17-post-fix-verification.md`: Xác minh sau sửa lỗi.
* `phase-1-22-ui-cleanup-security-audit.md`: Rà soát giao diện người dùng Swing và bảo mật form.
* `phase-1-23-post-implementation-verification.md`: Kiểm chứng hoàn tất giao diện.

#### 4. Chuẩn bị Phát hành & Kiểm toán Cổng Triển khai (Phases 1.24 - 1.33):
* `phase-1-24-security-data-audit.md`: Kiểm toán tính toàn vẹn dữ liệu và phân tích mã băm PBKDF2/SHA-256.
* `phase-1-25-release-readiness-report.md`: Đánh giá tính sẵn sàng đóng gói phát hành.
* `phase-1-26-release-build-report.md`: Kiểm chứng build phân phối độc lập client/server.
* `phase-1-27-production-release-preparation-report.md`: Chuẩn bị quy trình migration MySQL 8.0.
* `phase-1-28-final-production-readiness-audit.md`: Kiểm toán Catalog Guard và vòng đời RMI.
* `phase-1-29-independent-verification-report.md`: Kiểm chứng độc lập các kịch bản kiểm thử.
* `phase-1-30-operational-security-hardening-report.md`: Tăng cường bảo mật vận hành và chống rò rỉ bí mật.
* `phase-1-31-staging-readiness-evidence.md`: Thu thập bằng chứng kiểm thử chấp nhận.
* `phase-1-32-independent-staging-gate-report.md`: Cổng đánh giá độc lập Staging vs Production.
* `phase-1-33-staging-deployment-checklist.md`: Tiền thân của bảng tiêu chí bàn giao hạ tầng.
