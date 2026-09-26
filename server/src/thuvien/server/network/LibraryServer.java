package thuvien.server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.server.router.RequestRouter;

/**
 * TCP Server that listens for client connections and dispatches them to a thread pool.
 */
public class LibraryServer {
    private static final Logger LOGGER = Logger.getLogger(LibraryServer.class.getName());

    private final int port;
    private final int threadPoolSize;
    private final int socketTimeoutMs;
    private final RequestRouter router;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    public LibraryServer(int port, int threadPoolSize, int socketTimeoutMs, RequestRouter router) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.socketTimeoutMs = socketTimeoutMs;
        this.router = router;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        this.serverSocket = new ServerSocket(port);
        this.threadPool = Executors.newFixedThreadPool(threadPoolSize);
        this.running = true;

        LOGGER.info(String.format("LibraryServer listening on TCP port %d with %d worker threads.", port, threadPoolSize));

        // Accept loop in dedicated daemon listener thread
        Thread listenerThread = new Thread(this::listen, "LibraryServer-Listener");
        listenerThread.setDaemon(false);
        listenerThread.start();
    }

    private void listen() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(socketTimeoutMs);
                LOGGER.info(String.format("Accepted client connection from %s", clientSocket.getRemoteSocketAddress()));
                threadPool.execute(new ClientHandler(clientSocket, router));
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.WARNING, "Error accepting client connection", e);
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        LOGGER.info("Stopping LibraryServer...");
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing ServerSocket", e);
        }

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("LibraryServer stopped gracefully.");
    }

    public boolean isRunning() {
        return running;
    }
}
