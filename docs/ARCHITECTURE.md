# System Architecture Blueprint: Remote School Library Management System

## 1. System Goals
The **Remote School Library Management System** is a distributed, 3-tier desktop network application designed for university network programming education. The primary engineering goals are:
* **True Network Decoupling**: Eliminate direct client-to-database connections by placing a TCP application server between the desktop UI and MySQL.
* **ACID Transaction Integrity**: Ensure atomic guarantees for multi-table operations (borrowing, returns, fine generation) under concurrent client access.
* **Role-Based Access Control**: Provide distinct privileges for Administrators, Librarians, and Students.
* **Swing GUI Responsiveness**: Guarantee that network I/O and server latency never block or freeze the client Event Dispatch Thread (EDT).
* **Maintainability & Portability**: Adhere to clean layered architecture, Java 8 standards, and NetBeans/Apache Ant build systems.

---

## 2. Client/Server Architecture
The system operates on a dedicated client-server network topology:
* **Client Tier**: A standalone desktop Java Swing application. Handles UI rendering, input capture, session storage, and socket communication via `TCPNetworkClient`.
* **Application Server Tier**: A standalone Java daemon listening on TCP port `8888`. Houses connection pooling (`ExecutorService`), request routing, security verification, business rules, and JDBC persistence.
* **Database Tier**: MySQL server hosting the normalized InnoDB relational schema. Accessible strictly by the application server tier.

---

## 3. Layer Diagram

```
+-------------------------------------------------------------+
|                     JAVA SWING CLIENT                       |
|                                                             |
|   +-----------------------------------------------------+   |
|   |             View Layer (Swing GUI)                  |   |
|   |     LoginForm, MainDashboardForm, Feature Panels    |   |
|   +--------------------------+--------------------------+   |
|                              | (UI Events)                  |
|   +--------------------------v--------------------------+   |
|   |         Client Controllers & AsyncWorker            |   |
|   |    ClientAuthController, ClientBookController, etc. |   |
|   +--------------------------+--------------------------+   |
|                              | (Request Assembly)           |
|   +--------------------------v--------------------------+   |
|   |         NetworkClient (TCP Socket Client)           |   |
|   |     Socket connection, Session Token Injection      |   |
|   +--------------------------+--------------------------+   |
+------------------------------|------------------------------+
                               |
                               | TCP Socket Stream (Port 8888)
                               | [ Request / Response Protocol ]
                               |
+------------------------------v------------------------------+
|                    JAVA LIBRARY SERVER                      |
|                                                             |
|   +-----------------------------------------------------+   |
|   |      Network Listener (ServerSocket + ThreadPool)   |   |
|   |          LibraryServer, ClientHandler Worker        |   |
|   +--------------------------+--------------------------+   |
|                              | (Raw Request Envelope)       |
|   +--------------------------v--------------------------+   |
|   |          RequestRouter & Security Verification      |   |
|   |     Token Check, Role-Based Authorization Guard     |   |
|   +--------------------------+--------------------------+   |
|                              | (Dispatched Action)          |
|   +--------------------------v--------------------------+   |
|   |                 Server Controllers                  |   |
|   +--------------------------+--------------------------+   |
|                              | (DTOs)                       |
|   +--------------------------v--------------------------+   |
|   |                    Service Layer                    |   |
|   |   AuthService, BorrowService, ReturnService, etc.   |   |
|   |   (Business Rules & Transaction Demarcation)        |   |
|   +--------------------------+--------------------------+   |
|                              | (Connection / Entity)        |
|   +--------------------------v--------------------------+   |
|   |                  Repository Layer                   |   |
|   |    BookRepository, BorrowRepository, UserRepository |   |
|   |    (PreparedStatement SQL Execution & Mapping)      |   |
|   +--------------------------+--------------------------+   |
|                              |                              |
|   +--------------------------v--------------------------+   |
|   |             HikariCP Connection Pool                |   |
|   +-----------------------------------------------------+   |
+------------------------------|------------------------------+
                               | JDBC Connection
+------------------------------v------------------------------+
|                     MySQL Database                          |
|                 InnoDB Storage Engine                       |
+-------------------------------------------------------------+
```

---

## 4. Package Structure

```
thuvien
├── common                           // Shared protocol & data models
│   ├── protocol                     // Request, Response, Action, StatusCode
│   ├── dto                          // Serializable Transfer Objects
│   ├── enums                        // UserRole, BookStatus, BorrowStatus, etc.
│   └── exception                    // LibraryException, NetworkException, etc.
├── server                           // Central Library TCP Server
│   ├── network                      // LibraryServer, ClientHandler
│   ├── router                       // RequestRouter
│   ├── security                     // SessionManager, PasswordHasher
│   ├── database                     // DatabaseManager (HikariCP), DatabaseConfig
│   ├── repository                   // Repositories & SQL mappers
│   └── service                      // Business logic & atomic transactions
└── client                           // Desktop Java Swing Client
    ├── network                      // NetworkClient, TCPNetworkClient
    ├── session                      // ClientSession
    ├── controller                   // UI Action Controllers
    └── view                         // Forms, Dialogs, Panels, AsyncWorker
```

---

## 5. Responsibility of Each Module

| Module | Core Responsibilities | Strict Prohibitions |
| :--- | :--- | :--- |
| **`common`** | Protocol definitions (`Request`, `Response`), action identifiers, DTO contracts, enums, shared exceptions. | Zero Swing components; zero database/JDBC dependencies. |
| **`server`** | TCP `ServerSocket`, thread pool concurrency, session token tracking, role-based authorization, transaction management, SQL execution via HikariCP. | Zero Swing UI components. |
| **`client`** | User interaction, Swing views, input validation, non-blocking network calls via `AsyncWorker`, session token retention. | Zero JDBC, zero SQL, zero database driver dependencies. |
| **`database`** | Relational data persistence, foreign key cascading, row locking, integrity constraints. | Zero direct client network access. |

---

## 6. Data Flow

```
[User Action in Swing View]
            ↓
[Controller validates format & dispatches via AsyncWorker]
            ↓
[NetworkClient attaches session token and serializes Request]
            ↓ (TCP Socket)
[ClientHandler receives bytes, deserializes Request]
            ↓
[RequestRouter verifies session token & checks role permission]
            ↓
[Server Controller extracts payload & calls Service]
            ↓
[Service executes business checks & opens DB transaction]
            ↓
[Repository runs PreparedStatement queries via HikariCP]
            ↓
[MySQL executes query and returns ResultSet]
            ↓
[Repository maps ResultSet to DTO; Service commits transaction]
            ↓
[Server Controller wraps result into Response.ok(...)]
            ↓ (TCP Socket)
[NetworkClient receives Response and returns to AsyncWorker]
            ↓ (SwingUtilities.invokeLater)
[View updates UI components safely on the EDT]
```

---

## 7. Authentication Flow

1. User enters username and password in `LoginForm`.
2. `ClientAuthController` packages credentials into `LoginRequestDTO` and sends `Action.LOGIN`.
3. `RequestRouter` recognizes `LOGIN` as an unauthenticated action and bypasses token checks.
4. `AuthService` verifies credentials against the stored cryptographic hash (`PasswordHasher`).
5. If valid, `SessionManager` generates a cryptographically random session token (UUID), registers the active session, and returns `UserSessionDTO`.
6. Client stores the token in `ClientSession`. All subsequent requests automatically include this token in the `Request` header.
7. `LoginForm` disposes and launches `MainDashboardForm` configured for the user's role.

---

## 8. Borrow Flow (Atomic Transaction)

1. Librarian inputs `studentId` and `bookId` into `BorrowReturnPanel`.
2. Client sends `Action.BORROW_BOOK` with `BorrowRequestDTO`.
3. `RequestRouter` verifies token and checks caller has role `ADMIN` or `LIBRARIAN`.
4. `BorrowService` executes the atomic borrow procedure:
   * Validates student existence and ensures student status is `ACTIVE`.
   * Checks student borrowing limit: `current_borrow_count < max_borrow_limit`.
   * Checks book availability: `available_copies > 0` and status `AVAILABLE`.
   * Starts database transaction (`connection.setAutoCommit(false)`).
   * Atomically decrements `available_copies`:
     ```sql
     UPDATE books SET available_copies = available_copies - 1
     WHERE id = ? AND available_copies > 0;
     ```
   * Inserts row into `borrow_records` (sets `due_date = NOW() + 14 days`).
   * Increments `students.current_borrow_count`.
   * Commits transaction (`connection.commit()`).
5. Server responds with `200 OK` and `BorrowRecordDTO`.
6. Client updates the catalog table and clears the borrow form.

---

## 9. Return Flow (Atomic Transaction)

1. Librarian submits return request with `studentId` and `bookId`.
2. Client sends `Action.RETURN_BOOK`.
3. `ReturnService` executes the atomic return procedure:
   * Finds active borrow record (`status = 'ACTIVE'`).
   * Verifies borrower identity.
   * Compares `return_date` against `due_date` to calculate overdue days.
   * If overdue: computes fine ($\text{days} \times 5,000 \text{ VND}$) and creates record in `fines`.
   * Updates `borrow_records` status to `RETURNED` and stamps `return_date`.
   * Atomically increments `books.available_copies`.
   * Decrements `students.current_borrow_count`.
   * Commits transaction (`connection.commit()`).
4. Server responds with `ReturnResultDTO` containing overdue days and fine amount.
5. Client displays return confirmation and prompts for fine payment if overdue.

---

## 10. Error Flow

* **Client Connection Failure**: If the server is unreachable, `TCPNetworkClient` catches `IOException` and throws `NetworkException`. The UI displays a friendly connection error dialog without crashing.
* **Authentication / Authorization Failure**: If a token is missing, expired, or the user lacks role permissions, `RequestRouter` halts processing immediately and returns `401 UNAUTHORIZED` or `403 FORBIDDEN`.
* **Business Rule Violations**: When business constraints fail (e.g. `BorrowLimitExceededException`), Services throw domain exceptions. The router translates them to `Response.error(reqId, StatusCode.BAD_REQUEST, e.getMessage())`.
* **Database / Server Failures**: Uncaught database exceptions rollback active transactions and return `500 SERVER_ERROR`. Detailed technical stack traces are logged on the server and shielded from clients.

---

## 11. Concurrency Model

* **Multi-Client Threading**: Server handles concurrent clients through a fixed pool of 20 worker threads (`ExecutorService`).
* **Database Connection Pooling**: 10 HikariCP connections serve the 20 worker threads efficiently because database operations are short-lived.
* **Race Condition Prevention on Final Copy**:
  * If two clients simultaneously attempt to borrow the last remaining copy of a book (`available_copies = 1`), both trigger atomic conditional updates:
    ```sql
    UPDATE books SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0;
    ```
  * MySQL InnoDB row-level locking guarantees that exactly one transaction updates the row (returns `affectedRows = 1`).
  * The second transaction finds `available_copies = 0` (returns `affectedRows = 0`), fails the availability check, rolls back, and returns `BookUnavailableException`.
  * The inventory count can never drop below zero.

---

## 12. Deployment Model

* **Server Deployment**:
  * Deployed on a server host (or localhost for development).
  * Requires Java 8 JRE and accessible MySQL instance.
  * Executed via:
    ```bash
    java -cp "dist/thuvien.jar;dist/lib/*" thuvien.server.ServerMain
    ```
* **Client Deployment**:
  * Deployed on librarian or student workstations.
  * Configured via `client.properties` pointing to server IP and port.
  * Executed via:
    ```bash
    java -cp "dist/thuvien.jar;dist/lib/*" thuvien.client.ClientMain
    ```
  * Requires zero database drivers or direct MySQL network access.
