package thuvien.client.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.RegisterStudentRequestDTO;

/**
 * Dialog for student self-registration using cryptographic activation codes issued by Admin.
 */
public class StudentRegistrationDialog extends JDialog {
    private final ClientAuthController authController;

    private JTextField studentCodeField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JTextField activationCodeField;

    private JButton registerButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public StudentRegistrationDialog(Frame parent, NetworkClient networkClient) {
        super(parent, "Đăng Ký Tài Khoản Sinh Viên", true);
        this.authController = new ClientAuthController(networkClient);
        initUI();
    }

    private void initUI() {
        setSize(480, 480);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel titleLabel = new JLabel("Đăng Ký Tài Khoản Thư Viện", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16.0f));
        JLabel subLabel = new JLabel("Dành cho sinh viên đã có hồ sơ và được cấp mã kích hoạt", JLabel.CENTER);
        subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        subLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        studentCodeField = addFormField(formPanel, gbc, "Mã sinh viên (*):", row++, "VD: STU002");
        fullNameField = addFormField(formPanel, gbc, "Họ và tên (*):", row++, "VD: Nguyễn Văn A");
        emailField = addFormField(formPanel, gbc, "Email liên hệ:", row++, "VD: a.nguyen@student.school.edu");
        usernameField = addFormField(formPanel, gbc, "Tên đăng nhập (*):", row++, "VD: nguyenvana");

        // Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        JLabel passLbl = new JLabel("Mật khẩu (*):");
        passLbl.setFont(passLbl.getFont().deriveFont(Font.BOLD));
        formPanel.add(passLbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        passwordField = new JPasswordField(15);
        formPanel.add(passwordField, gbc);
        row++;

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        JLabel confirmPassLbl = new JLabel("Xác nhận mật khẩu (*):");
        confirmPassLbl.setFont(confirmPassLbl.getFont().deriveFont(Font.BOLD));
        formPanel.add(confirmPassLbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        confirmPasswordField = new JPasswordField(15);
        formPanel.add(confirmPasswordField, gbc);
        row++;

        // Activation code
        activationCodeField = addFormField(formPanel, gbc, "Mã kích hoạt (*):", row++, "VD: ACT-XXXXXXXX");

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 8));
        statusLabel = new JLabel("(*) Các trường bắt buộc nhập", JLabel.CENTER);
        statusLabel.setForeground(new Color(100, 100, 100));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        registerButton = new JButton("Đăng ký");
        registerButton.setFont(registerButton.getFont().deriveFont(Font.BOLD));
        registerButton.setPreferredSize(new Dimension(110, 32));
        registerButton.addActionListener(e -> performRegister());

        cancelButton = new JButton("Hủy bỏ");
        cancelButton.setPreferredSize(new Dimension(90, 32));
        cancelButton.addActionListener(e -> dispose());

        btnPanel.add(registerButton);
        btnPanel.add(cancelButton);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
        getRootPane().setDefaultButton(registerButton);
    }

    private JTextField addFormField(JPanel panel, GridBagConstraints gbc, String labelText, int row, String tooltip) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        JTextField field = new JTextField(15);
        if (tooltip != null) {
            field.setToolTipText(tooltip);
        }
        panel.add(field, gbc);
        return field;
    }

    private void performRegister() {
        String studentCode = studentCodeField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        String activationCode = activationCodeField.getText().trim();

        if (studentCode.isEmpty() || fullName.isEmpty() || username.isEmpty() || password.isEmpty() || activationCode.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng điền đầy đủ các thông tin bắt buộc (*).",
                    "Thiếu Thông Tin",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (password.length() < 6) {
            JOptionPane.showMessageDialog(this,
                    "Mật khẩu phải có độ dài tối thiểu 6 ký tự.",
                    "Mật Khẩu Quá Ngắn",
                    JOptionPane.WARNING_MESSAGE);
            passwordField.requestFocusInWindow();
            return;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this,
                    "Mật khẩu xác nhận không khớp.",
                    "Lỗi Xác Nhận Mật Khẩu",
                    JOptionPane.WARNING_MESSAGE);
            confirmPasswordField.requestFocusInWindow();
            return;
        }

        RegisterStudentRequestDTO request = new RegisterStudentRequestDTO(
                studentCode, fullName, email, username, password, confirmPassword, activationCode);

        registerButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang xử lý đăng ký với máy chủ...");

        AsyncWorker.run(
                () -> authController.registerStudent(request),
                (Boolean success) -> {
                    JOptionPane.showMessageDialog(this,
                            "Chúc mừng! Bạn đã đăng ký tài khoản thành công.\n"
                            + "Tên đăng nhập: " + username + "\n"
                            + "Bây giờ bạn có thể đăng nhập vào hệ thống.",
                            "Đăng Ký Thành Công",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                },
                (Exception ex) -> {
                    registerButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    statusLabel.setText("Đăng ký thất bại.");
                    String msg = ex.getMessage() != null && !ex.getMessage().isEmpty()
                            ? ex.getMessage()
                            : "Đã xảy ra lỗi trong quá trình đăng ký.";
                    JOptionPane.showMessageDialog(this, msg, "Lỗi Đăng Ký", JOptionPane.ERROR_MESSAGE);
                }
        );
    }
}
