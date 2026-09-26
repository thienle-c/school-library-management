# Database Design Specification: Remote School Library System

## 1. Relational Architecture & Normalization Strategy
The database is designed to **Third Normal Form (3NF)**:
* Every non-key attribute is non-transitively dependent on the primary key.
* Multi-valued author relationships are normalized through a dedicated junction table (`book_authors`).
* Redundant textual category data is abstracted into `categories`.
* Financial penalties are isolated into a 1-to-1 extension table (`fines`).
* History and audit records are decoupled into an immutable event log (`audit_logs`).

---

## 2. Entity Specifications

### 1. `users` (System User Accounts & Authentication)
* **Purpose**: System accounts for Administrators, Librarians, and Students.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `username` (VARCHAR(50), Unique, Not Null)
  * `password_hash` (VARCHAR(255), Not Null) — SHA-256 salted hash
  * `role` (ENUM('ADMIN', 'LIBRARIAN', 'STUDENT'), Not Null)
  * `full_name` (VARCHAR(100), Not Null)
  * `email` (VARCHAR(100), Nullable)
  * `phone` (VARCHAR(20), Nullable)
  * `is_active` (BOOLEAN, Default TRUE, Not Null)
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)
  * `updated_at` (TIMESTAMP, Default CURRENT_TIMESTAMP ON UPDATE)

### 2. `students` (Student Profiles & Library Privileges)
* **Purpose**: Extends student identity with borrowing limits and active borrow counts.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `user_id` (BIGINT, Unique, Nullable) — Foreign key to `users(id)`
  * `student_code` (VARCHAR(30), Unique, Not Null)
  * `full_name` (VARCHAR(100), Not Null)
  * `class_name` (VARCHAR(50), Not Null)
  * `phone` (VARCHAR(20), Nullable)
  * `email` (VARCHAR(100), Nullable)
  * `max_borrow_limit` (INT, Default 5, Not Null)
  * `current_borrow_count` (INT, Default 0, Not Null)
  * `status` (ENUM('ACTIVE', 'SUSPENDED', 'GRADUATED'), Default 'ACTIVE')
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)
* **Constraints**: `CHECK (current_borrow_count >= 0)`.

### 3. `categories` (Book Classifications)
* **Purpose**: Categorization taxonomy for catalog indexing.
* **Attributes**:
  * `id` (INT, Primary Key, Auto Increment)
  * `name` (VARCHAR(100), Unique, Not Null)
  * `description` (VARCHAR(255), Nullable)

### 4. `authors` (Book Authors)
* **Purpose**: Biographical details for authors.
* **Attributes**:
  * `id` (INT, Primary Key, Auto Increment)
  * `name` (VARCHAR(150), Not Null)
  * `bio` (TEXT, Nullable)
  * `nationality` (VARCHAR(50), Nullable)

### 5. `books` (Library Catalog Inventory)
* **Purpose**: Master inventory of library books and physical availability.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `isbn` (VARCHAR(20), Unique, Not Null)
  * `title` (VARCHAR(255), Not Null)
  * `category_id` (INT, Not Null) — Foreign key to `categories(id)`
  * `publisher` (VARCHAR(150), Nullable)
  * `publish_year` (INT, Nullable)
  * `edition` (VARCHAR(50), Nullable)
  * `total_copies` (INT, Default 1, Not Null)
  * `available_copies` (INT, Default 1, Not Null)
  * `shelf_location` (VARCHAR(50), Nullable)
  * `status` (ENUM('AVAILABLE', 'BORROWED', 'RESERVED', 'LOST'), Default 'AVAILABLE')
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)
  * `updated_at` (TIMESTAMP, Default CURRENT_TIMESTAMP ON UPDATE)
* **Constraints**: `CHECK (available_copies >= 0 AND available_copies <= total_copies)`.

### 6. `book_authors` (Book-Author Junction)
* **Purpose**: Many-to-many relationship supporting co-authored publications.
* **Attributes**:
  * `book_id` (BIGINT, Foreign Key to `books(id)` ON DELETE CASCADE)
  * `author_id` (INT, Foreign Key to `authors(id)` ON DELETE RESTRICT)
  * **Primary Key**: Composite `(book_id, author_id)`.

### 7. `borrow_records` (Circulation Loans)
* **Purpose**: Loan records tracking active, returned, and overdue borrowing.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `student_id` (BIGINT, Foreign Key to `students(id)`)
  * `book_id` (BIGINT, Foreign Key to `books(id)`)
  * `issued_by_user_id` (BIGINT, Foreign Key to `users(id)`)
  * `borrow_date` (DATE, Not Null)
  * `due_date` (DATE, Not Null)
  * `return_date` (DATE, Nullable)
  * `status` (ENUM('ACTIVE', 'RETURNED', 'OVERDUE', 'LOST'), Default 'ACTIVE')
  * `notes` (TEXT, Nullable)
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)

### 8. `fines` (Overdue Financial Penalties)
* **Purpose**: Financial tracking for late returns.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `borrow_record_id` (BIGINT, Unique, Foreign Key to `borrow_records(id)`)
  * `student_id` (BIGINT, Foreign Key to `students(id)`)
  * `overdue_days` (INT, Default 0, Not Null)
  * `fine_rate_per_day` (DECIMAL(10,2), Default 5000.00, Not Null)
  * `fine_amount` (DECIMAL(10,2), Default 0.00, Not Null)
  * `is_paid` (BOOLEAN, Default FALSE, Not Null)
  * `paid_date` (TIMESTAMP, Nullable)
  * `collected_by_user_id` (BIGINT, Nullable, Foreign Key to `users(id)`)
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)

### 9. `reservations` (Book Holds)
* **Purpose**: Queued book holds when zero copies are available.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `student_id` (BIGINT, Foreign Key to `students(id)`)
  * `book_id` (BIGINT, Foreign Key to `books(id)`)
  * `reservation_date` (TIMESTAMP, Default CURRENT_TIMESTAMP)
  * `expiry_date` (DATE, Not Null)
  * `status` (ENUM('PENDING', 'FULFILLED', 'CANCELLED', 'EXPIRED'), Default 'PENDING')

### 10. `audit_logs` (System Security History)
* **Purpose**: Immutable audit log of administrative and operational actions.
* **Attributes**:
  * `id` (BIGINT, Primary Key, Auto Increment)
  * `user_id` (BIGINT, Nullable, Foreign Key to `users(id)` ON DELETE SET NULL)
  * `action` (VARCHAR(50), Not Null)
  * `entity_name` (VARCHAR(50), Not Null)
  * `entity_id` (BIGINT, Not Null)
  * `details` (TEXT, Nullable)
  * `created_at` (TIMESTAMP, Default CURRENT_TIMESTAMP)

---

## 3. Relationships & Cardinality

```
users (1) --------- (0..1) students
users (1) --------- (0..*) borrow_records (as issuer)
users (1) --------- (0..*) fines (as collector)
users (1) --------- (0..*) audit_logs (as performer)

categories (1) ---- (0..*) books
authors (1..*) ---- (1..*) books (via book_authors)

students (1) ------ (0..*) borrow_records
students (1) ------ (0..*) reservations
students (1) ------ (0..*) fines

books (1) --------- (0..*) borrow_records
books (1) --------- (0..*) reservations

borrow_records (1) - (0..1) fines
```

---

## 4. Indexing Plan

| Table | Index Name | Columns | Query Target |
| :--- | :--- | :--- | :--- |
| `books` | `idx_books_isbn` | `isbn` | Fast exact-match barcode/ISBN lookups |
| `books` | `idx_books_title` | `title` | Title prefix and substring searches |
| `books` | `idx_books_search` | `(status, category_id, title)` | Filtered catalog searches |
| `students` | `idx_students_code` | `student_code` | Student card swipe / code lookups |
| `borrow_records` | `idx_borrow_student_status` | `(student_id, status)` | Active borrow count validation |
| `borrow_records` | `idx_borrow_book_status` | `(book_id, status)` | Book circulation status checks |
| `borrow_records` | `idx_borrow_due_date` | `due_date` | Nightly overdue detection jobs |
| `fines` | `idx_fines_student_paid` | `(student_id, is_paid)` | Unpaid fee checks during borrow |

---

## 5. Transaction & Locking Requirements

### Atomic Borrow Transaction
* Must execute within an explicit transaction:
  1. Verify `students.current_borrow_count < max_borrow_limit`.
  2. Conditional decrement:
     ```sql
     UPDATE books SET available_copies = available_copies - 1
     WHERE id = ? AND available_copies > 0;
     ```
  3. Insert `borrow_records`.
  4. Increment `students.current_borrow_count`.
  5. Commit transaction.
* If any step fails or affected rows == 0, rollback all changes.

### Atomic Return Transaction
* Must execute within an explicit transaction:
  1. Find active borrow record.
  2. Check due date and generate fine if overdue.
  3. Update borrow record status to `RETURNED`.
  4. Increment `books.available_copies`.
  5. Decrement `students.current_borrow_count`.
  6. Commit transaction.
