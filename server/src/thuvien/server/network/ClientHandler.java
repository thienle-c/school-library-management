package thuvien.server.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;
import thuvien.server.router.RequestRouter;

/**
 * Worker thread for an individual TCP client connection.
 */
public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final RequestRouter router;

    public ClientHandler(Socket socket, RequestRouter router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        LOGGER.info(String.format("ClientHandler started for %s", clientAddress));

        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()))) {
            
            // Flush header immediately so client can establish input stream
            out.flush();

            while (!socket.isClosed() && socket.isConnected()) {
                Request request;
                try {
                    Object received = in.readObject();
                    if (received instanceof Request) {
                        request = (Request) received;
                    } else {
                        Response errorResp = Response.error(
                                "UNKNOWN",
                                StatusCode.BAD_REQUEST,
                                "Malformed request: payload must be a Request object."
                        );
                        out.writeObject(errorResp);
                        out.flush();
                        continue;
                    }
                } catch (EOFException | SocketException e) {
                    LOGGER.info(String.format("Client disconnected gracefully: %s", clientAddress));
                    break;
                } catch (SocketTimeoutException e) {
                    LOGGER.warning(String.format("Socket timeout for client %s, terminating idle connection.", clientAddress));
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING, "Unknown class received from client", e);
                    Response errorResp = Response.error(
                            "UNKNOWN",
                            StatusCode.BAD_REQUEST,
                            "Malformed request: unrecognized serialized type."
                    );
                    out.writeObject(errorResp);
                    out.flush();
                    continue;
                }

                // Process request through router
                Response response = router.route(request);
                out.writeObject(response);
                out.flush();
                out.reset(); // Prevent object caching across requests
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, String.format("Client connection ended (%s): %s", clientAddress, e.getMessage()));
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error closing socket", e);
            }
            LOGGER.info(String.format("ClientHandler terminated for %s", clientAddress));
        }
    }
}
