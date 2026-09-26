package thuvien.client;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import thuvien.client.network.TCPNetworkClient;
import thuvien.client.view.LoginForm;

/**
 * Bootstrap entry point for the Remote School Library Swing Client.
 */
public class ClientMain {
    private static final Logger LOGGER = Logger.getLogger(ClientMain.class.getName());

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;
        int timeout = 10000;

        // Load client configuration
        try (InputStream in = ClientMain.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                host = props.getProperty("server.host", "127.0.0.1");
                port = Integer.parseInt(props.getProperty("server.port", "8888"));
                timeout = Integer.parseInt(props.getProperty("network.timeout", "10000"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load client.properties, using defaults.", e);
        }

        final TCPNetworkClient networkClient = new TCPNetworkClient(host, port, timeout);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            LoginForm loginForm = new LoginForm(networkClient);
            loginForm.setVisible(true);
        });
    }
}
