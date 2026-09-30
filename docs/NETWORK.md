# Networking Architecture & Protocol Specification

## 1. TCP Connection Lifecycle

The networking layer utilizes persistent Java TCP Sockets (`java.net.Socket` and `java.net.ServerSocket`).

```
Swing Client                                           Library Server
    |                                                        |
    |---- 1. TCP SYN / Connect (Port 9999) ----------------->|
    |                                                        |-- 2. ServerSocket.accept()
    |                                                        |-- 3. Assign to ExecutorService
    |<--- 4. Established Connection (Object Streams) --------|
    |                                                        |
    |---- 5. Send Request (Action: PING) ------------------->|
    |                                                        |-- 6. RequestRouter responds PONG
    |<--- 7. Send Response (200 OK: "PONG") -----------------|
    |                                                        |
    |---- 8. Send Request (Action: LOGIN) ------------------>|
    |                                                        |-- 9. RequestRouter validates
    |                                                        |-- 10. AuthService authenticates
    |<--- 11. Send Response (Token Generated) ---------------|
    |                                                        |
    |---- 12. Send Request (Action: BORROW_BOOK + Token) --->|
    |                                                        |-- 13. RequestRouter verifies Token
    |                                                        |-- 14. BorrowService executes TX
    |<--- 15. Send Response (BorrowRecord DTO) --------------|
    |                                                        |
    |---- 16. Socket Close / Disconnect -------------------->|
    |                                                        |-- 17. Catch EOF / Release Worker
```

---

## 2. Client Connection Handling
* The client initializes `TCPNetworkClient` pointing to the host and port defined in `client.properties` (default `127.0.0.1:9999`, configurable in `LoginForm` or via `-Dserver.host=<IP> -Dserver.port=9999`).
* Socket timeout is set to `10,000 ms` to avoid locking the UI if network drops.
* A persistent stream is maintained to minimize socket handshake overhead during consecutive user actions.
* In the event of a connection break, `TCPNetworkClient` automatically cleans up dead socket handles and re-establishes connectivity on the next user action.

---

## 3. Server Connection & Thread Pool Architecture
* `LibraryServer` binds to `0.0.0.0` on TCP port `9999` by default (`server.properties`).
* An `ExecutorService` thread pool with 20 pre-allocated worker threads processes incoming client sockets.
* When a client connects:
  1. `ServerSocket.accept()` returns a connected `Socket`.
  2. The socket timeout is configured: `socket.setSoTimeout(30000)`.
  3. A new `ClientHandler` runnable is submitted to the thread pool.
  4. The worker thread reads serialized `Request` envelopes in a persistent loop until the client disconnects or times out.

---

## 4. Request / Response Protocol Specification

Every message transmitted over the TCP stream follows a structured envelope.

### Request Message Structure
* `requestId` (String): Unique transaction tracking identifier (UUID).
* `action` (Enum): Name of the operation requested from `Action`.
* `token` (String): Active session token generated during `LOGIN`. Optional only during authentication.
* `payload` (Object): Specific DTO payload required for the operation.

#### Example Request Message:
```json
{
  "requestId": "REQ-10001",
  "action": "BORROW_BOOK",
  "token": "d7b4c8a2-3f1e-4b9a-8c7d-9e0f1a2b3c4d",
  "payload": {
    "studentId": 102,
    "bookId": 501,
    "durationDays": 14,
    "notes": "Course textbook loan"
  }
}
```

### Response Message Structure
* `requestId` (String): Matches the `requestId` of the originating request.
* `statusCode` (int): Standard status code (200, 201, 400, 401, 403, 404, 409, 500).
* `message` (String): User-friendly summary message.
* `data` (Object): Operation result payload (DTO, list, or null).

#### Example Response Message:
```json
{
  "requestId": "REQ-10001",
  "statusCode": 200,
  "message": "Book borrowed successfully",
  "data": {
    "id": 9001,
    "studentId": 102,
    "bookId": 501,
    "borrowDate": "2026-09-20",
    "dueDate": "2026-10-04",
    "status": "ACTIVE"
  }
}
```

#### Example Error Response Message:
```json
{
  "requestId": "REQ-10002",
  "statusCode": 400,
  "message": "Student has reached the maximum borrowing limit of 5 books.",
  "data": null
}
```

---

## 5. Resilience & Edge Cases

### Inactivity Timeout
* If a connected client sends no requests for 30 consecutive seconds, `ClientHandler` catches `SocketTimeoutException`, closes the socket, logs the event, and releases the thread back to the pool.

### Abrupt Disconnect
* If a client machine powers off or loses Wi-Fi connection, `ClientHandler` encounters an `EOFException` or `SocketException`. The handler exits cleanly without crashing the server.

### Malformed Request Handling
* If a client transmits non-serialized data, corrupt bytes, or objects not matching the `Request` structure:
  1. The server catches `ClassNotFoundException` or deserialization errors.
  2. Sends an error response with `400 BAD_REQUEST`.
  3. Resets the stream or closes the malformed connection safely.

---

## 6. Concurrent Clients
* The system comfortably sustains up to 20 concurrent active socket operations simultaneously.
* Socket backlog handles incoming bursts during peak periods (e.g. library opening hours).
* Business transactions (such as borrowing the last available copy of a book) remain completely isolated and concurrency-safe via database-level row locking.

---

## 7. Wi-Fi / LAN Connectivity & Windows Firewall Configuration

### Packaging Isolated Server & Client Distributions
Run the Apache Ant packaging target to build standalone distributions for the Server machine and Client machine(s):

```bash
ant package-all
```

This creates two isolated distributions under `dist/`:
* **`dist/server-dist/`**: Contains `server.jar`, `lib/` (MySQL JDBC + HikariCP + SLF4J), `resources/` (`server.properties`, `db.properties`), and `run-server.bat`.
* **`dist/client-dist/`**: Contains `client.jar` (pure Swing + TCP client + common DTOs, **zero** JDBC drivers or `db.properties`), `resources/` (`client.properties`), and `run-client.bat`.

### Server Setup (Wi-Fi / LAN Host)
1. Ensure MySQL (e.g., Laragon / MySQL 5.7+ / 8.0+) is running on the Server machine and `resources/db.properties` is configured.
2. Run `run-server.bat` inside `dist/server-dist/` (or `ant run-server`).
3. The server binds to `0.0.0.0:9999` and prints all active non-loopback IPv4 addresses on startup:
   ```text
   Server started
   Port: 9999
   Available IPv4 addresses:
   - 192.168.1.100
   ```
4. Allow inbound TCP port `9999` on Windows Firewall (do **not** disable Windows Firewall globally):
   ```cmd
   netsh advfirewall firewall add rule name="School Library Server TCP 9999" dir=in action=allow protocol=TCP localport=9999
   ```
   Or via PowerShell (Administrator):
   ```powershell
   New-NetFirewallRule -DisplayName "School Library Server TCP 9999" -Direction Inbound -Protocol TCP -LocalPort 9999 -Action Allow -Profile Private,Domain
   ```

### Client Setup (Remote Machine on Same Wi-Fi / LAN)
1. Copy `dist/client-dist/` to the Client machine.
2. Launch the Client using `run-client.bat`:
   ```cmd
   run-client.bat
   ```
   Or pass the Server IP and Port directly as command-line arguments:
   ```cmd
   run-client.bat 192.168.1.100 9999
   ```
   *(Alternatively, edit `resources/client.properties` with `server.host=192.168.1.100` and `server.port=9999`, or enter **Server IP** and **Port** directly in `LoginForm`).*
3. Click **"Kiểm tra kết nối"** in `LoginForm` to execute an unauthenticated `PING -> PONG` check over TCP (`"Server connected"`), then log in.

---

## 8. Two-Machine LAN Deployment Checklist & Troubleshooting

### Port Connectivity Diagnostic (From Client Machine)
Before logging in from a second laptop, test TCP port reachability in PowerShell:
```powershell
Test-NetConnection 192.168.1.100 -Port 9999
```
* **Expected**: `TcpTestSucceeded : True`
* **If `TcpTestSucceeded : False`**, diagnose in this order:
  1. **Server not running**: Verify `run-server.bat` is active on the Server machine and printed `Port: 9999`.
  2. **Firewall blocking**: Ensure inbound rule for TCP `9999` is enabled for the active network profile (Private/Domain/Public).
  3. **Wrong Server IP**: Verify the IPv4 address printed by `ServerMain` matches the IP entered on the Client.
  4. **Different subnet / VLAN**: Confirm both machines are connected to the same Wi-Fi SSID/LAN subnet.
  5. **Wi-Fi AP Isolation**: Public/guest Wi-Fi routers may block peer-to-peer client communication (AP isolation). Use a private LAN or mobile hotspot if guest Wi-Fi blocks local peers.


