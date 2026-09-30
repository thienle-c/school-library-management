package thuvien.client.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.client.session.ClientSession;
import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

/**
 * TCP Socket implementation of NetworkClient.
 * Maintains client connection, auto-injects session tokens, and executes requests.
 */
public class TCPNetworkClient implements NetworkClient {
    private static final Logger LOGGER = Logger.getLogger(TCPNetworkClient.class.getName());

    private String host;
    private int port;
    private final int timeoutMs;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private long lastActivityTime = 0L;
    // Proactively renew connection if idle for > 20s (less than server 30s idle timeout)
    private static final long IDLE_RENEWAL_THRESHOLD_MS = 20000L;

    public TCPNetworkClient(String host, int port, int timeoutMs) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public synchronized String getHost() {
        return host;
    }

    @Override
    public synchronized int getPort() {
        return port;
    }

    @Override
    public synchronized void setServerAddress(String newHost, int newPort) {
        String normalizedHost = (newHost != null && !newHost.trim().isEmpty()) ? newHost.trim() : "127.0.0.1";
        if (!normalizedHost.equals(this.host) || newPort != this.port) {
            disconnect();
            this.host = normalizedHost;
            this.port = newPort;
        }
    }

    @Override
    public synchronized boolean connect(String newHost, int newPort) throws NetworkException {
        setServerAddress(newHost, newPort);
        disconnect();
        return connect();
    }

    @Override
    public synchronized boolean ping() throws NetworkException {
        Response resp = send(new Request(Action.PING, "PING"));
        return resp != null && resp.isSuccess() && "PONG".equals(resp.getData());
    }

    @Override
    public synchronized boolean connect() throws NetworkException {
        if (isConnected() && !isStale()) return true;

        disconnect(); // Ensure previous resources closed cleanly

        try {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), timeoutMs);
            this.socket.setSoTimeout(timeoutMs);

            // Establish streams (output stream first, flush header)
            this.out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            this.out.flush();
            this.in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
            this.lastActivityTime = System.currentTimeMillis();

            LOGGER.info(String.format("Connected to LibraryServer at %s:%d", host, port));
            return true;
        } catch (IOException e) {
            disconnect();
            throw new NetworkException(String.format("Không thể kết nối đến máy chủ tại %s:%d: %s", host, port, e.getMessage()), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Error during client disconnect", e);
        } finally {
            socket = null;
            out = null;
            in = null;
            lastActivityTime = 0L;
            LOGGER.info("Disconnected from LibraryServer.");
        }
    }

    @Override
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized boolean isStale() {
        if (!isConnected()) return true;
        long idleTime = System.currentTimeMillis() - lastActivityTime;
        return idleTime > IDLE_RENEWAL_THRESHOLD_MS;
    }

    @Override
    public synchronized Response send(Request request) throws NetworkException {
        // 1. Proactively reconnect if socket was disconnected or has been idle past threshold
        if (!isConnected() || isStale()) {
            LOGGER.fine("Connection is absent or idle, establishing fresh connection...");
            disconnect();
            connect();
        }

        // 2. Auto-inject current session token if present
        if (request.getToken() == null || request.getToken().isEmpty()) {
            request.setToken(ClientSession.getInstance().getToken());
        }

        boolean isReadOnly = request.getAction() != null && request.getAction().isReadOnly();

        // 3. Attempt transmission
        try {
            Response resp = sendInternal(request);
            lastActivityTime = System.currentTimeMillis();
            return resp;
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "I/O error during request " + request.getAction() + ": " + e.getMessage());
            disconnect(); // Reset socket immediately on I/O error

            // 4. Safe recovery: retry once ONLY for read-only actions
            if (isReadOnly) {
                LOGGER.info("Attempting single reconnection retry for READ-ONLY action: " + request.getAction());
                try {
                    connect();
                    Response retryResp = sendInternal(request);
                    lastActivityTime = System.currentTimeMillis();
                    return retryResp;
                } catch (Exception retryEx) {
                    LOGGER.log(Level.SEVERE, "Retry failed for read-only action: " + request.getAction(), retryEx);
                    disconnect();
                    throw new NetworkException("Không thể kết nối đến máy chủ. Hệ thống sẽ thử kết nối lại sau.", retryEx);
                }
            } else {
                // NEVER blindly retry mutations (borrow, return, create, update, delete, pay fine)
                throw new NetworkException("Kết nối mạng bị gián đoạn trong khi gửi yêu cầu. Vui lòng kiểm tra lại trước khi thử lại.", e);
            }
        }
    }

    private Response sendInternal(Request request) throws IOException, ClassNotFoundException, NetworkException {
        out.writeObject(request);
        out.flush();
        out.reset(); // Flush object cache

        Object received = in.readObject();
        if (received instanceof Response) {
            return (Response) received;
        } else {
            throw new NetworkException("Dữ liệu phản hồi từ máy chủ không đúng định dạng (Expected Response).");
        }
    }
}
