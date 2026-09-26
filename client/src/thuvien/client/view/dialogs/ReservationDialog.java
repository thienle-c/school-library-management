package thuvien.client.view.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import thuvien.client.controller.ClientReservationController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.ReservationDTO;

/**
 * Modal dialog for reserving a book.
 */
public class ReservationDialog extends JDialog {
    private final ClientReservationController reservationController;
    private boolean saved = false;

    private JTextField bookIdField;
    private JTextField studentIdField;
    private JButton submitButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public ReservationDialog(Window owner, NetworkClient networkClient, Long defaultBookId, Long defaultStudentId) {
        super(owner, "Đặt Trước Sách (Giữ Chỗ)", ModalityType.APPLICATION_MODAL);
        this.reservationController = new ClientReservationController(networkClient);

        initUI(defaultBookId, defaultStudentId);
    }

    private void initUI(Long defaultBookId, Long defaultStudentId) {
        setSize(440, 270);
        setLocationRelativeTo(getOwner());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Book ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mã ID sách:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        bookIdField = new JTextField(defaultBookId != null ? defaultBookId.toString() : "", 15);
        formPanel.add(bookIdField, gbc);
        row++;

        // Student ID (Staff can specify; For Student role, it's auto-resolved)
        boolean isStudent = ClientSession.getInstance().isStudent();
        if (!isStudent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
            formPanel.add(new JLabel("Mã ID sinh viên:*"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            studentIdField = new JTextField(defaultStudentId != null ? defaultStudentId.toString() : "", 15);
            formPanel.add(studentIdField, gbc);
            row++;
        }

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom area
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel("Nhập mã ID sách để đặt trước.", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        cancelButton = new JButton("Hủy bỏ");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        submitButton = new JButton("Xác Nhận Đặt Trước");
        submitButton.setPreferredSize(new Dimension(160, 30));
        submitButton.setFont(submitButton.getFont().deriveFont(Font.BOLD));
        submitButton.addActionListener(e -> performReservation());
        buttonPanel.add(submitButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(submitButton);
    }

    private void performReservation() {
        String bookStr = bookIdField.getText().trim();
        if (bookStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã ID sách.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long bookId;
        try {
            bookId = Long.parseLong(bookStr);
            if (bookId <= 0) {
                JOptionPane.showMessageDialog(this, "Mã ID sách phải là số nguyên dương.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Mã ID sách phải là số hợp lệ.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long studentId = null;
        if (!ClientSession.getInstance().isStudent()) {
            String stuStr = studentIdField.getText().trim();
            if (stuStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã ID sinh viên.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                studentId = Long.parseLong(stuStr);
                if (studentId <= 0) {
                    JOptionPane.showMessageDialog(this, "Mã ID sinh viên phải là số nguyên dương.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Mã ID sinh viên phải là số hợp lệ.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        final Long finalStudentId = studentId;
        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang gửi yêu cầu đặt trước đến máy chủ...");

        AsyncWorker.run(
                () -> reservationController.createReservation(bookId, finalStudentId),
                (ReservationDTO r) -> {
                    saved = true;
                    String msg = String.format("Đặt trước sách thành công!\nMã đặt trước: #%d\nTên sách: %s\nTrạng thái: %s\nHạn giữ chỗ: %s",
                            r.getId(), r.getBookTitle(), r.getStatus(), r.getExpiryDate());
                    JOptionPane.showMessageDialog(this, msg, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                },
                (Exception ex) -> {
                    submitButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    statusLabel.setText("Đặt trước thất bại.");
                    JOptionPane.showMessageDialog(this, "Đặt trước sách thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    public boolean isSaved() {
        return saved;
    }
}
