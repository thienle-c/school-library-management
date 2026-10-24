# BÁO CÁO AUDIT ENTRY POINT VÀ ĐỒNG BỘ CẤU HÌNH NETBEANS RUN VỚI KIẾN TRÚC RMI
## Remote School Library Management System

**Dự án:** Remote School Library Management System  
**Thư mục làm việc:** `D:\workspace\thuvien`  
**Ngày thực hiện:** 14/10/2026  
**Chuyên viên đánh giá:** Senior Java Security Engineer & Build/Release Engineer  
**Trạng thái:** `GIAI ĐOẠN A — AUDIT ONLY` (Chỉ kiểm tra, phân tích nguyên nhân, bảo toàn nguyên trạng mã nguồn và cấu hình)

---

## 1. Tóm tắt vấn đề (Incident Summary)

Khi người dùng mở NetBeans 8.0.2 và nhấn **Run** để chạy `ServerMain.java`, cửa sổ console của NetBeans hiển thị:

```text
Starting Remote School Library Server...
LibraryServerPool - Starting...
LibraryServerPool - Start completed.
HikariCP DataSource initialized successfully.
LibraryServer listening on TCP port 9999 with 20 worker threads.
Server started
Port: 9999
Available IPv4 addresses:
- 26.122.195.188
- 10.0.107.143
Server is online and ready for client connections on 0.0.0.0:9999.
```

**Mâu thuẫn kỹ thuật:**
- Đầu ra trên cho thấy server đang mở **cổng TCP thô 9999** với `LibraryServer`.
- Tuy nhiên, kiến trúc mục tiêu của dự án là **Java RMI + Java Serialization**, sử dụng **RMI Registry cổng TCP 1099** và **RMI Remote Object cổng TCP 1100**.
- Phía Client (`thuvien.client.ClientMain` kết nối qua `RMIClient`) mặc định kết nối tới **cổng 1099**. Do đó, khi Server chạy trên cổng 9999, Client mở lên sẽ **không thể kết nối được**, báo lỗi kết nối RMI.

---

## 2. Bằng chứng mã nguồn và cấu hình (Source & Config Evidence)

### 2.1. Tệp `server/src/thuvien/server/ServerMain.java`
- **Dòng 25:** Ghi chú Javadoc: `Bootstrap entry point for the Remote School Library TCP Server.`
- **Dòng 33:** `int port = 9999;`
- **Dòng 42:** `port = Integer.parseInt(props.getProperty("server.port", "9999"));`
- **Dòng 89:** `LibraryServer server = new LibraryServer(port, threadPoolSize, socketTimeout, router);`
- **Dòng 99-101:**
  ```java
  server.start();
  printStartupBanner(port);
  LOGGER.info(String.format("Server is online and ready for client connections on 0.0.0.0:%d.", port));
  ```
- **Dòng 110-112:**
  ```java
  System.out.println("Server started");
  System.out.println("Port: " + port);
  System.out.println("Available IPv4 addresses:");
  ```
👉 **Xác nhận:** Toàn bộ log console mà người dùng nhìn thấy xuất phát chính xác 100% từ phương thức `main` của `ServerMain.java`. Lớp này khởi chạy `LibraryServer` (TCP Server cũ), hoàn toàn không khởi tạo RMI Registry hay Remote Object.

---

### 2.2. Tệp `nbproject/project.properties`
- **Dòng 60:**
  ```properties
  main.class=thuvien.server.ServerMain
  ```
- **Dòng 65-67:**
  ```properties
  run.classpath=\
      ${javac.classpath}:\
      ${build.classes.dir}
  ```
👉 **Xác nhận:** Thuộc tính `main.class` của dự án trong NetBeans đang trỏ thẳng vào `thuvien.server.ServerMain`. Do đó, bất kỳ khi nào người dùng nhấn **Run Project (F6)** trên thanh công cụ NetBeans, NetBeans sẽ tự động thực thi `ServerMain` (khởi chạy TCP Server cổng 9999).

---

### 2.3. Tệp `nbproject/build-impl.xml`
- **Dòng 1038-1044 (Target `run` — kích hoạt khi nhấn Run Project / F6):**
  ```xml
  <target depends="init,compile" description="Run a main class." name="run">
      <j2seproject1:java>
          <customize>
              <arg line="${application.args}"/>
          </customize>
      </j2seproject1:java>
  </target>
  ```
  *(Macro `<j2seproject1:java>` sử dụng thuộc tính `${main.class}` được khai báo trong `project.properties`, tức `thuvien.server.ServerMain`)*.
- **Dòng 1048-1051 (Target `run-single` — kích hoạt khi chuột phải file -> Run File / Shift+F6):**
  ```xml
  <target depends="init,compile-single" name="run-single">
      <fail unless="run.class">Must select one file in the IDE or set run.class</fail>
      <j2seproject1:java classname="${run.class}"/>
  </target>
  ```
👉 **Xác nhận:** Khi người dùng mở `ServerMain.java` và chọn **Run File**, NetBeans truyền `${run.class}=thuvien.server.ServerMain` và chạy trực tiếp lớp này.

---

### 2.4. Tệp `server/src/thuvien/server/rmi/LibraryRMIServer.java`
- **Dòng 26-28:**
  ```java
  public static final int DEFAULT_REGISTRY_PORT = 1099;
  public static final int DEFAULT_SERVICE_PORT = 1100;
  public static final String DEFAULT_SERVICE_NAME = "LibraryRemoteService";
  ```
- **Dòng 79-88:**
  ```java
  registry = LocateRegistry.createRegistry(registryPort);
  remoteService = new LibraryRemoteServiceImpl(servicePort, new RequestRouter());
  registry.rebind(serviceName, remoteService);
  ```
- **Dòng 102-108:**
  ```java
  System.out.println("RMI Server started");
  System.out.println("RMI Registry port: " + registryPort);
  System.out.println("RMI Remote Object port: " + servicePort);
  System.out.println("RMI hostname: " + activeHost);
  System.out.println("Service: " + serviceName);
  System.out.println("Available IPv4 addresses:");
  ```
- **Dòng 248:** Khai báo phương thức `public static void main(String[] args)`.
👉 **Xác nhận:** `LibraryRMIServer.java` mới chính là Server RMI chuẩn mực của dự án, khởi tạo RMI Registry tại 1099, bind Remote Object tại cổng cố định 1100, và in banner chuẩn của RMI Server.

---

### 2.5. Tệp `build.xml`
- **Dòng 70-76 (`run-rmi-server`) và dòng 88-94 (`run-server`):**
  ```xml
  <target name="run-server" depends="compile" description="Runs the Library Server">
      <java classname="thuvien.server.rmi.LibraryRMIServer" fork="true">
          <classpath>
              <path path="${run.classpath}"/>
          </classpath>
      </java>
  </target>
  ```
- **Dòng 142 (Target `package-server`):**
  ```xml
  <attribute name="Main-Class" value="thuvien.server.rmi.LibraryRMIServer"/>
  ```
- **Dòng 161 (Script `dist/server-dist/run-server.bat`):**
  ```bat
  java -cp "resources;server.jar;lib/*" thuvien.server.rmi.LibraryRMIServer %*
  ```
👉 **Xác nhận:** Toàn bộ cấu hình Ant độc lập (`run-server`, `package-server`, `run-server.bat`) đều đã được cấu hình chạy `LibraryRMIServer`. Tuy nhiên, cấu hình nội bộ của NetBeans (`nbproject/project.properties`) và tệp `ServerMain.java` vẫn chưa được đồng bộ theo.

---

### 2.6. Tệp `client/src/thuvien/client/ClientMain.java`
- **Dòng 20-22:**
  ```java
  String host = "127.0.0.1";
  int port = 1099;
  String serviceName = "LibraryRemoteService";
  ```
- **Dòng 66:**
  ```java
  final NetworkClient networkClient = new RMIClient(host, port, serviceName);
  ```
👉 **Xác nhận:** Client Swing được thiết kế để kết nối trực tiếp với RMI Registry tại cổng 1099 qua `RMIClient`. Client không hề kết nối vào cổng TCP 9999.

---

### 2.7. Tệp `server/resources/server.properties`
- **Dòng 1-5:**
  ```properties
  # Java RMI Server Configuration
  rmi.registry.port=1099
  rmi.service.port=1100
  rmi.service.name=LibraryRemoteService
  ```
- **Dòng 7-10:**
  ```properties
  # Legacy TCP Server Configuration (Retained for backward compatibility)
  server.port=9999
  server.threadPoolSize=20
  server.socketTimeout=30000
  ```
👉 **Xác nhận:** Tệp cấu hình lưu song song cả 2 thông số. Nhưng `ServerMain.java` chỉ đọc nhánh legacy `server.port=9999`, trong khi `LibraryRMIServer.java` đọc nhánh `rmi.registry.port=1099` và `rmi.service.port=1100`.

---

## 3. Bảng đối chiếu thực tế (Fact-Checking Matrix)

| Nội dung kiểm tra | Phân loại trạng thái | Chi tiết đối chiếu thực tế |
|---|:---:|---|
| **`ServerMain.java` khởi chạy cổng TCP 9999** | **CONFIRMED IN SOURCE & RUNTIME** | Xác minh trực tiếp tại dòng 89 `ServerMain.java` (`new LibraryServer(port=9999)`). Trùng khớp 100% với log người dùng gặp phải. |
| **`LibraryRMIServer.java` khởi chạy RMI cổng 1099 & 1100** | **CONFIRMED IN SOURCE & BUILD** | Xác minh tại dòng 79-88 `LibraryRMIServer.java`. Target `ant run-server` và script `run-server.bat` đều gọi đúng lớp này. |
| **`nbproject/project.properties` trỏ `main.class` về TCP cũ** | **CONFLICTING IMPLEMENTATIONS** | Dòng 60 ghi `main.class=thuvien.server.ServerMain`. Xung đột trực tiếp với target `run-server` trong `build.xml` (vốn trỏ về `LibraryRMIServer`). |
| **Client sử dụng RMI cổng 1099** | **CONFIRMED IN SOURCE** | `ClientMain.java` khởi tạo `RMIClient` mặc định kết nối 1099. Không thể giao tiếp nếu Server mở cổng 9999. |
| **README khẳng định code TCP cũ chỉ là tham khảo** | **DOCUMENTED ONLY vs CODE CONFLICT** | README (dòng 118) tuyên bố: *"Older raw TCP classes remain strictly as historical coursework reference material. They are not used by the active desktop client or server runtime"*. Nhưng trên thực tế NetBeans vẫn đặt `ServerMain` làm main class mặc định! |
| **Hai máy kết nối thành công qua Wi-Fi** | **NOT VERIFIED** | Cần thực hiện kiểm tra thực tế giữa 2 máy sau khi đã đồng bộ RMI server entry point. |

---

## 4. Phân tích nguyên nhân gốc rễ (Root Cause Analysis)

Khi dự án được chuyển đổi từ tầng giao tiếp **TCP Socket sang Java RMI**:
1. Đã tạo mới lớp `thuvien.server.rmi.LibraryRMIServer` làm máy chủ RMI.
2. Đã cập nhật `build.xml` (target `run-server`) và `package-server` trỏ vào `LibraryRMIServer`.
3. Đã cập nhật `ClientMain.java` chuyển sang dùng `RMIClient`.
4. **Tuy nhiên, đã bỏ sót 2 vị trí quan trọng đối với người dùng NetBeans:**
   - **Vị trí 1:** Thuộc tính `main.class=thuvien.server.ServerMain` trong `nbproject/project.properties` chưa được cập nhật sang `thuvien.server.rmi.LibraryRMIServer`.
   - **Vị trí 2:** Lớp `ServerMain.java` ở package gốc `thuvien.server` vẫn giữ nguyên mã nguồn khởi chạy `LibraryServer` (TCP port 9999) thay vì uỷ quyền khởi chạy `LibraryRMIServer`.
5. Khi người dùng mở dự án trong NetBeans:
   - Theo thói quen nhìn thấy tên `ServerMain.java`, người dùng bấm **Run File** (Shift+F6).
   - Hoặc người dùng bấm nút **Run Project** (F6) trên thanh công cụ NetBeans.
   - Cả 2 thao tác này đều kích hoạt `ServerMain.java` -> Server TCP 9999 lắng nghe -> Không tương thích với Client RMI.

---

## 5. Các giả thuyết bị loại trừ (Ruled-Out Hypotheses)

1. **Giả thuyết 1: "NetBeans đang chạy file class biên dịch cũ hoặc dính cache build"**
   - *Kết quả kiểm tra:* Đã kiểm tra trực tiếp mã nguồn `ServerMain.java` (dòng 89). Mã nguồn gốc hiện tại đang viết lệnh chạy `LibraryServer` trên cổng 9999. Do đó dù có `ant clean` hay xóa sạch thư mục `build/classes`, việc chạy `ServerMain.java` vẫn luôn ra cổng 9999.
   - *Kết luận:* **LOẠI TRỪ**.

2. **Giả thuyết 2: "Tiến trình khác đang chiếm cổng hoặc log từ server cũ chạy ngầm"**
   - *Kết quả kiểm tra:* Khi kiểm tra `Get-NetTCPConnection`, cổng 9999 hoàn toàn không bị chiếm giữ trước khi chạy. Đầu ra console xuất phát trực tiếp từ JVM do NetBeans vừa khởi tạo.
   - *Kết luận:* **LOẠI TRỪ**.

3. **Giả thuyết 3: "RMI Server và TCP Server bị xung đột cổng"**
   - *Kết quả kiểm tra:* TCP Server dùng cổng 9999, RMI Server dùng cổng 1099 và 1100. Các cổng này hoàn toàn tách biệt. Vấn đề không phải là xung đột cổng (Port conflict), mà là **chạy sai lớp entry point**.
   - *Kết luận:* **LOẠI TRỪ**.

---

## 6. Đề xuất phương án xử lý cho Giai đoạn B (Remediation Plan)

Để đảm bảo dù người dùng chạy bằng bất kỳ cách nào trong NetBeans hay Ant thì hệ thống luôn khởi chạy đúng **RMI Server (1099/1100)**, đồng thời giữ nguyên mã TCP cũ cho mục đích tham khảo bài tập môn học:

### Phương án đề xuất (Toàn diện và triệt để nhất):
1. **Cập nhật `nbproject/project.properties`:**
   - Đổi dòng 60:
     ```properties
     main.class=thuvien.server.rmi.LibraryRMIServer
     ```
   - Khi đó, nhấn **Run Project (F6)** trong NetBeans sẽ lập tức chạy RMI Server chuẩn (1099/1100).

2. **Đồng bộ hoá `ServerMain.java` làm cổng chào điều phối (Unified Entrypoint):**
   - Cập nhật phương thức `main` của `ServerMain.java` để uỷ quyền trực tiếp gọi:
     ```java
     thuvien.server.rmi.LibraryRMIServer.main(args);
     ```
   - In thông báo rõ ràng: *"Đang khởi chạy Remote School Library RMI Server (cổng 1099/1100)..."*
   - Nếu ai đó cần chạy TCP Server cũ để đối chiếu bài tập, có thể chạy qua cờ `--tcp` hoặc chạy trực tiếp lớp legacy riêng (ví dụ `thuvien.server.network.LegacyTCPServerRunner`).
   - Như vậy, dù người dùng bấm **Run Project (F6)**, hay chuột phải `ServerMain.java` -> **Run File**, hay chuột phải `LibraryRMIServer.java` -> **Run File**, hệ thống đều chạy đồng nhất RMI Server.

3. **Cập nhật `README.md`:**
   - Bổ sung mục hướng dẫn rõ ràng cho NetBeans 8.0.2:
     - Cách nhấn Run Project (F6).
     - Giải thích rõ `ServerMain.java` và `LibraryRMIServer.java` đều khởi chạy RMI Server.
     - Cách phân biệt và chạy TCP cũ nếu muốn so sánh kiến trúc.
   - Giữ nguyên các hướng dẫn chạy qua Ant và file batch độc lập.

---

## 7. Các mục cần người dùng phê duyệt trước khi thực hiện Giai đoạn B

Trước khi thực hiện chỉnh sửa, đề nghị người dùng xác nhận:

1. **Cho phép cập nhật `nbproject/project.properties`:** Đổi `main.class=thuvien.server.rmi.LibraryRMIServer`.
2. **Cho phép cập nhật `ServerMain.java`:** Chuyển `ServerMain.java` thành điểm uỷ quyền gọi `LibraryRMIServer.main(args)` (đảm bảo bấm Run File trên `ServerMain.java` sẽ mở cổng 1099/1100 thay vì 9999).
3. **Cho phép cập nhật `README.md`:** Bổ sung hướng dẫn chạy NetBeans 8.0.2 và làm rõ vai trò các entry point.
4. **Bảo lưu mã nguồn TCP cũ:** Giữ nguyên `thuvien.server.network.LibraryServer` và các lớp socket cũ trong package `network`, không xoá bỏ.

---

```text
================================================================================
               AUDIT COMPLETE — AWAITING USER APPROVAL
================================================================================
Đã xác định chính xác 100% nguyên nhân gây ra log cổng 9999 trong NetBeans.
Chưa có tệp mã nguồn hay cấu hình nào bị thay đổi.
Đang chờ lệnh phê duyệt từ người dùng để tiến hành Giai đoạn B (Remediation).
================================================================================
```
