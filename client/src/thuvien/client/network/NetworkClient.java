package thuvien.client.network;

import thuvien.common.exception.NetworkException;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;

public interface NetworkClient {
    boolean connect() throws NetworkException;
    void disconnect();
    boolean isConnected();
    Response send(Request request) throws NetworkException;
}
