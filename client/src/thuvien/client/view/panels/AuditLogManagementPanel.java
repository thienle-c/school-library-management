package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientAuditLogController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.AuditLogDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;

/**
 * Giao diện quản trị xem xét nhật ký hệ thống và sự kiện bảo mật.
 * Giới hạn quyền dành cho Quản trị viên (ADMIN).
 */
public class AuditLogManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(AuditLogManagementPanel.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientAuditLogController auditLogController;

    private JComboBox<Integer> limitCombo;
    private JTextField filterField;
    private JButton refreshBtn;
    private JTable logTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    // Detail inspector
    private JLabel detailIdVal;
    private JLabel detailTimeVal;
    private JLabel detailUserVal;
    private JLabel detailActionVal;
    private JLabel detailEntityVal;
    private JLabel detailEntityIdVal;
    private JLabel detailNotesVal;

    private List<AuditLogDTO> allLogs = new ArrayList<>();
    private Timer pollTimer;
    private boolean isPolling = false;

    public AuditLogManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.auditLogController = new ClientAuditLogController(networkClient);

        initUI();
        loadAuditLogs();
        startAutoRefresh();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // =====================================================================
        // 1. Top Toolbar
        // =====================================================================
        JPanel topToolbar = new JPanel(new BorderLayout(10, 0));
        topToolbar.setOpaque(false);
        topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JLabel title = new JLabel("Nhật ký Hệ thống & Sự kiện Bảo mật");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16.0f));
        title.setForeground(new Color(25, 45, 80));
        leftPanel.add(title);

        leftPanel.add(new JLabel("Số lượng:"));
        limitCombo = new JComboBox<>(new Integer[]{25, 50, 100, 200});
        limitCombo.setSelectedItem(50);
        limitCombo.addActionListener(e -> loadAuditLogs());
        leftPanel.add(limitCombo);

        refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> loadAuditLogs());
        leftPanel.add(refreshBtn);

        topToolbar.add(leftPanel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(new JLabel("Tìm kiếm:"));
        filterField = new JTextField(15);
        filterField.setToolTipText("Lọc theo hành động, người dùng, đối tượng hoặc chi tiết...");
        filterField.addActionListener(e -> applyFilter());
        rightPanel.add(filterField);

        JButton filterBtn = new JButton("Tìm");
        filterBtn.addActionListener(e -> applyFilter());
        rightPanel.add(filterBtn);

        JButton resetBtn = new JButton("Xóa bộ lọc");
        resetBtn.addActionListener(e -> {
            filterField.setText("");
            applyFilter();
        });
        rightPanel.add(resetBtn);

        topToolbar.add(rightPanel, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // =====================================================================
        // 2. Table & Detail Split
        // =====================================================================
        String[] columnNames = {"Mã", "Thời gian", "Người thực hiện", "Hành động", "Đối tượng", "Mã ĐT", "Chi tiết thao tác"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        logTable = new JTable(tableModel);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setRowHeight(25);
        logTable.getTableHeader().setFont(logTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        logTable.getTableHeader().setReorderingAllowed(false);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        logTable.getColumnModel().getColumn(6).setPreferredWidth(280);

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedLog();
            }
        });

        JScrollPane tableScroll = new JScrollPane(logTable);
        tableScroll.getViewport().setBackground(Color.WHITE);

        // Detail Inspector Panel
        JPanel detailCard = createDetailPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailCard);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        add(splitPane, BorderLayout.CENTER);

        // =====================================================================
        // 3. Status Bar
        // =====================================================================
        statusLabel = new JLabel("Sẵn sàng.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        statusLabel.setForeground(new Color(110, 115, 125));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 235)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel title = new JLabel("Chi tiết Bản ghi Nhật ký");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12.5f));
        title.setForeground(new Color(50, 60, 75));
        panel.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(4, 2, 10, 4));
        grid.setOpaque(false);

        detailIdVal = addField(grid, "Mã nhật ký:");
        detailTimeVal = addField(grid, "Thời gian ghi nhận:");
        detailUserVal = addField(grid, "Tài khoản thực hiện:");
        detailActionVal = addField(grid, "Loại hành động:");
        detailEntityVal = addField(grid, "Đối tượng tác động:");
        detailEntityIdVal = addField(grid, "Mã đối tượng:");
        detailNotesVal = addField(grid, "Chi tiết thao tác:");

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JLabel addField(JPanel panel, String labelText) {
        JPanel fPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fPanel.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11.5f));
        lbl.setForeground(new Color(80, 85, 95));
        JLabel val = new JLabel("—");
        val.setFont(val.getFont().deriveFont(Font.PLAIN, 11.5f));
        fPanel.add(lbl);
        fPanel.add(val);
        panel.add(fPanel);
        return val;
    }

    private void startAutoRefresh() {
        pollTimer = new Timer(6000, e -> {
            if (isShowing() && !isPolling) {
                loadSilently();
            }
        });
        pollTimer.start();
    }

    public void loadAuditLogs() {
        int limit = (Integer) limitCombo.getSelectedItem();
        setControlsEnabled(false);
        statusLabel.setText("Đang tải nhật ký hệ thống từ máy chủ...");

        AsyncWorker.run(
                () -> auditLogController.listRecentLogs(limit),
                logs -> {
                    allLogs = logs != null ? logs : new ArrayList<>();
                    applyFilter();
                    setControlsEnabled(true);
                },
                ex -> {
                    setControlsEnabled(true);
                    handleError(ex, "Lỗi tải nhật ký hệ thống");
                }
        );
    }

    private void loadSilently() {
        isPolling = true;
        int limit = (Integer) limitCombo.getSelectedItem();
        AsyncWorker.run(
                () -> auditLogController.listRecentLogs(limit),
                logs -> {
                    isPolling = false;
                    allLogs = logs != null ? logs : new ArrayList<>();
                    applyFilterSilently();
                },
                ex -> isPolling = false
        );
    }

    private void applyFilter() {
        applyFilterInternal(false);
    }

    private void applyFilterSilently() {
        applyFilterInternal(true);
    }

    private void applyFilterInternal(boolean silent) {
        String query = filterField.getText().trim().toLowerCase();

        Long prevSelectedId = null;
        if (silent) {
            int selRow = logTable.getSelectedRow();
            if (selRow >= 0 && selRow < tableModel.getRowCount()) {
                Object val = tableModel.getValueAt(selRow, 0);
                if (val instanceof Long) prevSelectedId = (Long) val;
            }
        }

        tableModel.setRowCount(0);
        int restoreRow = -1;

        List<AuditLogDTO> matched = new ArrayList<>();
        for (AuditLogDTO log : allLogs) {
            if (query.isEmpty()
                    || (log.getAction() != null && log.getAction().toLowerCase().contains(query))
                    || (log.getUsername() != null && log.getUsername().toLowerCase().contains(query))
                    || (log.getEntityName() != null && log.getEntityName().toLowerCase().contains(query))
                    || (log.getDetails() != null && log.getDetails().toLowerCase().contains(query))) {
                matched.add(log);
                tableModel.addRow(new Object[]{
                        log.getId(),
                        log.getCreatedAt() != null ? DATE_FORMAT.format(log.getCreatedAt()) : "N/A",
                        log.getUsername() != null ? log.getUsername() : (log.getUserId() != null ? "User #" + log.getUserId() : "HỆ THỐNG"),
                        log.getAction() != null ? log.getAction() : "—",
                        log.getEntityName() != null ? log.getEntityName() : "—",
                        log.getEntityId() != null ? log.getEntityId() : "—",
                        log.getDetails() != null ? log.getDetails() : "—"
                });

                if (prevSelectedId != null && prevSelectedId.equals(log.getId())) {
                    restoreRow = tableModel.getRowCount() - 1;
                }
            }
        }

        if (restoreRow >= 0 && restoreRow < logTable.getRowCount()) {
            logTable.setRowSelectionInterval(restoreRow, restoreRow);
        }

        if (!silent) {
            if (matched.isEmpty()) {
                statusLabel.setText("Không tìm thấy sự kiện nhật ký nào phù hợp.");
            } else {
                statusLabel.setText("Hiển thị " + matched.size() + " sự kiện nhật ký (Giới hạn: " + limitCombo.getSelectedItem() + ").");
            }
            clearDetail();
        }
    }

    private void displaySelectedLog() {
        int row = logTable.getSelectedRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            clearDetail();
            return;
        }

        Long id = (Long) tableModel.getValueAt(row, 0);
        AuditLogDTO selected = null;
        for (AuditLogDTO log : allLogs) {
            if (log.getId() != null && log.getId().equals(id)) {
                selected = log;
                break;
            }
        }

        if (selected != null) {
            detailIdVal.setText(String.valueOf(selected.getId()));
            detailTimeVal.setText(selected.getCreatedAt() != null ? DATE_FORMAT.format(selected.getCreatedAt()) : "N/A");
            detailUserVal.setText(selected.getUsername() != null ? selected.getUsername() : "User #" + selected.getUserId());
            detailActionVal.setText(selected.getAction() != null ? selected.getAction() : "—");
            detailEntityVal.setText(selected.getEntityName() != null ? selected.getEntityName() : "—");
            detailEntityIdVal.setText(selected.getEntityId() != null ? String.valueOf(selected.getEntityId()) : "—");
            detailNotesVal.setText(selected.getDetails() != null ? selected.getDetails() : "—");
        } else {
            clearDetail();
        }
    }

    private void clearDetail() {
        detailIdVal.setText("—");
        detailTimeVal.setText("—");
        detailUserVal.setText("—");
        detailActionVal.setText("—");
        detailEntityVal.setText("—");
        detailEntityIdVal.setText("—");
        detailNotesVal.setText("—");
    }

    private void setControlsEnabled(boolean enabled) {
        refreshBtn.setEnabled(enabled);
        limitCombo.setEnabled(enabled);
        filterField.setEnabled(enabled);
    }

    private void handleError(Throwable ex, String title) {
        LOGGER.log(Level.WARNING, title, ex);
        statusLabel.setText("Lỗi: " + ex.getMessage());

        if (ex instanceof AuthenticationException) {
            String msg = "Phiên làm việc đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.";
            if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
            }
            JOptionPane.showMessageDialog(this,
                    msg,
                    "Hết phiên",
                    JOptionPane.WARNING_MESSAGE);
            ClientSession.getInstance().clear();
            networkClient.disconnect();
            java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
            if (parent != null) {
                parent.dispose();
            }
            new LoginForm(networkClient).setVisible(true);
        } else if (ex instanceof AuthorizationException) {
            JOptionPane.showMessageDialog(this,
                    "Từ chối truy cập: Bạn không có quyền xem nhật ký hệ thống.",
                    "Không có quyền",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    title,
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
