# Networking Architecture & Protocol Specification

## 1. TCP Connection Lifecycle

The networking layer utilizes persistent Java TCP Sockets (`java.net.Socket` and `java.net.ServerSocket`).

```
Swing Client                                           Library Server
    |                                                        |
    |---- 1. TCP SYN / Connect (Port 8888) ----------------->|
    |                                                        |-- 2. ServerSocket.accept()
    |                                                        |-- 3. Assign to ExecutorService
    |<--- 4. Established Connection (Object Streams) --------|
    |                                                        |
    |---- 5. Send Request (Action: LOGIN) ------------------>|
    |                                                        |-- 6. RequestRouter validates
    |                                                        |-- 7. AuthService authenticates
    |<--- 8. Send Response (Token Generated) ----------------|
    |                                                        |
    |---- 9. Send Request (Action: BORROW_BOOK + Token) ---->|
    |                                                        |-- 10. RequestRouter verifies Token
    |                                                        |-- 11. BorrowService executes TX
    |<--- 12. Send Response (BorrowRecord DTO) --------------|
    |                                                        |
    |---- 13. Socket Close / Disconnect -------------------->|
    |                                                        |-- 14. Catch EOF / Release Worker
```

---

## 2. Client Connection Handling
* The client initializes `TCPNetworkClient` pointing to the host and port defined in `client.properties`.
* Socket timeout is set to `10,000 ms` to avoid locking the UI if network drops.
* A persistent stream is maintained to minimize socket handshake overhead during consecutive user actions.
* In the event of a connection break, `TCPNetworkClient` automatically cleans up dead socket handles and re-establishes connectivity on the next user action.

---

## 3. Server Connection & Thread Pool Architecture
* `LibraryServer` binds to a dedicated TCP port (`8888` by default).
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
