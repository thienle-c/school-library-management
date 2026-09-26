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
        setSize(460, 360);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel headerLabel = new JLabel("Quản Lý Thư Viện Trường Học", JLabel.CENTER);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 18.0f));
        JLabel subHeaderLabel = new JLabel("Ứng Dụng Khách Kết Nối TCP Từ Xa", JLabel.CENTER);
        subHeaderLabel.setFont(subHeaderLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        subHeaderLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(headerLabel, BorderLayout.NORTH);
        headerPanel.add(subHeaderLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel userLabel = new JLabel("Tên đăng nhập:");
        userLabel.setFont(userLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        usernameField = new JTextField(15);
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
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
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    private void performLogin() {
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
                        errorMsg = "Không thể kết nối đến máy chủ thư viện.\n\n"
                                + "Chi tiết: " + ex.getMessage() + "\n\n"
                                + "Vui lòng kiểm tra xem máy chủ đang chạy và kết nối mạng ổn định.";
                        statusLabel.setText("Kết nối đến máy chủ thất bại.");
                        LOGGER.log(Level.WARNING, "Login network failure", ex);
                    } else {
                        errorTitle = "Lỗi Hệ Thống";
                        errorMsg = "Đã xảy ra lỗi trong quá trình xác thực: " + ex.getMessage();
                        statusLabel.setText("Lỗi hệ thống khi đăng nhập.");
                        LOGGER.log(Level.SEVERE, "Unexpected login error", ex);
                    }

                    JOptionPane.showMessageDialog(LoginForm.this, errorMsg, errorTitle, JOptionPane.ERROR_MESSAGE);
                }
        );
    }

}
