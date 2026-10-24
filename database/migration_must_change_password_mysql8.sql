-- ==============================================================================
-- Remote School Library Management System
-- Database Schema Migration: Add `must_change_password` to `users` table
-- Compatible with: MySQL 5.7+ / MySQL 8.0+ (InnoDB)
-- ==============================================================================
-- MỤC ĐÍCH:
--   Bổ sung cột `must_change_password` vào bảng `users` một cách an toàn và có thể
--   chạy lại nhiều lần (idempotent) mà không gây lỗi hoặc làm thay đổi dữ liệu hiện có.
--
-- LƯU Ý BẢO MẬT:
--   - MySQL 8.0 KHÔNG hỗ trợ cú pháp 'ALTER TABLE ... ADD COLUMN IF NOT EXISTS'.
--   - Script này sử dụng Stored Procedure tạm thời để kiểm tra INFORMATION_SCHEMA
--     nhằm đảm bảo tính tương thích và kiểm tra tính toàn vẹn kiểu dữ liệu.
-- ==============================================================================

USE school_library;

DELIMITER //

DROP PROCEDURE IF EXISTS MigrateAddMustChangePassword //

CREATE PROCEDURE MigrateAddMustChangePassword()
BEGIN
    DECLARE col_count INT DEFAULT 0;
    DECLARE col_type VARCHAR(64) DEFAULT '';
    DECLARE is_null VARCHAR(3) DEFAULT '';
    DECLARE col_default VARCHAR(64) DEFAULT '';
    DECLARE users_count_before INT DEFAULT 0;
    DECLARE users_count_after INT DEFAULT 0;

    -- 1. Đếm số lượng bản ghi users trước khi migration
    SELECT COUNT(*) INTO users_count_before FROM users;

    -- 2. Kiểm tra thông tin cột trong INFORMATION_SCHEMA.COLUMNS
    SELECT COUNT(*), 
           COALESCE(MAX(DATA_TYPE), ''), 
           COALESCE(MAX(IS_NULLABLE), ''), 
           COALESCE(MAX(COLUMN_DEFAULT), '')
    INTO col_count, col_type, is_null, col_default
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'must_change_password';

    -- 3. Phân nhánh xử lý an toàn
    IF col_count = 0 THEN
        -- Cột chưa tồn tại: Thực hiện thêm cột mới
        ALTER TABLE users 
        ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE 
        AFTER is_active;

        -- Xác minh số bản ghi sau khi thêm cột
        SELECT COUNT(*) INTO users_count_after FROM users;
        IF users_count_before <> users_count_after THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'MIGRATION ERROR: Số lượng bản ghi trong bảng users bị thay đổi ngoài ý muốn!';
        END IF;

        SELECT 'SUCCESS: Cột must_change_password đã được thêm thành công vào bảng users.' AS migration_status,
               users_count_after AS total_users;
    ELSE
        -- Cột đã tồn tại: Kiểm tra định nghĩa kiểu dữ liệu, nullability và giá trị mặc định
        -- Trong MySQL, BOOLEAN là alias của TINYINT(1)
        IF col_type IN ('tinyint', 'boolean') AND is_null = 'NO' AND col_default IN ('0', 'b\'0\'', 'FALSE') THEN
            SELECT 'SKIP: Cột must_change_password đã tồn tại với định nghĩa hợp lệ. Không cần thay đổi.' AS migration_status,
                   users_count_before AS total_users;
        ELSE
            -- Cột đã tồn tại nhưng sai kiểu hoặc sai ràng buộc: Dừng và báo lỗi, không tự sửa âm thầm
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'MIGRATION ERROR: Cột must_change_password đã tồn tại nhưng có định nghĩa sai (DATA_TYPE, NULLABLE hoặc DEFAULT)! Cần quản trị viên kiểm tra thủ công.';
        END IF;
    END IF;

END //

DELIMITER ;

-- Thực thi thủ tục migration
CALL MigrateAddMustChangePassword();

-- Dọn dẹp thủ tục tạm thời
DROP PROCEDURE IF EXISTS MigrateAddMustChangePassword;

-- Kiểm tra lại cấu trúc bảng users sau migration
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'must_change_password';
