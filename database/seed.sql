-- ==========================================================
-- Initial Seed Data for Remote School Library System
-- Default admin, librarians, sample students, books, and categories
-- NOTE: The accounts and credentials below are DEMO / TEST data
-- strictly for local evaluation and coursework demonstrations.
-- DO NOT use these credentials in a production environment.
-- ==========================================================

-- Passwords hashed with SHA-256 for standard setup:
-- 'admin123' -> 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- 'lib123'   -> 43b185ba1473ae60e2fb7db33f9cfb9f5f148421c601dfa525f0a20e8b15d97f
-- 'student123' -> 7810d2925b6a3780a424ca1952e464c8d5ea2f37cbb51ec7dbad973d4abfc91a

INSERT INTO users (username, password_hash, role, full_name, email, is_active) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', 'System Administrator', 'admin@school.edu', TRUE),
('librarian1', '43b185ba1473ae60e2fb7db33f9cfb9f5f148421c601dfa525f0a20e8b15d97f', 'LIBRARIAN', 'Head Librarian Nguyen', 'lib1@school.edu', TRUE),
('librarian2', '43b185ba1473ae60e2fb7db33f9cfb9f5f148421c601dfa525f0a20e8b15d97f', 'LIBRARIAN', 'Assistant Librarian Tran', 'lib2@school.edu', TRUE),
('student1', '7810d2925b6a3780a424ca1952e464c8d5ea2f37cbb51ec7dbad973d4abfc91a', 'STUDENT', 'Le Van An', 'an.le@student.school.edu', TRUE);

-- Insert Sample Categories
INSERT INTO categories (name, description) VALUES
('Computer Science', 'Software engineering, algorithms, networking, and security'),
('Mathematics', 'Calculus, linear algebra, statistics, and discrete mathematics'),
('Physics', 'Classical mechanics, quantum physics, thermodynamics'),
('Literature', 'World literature, classics, and poetry'),
('Economics', 'Microeconomics, macroeconomics, finance, and accounting');

-- Insert Sample Authors
INSERT INTO authors (name, bio, nationality) VALUES
('Andrew S. Tanenbaum', 'Professor of Computer Science, author of Computer Networks', 'American-Dutch'),
('Robert C. Martin', 'Software craftsman, author of Clean Code and Clean Architecture', 'American'),
('Joshua Bloch', 'Author of Effective Java, former Java Architect at Sun Microsystems', 'American'),
('James F. Kurose', 'Professor of Computer Science, Computer Networking A Top-Down Approach', 'American'),
('Thomas H. Cormen', 'Co-author of Introduction to Algorithms (CLRS)', 'American');

-- Insert Sample Books
INSERT INTO books (isbn, title, category_id, publisher, publish_year, edition, total_copies, available_copies, shelf_location, status) VALUES
('978-0132126953', 'Computer Networks', 1, 'Pearson', 2011, '5th Edition', 5, 5, 'CS-01-A', 'AVAILABLE'),
('978-0131177055', 'Clean Code', 1, 'Prentice Hall', 2008, '1st Edition', 4, 4, 'CS-01-B', 'AVAILABLE'),
('978-0134685991', 'Effective Java', 1, 'Addison-Wesley', 2018, '3rd Edition', 3, 3, 'CS-02-A', 'AVAILABLE'),
('978-0133594140', 'Computer Networking: A Top-Down Approach', 1, 'Pearson', 2016, '7th Edition', 4, 4, 'CS-02-B', 'AVAILABLE'),
('978-0262033848', 'Introduction to Algorithms', 1, 'MIT Press', 2009, '3rd Edition', 6, 6, 'CS-03-A', 'AVAILABLE');

-- Link Books to Authors
INSERT INTO book_authors (book_id, author_id) VALUES
(1, 1), -- Computer Networks -> Tanenbaum
(2, 2), -- Clean Code -> Martin
(3, 3), -- Effective Java -> Bloch
(4, 4), -- Computer Networking -> Kurose
(5, 5); -- Algorithms -> Cormen

-- Insert Sample Students
INSERT INTO students (user_id, student_code, full_name, class_name, phone, email, max_borrow_limit, current_borrow_count, status) VALUES
(4, 'STU001', 'Le Van An', 'CNTT-K18', '0912345678', 'an.le@student.school.edu', 5, 0, 'ACTIVE'),
(NULL, 'STU002', 'Pham Thi Bich', 'CNTT-K18', '0923456789', 'bich.pham@student.school.edu', 5, 0, 'ACTIVE'),
(NULL, 'STU003', 'Hoang Minh Chau', 'DTVT-K19', '0934567890', 'chau.hoang@student.school.edu', 5, 0, 'ACTIVE');

-- Insert Sample Activation Codes for Unlinked Students
-- Raw Code for STU002: 'ACT-STU002'
-- Raw Code for STU003: 'ACT-STU003'
INSERT INTO student_activations (student_id, code_hash, is_used) VALUES
(2, '8856b89283606c3738dc08b16f4d6a70405bceca086c559adc0d82d1f43be01f', FALSE),
(3, '3ff6608cee3fde2b4ca67bca048f1afdd5043a87016dcd89c7a4d35d9c5b6de5', FALSE);

