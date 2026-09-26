# Performance Targets & Measurement Methodology

## 1. Measurable Performance Targets

The following targets represent the performance goals for the Remote School Library Management System under normal operational load:

| Metric | Target Value | Condition |
| :--- | :--- | :--- |
| **Average Request Latency** | $< 50\text{ ms}$ | Local LAN connection, standard catalog lookups |
| **P99 Request Latency** | $< 200\text{ ms}$ | Peak concurrent load, multi-table transactions |
| **Concurrent Active Clients** | $20\text{ simultaneous sessions}$ | Handled by fixed `ExecutorService` thread pool |
| **Database Pool Size** | 10 connections max | Serves 20 worker threads via HikariCP |
| **Database Query Execution Time** | $< 15\text{ ms}$ | Indexed queries (`isbn`, `title`, `student_code`) |
| **Max Page Size** | $\le 50\text{ items}$ | Enforced across all search endpoints |
| **Client JVM Heap Footprint** | $< 128\text{ MB}$ | Idle or active Swing UI browsing |
| **Server JVM Heap Footprint** | $< 256\text{ MB}$ | Active with 20 connected client sockets |
| **Logging Overhead** | $< 2\text{ ms}$ per request | Non-blocking `java.util.logging` formatted output |

---

## 2. Measurement Methodology

### Request Latency Measurement
* **Location**: Measured at the boundaries of `ClientHandler` on the server and `TCPNetworkClient` on the client.
* **Methodology**:
  ```java
  long start = System.nanoTime();
  Response response = router.route(request);
  long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
  ```
* Requests exceeding $500\text{ ms}$ trigger a warning log with the action name and request ID.

### Concurrency Stress Testing
* **Methodology**:
  1. A standalone benchmark harness in `test/` spawns $N$ concurrent client threads (from 5 to 30 threads).
  2. Each thread repeatedly executes a mix of read operations (`BOOK_SEARCH`) and write operations (`BORROW_BOOK`).
  3. Latencies are recorded in a histogram to determine P50, P90, and P99 metrics.
  4. Database connection pool saturation is observed via `HikariPoolMXBean`.

### Query Performance Profiling
* **Methodology**:
  * Execute MySQL `EXPLAIN` on all queries defined in `server/repository/` to confirm proper B-Tree index utilization (`type: ref` or `type: eq_ref`).
  * Verify that no query results in a full table scan (`type: ALL`) on catalogs exceeding 1,000 rows.

### Memory Leak Verification
* **Methodology**:
  * Verify that closing a client socket properly dereferences `ClientHandler`, releasing object streams.
  * Verify that repeated borrow/return cycles do not accumulate uncollected objects in `SessionManager`.
  * Profile using standard JVM tools (`jcmd`, `jconsole`, or `VisualVM`) targeting Java 8.
