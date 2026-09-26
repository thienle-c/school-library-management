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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import thuvien.client.controller.ClientStudentController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.StudentStatus;

/**
 * Modal dialog for creating and editing Student records.
 */
public class StudentFormDialog extends JDialog {
    private final ClientStudentController studentController;
    private final StudentDTO studentToEdit;
    private boolean saved = false;

    private JTextField codeField;
    private JTextField nameField;
    private JTextField classField;
    private JTextField phoneField;
    private JTextField emailField;
    private JSpinner limitSpinner;
    private JComboBox<StudentStatus> statusCombo;

    private JButton saveButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public StudentFormDialog(Window owner, NetworkClient networkClient, StudentDTO studentToEdit) {
        super(owner, studentToEdit == null ? "Thêm Sinh Viên Mới" : "Chỉnh Sửa Sinh Viên (" + studentToEdit.getStudentCode() + ")", ModalityType.APPLICATION_MODAL);
        this.studentController = new ClientStudentController(networkClient);
        this.studentToEdit = studentToEdit;

        initUI();
    }

    private void initUI() {
        setSize(480, 430);
        setLocationRelativeTo(getOwner());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Code
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mã sinh viên:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        codeField = new JTextField(studentToEdit != null ? studentToEdit.getStudentCode() : "", 15);
        if (studentToEdit != null) {
            codeField.setEditable(false);
        }
        formPanel.add(codeField, gbc);
        row++;

        // Full Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Họ và tên:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        nameField = new JTextField(studentToEdit != null ? studentToEdit.getFullName() : "", 15);
        formPanel.add(nameField, gbc);
        row++;

        // Class Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Lớp / Khóa:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        classField = new JTextField(studentToEdit != null && studentToEdit.getClassName() != null ? studentToEdit.getClassName() : "", 15);
        formPanel.add(classField, gbc);
        row++;

        // Phone
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Số điện thoại:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        phoneField = new JTextField(studentToEdit != null && studentToEdit.getPhone() != null ? studentToEdit.getPhone() : "", 15);
        formPanel.add(phoneField, gbc);
        row++;

        // Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Địa chỉ Email:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        emailField = new JTextField(studentToEdit != null && studentToEdit.getEmail() != null ? studentToEdit.getEmail() : "", 15);
        formPanel.add(emailField, gbc);
        row++;

        // Max Borrow Limit
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Hạn mức mượn:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        int limit = studentToEdit != null ? studentToEdit.getMaxBorrowLimit() : 5;
        limitSpinner = new JSpinner(new SpinnerNumberModel(limit, 1, 20, 1));
        formPanel.add(limitSpinner, gbc);
        row++;

        // Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Trạng thái:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        statusCombo = new JComboBox<>(StudentStatus.values());
        if (studentToEdit != null && studentToEdit.getStatus() != null) {
            statusCombo.setSelectedItem(studentToEdit.getStatus());
        } else {
            statusCombo.setSelectedItem(StudentStatus.ACTIVE);
        }
        formPanel.add(statusCombo, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom area
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel(" ", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        cancelButton = new JButton("Hủy bỏ");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        saveButton = new JButton(studentToEdit == null ? "Tạo Sinh Viên" : "Lưu Thay Đổi");
        saveButton.setPreferredSize(new Dimension(130, 30));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> saveStudent());
        buttonPanel.add(saveButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(saveButton);
    }

    private void saveStudent() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();

        if (code.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Mã sinh viên và Họ tên.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StudentDTO student = (studentToEdit != null) ? studentToEdit : new StudentDTO();
        student.setStudentCode(code);
        student.setFullName(name);
        student.setClassName(classField.getText().trim());
        student.setPhone(phoneField.getText().trim());
        student.setEmail(emailField.getText().trim());
        student.setMaxBorrowLimit((Integer) limitSpinner.getValue());
        student.setStatus((StudentStatus) statusCombo.getSelectedItem());

        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang lưu thông tin sinh viên lên máy chủ...");

        if (studentToEdit == null) {
            // Create
            AsyncWorker.run(
                    () -> studentController.createStudent(student),
                    (Long newId) -> {
                        saved = true;
                        JOptionPane.showMessageDialog(this, "Tạo hồ sơ sinh viên thành công! Mã ID: " + newId, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    },
                    (Exception ex) -> {
                        saveButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        statusLabel.setText("Lưu thất bại.");
                        JOptionPane.showMessageDialog(this, "Thêm sinh viên thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
            );
        } else {
            // Update
            AsyncWorker.run(
                    () -> studentController.updateStudent(student),
                    (Boolean ok) -> {
                        saved = true;
                        JOptionPane.showMessageDialog(this, "Cập nhật hồ sơ sinh viên thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    },
                    (Exception ex) -> {
                        saveButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        statusLabel.setText("Cập nhật thất bại.");
                        JOptionPane.showMessageDialog(this, "Cập nhật sinh viên thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
            );
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
