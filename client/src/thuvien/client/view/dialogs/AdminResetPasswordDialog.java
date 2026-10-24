package thuvien.client.view.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.session.PermissionHelper;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.ModernUIHelper;
import thuvien.common.dto.AdminResetPasswordResponseDTO;
import thuvien.common.dto.UserDTO;

/**
 * Modern UI Dialog for Administrators to safely reset user passwords.
 * Implements SEC-02 protocol: generates a 16-char temporary password,
 * marks must_change_password=TRUE, and invalidates active sessions.
 */
public class AdminResetPasswordDialog extends JDialog {

    private final NetworkClient networkClient;
    private final ClientAuthController authController;
    private final List<UserDTO> allUsers = new ArrayList<>();

    private JTable userTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton resetBtn;
    private JButton refreshBtn;
    private JButton closeBtn;
    private JLabel statusLabel;

    public AdminResetPasswordDialog(Frame parent, NetworkClient networkClient) {
        super(parent, "Quản Trị Viên — Đặt Lại Mật Khẩu Người Dùng", true);
        this.networkClient = networkClient;
        this.authController = new ClientAuthController(networkClient);

        initComponents();
        loadUsers();
    }

    private void initComponents() {
        setSize(780, 520);
        setMinimumSize(new Dimension(680, 440));
        setLocationRelativeTo(getParent());
        getContentPane().setBackground(ModernUIHelper.BG_APP);
        setLayout(new BorderLayout(12, 12));

        // Top Header Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 8));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

        JLabel titleLabel = new JLabel("CẤP LẠI MẬT KHẨU TẠM THỜI CHO NGƯỜI DÙNG");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(ModernUIHelper.TEXT_TITLE);
        titleLabel.setIcon(ModernUIHelper.getVectorIcon("LOCK", 18, ModernUIHelper.PRIMARY_BLUE));
        titleLabel.setIconTextGap(8);

        JLabel descLabel = new JLabel("Chọn tài khoản để đặt lại mật khẩu an toàn. Mật khẩu tạm thời sẽ được sinh ngẫu nhiên 16 ký tự và tài khoản sẽ bắt buộc đổi mật khẩu ở lần đăng nhập tới.");
        descLabel.setFont(ModernUIHelper.FONT_SMALL);
        descLabel.setForeground(ModernUIHelper.TEXT_MUTED);

        JPanel headerTextPanel = new JPanel();
        headerTextPanel.setLayout(new BoxLayout(headerTextPanel, BoxLayout.Y_AXIS));
        headerTextPanel.setOpaque(false);
        headerTextPanel.add(titleLabel);
        headerTextPanel.add(Box.createVerticalStrut(4));
        headerTextPanel.add(descLabel);

        // Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchBar.setOpaque(false);
        JLabel searchLbl = new JLabel("Tìm kiếm:");
        searchLbl.setFont(ModernUIHelper.FONT_LABEL);
        searchField = new JTextField(16);
        ModernUIHelper.styleTextField(searchField);
        searchField.addActionListener(e -> filterUsers());
        JButton filterBtn = new JButton("Lọc");
        ModernUIHelper.styleSecondaryButton(filterBtn, "Lọc");
        filterBtn.addActionListener(e -> filterUsers());
        searchBar.add(searchLbl);
        searchBar.add(searchField);
        searchBar.add(filterBtn);

        topPanel.add(headerTextPanel, BorderLayout.WEST);
        topPanel.add(searchBar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Center Table Panel
        String[] columns = {"ID", "Tên đăng nhập", "Họ và tên", "Vai trò", "Trạng thái", "Bắt buộc đổi MK"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        userTable = new JTable(tableModel);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setRowHeight(28);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        userTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        userTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        userTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        userTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        userTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        userTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        userTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        userTable.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        userTable.getSelectionModel().addListSelectionListener(e -> updateButtonState());

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 20, 0, 20),
                BorderFactory.createLineBorder(ModernUIHelper.BORDER_INPUT)
        ));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 8));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 20, 16, 20));

        statusLabel = new JLabel("Chọn một tài khoản để thực hiện đặt lại mật khẩu.");
        statusLabel.setFont(ModernUIHelper.FONT_SMALL);
        statusLabel.setForeground(ModernUIHelper.TEXT_MUTED);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnGroup.setOpaque(false);

        refreshBtn = new JButton("Làm mới");
        ModernUIHelper.styleSecondaryButton(refreshBtn, "Làm mới");
        refreshBtn.setIcon(ModernUIHelper.getVectorIcon("REFRESH", 13, ModernUIHelper.PRIMARY_BLUE));
        refreshBtn.addActionListener(e -> loadUsers());

        resetBtn = new JButton("Đặt Lại Mật Khẩu Tạm");
        ModernUIHelper.stylePrimaryButton(resetBtn, "Đặt Lại Mật Khẩu Tạm");
        resetBtn.setIcon(ModernUIHelper.getVectorIcon("KEY", 13, Color.WHITE));
        resetBtn.setEnabled(false);
        resetBtn.addActionListener(e -> onResetPassword());

        closeBtn = new JButton("Đóng");
        ModernUIHelper.styleSecondaryButton(closeBtn, "Đóng");
        closeBtn.addActionListener(e -> dispose());

        btnGroup.add(refreshBtn);
        btnGroup.add(resetBtn);
        btnGroup.add(closeBtn);

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(btnGroup, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        ModernUIHelper.styleDialog(this);
    }

    private void loadUsers() {
        statusLabel.setText("Đang tải danh sách người dùng từ máy chủ...");
        resetBtn.setEnabled(false);
        refreshBtn.setEnabled(false);

        AsyncWorker.run(
                () -> authController.listUsers(),
                (List<UserDTO> users) -> {
                    allUsers.clear();
                    if (users != null) {
                        allUsers.addAll(users);
                    }
                    renderTable(allUsers);
                    refreshBtn.setEnabled(true);
                    statusLabel.setText("Đã tải " + allUsers.size() + " người dùng thành công.");
                },
                (Exception ex) -> {
                    refreshBtn.setEnabled(true);
                    statusLabel.setText("Lỗi nạp danh sách: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Không thể nạp danh sách người dùng: " + ex.getMessage(),
                            "Lỗi Tải Dữ Liệu", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void filterUsers() {
        String kw = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        if (kw.isEmpty()) {
            renderTable(allUsers);
            return;
        }
        List<UserDTO> filtered = new ArrayList<>();
        for (UserDTO u : allUsers) {
            boolean match = (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw)) ||
                            (u.getFullName() != null && u.getFullName().toLowerCase().contains(kw)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw)) ||
                            (u.getRole() != null && u.getRole().name().toLowerCase().contains(kw));
            if (match) filtered.add(u);
        }
        renderTable(filtered);
    }

    private void renderTable(List<UserDTO> users) {
        tableModel.setRowCount(0);
        for (UserDTO u : users) {
            tableModel.addRow(new Object[]{
                    u.getId(),
                    u.getUsername(),
                    u.getFullName(),
                    u.getRole() != null ? u.getRole().name() : "",
                    u.isActive() ? "HOẠT ĐỘNG" : "BỊ KHÓA",
                    u.isMustChangePassword() ? "CẦN ĐỔI" : "BÌNH THƯỜNG"
            });
        }
        updateButtonState();
    }

    private void updateButtonState() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            resetBtn.setEnabled(false);
            return;
        }
        Long selectedId = (Long) tableModel.getValueAt(row, 0);
        String selectedUser = (String) tableModel.getValueAt(row, 1);
        String status = (String) tableModel.getValueAt(row, 4);

        Long currentAdminId = null;
        ClientSession cs = PermissionHelper.getActiveSession(networkClient);
        if (cs != null && cs.getCurrentUser() != null) {
            currentAdminId = cs.getCurrentUser().getUserId();
        }

        if (currentAdminId != null && currentAdminId.equals(selectedId)) {
            resetBtn.setEnabled(false);
            statusLabel.setText("Không thể tự reset mật khẩu của chính mình (Hãy dùng 'Đổi mật khẩu').");
            return;
        }

        if ("BỊ KHÓA".equalsIgnoreCase(status)) {
            resetBtn.setEnabled(false);
            statusLabel.setText("Tài khoản '" + selectedUser + "' đang bị khóa, không thể đặt lại mật khẩu.");
            return;
        }

        resetBtn.setEnabled(true);
        statusLabel.setText("Đang chọn tài khoản: " + selectedUser + " (ID: " + selectedId + ")");
    }

    private void onResetPassword() {
        int row = userTable.getSelectedRow();
        if (row < 0) return;

        Long targetId = (Long) tableModel.getValueAt(row, 0);
        String targetUsername = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn đặt lại mật khẩu cho tài khoản '" + targetUsername + "' (ID: " + targetId + ")?\n\n" +
                "- Hệ thống sẽ sinh ngẫu nhiên một mật khẩu tạm thời 16 ký tự an toàn.\n" +
                "- Mọi phiên làm việc đang mở của tài khoản này sẽ bị thu hồi lập tức.\n" +
                "- Tài khoản sẽ bắt buộc phải đổi sang mật khẩu mới ở lần đăng nhập tiếp theo.",
                "Xác Nhận Đặt Lại Mật Khẩu",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        resetBtn.setEnabled(false);
        refreshBtn.setEnabled(false);
        statusLabel.setText("Đang sinh mật khẩu ngẫu nhiên và cập nhật cơ sở dữ liệu...");

        AsyncWorker.run(
                () -> authController.adminResetPassword(targetId),
                (AdminResetPasswordResponseDTO result) -> {
                    resetBtn.setEnabled(true);
                    refreshBtn.setEnabled(true);
                    statusLabel.setText("Đặt lại mật khẩu cho '" + targetUsername + "' thành công!");
                    loadUsers();
                    showTemporaryPasswordDialog(result);
                },
                (Exception ex) -> {
                    resetBtn.setEnabled(true);
                    refreshBtn.setEnabled(true);
                    statusLabel.setText("Thao tác thất bại: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Không thể đặt lại mật khẩu: " + ex.getMessage(),
                            "Lỗi Thao Tác", JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void showTemporaryPasswordDialog(AdminResetPasswordResponseDTO result) {
        JDialog dlg = new JDialog(this, "Mật Khẩu Tạm Thời Mới Được Cấp", true);
        dlg.setSize(520, 260);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(ModernUIHelper.BG_CARD);
        dlg.setLayout(new BorderLayout(10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));

        JLabel info = new JLabel("Mật khẩu tạm thời cho tài khoản '" + result.getUsername() + "' đã được tạo thành công:");
        info.setFont(ModernUIHelper.FONT_LABEL);
        info.setForeground(ModernUIHelper.TEXT_TITLE);

        JTextField pwdField = new JTextField(result.getTemporaryPassword());
        pwdField.setEditable(false);
        pwdField.setFont(new Font("Consolas", Font.BOLD, 18));
        pwdField.setHorizontalAlignment(SwingConstants.CENTER);
        pwdField.setBackground(new Color(0xF3, 0xF4, 0xF6));
        pwdField.setForeground(new Color(0x1F, 0x29, 0x37));
        pwdField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x3B, 0x82, 0xF6), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JLabel warn = new JLabel("LƯU Ý QUAN TRỌNG: Mật khẩu này chỉ hiển thị một lần duy nhất!");
        warn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        warn.setForeground(new Color(0xDC, 0x26, 0x26));

        JLabel note = new JLabel("Vui lòng sao chép và chuyển cho người dùng. Họ bắt buộc phải đổi mật khẩu khi đăng nhập.");
        note.setFont(ModernUIHelper.FONT_SMALL);
        note.setForeground(ModernUIHelper.TEXT_MUTED);

        content.add(info);
        content.add(Box.createVerticalStrut(10));
        content.add(pwdField);
        content.add(Box.createVerticalStrut(8));
        content.add(warn);
        content.add(Box.createVerticalStrut(2));
        content.add(note);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setOpaque(false);

        JButton copyBtn = new JButton("Sao chép mật khẩu");
        ModernUIHelper.stylePrimaryButton(copyBtn, "Sao chép mật khẩu");
        copyBtn.setIcon(ModernUIHelper.getVectorIcon("COPY", 13, Color.WHITE));
        copyBtn.addActionListener(e -> {
            StringSelection selection = new StringSelection(result.getTemporaryPassword());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            copyBtn.setText("Đã sao chép!");
        });

        JButton okBtn = new JButton("Hoàn tất");
        ModernUIHelper.styleSecondaryButton(okBtn, "Hoàn tất");
        okBtn.addActionListener(e -> dlg.dispose());

        btnPanel.add(copyBtn);
        btnPanel.add(okBtn);

        dlg.add(content, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }
}
