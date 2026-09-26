-- ==========================================================
-- Performance Indexes for Remote School Library System
-- Designed for sub-10ms queries, pagination, and multi-client concurrency
-- ==========================================================

-- Books: Index frequently searched columns
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_isbn ON books(isbn);
CREATE INDEX idx_books_status ON books(status);
CREATE INDEX idx_books_category ON books(category_id);
CREATE INDEX idx_books_composite_search ON books(status, category_id, title);

-- Students: Index student_code and status
CREATE INDEX idx_students_code ON students(student_code);
CREATE INDEX idx_students_status ON students(status);

-- Borrow Records: Critical for borrow/return transactional lookups
CREATE INDEX idx_borrow_student_status ON borrow_records(student_id, status);
CREATE INDEX idx_borrow_book_status ON borrow_records(book_id, status);
CREATE INDEX idx_borrow_due_date ON borrow_records(due_date);
CREATE INDEX idx_borrow_status ON borrow_records(status);

-- Fines: Filter unpaid fines per student
CREATE INDEX idx_fines_student_paid ON fines(student_id, is_paid);
CREATE INDEX idx_fines_paid ON fines(is_paid);

-- Reservations: Lookup active reservations per book
CREATE INDEX idx_res_book_status ON reservations(book_id, status);
CREATE INDEX idx_res_student_status ON reservations(student_id, status);

-- Audit Logs: Query by timestamp and entity
CREATE INDEX idx_audit_created ON audit_logs(created_at);
CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
