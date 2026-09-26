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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import thuvien.client.controller.ClientBorrowController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;

/**
 * Modal dialog for creating a new Borrow transaction.
 */
public class BorrowDialog extends JDialog {
    private final ClientBorrowController borrowController;
    private boolean saved = false;

    private JTextField studentIdField;
    private JTextField bookIdField;
    private JSpinner durationSpinner;
    private JTextField notesField;
    private JButton submitButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public BorrowDialog(Window owner, NetworkClient networkClient, Long defaultStudentId, Long defaultBookId) {
        super(owner, "Lập Phiếu Mượn Sách", ModalityType.APPLICATION_MODAL);
        this.borrowController = new ClientBorrowController(networkClient);

        initUI(defaultStudentId, defaultBookId);
    }

    private void initUI(Long defaultStudentId, Long defaultBookId) {
        setSize(460, 330);
        setLocationRelativeTo(getOwner());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Student ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mã ID sinh viên:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        studentIdField = new JTextField(defaultStudentId != null ? defaultStudentId.toString() : "", 15);
        if (ClientSession.getInstance().isStudent()) {
            studentIdField.setToolTipText("Mã ID hồ sơ sinh viên của bạn");
        }
        formPanel.add(studentIdField, gbc);
        row++;

        // Book ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mã ID sách:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        bookIdField = new JTextField(defaultBookId != null ? defaultBookId.toString() : "", 15);
        formPanel.add(bookIdField, gbc);
        row++;

        // Duration Days
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Thời hạn mượn (ngày):*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        durationSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 90, 1));
        formPanel.add(durationSpinner, gbc);
        row++;

        // Notes
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        notesField = new JTextField("", 15);
        formPanel.add(notesField, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom area
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel("Nhập thông tin mượn sách.", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        cancelButton = new JButton("Hủy bỏ");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        submitButton = new JButton("Xác Nhận Mượn");
        submitButton.setPreferredSize(new Dimension(135, 30));
        submitButton.setFont(submitButton.getFont().deriveFont(Font.BOLD));
        submitButton.addActionListener(e -> performBorrow());
        buttonPanel.add(submitButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(submitButton);
    }

    private void performBorrow() {
        String stuStr = studentIdField.getText().trim();
        String bookStr = bookIdField.getText().trim();

        if (stuStr.isEmpty() || bookStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập cả Mã ID sinh viên và Mã ID sách.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long studentId;
        long bookId;
        try {
            studentId = Long.parseLong(stuStr);
            bookId = Long.parseLong(bookStr);
            if (studentId <= 0 || bookId <= 0) {
                JOptionPane.showMessageDialog(this, "Mã ID phải là số nguyên dương.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Mã ID phải là giá trị số hợp lệ.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int duration = (Integer) durationSpinner.getValue();
        String notes = notesField.getText().trim();

        BorrowRequestDTO req = new BorrowRequestDTO(studentId, bookId, duration, notes);

        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang xử lý yêu cầu mượn sách trên máy chủ...");

        AsyncWorker.run(
                () -> borrowController.borrowBook(req),
                (BorrowRecordDTO record) -> {
                    saved = true;
                    String msg = String.format("Cho mượn sách thành công!\nMã phiếu: #%d\nTên sách: %s\nSinh viên: %s\nHạn trả: %s",
                            record.getId(), record.getBookTitle(), record.getStudentName(), record.getDueDate());
                    JOptionPane.showMessageDialog(this, msg, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                },
                (Exception ex) -> {
                    submitButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    statusLabel.setText("Mượn sách thất bại.");
                    JOptionPane.showMessageDialog(this, "Lập phiếu mượn thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    public boolean isSaved() {
        return saved;
    }
}
