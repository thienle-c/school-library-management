# Remote School Library Management System — AI Development & Architecture Rules

## 1. Project Purpose
A client-server school library management system designed for university Computer Networking and Network Programming coursework. The system enables book and student management, borrowing, returns, fines, holds/reservations, search, reporting, and audit logging over a raw TCP network socket.

---

## 2. Technology Constraints
* **Language & Runtime**: Java 8 (OpenJDK 1.8 / Amazon Corretto 8). Source and target compatibility must remain strictly `1.8`.
* **Build System**: Apache Ant via NetBeans (`build.xml`, `nbproject/project.xml`, `nbproject/project.properties`).
* **IDE Compatibility**: Must remain fully openable and buildable in Apache NetBeans 8.x without modification. Do NOT migrate to Maven or Gradle.
* **Networking Transport**: Raw Java TCP Sockets (`java.net.Socket`, `java.net.ServerSocket`). No HTTP, RMI, or web framework containers.
* **UI**: Java Swing (Desktop client).
* **Database**: MySQL 5.7+ / 8.0+ using InnoDB engine.
* **Database Access**: Native JDBC with HikariCP connection pooling.
* **Testing Framework**: JUnit 4.

---

## 3. Client/Server Architecture

The system is strictly partitioned into a 3-tier distributed architecture:

```
Java Swing Client
        |
        | TCP Socket (Port 8888)
        v
Java Library Server
        |
        +-- RequestRouter
        |
        +-- Controller
        |
        +-- Service Layer
        |
        +-- Repository Layer
        |
        v
      MySQL (InnoDB)
```

### Strict Architectural Boundaries:
* **The Client MUST NOT connect directly to MySQL.**
* **Only the Server is permitted to access the database.**
* Client must not contain any JDBC driver, `java.sql.*`, or connection pool dependencies.
* Server must not contain any Swing dependencies (`javax.swing.*`).
* All client-to-server communication must flow over TCP Sockets through the defined Request/Response protocol.

---

## 4. Package Responsibilities

### Common Module (`common/src/thuvien/common`)
* Shared between Client and Server.
* Contains protocol envelopes (`Request`, `Response`, `Action`, `StatusCode`).
* Contains domain Data Transfer Objects (`DTOs`).
* Contains type-safe domain enumerations (`UserRole`, `BookStatus`, `BorrowStatus`, `ReservationStatus`, `StudentStatus`).
* Contains shared domain exceptions (`LibraryException`, `ValidationException`, `NetworkException`).
* **Forbidden**: No Swing imports, no JDBC/SQL imports.

### Server Module (`server/src/thuvien/server`)
* **`network`**: `ServerSocket` lifecycle, thread pool management, client socket I/O workers.
* **`router`**: `RequestRouter` validating session tokens, enforcing role-based permissions, and routing actions.
* **`controller`**: Receives requests from the router, delegates to Services, produces `Response` envelopes.
* **`service`**: Implements all business rules, calculations, and multi-statement database transactions.
* **`repository`**: Direct SQL execution via `PreparedStatement`, mappings between `ResultSet` and domain entities.
* **`database`**: `DatabaseManager` (HikariCP DataSource lifecycle) and configuration loaders.
* **`security`**: Password hashing (SHA-256 + salt) and in-memory session token store.
* **Forbidden**: No Swing imports.

### Client Module (`client/src/thuvien/client`)
* **`view`**: Swing UI forms (`JFrame`), dialogs (`JDialog`), panels (`JPanel`), and table models.
* **`controller`**: UI action handlers that assemble `Request` objects and handle `Response` outcomes.
* **`network`**: `TCPNetworkClient` maintaining persistent socket connection, auto-injecting session tokens.
* **`session`**: `ClientSession` tracking logged-in user details and active session token.
* **`view.common`**: `AsyncWorker` (`SwingWorker`) executing socket I/O off the Swing Event Dispatch Thread (EDT).
* **Forbidden**: No `java.sql.*`, `javax.sql.*`, or database driver imports.

---

## 5. Network Rules
1. **Transport**: TCP using `Socket` and `ServerSocket`.
2. **Concurrency**: Server must support multiple simultaneous clients using an `ExecutorService` fixed thread pool (default 20 workers). Never spawn unbounded threads per connection.
3. **Protocol Envelopes**:
   * Every request must have: `requestId` (UUID), `action` (Enum), `token` (String), `payload` (Object).
   * Every response must have: `requestId` (UUID), `statusCode` (int), `message` (String), `data` (Object).
4. **Timeouts**: Socket read timeout (`setSoTimeout`) must be configured (server: 30s, client: 10s).
5. **Disconnection**: Client handlers must handle EOF and socket exceptions gracefully without terminating the server.
6. **Error Handling**: Malformed or unexpected payloads must yield a `400 BAD_REQUEST` response without crashing worker threads.
7. **Logging**: Log client connection, client disconnection, action type, request latency, and network exceptions. Never log passwords or session secrets.

---

## 6. Database Rules
1. **Engine**: MySQL with InnoDB tables for transaction and foreign key support.
2. **SQL Execution**: **All** queries must use `PreparedStatement`. Never concatenate raw input into SQL strings.
3. **Connection Management**: All database access goes through `DatabaseManager` (HikariCP). Do not create raw connections outside the pool.
4. **Resource Cleanup**: Always close `ResultSet`, `PreparedStatement`, and `Connection` using try-with-resources.
5. **Query Performance**:
   * Avoid `SELECT *`. Select only required columns.
   * Avoid N+1 query patterns; use SQL JOINs when retrieving relational graphs.
   * Paginate large catalogs and audit tables using `LIMIT ? OFFSET ?`.
   * Add B-Tree indexes on searched and foreign key columns (`isbn`, `title`, `student_code`, `status`, `due_date`).

---

## 7. Java Coding Rules
1. **Compatibility**: Target Java 1.8. Avoid APIs introduced in Java 9 or later.
2. **Naming Conventions**:
   * Classes/Interfaces: `PascalCase` (`BorrowService`, `BookRepository`)
   * Methods/Variables: `camelCase` (`calculateFine()`, `availableCopies`)
   * Constants: `UPPER_SNAKE_CASE` (`MAX_BORROW_LIMIT`, `DEFAULT_DAILY_RATE`)
3. **Type Safety**: Avoid raw types. Use explicit generics (`List<BookDTO>`, `Map<String, UserSessionDTO>`).
4. **Enums**: Use enums for fixed business states (`BookStatus`, `UserRole`, `BorrowStatus`). Never use magic strings.
5. **Exception Handling**: Never silently suppress exceptions (`catch (Exception e) {}`). Throw domain exceptions and translate to protocol error responses.
6. **Simplicity**: Favor clean, understandable OOP. Avoid over-engineering, unnecessary design patterns, or deep inheritance hierarchies.

---

## 8. Security Rules
1. **Passwords**: Never store passwords as plaintext. Passwords must be hashed with a cryptographic hash and salt (e.g. SHA-256 or BCrypt).
2. **Credentials Isolation**: Database credentials (`db.properties`) must remain exclusively on the server and must never be exposed or sent to clients.
3. **Input Validation**: Validate format, length, nullability, and numeric ranges on the server side prior to processing.
4. **Authentication & Authorization**:
   * Server validates session tokens on every action except `LOGIN`.
   * Enforce role-based access control (`ADMIN`, `LIBRARIAN`, `STUDENT`) in `RequestRouter` on the server.
5. **SQL Injection Prevention**: Prevent SQL injection entirely through strict `PreparedStatement` parameter binding.

---

## 9. Concurrency Rules

### Atomic Borrow Transaction
Borrowing a book copy must follow an atomic server-side transaction:
1. Validate Student existence and check `status == ACTIVE`.
2. Validate Book existence and verify `status == AVAILABLE`.
3. Check availability with row lock (`FOR UPDATE` or atomic decrement `UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`).
4. Check student borrow limit (`current_borrow_count < max_borrow_limit`).
5. Open transaction (`connection.setAutoCommit(false)`).
6. Create `borrow_records` entry.
7. Increment `students.current_borrow_count`.
8. Commit transaction (Rollback on any failure).

*Strict Guarantee*: Concurrent borrowing of the final available copy must never produce negative `available_copies`.

### Atomic Return Transaction
1. Locate active `borrow_records` row.
2. Verify borrower matches student record.
3. Calculate overdue duration and fine amount.
4. If overdue, insert `fines` record.
5. Update `borrow_records` status to `RETURNED` and set `return_date`.
6. Increment `books.available_copies` and decrement `students.current_borrow_count`.
7. Commit transaction (Rollback on any failure).

---

## 10. Performance Rules
1. **Swing EDT Protection**: Never perform network socket I/O or database operations on the Swing Event Dispatch Thread (EDT). Use `AsyncWorker` (`SwingWorker`).
2. **Connection Pooling**: Use HikariCP with bounded pool sizing (10 max, 2 min idle) and statement caching.
3. **Thread Pooling**: Use a bounded `ExecutorService` (20 threads) to prevent server thread exhaustion.
4. **Pagination**: Implement pagination (`PageRequestDTO`, `PageResponseDTO`) for book search and audit log views.
5. **Monitoring**: Track request durations in milliseconds and log slow requests exceeding 500ms.

---

## 11. Testing Rules
1. Every critical Service and Network component must have JUnit 4 unit tests.
2. **Priority Test Suites**:
   * `ProtocolTest`: Verify serialization and deserialization of protocol envelopes.
   * `RequestRouterTest`: Verify authentication guards and role-based action access.
   * `BorrowServiceTest`: Verify successful borrow, borrow limit exceedance, unavailable book rejection, and transaction rollback.
   * `ReturnServiceTest`: Verify on-time return, overdue fine computation, and inventory restoration.
   * `FineServiceTest`: Verify overdue calculation mathematics and payment state updates.
3. Keep unit tests isolated from live database instances where possible, using mocks or test-specific fixtures.

---

## 12. NetBeans / Ant Rules
1. Do not remove or alter NetBeans project metadata in `nbproject/`.
2. Preserve `build.xml` and its import of `nbproject/build-impl.xml`.
3. Ensure the project compiles cleanly using:
   ```bash
   ant clean compile jar test
   ```
4. Place external runtime and test libraries into `lib/` and reference them via `nbproject/project.properties`.

---

## 13. AI Coding-Agent Rules
* **Read First**: Always read relevant documentation (`AGENTS.md`, `.agent/rules/*`, `docs/*`) before modifying code.
* **Targeted Changes**: Do not scan the entire repository unnecessarily. Only touch files directly related to the active task.
* **No Unrelated Modifications**: Do not reformat, refactor, or rewrite unrelated files.
* **No Class Duplication**: Inspect existing packages before creating any new class, DTO, or utility.
* **No External Code Copying**: Do not copy source code from external GitHub repositories. External repositories are reference material only.
* **Dependency Discipline**: Do not introduce new libraries or JARs without explicit necessity and user consent.
* **Compile After Changes**: Always execute `ant compile` after significant edits.
* **Test Verification**: Run relevant JUnit tests via `ant test` to verify functionality.
* **Transparency**: Report all modified/created files and call out any unresolved issues or assumptions.

---

## 14. Definition of Done
A task is complete only when:
* Implementation satisfies all task-specific requirements.
* Code compiles cleanly with Ant and Java 8 (`ant compile`).
* All relevant automated tests pass (`ant test`).
* No architectural rules or module boundaries are violated (e.g., client never accesses MySQL).
* Database operations use `PreparedStatement` and connection pooling.
* No sensitive credentials or passwords are hardcoded or logged.
* Changes are documented and summarized clearly for the user.
