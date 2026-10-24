# HƯỚNG DẪN SAO LƯU VÀ PHỤC HỒI DỮ LIỆU MYSQL 8 (BACKUP & RESTORE RUNBOOK)

> **Hệ thống**: Remote School Library Management System  
> **Cơ sở dữ liệu**: MySQL 8.0+ (Storage Engine: InnoDB, Charset: utf8mb4)  
> **Tên Database**: `school_library`  

---

## 1. NGUYÊN TẮC SAO LƯU AN TOÀN

1. **Sao lưu trước mọi hoạt động lớn**: Bắt buộc tạo một bản sao lưu toàn vẹn trước khi nâng cấp ứng dụng, sửa schema, chạy migration hoặc bảo trì máy chủ.
2. **Không ghi mật khẩu dạng rõ trên dòng lệnh**: Tuyệt đối không truyền tham số `-pMyPassword` trực tiếp vào command line để tránh bị lưu trong lịch sử lệnh (PowerShell `History`, Linux `.bash_history`) hoặc bị nhìn thấy qua `Get-Process / ps aux`. Sử dụng cờ `-p` để nhập mật khẩu an toàn qua prompt hoặc sử dụng tệp cấu hình bảo mật.
3. **Lưu trữ cách ly (Isolated Storage)**: Thư mục chứa tệp backup phải nằm ngoài thư mục chia sẻ công khai của client, ngoài thư mục phân phối `dist/` và được cấu hình trong `.gitignore` để tránh đưa vào mã nguồn.
4. **Đảm bảo tính nhất quán (ACID Snapshot)**: Sử dụng cờ `--single-transaction` để lấy ảnh chụp dữ liệu nhất quán trong khi các giao dịch đọc/ghi khác vẫn đang hoạt động mà không cần khóa toàn bộ database (*non-locking backup*).

---

## 2. QUY TRÌNH SAO LƯU CƠ SỞ DỮ LIỆU (BACKUP PROCEDURE)

### Bước 2.1: Chuẩn bị thư mục lưu trữ sao lưu
Tạo một thư mục riêng biệt trên máy chủ (ví dụ: `D:\library_backups`):
```cmd
mkdir D:\library_backups
```

### Bước 2.2: Thực hiện lệnh sao lưu với `mysqldump`
Mở PowerShell hoặc Command Prompt và thực thi lệnh:

```bash
mysqldump -u root -p \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --hex-blob \
    --default-character-set=utf8mb4 \
    --result-file=D:\library_backups\school_library_backup_YYYYMMDD.sql \
    school_library
```

> **Giải thích các tham số kỹ thuật**:
> - `--result-file=...`: **Bắt buộc trên Windows / PowerShell**. Viết trực tiếp tệp UTF-8 nhị phân ra đĩa, tránh lỗi toán tử `>` của PowerShell tự chuyển thành UTF-16LE gây lỗi `ASCII '\0'` khi phục hồi.
> - `--single-transaction`: Tạo giao dịch đọc snapshot cô lập (dành riêng cho engine InnoDB), đảm bảo dữ liệu mượn/trả sách nhất quán tại thời điểm sao lưu mà không làm gián đoạn người dùng.
> - `--quick`: Xuất từng dòng dữ liệu trực tiếp ra đĩa thay vì nạp toàn bộ bảng vào bộ nhớ RAM máy chủ.
> - `--routines` & `--triggers`: Bao gồm toàn bộ trigger và thủ tục lưu trữ (nếu có).
> - `--hex-blob`: Mã hóa các trường nhị phân (binary/blob) dưới dạng chuỗi hexa, tránh lỗi corrupt khi truyền qua các hệ điều hành khác nhau.
> - `--default-character-set=utf8mb4`: Bảo toàn toàn bộ tiếng Việt có dấu và các ký tự Unicode đầy đủ.

### Bước 2.3: Phương pháp truyền mật khẩu an toàn qua biến môi trường (Cho tự động hóa / Cron)
Nếu cần cấu hình tác vụ sao lưu định kỳ tự động (Windows Task Scheduler hoặc Linux Crontab):
```powershell
# Trên PowerShell (Tự động xóa biến môi trường ngay sau khi hoàn thành)
$env:MYSQL_PWD = "YourSecurePasswordHere"
& "D:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysqldump.exe" -u thuvien_app --single-transaction --quick school_library > D:\library_backups\school_library_auto.sql
Remove-Item Env:\MYSQL_PWD
```

### Bước 2.4: Quy trình Thẩm định Bản sao lưu 3 Cấp độ (3-Tier Backup Verification)
Trước khi kết luận việc sao lưu thành công, quản trị viên bắt buộc thực hiện kiểm tra theo 3 cấp độ:

1. **Cấp độ 1 — Kiểm tra Tệp tồn tại và Dung lượng (File Existence & Size Check)**:
   - Kiểm tra tệp sao lưu đã được tạo trên đĩa và dung lượng bắt buộc `size > 0` (thường lớn hơn vài chục KB tùy dung lượng dữ liệu).
   - Ngăn chặn tình trạng lệnh dump bị lỗi quyền hạn hoặc hết dung lượng đĩa sinh ra file 0 bytes.
   ```powershell
   Get-Item D:\library_backups\school_library_backup_*.sql | Select-Object Name, Length, LastWriteTime
   ```

2. **Cấp độ 2 — Kiểm tra Cấu trúc Tệp và Tính trọn vẹn (Structural Integrity Check)**:
   - Kiểm tra 10 dòng đầu xem có header MySQL dump hợp lệ:
     ```powershell
     Get-Content D:\library_backups\school_library_backup_*.sql -TotalCount 10
     # Kết quả phải chứa: -- MySQL dump ...
     ```
   - Kiểm tra 5 dòng cuối để bảo đảm tiến trình dump không bị ngắt quãng giữa chừng (truncated dump do đứt kết nối hoặc kill process):
     ```powershell
     Get-Content D:\library_backups\school_library_backup_*.sql -Tail 5
     # Kết quả bắt buộc phải chứa: -- Dump completed on YYYY-MM-DD ...
     ```

3. **Cấp độ 3 — Diễn tập Phục hồi Thực sự (Real Dry-Run Restore Test)**:
   - Một tệp dump hợp lệ về cấu trúc vẫn có thể bị lỗi cú pháp khi nạp dữ liệu (ví dụ xung đột charset, version compatibility, lỗi trigger).
   - Vì vậy, việc nạp thử nghiệm vào một CSDL kiểm thử độc lập (`school_library_restore_test`) là phương pháp kiểm chứng cuối cùng để bảo đảm khả năng phục hồi thực sự.

---

## 3. QUY TRÌNH DIỄN TẬP PHỤC HỒI DỮ LIỆU (RESTORE RUNBOOK)

> [!CAUTION]
> Tuyệt đối **KHÔNG khôi phục trực tiếp đè lên database đang vận hành (`school_library`)** khi chưa kiểm tra tính hợp lệ của tệp sao lưu. Luôn diễn tập khôi phục trên một cơ sở dữ liệu kiểm thử riêng biệt (`school_library_restore_test`).

### Bước 3.1: Tạo database kiểm thử để xác minh
```sql
CREATE DATABASE IF NOT EXISTS school_library_restore_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### Bước 3.2: Khôi phục dữ liệu từ tệp sao lưu
Sử dụng công cụ dòng lệnh `mysql`:
```bash
mysql -u root -p school_library_restore_test < D:\library_backups\school_library_backup_20261022.sql
```

### Bước 3.3: Kiểm tra tính toàn vẹn dữ liệu sau khi khôi phục (Integrity Verification)
Thực thi các câu lệnh kiểm tra đối chiếu số lượng bản ghi và ràng buộc khóa ngoại:

```sql
USE school_library_restore_test;

-- 1. Kiểm tra số lượng bản ghi của các bảng cốt lõi
SELECT 'users' AS table_name, COUNT(*) AS total_rows FROM users
UNION ALL
SELECT 'students', COUNT(*) FROM students
UNION ALL
SELECT 'books', COUNT(*) FROM books
UNION ALL
SELECT 'categories', COUNT(*) FROM categories
UNION ALL
SELECT 'borrow_records', COUNT(*) FROM borrow_records
UNION ALL
SELECT 'fines', COUNT(*) FROM fines
UNION ALL
SELECT 'reservations', COUNT(*) FROM reservations;

-- 2. Kiểm tra tính toàn vẹn tồn kho sách (không có sách nào bị âm tồn kho)
SELECT id, isbn, title, total_copies, available_copies 
FROM books 
WHERE available_copies < 0 OR available_copies > total_copies;
-- Kết quả kỳ vọng: 0 rows

-- 3. Kiểm tra tính hợp lệ của số lượng mượn sinh viên
SELECT id, student_code, full_name, current_borrow_count, max_borrow_limit 
FROM students 
WHERE current_borrow_count < 0;
-- Kết quả kỳ vọng: 0 rows

-- 4. Kiểm tra ràng buộc mượn trả và tiền phạt
SELECT br.id, br.student_id, br.book_id, br.status, f.fine_amount, f.is_paid
FROM borrow_records br
LEFT JOIN fines f ON br.id = f.borrow_record_id
WHERE br.status = 'ACTIVE' AND br.return_date IS NOT NULL;
-- Kết quả kỳ vọng: 0 rows
```

### Bước 3.4: Dọn dẹp sau khi diễn tập
Sau khi xác nhận bản sao lưu hoàn toàn hợp lệ và có thể phục hồi an toàn:
```sql
DROP DATABASE school_library_restore_test;
```

---

## 4. CHIẾN LƯỢC LƯU TRỮ VÀ VÒNG ĐỜI SAO LƯU (RETENTION POLICY)

- **Chu kỳ sao lưu đề xuất**:
  - Sao lưu toàn phần (*Full Backup*): 1 lần mỗi tuần vào cuối tuần.
  - Sao lưu chênh lệch / hàng ngày: Thực hiện vào 23:00 mỗi ngày học.
- **Thời gian lưu giữ**:
  - Bản sao lưu hàng ngày: Giữ 7 ngày gần nhất.
  - Bản sao lưu hàng tuần: Giữ 4 tuần gần nhất.
  - Bản sao lưu cuối tháng / cuối kỳ học: Giữ 1 năm phục vụ lưu trữ học vụ.
- **Bảo mật bản sao lưu**:
  - Thư mục sao lưu phải được phân quyền NTFS/Linux chỉ tài khoản Administrator hoặc dịch vụ sao lưu được truy cập.
  - Tệp sao lưu khi chuyển giao ra môi trường ngoài cần được mã hóa bằng 7-Zip/AES-256 hoặc GPG.
