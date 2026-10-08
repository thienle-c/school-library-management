package thuvien.client.network;

import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.common.rmi.LibraryRemoteService;

/**
 * Java RMI Client implementation of NetworkClient.
 * Connects to the RMI Registry via LocateRegistry.getRegistry(host, port),
 * looks up the remote service interface LibraryRemoteService,
 * and communicates directly with the Remote Object over fixed port 1100.
 *
 * Includes detailed error diagnostics for multi-machine LAN setups:
 * - Differentiates Registry port (1099) unreachable vs. Remote Object port (1100) unreachable.
 * - Detects java.rmi.server.hostname resolution failures.
 * - Detects missing/unregistered service names.
 */
public class RMIClient implements NetworkClient {
    private static final Logger LOGGER = Logger.getLogger(RMIClient.class.getName());

    public static final int DEFAULT_REGISTRY_PORT = 1099;
    public static final String DEFAULT_SERVICE_NAME = "LibraryRemoteService";

    private String host;
    private int port;
    private String serviceName;
    private LibraryRemoteService remoteService;

    public RMIClient() {
        this("127.0.0.1", DEFAULT_REGISTRY_PORT, DEFAULT_SERVICE_NAME);
    }

    public RMIClient(String host, int port) {
        this(host, port, DEFAULT_SERVICE_NAME);
    }

    public RMIClient(String host, int port, String serviceName) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
        this.port = port > 0 ? port : DEFAULT_REGISTRY_PORT;
        this.serviceName = (serviceName != null && !serviceName.trim().isEmpty()) ? serviceName.trim() : DEFAULT_SERVICE_NAME;
    }

    @Override
    public synchronized boolean connect() throws NetworkException {
        // Step 1: Connect to Registry and lookup service
        Registry registry;
        try {
            LOGGER.info("Connecting to RMI Registry at " + host + ":" + port + "...");
            registry = LocateRegistry.getRegistry(host, port);
            LOGGER.info("Looking up service '" + serviceName + "' in RMI Registry...");
            this.remoteService = (LibraryRemoteService) registry.lookup(serviceName);
            LOGGER.info("RMI Registry lookup successful: " + serviceName);
        } catch (NotBoundException e) {
            this.remoteService = null;
            LOGGER.log(Level.WARNING, "Service '" + serviceName + "' is not bound on RMI Registry at " + host + ":" + port, e);
            throw new NetworkException("Không tìm thấy service '" + serviceName + "' trên RMI Registry tại " + host + ":" + port
                    + ". Vui lòng kiểm tra lại cấu hình tên service trên Server.", e);
        } catch (ConnectException | ConnectIOException | UnknownHostException e) {
            this.remoteService = null;
            LOGGER.log(Level.WARNING, "Cannot reach RMI Registry at " + host + ":" + port, e);
            throw new NetworkException("Không thể kết nối tới RMI Registry tại " + host + ":" + port
                    + ". Vui lòng kiểm tra: (1) Server đã khởi chạy chưa, (2) IP/Port có đúng không, và (3) Firewall trên Server đã mở cổng " + port + " (TCP) chưa.", e);
        } catch (Exception e) {
            this.remoteService = null;
            LOGGER.log(Level.WARNING, "Failed to connect to RMI Registry at " + host + ":" + port, e);
            throw new NetworkException("Lỗi kết nối tới RMI Registry (" + host + ":" + port + "): " + e.getMessage(), e);
        }

        // Step 2: Test Remote Object communication over port 1100 to verify stub and hostname
        try {
            LOGGER.info("Verifying RMI Remote Object communication via ping()...");
            String pong = this.remoteService.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new NetworkException("RMI Remote Object phản hồi không đúng kỳ vọng: " + pong);
            }
            LOGGER.info("RMI Remote Object communication verified successfully.");
            return true;
        } catch (RemoteException e) {
            this.remoteService = null;
            String msg = (e.getMessage() != null) ? e.getMessage() : "";
            LOGGER.log(Level.WARNING, "RMI Remote Object verification failed: " + msg, e);
            if (e instanceof UnknownHostException || msg.contains("UnknownHostException")) {
                throw new NetworkException("Lỗi hostname RMI: Máy Client không thể phân giải địa chỉ hostname nhận từ Server stub (" + msg + ")."
                        + " Vui lòng thiết lập java.rmi.server.hostname trên Server là địa chỉ IPv4 LAN thực tế thay vì tên máy.", e);
            }
            throw new NetworkException("Kết nối tới RMI Registry (cổng " + port + ") thành công, nhưng không thể kết nối tới RMI Remote Object (cổng 1100)."
                    + " Nguyên nhân có thể do: (1) Cổng 1100 (TCP) đang bị Firewall trên Server chặn, hoặc (2) java.rmi.server.hostname trên Server không truy cập được từ máy Client."
                    + " Chi tiết lỗi: " + msg, e);
        } catch (NetworkException e) {
            this.remoteService = null;
            throw e;
        } catch (Exception e) {
            this.remoteService = null;
            LOGGER.log(Level.WARNING, "Unexpected error verifying remote object: " + e.getMessage(), e);
            throw new NetworkException("Lỗi kiểm tra Remote Object RMI: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        this.remoteService = null;
        LOGGER.info("RMIClient disconnected.");
    }

    @Override
    public synchronized boolean isConnected() {
        return this.remoteService != null;
    }

    @Override
    public void setServerAddress(String host, int port) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
        this.port = port > 0 ? port : DEFAULT_REGISTRY_PORT;
        disconnect();
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LibraryRemoteService getRemoteService() throws NetworkException {
        if (!isConnected()) {
            connect();
        }
        return remoteService;
    }

    @Override
    public boolean ping() throws NetworkException {
        try {
            LibraryRemoteService service = getRemoteService();
            String pong = service.ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (RemoteException e) {
            disconnect();
            String msg = (e.getMessage() != null) ? e.getMessage() : "";
            if (e instanceof UnknownHostException || msg.contains("UnknownHostException")) {
                throw new NetworkException("Lỗi hostname RMI: Không thể phân giải địa chỉ hostname của Server (" + msg + ").", e);
            }
            throw new NetworkException("RMI ping failed: Không thể kết nối tới Remote Object (cổng 1100): " + msg, e);
        }
    }

    @Override
    public Response send(Request request) throws NetworkException {
        if (request == null) {
            throw new NetworkException("Request cannot be null.");
        }

        try {
            LibraryRemoteService service = getRemoteService();
            // Automatically inject active session token from ClientSession if missing
            if (request.getToken() == null || request.getToken().trim().isEmpty()) {
                String sessionToken = thuvien.client.session.ClientSession.getInstance().getToken();
                if (sessionToken != null && !sessionToken.trim().isEmpty()) {
                    request.setToken(sessionToken);
                }
            }

            return service.execute(request);
        } catch (RemoteException e) {
            disconnect();
            LOGGER.log(Level.WARNING, "RemoteException during RMI method execution: " + e.getMessage(), e);
            return Response.error(request.getRequestId(), StatusCode.SERVER_ERROR,
                    "Lỗi kết nối RMI từ xa: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during RMI send: " + e.getMessage(), e);
            throw new NetworkException("RMI request execution error: " + e.getMessage(), e);
        }
    }
}
