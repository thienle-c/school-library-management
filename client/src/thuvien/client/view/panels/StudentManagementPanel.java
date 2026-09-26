package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.controller.ClientStudentController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.client.view.dialogs.StudentFormDialog;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.StudentStatus;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Panel providing student account management for staff (Librarian/Admin)
 * and personal profile inspection for student accounts.
 */
public class StudentManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(StudentManagementPanel.class.getName());
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientStudentController studentController;
    private final boolean isStaff;

    // Staff controls
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JButton refreshBtn;
    private JButton newStudentBtn;
    private JButton editStudentBtn;
    private JButton suspendStudentBtn;
    private JButton hardDeleteStudentBtn;
    private JButton activationCodeBtn;
    private JLabel statusLabel;

    // Detail panel
    private JLabel detailIdVal;
    private JLabel detailCodeVal;
    private JLabel detailNameVal;
    private JLabel detailClassVal;
    private JLabel detailPhoneVal;
    private JLabel detailEmailVal;
    private JLabel detailBorrowVal;
    private JLabel detailStatusVal;

    // Student self profile labels
    private JLabel profCodeVal;
    private JLabel profNameVal;
    private JLabel profClassVal;
    private JLabel profPhoneVal;
    private JLabel profEmailVal;
    private JLabel profBorrowVal;
    private JLabel profStatusVal;

    private List<StudentDTO> allStudents = new ArrayList<>();
    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public StudentManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.studentController = new ClientStudentController(networkClient);
        this.isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        if (isStaff) {
            initStaffUI();
            loadStudents();
            initAutoRefresh();
        } else {
            initStudentProfileUI();
            loadStudentProfile();
        }
    }

    private void initStaffUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // Top Toolbar
        JPanel topToolbar = new JPanel(new BorderLayout(10, 0));
        topToolbar.setOpaque(false);
        topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        leftPanel.add(new JLabel("Tìm kiếm sinh viên:"));
        searchField = new JTextField(14);
        searchField.setToolTipText("Tìm kiếm theo Mã SV, Họ tên, Lớp, Email, SĐT hoặc Mã ID");
        searchField.addActionListener(e -> applyFilter(false));
        SearchAutoCompleteHelper.attach(searchField, "STUDENT", new ClientSearchSuggestionController(networkClient), () -> applyFilter(false));
        leftPanel.add(searchField);

        statusFilterCombo = new JComboBox<>(new String[]{"Tất cả trạng thái", "Đang hoạt động", "Tạm khóa", "Đã tốt nghiệp"});
        statusFilterCombo.addActionListener(e -> applyFilter(false));
        leftPanel.add(statusFilterCombo);

        JButton filterBtn = new JButton("Tìm");
        filterBtn.addActionListener(e -> applyFilter(false));
        leftPanel.add(filterBtn);

        JButton resetBtn = new JButton("Xóa bộ lọc");
        resetBtn.addActionListener(e -> {
            searchField.setText("");
            statusFilterCombo.setSelectedIndex(0);
            applyFilter(false);
        });
        leftPanel.add(resetBtn);

        refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> loadStudents());
        leftPanel.add(refreshBtn);

        topToolbar.add(leftPanel, BorderLayout.WEST);

        // Action buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        newStudentBtn = new JButton("Thêm Sinh Viên");
        newStudentBtn.setBackground(new Color(230, 245, 230));
        newStudentBtn.addActionListener(e -> openCreateDialog());
        rightPanel.add(newStudentBtn);

        editStudentBtn = new JButton("Chỉnh Sửa");
        editStudentBtn.setEnabled(false);
        editStudentBtn.addActionListener(e -> openEditDialog());
        rightPanel.add(editStudentBtn);

        // "Tạm khóa" button (available for Admin & Librarian)
        suspendStudentBtn = new JButton("Tạm Khóa");
        suspendStudentBtn.setBackground(new Color(255, 245, 230));
        suspendStudentBtn.setEnabled(false);
        suspendStudentBtn.setToolTipText("Chuyển trạng thái sinh viên sang tạm khóa/ngưng hoạt động");
        suspendStudentBtn.addActionListener(e -> suspendSelectedStudent());
        rightPanel.add(suspendStudentBtn);

        // Admin-only actions: Cấp mã kích hoạt & Xóa vĩnh viễn
        if (ClientSession.getInstance().isAdmin()) {
            activationCodeBtn = new JButton("Cấp Mã Kích Hoạt");
            activationCodeBtn.setBackground(new Color(230, 240, 255));
            activationCodeBtn.setEnabled(false);
            activationCodeBtn.setToolTipText("Tạo mã kích hoạt an toàn để sinh viên tự đăng ký tài khoản");
            activationCodeBtn.addActionListener(e -> generateActivationCodeForSelectedStudent());
            rightPanel.add(activationCodeBtn);

            hardDeleteStudentBtn = new JButton("Xóa Vĩnh Viễn");
            hardDeleteStudentBtn.setBackground(new Color(255, 230, 230));
            hardDeleteStudentBtn.setForeground(new Color(180, 0, 0));
            hardDeleteStudentBtn.setFont(hardDeleteStudentBtn.getFont().deriveFont(Font.BOLD));
            hardDeleteStudentBtn.setEnabled(false);
            hardDeleteStudentBtn.setToolTipText("Xóa vĩnh viễn sinh viên khỏi cơ sở dữ liệu (Chỉ Quản trị viên)");
            hardDeleteStudentBtn.addActionListener(e -> hardDeleteSelectedStudent());
            rightPanel.add(hardDeleteStudentBtn);
        }

        topToolbar.add(rightPanel, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // Table
        String[] columns = {"Mã ID", "Mã Sinh Viên", "Họ Và Tên", "Lớp / Khóa", "Điện Thoại", "Địa Chỉ Email", "Đang Mượn / Tối Đa", "Trạng Thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        studentTable = new JTable(tableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(24);
        studentTable.getTableHeader().setFont(studentTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        studentTable.setAutoCreateRowSorter(true);

        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateStaffDetails();
            }
        });

        JScrollPane tableScroll = new JScrollPane(studentTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));

        JPanel detailCard = createStaffDetailPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailCard);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerSize(6);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Status
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        bottomBar.add(statusLabel, BorderLayout.WEST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void initAutoRefresh() {
        pollTimer = new javax.swing.Timer(6000, e -> {
            if (isShowing() && isDisplayable() && !isRefreshing) {
                loadStudentsSilently();
            }
        });
        addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent event) {
                if (pollTimer != null && !pollTimer.isRunning()) {
                    pollTimer.start();
                }
            }
            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent event) {
                if (pollTimer != null && pollTimer.isRunning()) {
                    pollTimer.stop();
                }
            }
            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent event) {}
        });
    }

    private JPanel createStaffDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JLabel header = new JLabel("Thông Tin Hồ Sơ Sinh Viên Được Chọn");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13.0f));
        panel.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        detailIdVal = new JLabel("Chưa chọn");
        detailCodeVal = new JLabel("Chưa chọn");
        detailNameVal = new JLabel("Chưa chọn");
        detailClassVal = new JLabel("Chưa chọn");
        detailPhoneVal = new JLabel("Chưa chọn");
        detailEmailVal = new JLabel("Chưa chọn");
        detailBorrowVal = new JLabel("Chưa chọn");
        detailStatusVal = new JLabel("Chưa chọn");

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        grid.add(new JLabel("Mã ID sinh viên:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailIdVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Mã số sinh viên:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailCodeVal, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        grid.add(new JLabel("Họ và tên:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailNameVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Lớp / Khóa:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailClassVal, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Số điện thoại:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailPhoneVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Địa chỉ Email:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailEmailVal, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        grid.add(new JLabel("Sách đang mượn / Hạn mức:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailBorrowVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailStatusVal, gbc);

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private void initStudentProfileUI() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        headerPanel.setOpaque(false);
        JLabel title = new JLabel("Hồ Sơ Cá Nhân Sinh Viên");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20.0f));
        JLabel subtitle = new JLabel("Thông tin tài khoản thư viện và hạn mức mượn sách");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12.5f));
        subtitle.setForeground(Color.GRAY);
        headerPanel.add(title);
        headerPanel.add(subtitle);
        add(headerPanel, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(new Color(250, 252, 255));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 238), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        profCodeVal = new JLabel("Đang tải...");
        profNameVal = new JLabel("Đang tải...");
        profClassVal = new JLabel("Đang tải...");
        profPhoneVal = new JLabel("Đang tải...");
        profEmailVal = new JLabel("Đang tải...");
        profBorrowVal = new JLabel("Đang tải...");
        profStatusVal = new JLabel("Đang tải...");

        addProfileRow(card, gbc, 0, "Mã số sinh viên:", profCodeVal);
        addProfileRow(card, gbc, 1, "Họ và tên:", profNameVal);
        addProfileRow(card, gbc, 2, "Lớp / Khóa học:", profClassVal);
        addProfileRow(card, gbc, 3, "Số điện thoại:", profPhoneVal);
        addProfileRow(card, gbc, 4, "Địa chỉ Email:", profEmailVal);
        addProfileRow(card, gbc, 5, "Tình trạng mượn sách:", profBorrowVal);
        addProfileRow(card, gbc, 6, "Trạng thái tài khoản:", profStatusVal);

        add(card, BorderLayout.CENTER);
    }

    private void addProfileRow(JPanel panel, GridBagConstraints gbc, int row, String label, JLabel valueLabel) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13.0f));
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 0.7;
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 13.0f));
        panel.add(valueLabel, gbc);
    }

    public void loadStudents() {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshBtn.setEnabled(false);
        statusLabel.setText("Đang tải danh sách sinh viên từ máy chủ...");

        AsyncWorker.run(
                studentController::listStudents,
                (List<StudentDTO> students) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    allStudents = students != null ? students : new ArrayList<>();
                    applyFilter(false);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    handleError(ex);
                }
        );
    }

    private void loadStudentsSilently() {
        if (isRefreshing) return;
        isRefreshing = true;

        AsyncWorker.run(
                studentController::listStudents,
                (List<StudentDTO> students) -> {
                    isRefreshing = false;
                    allStudents = students != null ? students : new ArrayList<>();
                    applyFilter(true);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                }
        );
    }

    private void applyFilter(boolean silent) {
        Long selectedId = null;
        StudentDTO prevSel = getSelectedStudent();
        if (prevSel != null) {
            selectedId = prevSel.getId();
        }

        String filter = searchField.getText().trim().toLowerCase();
        int statusIdx = statusFilterCombo.getSelectedIndex();

        tableModel.setRowCount(0);
        int count = 0;
        int restoreRow = -1;

        for (StudentDTO s : allStudents) {
            // Status filter
            if (statusIdx == 1 && s.getStatus() != StudentStatus.ACTIVE) continue;
            if (statusIdx == 2 && s.getStatus() != StudentStatus.SUSPENDED) continue;
            if (statusIdx == 3 && s.getStatus() != StudentStatus.GRADUATED) continue;

            // Keyword filter (ID, Code, Name, Class, Phone, Email)
            if (!filter.isEmpty()) {
                String idStr = s.getId() != null ? s.getId().toString() : "";
                String code = s.getStudentCode() != null ? s.getStudentCode().toLowerCase() : "";
                String name = s.getFullName() != null ? s.getFullName().toLowerCase() : "";
                String cl = s.getClassName() != null ? s.getClassName().toLowerCase() : "";
                String phone = s.getPhone() != null ? s.getPhone().toLowerCase() : "";
                String email = s.getEmail() != null ? s.getEmail().toLowerCase() : "";

                if (!idStr.contains(filter) && !code.contains(filter) && !name.contains(filter)
                        && !cl.contains(filter) && !phone.contains(filter) && !email.contains(filter)) {
                    continue;
                }
            }

            if (selectedId != null && selectedId.equals(s.getId())) {
                restoreRow = count;
            }

            tableModel.addRow(new Object[]{
                    s.getId(),
                    s.getStudentCode(),
                    s.getFullName(),
                    s.getClassName() != null ? s.getClassName() : "",
                    s.getPhone() != null ? s.getPhone() : "",
                    s.getEmail() != null ? s.getEmail() : "",
                    String.format("%d / %d", s.getCurrentBorrowCount(), s.getMaxBorrowLimit()),
                    mapStudentStatus(s.getStatus())
            });
            count++;
        }

        if (restoreRow != -1) {
            int viewRow = studentTable.convertRowIndexToView(restoreRow);
            studentTable.setRowSelectionInterval(viewRow, viewRow);
            updateStaffDetails();
        } else if (!silent) {
            clearStaffDetails();
        }

        if (count == 0) {
            statusLabel.setText("Không tìm thấy sinh viên nào phù hợp.");
        } else {
            statusLabel.setText(String.format("Đã tải %d sinh viên lúc %s", count, TIME_FMT.format(new Date())));
        }
    }

    private String mapStudentStatus(StudentStatus status) {
        if (status == null) return "Chưa rõ";
        switch (status) {
            case ACTIVE: return "Đang hoạt động";
            case SUSPENDED: return "Tạm khóa";
            case GRADUATED: return "Đã tốt nghiệp";
            default: return status.name();
        }
    }

    private StudentDTO getSelectedStudent() {
        int row = studentTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = studentTable.convertRowIndexToModel(row);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        if (idObj instanceof Long) {
            long targetId = (Long) idObj;
            for (StudentDTO s : allStudents) {
                if (s.getId().equals(targetId)) {
                    return s;
                }
            }
        }
        return null;
    }

    private void updateStaffDetails() {
        StudentDTO s = getSelectedStudent();
        boolean hasSel = (s != null);
        if (editStudentBtn != null) editStudentBtn.setEnabled(hasSel);
        if (suspendStudentBtn != null) suspendStudentBtn.setEnabled(hasSel);
        if (hardDeleteStudentBtn != null) hardDeleteStudentBtn.setEnabled(hasSel);
        if (activationCodeBtn != null) activationCodeBtn.setEnabled(hasSel && s.getUserId() == null);

        if (!hasSel) {
            clearStaffDetails();
            return;
        }

        detailIdVal.setText(s.getId().toString());
        detailCodeVal.setText(s.getStudentCode());
        detailNameVal.setText(s.getFullName());
        detailClassVal.setText(s.getClassName() != null ? s.getClassName() : "Không rõ");
        detailPhoneVal.setText(s.getPhone() != null && !s.getPhone().isEmpty() ? s.getPhone() : "Chưa có");
        detailEmailVal.setText(s.getEmail() != null && !s.getEmail().isEmpty() ? s.getEmail() : "Chưa có");
        detailBorrowVal.setText(String.format("%d cuốn (Hạn mức tối đa: %d)", s.getCurrentBorrowCount(), s.getMaxBorrowLimit()));
        detailStatusVal.setText(mapStudentStatus(s.getStatus()));
    }

    private void clearStaffDetails() {
        detailIdVal.setText("Chưa chọn");
        detailCodeVal.setText("Chưa chọn");
        detailNameVal.setText("Chưa chọn");
        detailClassVal.setText("Chưa chọn");
        detailPhoneVal.setText("Chưa chọn");
        detailEmailVal.setText("Chưa chọn");
        detailBorrowVal.setText("Chưa chọn");
        detailStatusVal.setText("Chưa chọn");
    }

    private void openCreateDialog() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dialog = new StudentFormDialog(owner, networkClient, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudents();
        }
    }

    private void openEditDialog() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một sinh viên để chỉnh sửa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dialog = new StudentFormDialog(owner, networkClient, s);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudents();
        }
    }

    private void suspendSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần tạm khóa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận tạm khóa sinh viên:\n%s (%s)?\n\nThao tác này sẽ chuyển trạng thái của sinh viên sang TẠM KHÓA (SUSPENDED).",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Tạm Khóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            if (suspendStudentBtn != null) suspendStudentBtn.setEnabled(false);
            statusLabel.setText("Đang tạm khóa sinh viên trên máy chủ...");

            AsyncWorker.run(
                    () -> studentController.deleteStudent(s.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã tạm khóa tài khoản sinh viên thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadStudents();
                    },
                    this::handleError
            );
        }
    }

    private void hardDeleteSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần xóa vĩnh viễn.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirmation 1: Explicit Warning
        int confirm1 = JOptionPane.showConfirmDialog(
                this,
                String.format("CẢNH BÁO NGUY HIỂM:\nBạn đang thực hiện XÓA VĨNH VIỄN sinh viên:\n%s (Mã SV: %s, ID: %d)\n\n"
                                + "Thao tác này KHÔNG THỂ HOÀN TÁC! Hồ sơ sinh viên sẽ bị xóa vĩnh viễn khỏi cơ sở dữ liệu.\n"
                                + "(Lưu ý: Nếu sinh viên có sách chưa trả hoặc tiền phạt chưa thanh toán, hệ thống sẽ từ chối xóa).\n\n"
                                + "Bạn có chắc chắn muốn tiếp tục?",
                        s.getFullName(), s.getStudentCode(), s.getId()),
                "Cảnh Báo: Xóa Vĩnh Viễn Sinh Viên",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm1 != JOptionPane.YES_OPTION) {
            return;
        }

        // Confirmation 2: Final Confirmation
        int confirm2 = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận lần 2: Bạn thực sự muốn XÓA VĨNH VIỄN sinh viên %s (%s)?",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Lần Cuối",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE
        );

        if (confirm2 == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            if (hardDeleteStudentBtn != null) hardDeleteStudentBtn.setEnabled(false);
            statusLabel.setText("Đang xóa vĩnh viễn sinh viên ID " + s.getId() + " trên máy chủ...");

            AsyncWorker.run(
                    () -> studentController.hardDeleteStudent(s.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã xóa vĩnh viễn sinh viên thành công khỏi hệ thống.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadStudents();
                    },
                    this::handleError
            );
        }
    }

    private void generateActivationCodeForSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần cấp mã kích hoạt.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (s.getUserId() != null) {
            JOptionPane.showMessageDialog(this, "Sinh viên này đã có tài khoản người dùng liên kết trong hệ thống.", "Đã Có Tài Khoản", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Tạo mã kích hoạt tài khoản mới cho sinh viên:\n%s (Mã SV: %s)?\n\nNếu đã có mã kích hoạt trước đó, mã cũ sẽ bị thay thế.",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Cấp Mã Kích Hoạt",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            statusLabel.setText("Đang tạo mã kích hoạt an toàn trên máy chủ...");
            AsyncWorker.run(
                    () -> studentController.generateActivationCode(s.getId()),
                    (String code) -> {
                        statusLabel.setText("Đã cấp mã kích hoạt cho " + s.getStudentCode());
                        JTextField copyField = new JTextField(code);
                        copyField.setEditable(false);
                        copyField.setFont(copyField.getFont().deriveFont(Font.BOLD, 15.0f));
                        copyField.setHorizontalAlignment(JTextField.CENTER);

                        Object[] message = {
                                "Mã kích hoạt tài khoản dành cho sinh viên " + s.getFullName() + ":",
                                copyField,
                                "Hãy cung cấp mã này cho sinh viên để tự đăng ký tài khoản trên ứng dụng."
                        };
                        JOptionPane.showMessageDialog(this, message, "Cấp Mã Kích Hoạt Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    },
                    this::handleError
            );
        }
    }


    public void loadStudentProfile() {
        profCodeVal.setText("Đang tải...");
        profNameVal.setText("Đang tải...");

        AsyncWorker.run(
                studentController::getMyProfile,
                (StudentDTO s) -> {
                    profCodeVal.setText(s.getStudentCode());
                    profNameVal.setText(s.getFullName());
                    profClassVal.setText(s.getClassName() != null ? s.getClassName() : "Không rõ");
                    profPhoneVal.setText(s.getPhone() != null && !s.getPhone().isEmpty() ? s.getPhone() : "Chưa có");
                    profEmailVal.setText(s.getEmail() != null && !s.getEmail().isEmpty() ? s.getEmail() : "Chưa có");
                    profBorrowVal.setText(String.format("%d cuốn sách đang mượn (Hạn mức tối đa: %d)",
                            s.getCurrentBorrowCount(), s.getMaxBorrowLimit()));
                    profStatusVal.setText(mapStudentStatus(s.getStatus()));
                },
                this::handleError
        );
    }

    private void handleError(Exception ex) {
        if (refreshBtn != null) refreshBtn.setEnabled(true);
        if (statusLabel != null) statusLabel.setText("Lỗi: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Student operation error", ex);

        if (ex instanceof AuthenticationException) {
            String msg = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
            if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
            }
            JOptionPane.showMessageDialog(this, msg, "Hết Hạn Phiên", JOptionPane.ERROR_MESSAGE);
            java.awt.Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) win.dispose();
            SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
        } else if (ex instanceof AuthorizationException) {
            JOptionPane.showMessageDialog(this, "Từ chối quyền truy cập: " + ex.getMessage(), "Lỗi Phân Quyền", JOptionPane.ERROR_MESSAGE);
        } else if (ex instanceof NetworkException) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối mạng: " + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thông Báo Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
