package thuvien.client.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.NetworkException;

/**
 * Authentication dialog / window for the Remote School Library client.
 * Connects to the server over TCP, handles login, and displays user-friendly status and errors.
 */
public class LoginForm extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(LoginForm.class.getName());

    private final NetworkClient networkClient;
    private final ClientAuthController authController;

    private JTextField serverHostField;
    private JTextField serverPortField;
    private JButton testConnectionButton;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel statusLabel;

    private JButton registerButton;

    public LoginForm(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.authController = new ClientAuthController(networkClient);
        initUI();
    }

    private void initUI() {
        setTitle("Hệ Thống Quản Lý Thư Viện - Đăng Nhập");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 440);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 12));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(18, 25, 18, 25));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel headerLabel = new JLabel("Quản Lý Thư Viện Trường Học", JLabel.CENTER);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 18.0f));
        JLabel subHeaderLabel = new JLabel("Ứng Dụng Khách Kết Nối Java RMI", JLabel.CENTER);
        subHeaderLabel.setFont(subHeaderLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        subHeaderLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(headerLabel, BorderLayout.NORTH);
        headerPanel.add(subHeaderLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Server IP
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel hostLabel = new JLabel("Server IP:");
        hostLabel.setFont(hostLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(hostLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        serverHostField = new JTextField(networkClient.getHost(), 15);
        formPanel.add(serverHostField, gbc);

        // Row 1: Server Port
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        JLabel portLabel = new JLabel("Port:");
        portLabel.setFont(portLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(portLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        serverPortField = new JTextField(String.valueOf(networkClient.getPort()), 15);
        formPanel.add(serverPortField, gbc);

        // Row 2: Test Connection button
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.weightx = 1.0;
        testConnectionButton = new JButton("Kiểm tra kết nối");
        testConnectionButton.addActionListener(e -> performTestConnection());
        formPanel.add(testConnectionButton, gbc);

        // Row 3: Username
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0.0;
        JLabel userLabel = new JLabel("Tên đăng nhập:");
        userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        usernameField = new JTextField(15);
        formPanel.add(usernameField, gbc);

        // Row 4: Password
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.weightx = 0.0;
        JLabel passLabel = new JLabel("Mật khẩu:");
        passLabel.setFont(passLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(passLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(15);
        formPanel.add(passwordField, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom area (Status + Action buttons)
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 8));
        statusLabel = new JLabel("Vui lòng nhập tài khoản và mật khẩu.", JLabel.CENTER);
        statusLabel.setForeground(new Color(80, 80, 80));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new BorderLayout(0, 8));

        JPanel loginRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButton = new JButton("Đăng nhập");
        loginButton.setPreferredSize(new Dimension(140, 32));
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD));
        loginButton.addActionListener(e -> performLogin());
        loginRow.add(loginButton);
        buttonPanel.add(loginRow, BorderLayout.NORTH);

        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        registerButton = new JButton("Chưa có tài khoản? Đăng ký tại đây");
        registerButton.setFont(registerButton.getFont().deriveFont(Font.PLAIN, 12.0f));
        registerButton.setForeground(new Color(30, 80, 160));
        registerButton.setBorderPainted(false);
        registerButton.setContentAreaFilled(false);
        registerButton.setFocusPainted(false);
        registerButton.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        registerButton.addActionListener(e -> {
            if (!applyServerSettingsFromUI()) {
                return;
            }
            StudentRegistrationDialog dialog = new StudentRegistrationDialog(this, networkClient);
            dialog.setVisible(true);
        });
        regRow.add(registerButton);
        buttonPanel.add(regRow, BorderLayout.SOUTH);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // Enter key submits form
        getRootPane().setDefaultButton(loginButton);
    }

    private void setInputsEnabled(boolean enabled) {
        serverHostField.setEnabled(enabled);
        serverPortField.setEnabled(enabled);
        testConnectionButton.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    private boolean applyServerSettingsFromUI() {
        String host = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập địa chỉ Server IP.",
                    "Lỗi Cấu Hình",
                    JOptionPane.WARNING_MESSAGE);
            serverHostField.requestFocusInWindow();
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Out of range");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Port phải là số nguyên hợp lệ (1 - 65535).",
                    "Lỗi Cấu Hình",
                    JOptionPane.WARNING_MESSAGE);
            serverPortField.requestFocusInWindow();
            return false;
        }
        networkClient.setServerAddress(host, port);
        return true;
    }

    private void performTestConnection() {
        if (!applyServerSettingsFromUI()) {
            return;
        }
        final String host = networkClient.getHost();
        final int port = networkClient.getPort();

        setInputsEnabled(false);
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setText("Đang kiểm tra kết nối tới " + host + ":" + port + "...");

        AsyncWorker.run(
                () -> {
                    networkClient.connect(host, port);
                    return networkClient.ping();
                },
                (Boolean pongOk) -> {
                    setInputsEnabled(true);
                    if (Boolean.TRUE.equals(pongOk)) {
                        statusLabel.setForeground(new Color(20, 130, 50));
                        statusLabel.setText("Server connected");
                        JOptionPane.showMessageDialog(LoginForm.this,
                                "Server connected",
                                "Kết Nối Thành Công",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setForeground(new Color(180, 40, 40));
                        statusLabel.setText("Cannot connect to server");
                        JOptionPane.showMessageDialog(LoginForm.this,
                                "Cannot connect to server",
                                "Lỗi Kết Nối",
                                JOptionPane.ERROR_MESSAGE);
                    }
                },
                (Exception ex) -> {
                    setInputsEnabled(true);
                    statusLabel.setForeground(new Color(180, 40, 40));
                    String errorMsg = (ex.getMessage() != null && !ex.getMessage().trim().isEmpty())
                            ? ex.getMessage()
                            : "Cannot connect to server";
                    JOptionPane.showMessageDialog(LoginForm.this,
                            errorMsg,
                            "Lỗi Kết Nối RMI",
                            JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void performLogin() {
        if (!applyServerSettingsFromUI()) {
            return;
        }
        String username = usernameField.getText().trim();
        char[] passChars = passwordField.getPassword();
        String password = new String(passChars);

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.",
                    "Lỗi Nhập Liệu",
                    JOptionPane.WARNING_MESSAGE);
            if (username.isEmpty()) {
                usernameField.requestFocusInWindow();
            } else {
                passwordField.requestFocusInWindow();
            }
            return;
        }

        // Disable inputs and show loading state
        setInputsEnabled(false);
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setText("Đang kết nối và xác thực với máy chủ...");

        // Non-blocking network call via AsyncWorker off the EDT
        AsyncWorker.run(
                () -> authController.login(username, password),
                (UserSessionDTO session) -> {
                    statusLabel.setText("Đăng nhập thành công.");
                    dispose();
                    SwingUtilities.invokeLater(() -> new MainDashboardForm(networkClient, session).setVisible(true));
                },
                (Exception ex) -> {
                    // Re-enable inputs on failure
                    setInputsEnabled(true);
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();

                    String errorTitle;
                    String errorMsg;

                    if (ex instanceof AuthenticationException) {
                        errorTitle = "Đăng Nhập Thất Bại";
                        errorMsg = (ex.getMessage() != null && !ex.getMessage().isEmpty())
                                ? ex.getMessage()
                                : "Tên đăng nhập hoặc mật khẩu không chính xác.";
                        statusLabel.setText(errorMsg);
                    } else if (ex instanceof NetworkException) {
                        errorTitle = "Lỗi Kết Nối Máy Chủ";
                        errorMsg = "Cannot connect to server (" + networkClient.getHost() + ":" + networkClient.getPort() + ").\n"
                                + "Vui lòng kiểm tra lại Server IP, Port và kết nối mạng.";
                        statusLabel.setText("Cannot connect to server");
                        LOGGER.log(Level.WARNING, "Login network failure", ex);
                    } else {
                        errorTitle = "Lỗi Hệ Thống";
                        errorMsg = "Đã xảy ra lỗi trong quá trình xác thực.";
                        statusLabel.setText("Lỗi hệ thống khi đăng nhập.");
                        LOGGER.log(Level.SEVERE, "Unexpected login error", ex);
                    }

                    JOptionPane.showMessageDialog(LoginForm.this, errorMsg, errorTitle, JOptionPane.ERROR_MESSAGE);
                }
        );
    }

}
