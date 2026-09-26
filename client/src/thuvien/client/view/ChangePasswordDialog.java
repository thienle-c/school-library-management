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
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;

/**
 * Dialog for authenticated users to change their password securely.
 */
public class ChangePasswordDialog extends JDialog {
    private final ClientAuthController authController;

    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;

    private JButton saveButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public ChangePasswordDialog(Frame parent, NetworkClient networkClient) {
        super(parent, "Đổi Mật Khẩu", true);
        this.authController = new ClientAuthController(networkClient);
        initUI();
    }

    private void initUI() {
        setSize(400, 260);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Current Password
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        JLabel curLbl = new JLabel("Mật khẩu hiện tại:");
        curLbl.setFont(curLbl.getFont().deriveFont(Font.BOLD));
        formPanel.add(curLbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        currentPasswordField = new JPasswordField(15);
        formPanel.add(currentPasswordField, gbc);

        // New Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        JLabel newLbl = new JLabel("Mật khẩu mới:");
        newLbl.setFont(newLbl.getFont().deriveFont(Font.BOLD));
        formPanel.add(newLbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        newPasswordField = new JPasswordField(15);
        formPanel.add(newPasswordField, gbc);

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        JLabel confLbl = new JLabel("Xác nhận mật khẩu:");
        confLbl.setFont(confLbl.getFont().deriveFont(Font.BOLD));
        formPanel.add(confLbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        confirmPasswordField = new JPasswordField(15);
        formPanel.add(confirmPasswordField, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 8));
        statusLabel = new JLabel("Mật khẩu mới phải có ít nhất 6 ký tự.", JLabel.CENTER);
        statusLabel.setForeground(new Color(100, 100, 100));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        saveButton = new JButton("Lưu thay đổi");
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.setPreferredSize(new Dimension(120, 32));
        saveButton.addActionListener(e -> performChangePassword());

        cancelButton = new JButton("Hủy bỏ");
        cancelButton.setPreferredSize(new Dimension(90, 32));
        cancelButton.addActionListener(e -> dispose());

        btnPanel.add(saveButton);
        btnPanel.add(cancelButton);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
        getRootPane().setDefaultButton(saveButton);
    }

    private void performChangePassword() {
        String currentPass = new String(currentPasswordField.getPassword());
        String newPass = new String(newPasswordField.getPassword());
        String confirmPass = new String(confirmPasswordField.getPassword());

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ các trường thông tin.",
                    "Lỗi Nhập Liệu",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (newPass.length() < 6) {
            JOptionPane.showMessageDialog(this,
                    "Mật khẩu mới phải có tối thiểu 6 ký tự.",
                    "Mật Khẩu Không Hợp Lệ",
                    JOptionPane.WARNING_MESSAGE);
            newPasswordField.requestFocusInWindow();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this,
                    "Mật khẩu mới và xác nhận mật khẩu không trùng khớp.",
                    "Lỗi Khớp Mật Khẩu",
                    JOptionPane.WARNING_MESSAGE);
            confirmPasswordField.requestFocusInWindow();
            return;
        }

        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang gửi yêu cầu đổi mật khẩu...");

        AsyncWorker.run(
                () -> authController.changePassword(currentPass, newPass),
                (Boolean success) -> {
                    JOptionPane.showMessageDialog(this,
                            "Đổi mật khẩu thành công!",
                            "Thông Báo",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                },
                (Exception ex) -> {
                    saveButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    statusLabel.setText("Đổi mật khẩu thất bại.");
                    String msg = ex.getMessage() != null && !ex.getMessage().isEmpty()
                            ? ex.getMessage()
                            : "Không thể đổi mật khẩu.";
                    JOptionPane.showMessageDialog(this, msg, "Lỗi Đổi Mật Khẩu", JOptionPane.ERROR_MESSAGE);
                }
        );
    }
}
