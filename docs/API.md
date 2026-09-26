# NETWORK PROTOCOL SPECIFICATION

## 1. Overview
The Remote School Library Management System uses a custom TCP Socket Request/Response protocol. All requests and responses are encapsulated in standard serialized envelopes containing operation identifiers, authorization tokens, and payloads.

---

## 2. Envelope Formats

### Request Envelope (`thuvien.common.protocol.Request`)
```json
{
  "requestId": "REQ-10001",
  "action": "ACTION_NAME",
  "token": "SESSION-TOKEN-UUID",
  "payload": { ... }
}
```

### Success Response Envelope (`thuvien.common.protocol.Response`)
```json
{
  "requestId": "REQ-10001",
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

### Error Response Envelope
```json
{
  "requestId": "REQ-10001",
  "statusCode": 400,
  "message": "Detailed error explanation",
  "data": null
}
```

---

## 3. Protocol Actions Catalog

### Authentication Module

#### `AUTH_LOGIN`
* **Auth Requirement**: None (Anonymous)
* **Allowed Roles**: Any
* **Request Payload**:
  ```json
  {
    "username": "admin",
    "password": "password123"
  }
  ```
* **Success Response Data (`UserSessionDTO`)**:
  ```json
  {
    "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": 1,
    "username": "admin",
    "fullName": "System Administrator",
    "role": "ADMIN"
  }
  ```
* **Errors**: `401 UNAUTHORIZED` on invalid credentials or inactive account.

#### `AUTH_LOGOUT`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`, `STUDENT`
* **Request Payload**: `null`
* **Success Response Data**: `null`

---

### Book Catalog Module

#### `BOOK_LIST`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`, `STUDENT`
* **Request Payload**: `null`
* **Success Response Data**: `List<BookDTO>`

#### `BOOK_SEARCH`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`, `STUDENT`
* **Request Payload (`BookSearchCriteriaDTO`)**:
  ```json
  {
    "keyword": "Networking",
    "categoryId": 1,
    "authorId": null,
    "status": "AVAILABLE",
    "page": 1,
    "pageSize": 20
  }
  ```
* **Success Response Data**: `PageResponseDTO<BookDTO>`

#### `BOOK_CREATE`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload (`BookDTO`)**:
  ```json
  {
    "isbn": "978-0132126953",
    "title": "Computer Networks",
    "categoryId": 1,
    "publisher": "Pearson",
    "publishYear": 2011,
    "totalCopies": 5,
    "shelfLocation": "CS-01-A"
  }
  ```
* **Success Response Data**: Newly created Book ID (`Long`).
* **Errors**: `409 CONFLICT` if ISBN already exists.

#### `BOOK_UPDATE`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload**: `BookDTO` with existing `id`.
* **Success Response Data**: `Boolean` (`true`).

#### `BOOK_DELETE`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`
* **Request Payload**: Book ID (`Long`).
* **Success Response Data**: `Boolean` (`true`).
* **Errors**: `400 BAD_REQUEST` if book currently has active borrow records.

---

### Student Module

#### `STUDENT_LIST`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload**: `null`
* **Success Response Data**: `List<StudentDTO>`

#### `STUDENT_SEARCH`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload**: Search keyword string (matches student code or full name).
* **Success Response Data**: `List<StudentDTO>`

---

### Circulation (Borrow & Return) Module

#### `BORROW_BOOK`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload (`BorrowRequestDTO`)**:
  ```json
  {
    "studentId": 102,
    "bookId": 501,
    "durationDays": 14,
    "notes": "Standard 14-day checkout"
  }
  ```
* **Success Response Data (`BorrowRecordDTO`)**:
  ```json
  {
    "id": 9001,
    "studentId": 102,
    "bookId": 501,
    "borrowDate": "2026-09-20",
    "dueDate": "2026-10-04",
    "status": "ACTIVE"
  }
  ```
* **Errors**:
  * `400 BAD_REQUEST`: Student borrowing limit exceeded or student suspended.
  * `400 BAD_REQUEST`: Book unavailable (0 copies).

#### `RETURN_BOOK`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload**:
  ```json
  {
    "studentId": 102,
    "bookId": 501
  }
  ```
* **Success Response Data (`ReturnResultDTO`)**:
  ```json
  {
    "borrowRecordId": 9001,
    "bookId": 501,
    "studentId": 102,
    "overdue": false,
    "overdueDays": 0,
    "fineAmount": 0.00,
    "message": "Returned on time."
  }
  ```

---

### Fines & Reservations Module

#### `FINE_LIST`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`
* **Request Payload**: `null` (or filter criteria)
* **Success Response Data**: `List<FineDTO>`

#### `RESERVATION_CREATE`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`, `STUDENT`
* **Request Payload**: `{ "studentId": 102, "bookId": 501 }`
* **Success Response Data**: `ReservationDTO`

#### `RESERVATION_CANCEL`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`, `LIBRARIAN`, `STUDENT`
* **Request Payload**: Reservation ID (`Long`).
* **Success Response Data**: `Boolean` (`true`).

---

### Administration Module

#### `USER_LIST`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`
* **Request Payload**: `null`
* **Success Response Data**: `List<UserDTO>`

#### `LIST_AUDIT_LOGS`
* **Auth Requirement**: Valid session token
* **Allowed Roles**: `ADMIN`
* **Request Payload**: Limit (`Integer`, default 50)
* **Success Response Data**: `List<AuditLogDTO>`

