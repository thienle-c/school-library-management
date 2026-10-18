package thuvien.client.network;

import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

public interface NetworkClient {
    boolean connect() throws NetworkException;
    default boolean connect(String host, int port) throws NetworkException {
        setServerAddress(host, port);
        return connect();
    }
    default void setServerAddress(String host, int port) {
        // Default no-op for test doubles
    }
    default String getHost() {
        return "127.0.0.1";
    }
    default int getPort() {
        return 1099;
    }
    default boolean ping() throws NetworkException {
        Response resp = send(new Request(Action.PING, "PING"));
        return resp != null && resp.isSuccess() && "PONG".equals(resp.getData());
    }
    void disconnect();
    boolean isConnected();
    Response send(Request request) throws NetworkException;
    default void setSession(thuvien.client.session.ClientSession session) {}
    default thuvien.client.session.ClientSession getSession() {
        return thuvien.client.session.ClientSession.getInstance();
    }
    default void setSessionToken(String token) {
        if (token != null) {
            thuvien.client.session.ClientSession cs = getSession();
            if (cs == null || cs == thuvien.client.session.ClientSession.getInstance()) {
                cs = new thuvien.client.session.ClientSession();
                setSession(cs);
            }
            thuvien.common.dto.UserSessionDTO user = cs.getCurrentUser();
            if (user == null) {
                user = new thuvien.common.dto.UserSessionDTO();
                cs.setSession(user);
            }
            user.setToken(token);
        }
    }
}
