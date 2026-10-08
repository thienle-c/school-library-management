# Networking Architecture & Java RMI Specification

## 1. Java RMI Architecture Overview

The communication architecture of the Remote School Library Management System is built on **Java Remote Method Invocation (Java RMI)** and **Java Object Serialization**:

```
Java Swing Client
       |
       |  Java RMI (Registry Port 1099, Service: LibraryRemoteService)
       v
LibraryRemoteServiceImpl (UnicastRemoteObject)
       |
       +--> RequestRouter (Authentication, RBAC, Account Lockout)
       |
       +--> Service Layer (Atomic Concurrency Locking & Business Rules)
       |
       +--> Repository Layer (PreparedStatements & HikariCP Pool)
       |
       v
     MySQL (InnoDB)
```

### Key RMI Architectural Principles:
1. **Remote Interface (`LibraryRemoteService`)**: Located in `thuvien.common.rmi`, extends `java.rmi.Remote`, with all remote methods declaring `throws RemoteException`.
2. **Remote Implementation (`LibraryRemoteServiceImpl`)**: Located in `thuvien.server.rmi`, extends `UnicastRemoteObject`, implementing the full remote API.
3. **RMI Registry**: Initialized by `LibraryRMIServer` via `LocateRegistry.createRegistry(1099)`.
4. **Client Adapter (`RMIClient`)**: Implements `NetworkClient`, looks up the remote service via `LocateRegistry.getRegistry(host, port).lookup("LibraryRemoteService")`, and invokes methods transparently.
5. **Java Serialization**: All DTOs, Requests, Responses, and Exceptions transferred over RMI implement `java.io.Serializable` with explicit `serialVersionUID`.
6. **Strict Database Isolation**: The client package never connects to MySQL and does not contain JDBC drivers or server classes.

---

## 2. Core Concurrency Guarantee: Single-Copy Double Borrowing Prevention

In accordance with course requirements:
> **"Server ensures two clients cannot borrow the same book copy simultaneously."**

### Server-side Atomic Transaction:
1. Client A and Client B concurrently invoke `borrowBook(token, request)` over RMI for the same `bookId` when `available_copies = 1`.
2. Both calls invoke `BorrowServiceImpl.borrowBook(...)` on separate worker threads.
3. The server starts an atomic MySQL transaction with `connection.setAutoCommit(false)`.
4. Concurrency is enforced at the database level with atomic conditional row update:
   ```sql
   UPDATE books
   SET available_copies = available_copies - 1,
       status = CASE WHEN (available_copies - 1) = 0 THEN 'BORROWED' ELSE status END
   WHERE id = ? AND available_copies > 0;
   ```
5. **Outcome**:
   - The first client's update succeeds (`rows affected == 1`), increments student borrow counter, writes borrow record, and commits.
   - The competing client's update returns `0 rows affected`.
   - The competing client transaction immediately rolls back and throws `BookUnavailableException` ("No copies available for book...").
   - `available_copies` remains exactly `0` and never drops below zero.
   - Verified by automated JUnit 4 test: `RMIEndToEndIntegrationTest.test13_ConcurrentBorrowSameBookOverRMI`.

---

## 3. Remote Interface & Method Catalog

The remote interface `thuvien.common.rmi.LibraryRemoteService` provides:

* `String ping() throws RemoteException`: Connection diagnostic.
* `UserSessionDTO login(LoginRequestDTO request) throws RemoteException, AuthenticationException, LibraryException`: Authenticate and obtain session token.
* `void logout(String token) throws RemoteException`: Invalidate token and clear session.
* `UserSessionDTO getCurrentUser(String token) throws RemoteException, ...`: Verify current session.
* `PageResponseDTO<BookDTO> searchBooks(String token, BookSearchCriteriaDTO criteria) throws RemoteException, ...`: Search book catalog with pagination.
* `BookDTO getBook(String token, Long id) throws RemoteException, ...`: Fetch single book details.
* `BorrowRecordDTO borrowBook(String token, BorrowRequestDTO request) throws RemoteException, ...`: Atomic book borrowing.
* `ReturnResultDTO returnBook(String token, Long studentId, Long bookId) throws RemoteException, ...`: Atomic book return and fine calculation.
* `List<FineDTO> getFines(String token, Long studentId) throws RemoteException, ...`: Query unpaid/paid fines.
* `boolean payFine(String token, Long fineId) throws RemoteException, ...`: Process fine settlement.
* `ReservationDTO createReservation(String token, Long studentId, Long bookId) throws RemoteException, ...`: Hold book reservation.
* `boolean cancelReservation(String token, Long reservationId) throws RemoteException, ...`: Cancel pending reservation.
* `Response execute(Request request) throws RemoteException`: Generic envelope dispatcher for custom client requests.

---

## 4. Packaging & Runtime Deployment

### Server Distribution (`dist/server-dist/`)
```text
dist/server-dist/
├── server.jar             # Contains common + server classes (Main-Class: thuvien.server.rmi.LibraryRMIServer)
├── run-server.bat         # Portable launcher script (checks Java PATH, relative %~dp0 resolution)
├── resources/
│   ├── server.properties  # RMI Registry port (1099), service port (1100), service name (LibraryRemoteService)
│   ├── db.properties      # MySQL credentials (root / localhost:3306)
│   └── db.properties.example
└── lib/
    ├── HikariCP-4.0.3.jar
    ├── mysql-connector-java-8.0.33.jar
    ├── slf4j-api-1.7.36.jar
    └── slf4j-jdk14-1.7.36.jar
```

### Client Distribution (`dist/client-dist/`)
```text
dist/client-dist/
├── client.jar             # Contains common + client classes (Main-Class: thuvien.client.ClientMain)
├── run-client.bat         # Portable launcher script (accepts optional [host] [port] arguments)
├── test-connection.bat    # Quick diagnostic script to test ports 1099 and 1100
├── resources/
│   └── client.properties  # RMI host (127.0.0.1), registry port (1099), service name
└── lib/                   # Empty directory (client requires ZERO database/JDBC libraries)
```

---

## 5. Two-Machine Wi-Fi / LAN Deployment Guide

### Port Architecture
* **TCP Port 1099**: RMI Registry lookup port (`LocateRegistry.getRegistry(host, 1099)`).
* **TCP Port 1100**: RMI Remote Object fixed export port (avoids random dynamic ports blocked by firewall).
* **Server Hostname**: Auto-configured to Server's actual LAN IPv4 via `System.setProperty("java.rmi.server.hostname", serverIp)`.

### Machine 1: Server
1. Start MySQL (e.g. via Laragon or standard MySQL service).
2. Open PowerShell as Administrator and configure Windows Firewall to allow both TCP 1099 and TCP 1100:
   ```powershell
   New-NetFirewallRule -DisplayName "School Library RMI Registry" -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow
   New-NetFirewallRule -DisplayName "School Library RMI Object" -Direction Inbound -LocalPort 1100 -Protocol TCP -Action Allow
   ```
3. Connect to the local Wi-Fi / LAN network.
4. Launch the server:
   ```cmd
   dist\server-dist\run-server.bat
   ```
5. The server prints its active configuration:
   ```text
   RMI Server started
   RMI Registry port: 1099
   RMI Remote Object port: 1100
   RMI hostname: 192.168.1.100
   Service: LibraryRemoteService
   Available IPv4 addresses:
   - 192.168.1.100
   ```

### Machine 2: Client
1. Connect to the same Wi-Fi / LAN network.
2. Verify port connectivity to the Server:
   ```cmd
   dist\client-dist\test-connection.bat 192.168.1.100
   ```
   Or in PowerShell:
   ```powershell
   Test-NetConnection -ComputerName 192.168.1.100 -Port 1099
   Test-NetConnection -ComputerName 192.168.1.100 -Port 1100
   ```
3. Launch the client:
   ```cmd
   dist\client-dist\run-client.bat 192.168.1.100 1099
   ```
4. On the login screen:
   * Verify **Server IP** is `192.168.1.100` and **Port** is `1099`.
   * Click **"Kiểm tra kết nối"** (ping over RMI). The client displays `Server connected`.
   * Log in with credentials (e.g. `librarian1` / `lib123` or `student1` / `student123`).

---

## 6. Diagnostic & Connectivity Troubleshooting

### Error Matrix:

| Error Symptom | Cause | Solution |
|---|---|---|
| `Không thể kết nối tới RMI Registry tại host:1099` | Server chưa bật hoặc Cổng 1099 bị chặn | Kiểm tra server đang chạy, mở port TCP 1099 trên Firewall server |
| `Kết nối RMI Registry thành công nhưng không kết nối được Remote Object (cổng 1100)` | Cổng 1100 bị Firewall chặn | Mở port TCP 1100 trên Firewall server (`LocalPort 1100`) |
| `Lỗi hostname: Máy Client không thể phân giải địa chỉ hostname...` | `java.rmi.server.hostname` trả về `127.0.0.1` hoặc tên máy nội bộ | Khai báo `rmi.server.host=<IP_LAN>` trong `server.properties` |
| `Service 'LibraryRemoteService' không tồn tại trên RMI Registry` | Sai tên service | Kiểm tra `rmi.service.name` trong `client.properties` và `server.properties` |
