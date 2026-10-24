package thuvien.client.view.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientBookController;
import thuvien.client.controller.ClientBorrowController;
import thuvien.client.controller.ClientStudentController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.session.PermissionHelper;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.BookDTO;
import thuvien.client.view.common.ModernUIHelper;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.BorrowRequestDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.BookStatus;
import thuvien.common.enums.StudentStatus;

/**
 * Modal dialog for creating a new Borrow transaction.
 */
public class BorrowDialog extends JDialog {
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    private final NetworkClient networkClient;
    private final ClientBorrowController borrowController;
    private final ClientStudentController studentController;
    private final ClientBookController bookController;

    private boolean saved = false;
    private StudentDTO resolvedStudent;
    private BookDTO resolvedBook;

    private JLabel studentInfoLabel;
    private JLabel bookInfoLabel;
    private JLabel dueDatePreviewLabel;
    private JButton lookupStudentBtn;
    private JButton browseStudentBtn;
    private JButton lookupBookBtn;
    private JButton browseBookBtn;
    private JButton checkConditionBtn;

    public BorrowDialog(Window owner, NetworkClient networkClient, Long defaultStudentId, Long defaultBookId) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.networkClient = networkClient;
        this.borrowController = new ClientBorrowController(networkClient);
        this.studentController = new ClientStudentController(networkClient);
        this.bookController = new ClientBookController(networkClient);

        if (!PermissionHelper.canManageBorrowReturn(networkClient)) {
            JOptionPane.showMessageDialog(owner, "Bạn không có quyền lập phiếu mượn sách.", "Từ chối truy cập", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        initComponents();
        initCustom(defaultStudentId, defaultBookId);
    }

    private void initCustom(Long defaultStudentId, Long defaultBookId) {

        ((javax.swing.JComponent) getContentPane()).setBorder(
                javax.swing.BorderFactory.createEmptyBorder(15, 20, 15, 20));
        setSize(680, 520);
        setMinimumSize(new Dimension(640, 480));
        setLocationRelativeTo(getOwner());
        getRootPane().setDefaultButton(submitButton);

        // Giao diện mới từ main
        ModernUIHelper.styleDialog(this);
        formPanel.setBackground(ModernUIHelper.BG_SURFACE);
        formPanel.setBorder(ModernUIHelper.createRoundedBorder(
                ModernUIHelper.BORDER_COLOR, 10, 14, 16, 14, 16));
        bottomPanel.setOpaque(false);
        buttonPanel.setOpaque(false);

        ModernUIHelper.styleTextField(studentIdField);
        ModernUIHelper.styleTextField(bookIdField);
        ModernUIHelper.styleSpinner(durationSpinner);
        ModernUIHelper.styleTextField(notesField);

        ModernUIHelper.styleLabel(lblStudentId, "Sinh viên (Mã SV / ID):*");
        ModernUIHelper.styleLabel(lblBookId, "Sách (ISBN / ID / Tên):*");
        ModernUIHelper.styleLabel(lblDuration, "Thời hạn mượn (ngày):*");
        ModernUIHelper.styleLabel(lblNotes, "Ghi chú phiếu mượn:");

        statusLabel.setForeground(ModernUIHelper.TEXT_MUTED);

        ModernUIHelper.stylePrimaryButton(submitButton, "Xác Nhận Mượn");
        submitButton.setIcon(ModernUIHelper.getVectorIcon(
                "CHECK", 13, java.awt.Color.WHITE));
        submitButton.setIconTextGap(6);

        ModernUIHelper.styleSecondaryButton(cancelButton, "Đóng");

        // Hướng dẫn nhập liệu
        studentIdField.setToolTipText(
                "Nhập Mã SV hoặc ID rồi bấm Tra cứu");
        bookIdField.setToolTipText(
                "Nhập ISBN, tên sách hoặc ID rồi bấm Tra cứu");

        // Action panel for Student
        JPanel studentActionPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 4, 0));
        studentActionPanel.setOpaque(false);

        lookupStudentBtn = new JButton("Tra cứu");
        lookupStudentBtn.setToolTipText(
                "Tra cứu thông tin sinh viên theo mã hoặc ID");
        browseStudentBtn = new JButton("Danh sách...");
        browseStudentBtn.setToolTipText(
                "Chọn sinh viên từ danh sách thư viện");

        studentActionPanel.add(lookupStudentBtn);
        studentActionPanel.add(browseStudentBtn);

        studentInfoLabel = new JLabel(
                "<html><font color='gray'>Nhập Mã SV rồi bấm Tra cứu hoặc chọn từ danh sách.</font></html>");
        studentInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Action panel for Book
        JPanel bookActionPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 4, 0));
        bookActionPanel.setOpaque(false);

        lookupBookBtn = new JButton("Tra cứu");
        lookupBookBtn.setToolTipText(
                "Tra cứu thông tin sách theo ISBN, tiêu đề hoặc ID");
        browseBookBtn = new JButton("Danh sách...");
        browseBookBtn.setToolTipText(
                "Chọn sách từ danh mục thư viện");

        bookActionPanel.add(lookupBookBtn);
        bookActionPanel.add(browseBookBtn);

        bookInfoLabel = new JLabel(
                "<html><font color='gray'>Nhập ISBN hoặc tiêu đề rồi bấm Tra cứu hoặc chọn từ danh sách.</font></html>");
        bookInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Ngày trả dự kiến
        dueDatePreviewLabel = new JLabel();
        dueDatePreviewLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dueDatePreviewLabel.setForeground(new Color(0, 70, 150));
        updateDueDatePreview();

        durationSpinner.addChangeListener(e -> updateDueDatePreview());

        // Nút kiểm tra điều kiện mượn
        checkConditionBtn = new JButton("Kiểm Tra Điều Kiện");
        checkConditionBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkConditionBtn.setToolTipText(
                "Kiểm tra sinh viên và tình trạng sách trước khi mượn");
        ModernUIHelper.styleSecondaryButton(checkConditionBtn, "Kiểm Tra Điều Kiện");
        buttonPanel.add(checkConditionBtn, 0);

        // Sắp xếp lại form
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(6, 4, 6, 6);
        formPanel.add(studentActionPanel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 6, 8, 6);
        formPanel.add(studentInfoLabel, gbc);

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                lblBookId,
                new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                bookIdField,
                new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(6, 4, 6, 6);
        formPanel.add(bookActionPanel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 6, 8, 6);
        formPanel.add(bookInfoLabel, gbc);

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                lblDuration,
                new GridBagConstraints(0, 4, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                durationSpinner,
                new GridBagConstraints(1, 4, 1, 1, 1.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 8, 6, 6);
        formPanel.add(dueDatePreviewLabel, gbc);

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                lblNotes,
                new GridBagConstraints(0, 5, 1, 1, 0.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        ((GridBagLayout) formPanel.getLayout()).setConstraints(
                notesField,
                new GridBagConstraints(1, 5, 2, 1, 1.0, 0.0,
                        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                        new Insets(6, 6, 6, 6), 0, 0));

        // Event Listeners cho các chức năng tra cứu và kiểm tra
        lookupStudentBtn.addActionListener(e -> lookupStudent());
        studentIdField.addActionListener(e -> lookupStudent());
        browseStudentBtn.addActionListener(e -> browseStudents());

        lookupBookBtn.addActionListener(e -> lookupBook());
        bookIdField.addActionListener(e -> lookupBook());
        browseBookBtn.addActionListener(e -> browseBooks());

        checkConditionBtn.addActionListener(e -> checkConditions(true));


        if (defaultStudentId != null) {
            studentIdField.setText(defaultStudentId.toString());
            lookupStudent();
        }
        if (defaultBookId != null) {
            bookIdField.setText(defaultBookId.toString());
            lookupBook();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        formPanel = new javax.swing.JPanel();
        lblStudentId = new javax.swing.JLabel();
        studentIdField = new javax.swing.JTextField();
        lblBookId = new javax.swing.JLabel();
        bookIdField = new javax.swing.JTextField();
        lblDuration = new javax.swing.JLabel();
        durationSpinner = new javax.swing.JSpinner();
        lblNotes = new javax.swing.JLabel();
        notesField = new javax.swing.JTextField();
        bottomPanel = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        buttonPanel = new javax.swing.JPanel();
        cancelButton = new javax.swing.JButton();
        submitButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Lập Phiếu Mượn Sách");
        setModal(true);
        setResizable(false);
        getContentPane().setLayout(new java.awt.BorderLayout(10, 10));

        formPanel.setLayout(new java.awt.GridBagLayout());

        lblStudentId.setText("Mã ID sinh viên:*");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(lblStudentId, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(studentIdField, gridBagConstraints);

        lblBookId.setText("Mã ID sách:*");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(lblBookId, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(bookIdField, gridBagConstraints);

        lblDuration.setText("Thời hạn mượn (ngày):*");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(lblDuration, gridBagConstraints);

        durationSpinner.setModel(new javax.swing.SpinnerNumberModel(14, 1, 90, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(durationSpinner, gridBagConstraints);

        lblNotes.setText("Ghi chú:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(lblNotes, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 6, 6);
        formPanel.add(notesField, gridBagConstraints);

        getContentPane().add(formPanel, java.awt.BorderLayout.CENTER);

        bottomPanel.setLayout(new java.awt.BorderLayout(5, 5));

        statusLabel.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        statusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel.setText("Nhập thông tin mượn sách.");
        bottomPanel.add(statusLabel, java.awt.BorderLayout.NORTH);

        buttonPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 5));

        cancelButton.setText("Hủy bỏ");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        buttonPanel.add(cancelButton);

        submitButton.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        submitButton.setText("Xác Nhận Mượn");
        submitButton.setPreferredSize(new java.awt.Dimension(135, 30));
        submitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                submitButtonActionPerformed(evt);
            }
        });
        buttonPanel.add(submitButton);

        bottomPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(bottomPanel, java.awt.BorderLayout.SOUTH);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void submitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_submitButtonActionPerformed
        performBorrow();
    }//GEN-LAST:event_submitButtonActionPerformed

    private void updateDueDatePreview() {
        int duration = (Integer) durationSpinner.getValue();
        long dueMillis = System.currentTimeMillis() + (long) duration * 24 * 3600 * 1000L;
        if (dueDatePreviewLabel != null) {
            dueDatePreviewLabel.setText("Hạn trả: " + DATE_FMT.format(new Date(dueMillis)));
        }
    }

    private void lookupStudent() {
        String input = studentIdField.getText().trim();
        if (input.isEmpty()) {
            studentInfoLabel.setText("<html><font color='gray'>Nhập Mã SV (VD: STU001) hoặc Mã ID rồi bấm Tra cứu.</font></html>");
            resolvedStudent = null;
            return;
        }
        studentInfoLabel.setText("<html><font color='blue'>Đang tra cứu sinh viên...</font></html>");
        AsyncWorker.run(
                () -> {
                    try {
                        long id = Long.parseLong(input);
                        StudentDTO s = studentController.getStudent(id);
                        if (s != null) return s;
                    } catch (Exception ignored) {}
                    List<StudentDTO> students = studentController.listStudents();
                    for (StudentDTO s : students) {
                        if (input.equalsIgnoreCase(s.getStudentCode()) ||
                                String.valueOf(s.getId()).equals(input) ||
                                s.getFullName().toLowerCase().contains(input.toLowerCase())) {
                            return s;
                        }
                    }
                    return null;
                },
                (StudentDTO s) -> {
                    resolvedStudent = s;
                    if (s != null) {
                        studentIdField.setText(String.valueOf(s.getId()));
                        boolean canBorrow = s.getStatus() == StudentStatus.ACTIVE && s.getCurrentBorrowCount() < s.getMaxBorrowLimit();
                        String color = canBorrow ? "#006622" : "#cc0000";
                        String statusText = s.getStatus() == StudentStatus.ACTIVE ? "Đang hoạt động" : "TẠM KHÓA";
                        String warning = "";
                        if (s.getStatus() != StudentStatus.ACTIVE) {
                            warning = " &nbsp;<b style='color:#cc0000;'>[TÀI KHOẢN KHÔNG HOẠT ĐỘNG]</b>";
                        } else if (s.getCurrentBorrowCount() >= s.getMaxBorrowLimit()) {
                            warning = " &nbsp;<b style='color:#cc0000;'>[ĐÃ ĐẠT GIỚI HẠN MƯỢN TỐI ĐA]</b>";
                        }
                        studentInfoLabel.setText(String.format(
                                "<html><span style='color:%s;'>[HỢP LỆ] <b>%s</b> (Mã: %s, ID: %d) &nbsp;|&nbsp; Đang mượn: <b>%d/%d</b> quyển &nbsp;|&nbsp; TT: %s%s</span></html>",
                                color, s.getFullName(), s.getStudentCode(), s.getId(), s.getCurrentBorrowCount(), s.getMaxBorrowLimit(), statusText, warning
                        ));
                        statusLabel.setText("Đã nhận diện sinh viên " + s.getFullName() + " (" + s.getStudentCode() + ").");
                    } else {
                        studentInfoLabel.setText("<html><font color='#cc0000'>[!] Không tìm thấy sinh viên có mã hoặc tên: " + input + "</font></html>");
                        statusLabel.setText("Không tìm thấy sinh viên phù hợp.");
                    }
                },
                (Exception ex) -> {
                    resolvedStudent = null;
                    studentInfoLabel.setText("<html><font color='#cc0000'>Lỗi tra cứu: " + ex.getMessage() + "</font></html>");
                }
        );
    }

    private void browseStudents() {
        statusLabel.setText("Đang tải danh sách sinh viên...");
        AsyncWorker.run(
                () -> studentController.listStudents(),
                (List<StudentDTO> students) -> {
                    statusLabel.setText("Chọn sinh viên từ danh sách.");
                    if (students == null || students.isEmpty()) {
                        JOptionPane.showMessageDialog(BorrowDialog.this, "Không có sinh viên nào trong hệ thống.", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    showStudentSelectionDialog(students);
                },
                (Exception ex) -> {
                    statusLabel.setText("Lỗi: " + ex.getMessage());
                    JOptionPane.showMessageDialog(BorrowDialog.this, "Không thể tải danh sách sinh viên: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void showStudentSelectionDialog(List<StudentDTO> students) {
        JDialog dlg = new JDialog(this, "Chọn Sinh Viên Mượn Sách", true);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.setSize(620, 380);
        dlg.setLocationRelativeTo(this);

        String[] cols = {"Mã ID", "Mã SV", "Họ và Tên", "Lớp", "Đang Mượn / Hạn Mức", "Trạng Thái"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        for (StudentDTO s : students) {
            model.addRow(new Object[]{
                    s.getId(),
                    s.getStudentCode(),
                    s.getFullName(),
                    s.getClassName() != null ? s.getClassName() : "",
                    s.getCurrentBorrowCount() + " / " + s.getMaxBorrowLimit(),
                    s.getStatus() == StudentStatus.ACTIVE ? "Đang hoạt động" : "Tạm khóa"
            });
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setAutoCreateRowSorter(true);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(e -> dlg.dispose());
        JButton select = new JButton("Chọn Sinh Viên");
        select.setFont(new Font("Segoe UI", Font.BOLD, 12));
        select.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int modelRow = table.convertRowIndexToModel(row);
                Long id = (Long) model.getValueAt(modelRow, 0);
                for (StudentDTO s : students) {
                    if (s.getId().equals(id)) {
                        studentIdField.setText(String.valueOf(s.getId()));
                        lookupStudent();
                        dlg.dispose();
                        break;
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dlg, "Vui lòng chọn một sinh viên trong danh sách.", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    select.doClick();
                }
            }
        });

        btnPanel.add(cancel);
        btnPanel.add(select);

        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void lookupBook() {
        String input = bookIdField.getText().trim();
        if (input.isEmpty()) {
            bookInfoLabel.setText("<html><font color='gray'>Nhập ISBN (VD: 978-0134685991), Tên sách hoặc ID rồi bấm Tra cứu.</font></html>");
            resolvedBook = null;
            return;
        }
        bookInfoLabel.setText("<html><font color='blue'>Đang tra cứu sách...</font></html>");
        AsyncWorker.run(
                () -> {
                    try {
                        long id = Long.parseLong(input);
                        BookDTO b = bookController.getBook(id);
                        if (b != null) return b;
                    } catch (Exception ignored) {}
                    List<BookDTO> books = bookController.listBooks();
                    for (BookDTO b : books) {
                        if (input.equalsIgnoreCase(b.getIsbn()) ||
                                String.valueOf(b.getId()).equals(input) ||
                                b.getTitle().toLowerCase().contains(input.toLowerCase())) {
                            return b;
                        }
                    }
                    return null;
                },
                (BookDTO b) -> {
                    resolvedBook = b;
                    if (b != null) {
                        bookIdField.setText(String.valueOf(b.getId()));
                        boolean hasCopies = b.getAvailableCopies() > 0 && b.getStatus() == BookStatus.AVAILABLE;
                        String color = hasCopies ? "#006622" : "#cc0000";
                        String copiesWarning = hasCopies ? "" : " &nbsp;<b style='color:#cc0000;'>[HẾT BẢN SAO KHẢ DỤNG]</b>";
                        bookInfoLabel.setText(String.format(
                                "<html><span style='color:%s;'>[HỢP LỆ] <b>%s</b> (Mã ID: %d, ISBN: %s) &nbsp;|&nbsp; Có sẵn: <b>%d/%d</b> bản &nbsp;|&nbsp; TT: %s%s</span></html>",
                                color, b.getTitle(), b.getId(), b.getIsbn(), b.getAvailableCopies(), b.getTotalCopies(), b.getStatus(), copiesWarning
                        ));
                        statusLabel.setText("Đã nhận diện sách: " + b.getTitle() + " (Còn " + b.getAvailableCopies() + "/" + b.getTotalCopies() + " bản).");
                    } else {
                        bookInfoLabel.setText("<html><font color='#cc0000'>[!] Không tìm thấy sách có ISBN, tiêu đề hoặc ID: " + input + "</font></html>");
                        statusLabel.setText("Không tìm thấy sách phù hợp.");
                    }
                },
                (Exception ex) -> {
                    resolvedBook = null;
                    bookInfoLabel.setText("<html><font color='#cc0000'>Lỗi tra cứu: " + ex.getMessage() + "</font></html>");
                }
        );
    }

    private void browseBooks() {
        statusLabel.setText("Đang tải danh mục sách...");
        AsyncWorker.run(
                () -> bookController.listBooks(),
                (List<BookDTO> books) -> {
                    statusLabel.setText("Chọn sách từ danh mục.");
                    if (books == null || books.isEmpty()) {
                        JOptionPane.showMessageDialog(BorrowDialog.this, "Không có sách nào trong thư viện.", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    showBookSelectionDialog(books);
                },
                (Exception ex) -> {
                    statusLabel.setText("Lỗi: " + ex.getMessage());
                    JOptionPane.showMessageDialog(BorrowDialog.this, "Không thể tải danh mục sách: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void showBookSelectionDialog(List<BookDTO> books) {
        JDialog dlg = new JDialog(this, "Chọn Sách Cần Mượn", true);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.setSize(680, 400);
        dlg.setLocationRelativeTo(this);

        String[] cols = {"Mã ID", "Mã ISBN", "Tên Sách", "Thể Loại", "Có Sẵn / Tổng Số", "Trạng Thái"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        for (BookDTO b : books) {
            model.addRow(new Object[]{
                    b.getId(),
                    b.getIsbn(),
                    b.getTitle(),
                    b.getCategoryName() != null ? b.getCategoryName() : "",
                    b.getAvailableCopies() + " / " + b.getTotalCopies(),
                    b.getStatus() == BookStatus.AVAILABLE ? "Có sẵn" : b.getStatus().name()
            });
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setAutoCreateRowSorter(true);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(e -> dlg.dispose());
        JButton select = new JButton("Chọn Sách");
        select.setFont(new Font("Segoe UI", Font.BOLD, 12));
        select.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                int modelRow = table.convertRowIndexToModel(row);
                Long id = (Long) model.getValueAt(modelRow, 0);
                for (BookDTO b : books) {
                    if (b.getId().equals(id)) {
                        bookIdField.setText(String.valueOf(b.getId()));
                        lookupBook();
                        dlg.dispose();
                        break;
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dlg, "Vui lòng chọn một cuốn sách trong danh mục.", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    select.doClick();
                }
            }
        });

        btnPanel.add(cancel);
        btnPanel.add(select);

        dlg.add(new JScrollPane(table), BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private boolean checkConditions(boolean showSuccessPopup) {
        String stuStr = studentIdField.getText().trim();
        String bookStr = bookIdField.getText().trim();

        if (stuStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã SV hoặc Mã ID của sinh viên mượn sách.", "Kiểm Tra Điều Kiện", JOptionPane.WARNING_MESSAGE);
            studentIdField.requestFocusInWindow();
            return false;
        }

        if (resolvedStudent == null) {
            try {
                long id = Long.parseLong(stuStr);
                resolvedStudent = studentController.getStudent(id);
            } catch (Exception ignored) {}
            if (resolvedStudent == null) {
                try {
                    List<StudentDTO> list = studentController.listStudents();
                    for (StudentDTO s : list) {
                        if (stuStr.equalsIgnoreCase(s.getStudentCode()) || String.valueOf(s.getId()).equals(stuStr)) {
                            resolvedStudent = s;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (resolvedStudent == null) {
            JOptionPane.showMessageDialog(this, "Không thể xác định thông tin sinh viên '" + stuStr + "'.\nVui lòng bấm 'Tra cứu' hoặc chọn từ danh sách.", "Kiểm Tra Điều Kiện", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (resolvedStudent.getStatus() != StudentStatus.ACTIVE) {
            JOptionPane.showMessageDialog(this,
                    String.format("Sinh viên %s (%s) có trạng thái: %s.\nTài khoản không ở trạng thái hoạt động nên không được phép mượn sách.",
                            resolvedStudent.getFullName(), resolvedStudent.getStudentCode(), resolvedStudent.getStatus()),
                    "Không Đủ Điều Kiện", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (resolvedStudent.getCurrentBorrowCount() >= resolvedStudent.getMaxBorrowLimit()) {
            JOptionPane.showMessageDialog(this,
                    String.format("Sinh viên %s (%s) đã mượn %d/%d quyển sách (đã đạt giới hạn tối đa).\nVui lòng yêu cầu sinh viên trả bớt sách trước khi mượn tiếp.",
                            resolvedStudent.getFullName(), resolvedStudent.getStudentCode(), resolvedStudent.getCurrentBorrowCount(), resolvedStudent.getMaxBorrowLimit()),
                    "Vượt Giới Hạn Mượn", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (bookStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Mã ISBN, Tiêu đề hoặc Mã ID của sách cần mượn.", "Kiểm Tra Điều Kiện", JOptionPane.WARNING_MESSAGE);
            bookIdField.requestFocusInWindow();
            return false;
        }

        if (resolvedBook == null) {
            try {
                long id = Long.parseLong(bookStr);
                resolvedBook = bookController.getBook(id);
            } catch (Exception ignored) {}
            if (resolvedBook == null) {
                try {
                    List<BookDTO> list = bookController.listBooks();
                    for (BookDTO b : list) {
                        if (bookStr.equalsIgnoreCase(b.getIsbn()) || String.valueOf(b.getId()).equals(bookStr)) {
                            resolvedBook = b;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (resolvedBook == null) {
            JOptionPane.showMessageDialog(this, "Không thể xác định thông tin sách '" + bookStr + "'.\nVui lòng bấm 'Tra cứu' hoặc chọn từ danh sách.", "Kiểm Tra Điều Kiện", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (resolvedBook.getAvailableCopies() <= 0 || resolvedBook.getStatus() != BookStatus.AVAILABLE) {
            JOptionPane.showMessageDialog(this,
                    String.format("Sách '%s' hiện đã hết bản sao khả dụng (Có sẵn: %d/%d bản, Trạng thái: %s).\nKhông thể lập phiếu mượn lúc này.",
                            resolvedBook.getTitle(), resolvedBook.getAvailableCopies(), resolvedBook.getTotalCopies(), resolvedBook.getStatus()),
                    "Hết Sách Khả Dụng", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        statusLabel.setText("Đủ điều kiện mượn sách! Bấm 'Xác Nhận Mượn' để hoàn tất.");
        statusLabel.setForeground(new Color(20, 130, 50));

        if (showSuccessPopup) {
            JOptionPane.showMessageDialog(this,
                    String.format("KẾT QUẢ KIỂM TRA ĐIỀU KIỆN:\n\n" +
                                    "- Sinh viên: %s (Mã: %s, ID: %d)\n" +
                                    "  Trạng thái: Hoạt động | Số sách đang mượn: %d/%d\n\n" +
                                    "- Sách: %s (ISBN: %s, ID: %d)\n" +
                                    "  Trạng thái: Khả dụng | Bản sao có sẵn: %d/%d\n\n" +
                                    "==> HỒ SƠ HOÀN TOÀN HỢP LỆ VÀ ĐỦ ĐIỀU KIỆN CHO MƯỢN!",
                            resolvedStudent.getFullName(), resolvedStudent.getStudentCode(), resolvedStudent.getId(),
                            resolvedStudent.getCurrentBorrowCount(), resolvedStudent.getMaxBorrowLimit(),
                            resolvedBook.getTitle(), resolvedBook.getIsbn(), resolvedBook.getId(),
                            resolvedBook.getAvailableCopies(), resolvedBook.getTotalCopies()),
                    "Đủ Điều Kiện Mượn", JOptionPane.INFORMATION_MESSAGE);
        }
        return true;
    }

    private void performBorrow() {
        if (!PermissionHelper.canManageBorrowReturn(networkClient)) {
            JOptionPane.showMessageDialog(this, "Bạn không có quyền lập phiếu mượn sách.", "Từ chối truy cập", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!checkConditions(false)) {
            return;
        }

        long studentId = resolvedStudent.getId();
        long bookId = resolvedBook.getId();
        int duration = (Integer) durationSpinner.getValue();
        String notes = notesField.getText().trim();

        BorrowRequestDTO req = new BorrowRequestDTO(studentId, bookId, duration, notes);

        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        checkConditionBtn.setEnabled(false);
        statusLabel.setForeground(new Color(0, 70, 150));
        statusLabel.setText("Đang gửi giao dịch mượn sách tới máy chủ...");

        AsyncWorker.run(
                () -> borrowController.borrowBook(req),
                (BorrowRecordDTO record) -> {
                    saved = true;
                    String msg = String.format(
                            "CHO MƯỢN SÁCH THÀNH CÔNG!\n\n" +
                            "- Mã phiếu mượn: #%d\n" +
                            "- Sinh viên: %s (Mã SV: %s)\n" +
                            "- Tên sách: %s (ISBN: %s)\n" +
                            "- Ngày mượn: %s\n" +
                            "- Hạn trả sách: %s\n" +
                            "- Trạng thái: %s",
                            record.getId(), record.getStudentName(), record.getStudentCode(),
                            record.getBookTitle(), record.getBookIsbn(),
                            record.getBorrowDate() != null ? DATE_FMT.format(record.getBorrowDate()) : "N/A",
                            record.getDueDate() != null ? DATE_FMT.format(record.getDueDate()) : "N/A",
                            record.getStatus()
                    );
                    JOptionPane.showMessageDialog(this, msg, "Lập Phiếu Mượn Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                },
                (Exception ex) -> {
                    submitButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    checkConditionBtn.setEnabled(true);
                    statusLabel.setForeground(new Color(180, 40, 40));
                    statusLabel.setText("Lập phiếu mượn thất bại: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lập phiếu mượn thất bại:\n" + ex.getMessage(), "Lỗi Mượn Sách", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    public boolean isSaved() {
        return saved;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField bookIdField;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JSpinner durationSpinner;
    private javax.swing.JPanel formPanel;
    private javax.swing.JLabel lblBookId;
    private javax.swing.JLabel lblDuration;
    private javax.swing.JLabel lblNotes;
    private javax.swing.JLabel lblStudentId;
    private javax.swing.JTextField notesField;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JTextField studentIdField;
    private javax.swing.JButton submitButton;
    // End of variables declaration//GEN-END:variables
}
