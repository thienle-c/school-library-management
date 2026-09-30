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
        int port = 9999;
        int timeout = 10000;

        // Load client configuration
        try (InputStream in = ClientMain.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                host = props.getProperty("server.host", "127.0.0.1").trim();
                port = Integer.parseInt(props.getProperty("server.port", "9999").trim());
                timeout = Integer.parseInt(props.getProperty("network.timeout", "10000").trim());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load client.properties, using defaults.", e);
        }

        // Allow system property overrides if provided
        String sysHost = System.getProperty("server.host");
        if (sysHost != null && !sysHost.trim().isEmpty()) {
            host = sysHost.trim();
        }
        String sysPort = System.getProperty("server.port");
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(sysPort.trim());
            } catch (NumberFormatException ignored) {}
        }

        // Allow optional command-line argument overrides: <host> [port]
        if (args != null && args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            host = args[0].trim();
        }
        if (args != null && args.length >= 2 && args[1] != null && !args[1].trim().isEmpty()) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {}
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
