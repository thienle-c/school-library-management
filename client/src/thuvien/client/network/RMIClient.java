package thuvien.client.network;

import java.rmi.RemoteException;
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
 * Obtains remote reference to LibraryRemoteService via RMI LocateRegistry.lookup()
 * and invokes remote methods using standard Java RMI + Java Serialization.
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
        try {
            LOGGER.info("Connecting to RMI Registry at " + host + ":" + port + "...");
            Registry registry = LocateRegistry.getRegistry(host, port);
            LOGGER.info("Looking up service '" + serviceName + "' in RMI Registry...");
            this.remoteService = (LibraryRemoteService) registry.lookup(serviceName);
            LOGGER.info("RMI lookup successful: " + serviceName);
            return true;
        } catch (Exception e) {
            this.remoteService = null;
            LOGGER.log(Level.WARNING, "Failed to lookup RMI service '" + serviceName + "' at " + host + ":" + port, e);
            throw new NetworkException("Cannot connect to RMI Server at " + host + ":" + port + ": " + e.getMessage(), e);
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
            throw new NetworkException("RMI ping failed: " + e.getMessage(), e);
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
