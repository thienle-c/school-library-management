package thuvien.client;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import thuvien.client.network.NetworkClient;
import thuvien.client.network.RMIClient;
import thuvien.client.view.LoginForm;

/**
 * Bootstrap entry point for the Remote School Library Swing Client using Java RMI.
 */
public class ClientMain {
    private static final Logger LOGGER = Logger.getLogger(ClientMain.class.getName());

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 1099;
        String serviceName = "LibraryRemoteService";

        // Load client configuration
        try (InputStream in = ClientMain.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String h = props.getProperty("rmi.server.host", props.getProperty("server.host", "127.0.0.1"));
                if (h != null) host = h.trim();
                String p = props.getProperty("rmi.registry.port", props.getProperty("server.port", "1099"));
                if (p != null) port = Integer.parseInt(p.trim());
                String s = props.getProperty("rmi.service.name", "LibraryRemoteService");
                if (s != null) serviceName = s.trim();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load client.properties, using defaults.", e);
        }

        // Allow system property overrides if provided
        String sysHost = System.getProperty("rmi.server.host", System.getProperty("server.host"));
        if (sysHost != null && !sysHost.trim().isEmpty()) {
            host = sysHost.trim();
        }
        String sysPort = System.getProperty("rmi.registry.port", System.getProperty("server.port"));
        if (sysPort != null && !sysPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(sysPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        String sysService = System.getProperty("rmi.service.name");
        if (sysService != null && !sysService.trim().isEmpty()) {
            serviceName = sysService.trim();
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

        final NetworkClient networkClient = new RMIClient(host, port, serviceName);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            LoginForm loginForm = new LoginForm(networkClient);
            loginForm.setVisible(true);
        });
    }
}
