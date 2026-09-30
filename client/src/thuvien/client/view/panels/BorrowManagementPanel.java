package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
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
import thuvien.client.controller.ClientBorrowController;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.client.view.dialogs.BorrowDialog;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.ReturnResultDTO;
import thuvien.common.enums.BorrowStatus;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Panel providing circulation management (Borrow & Return) for staff,
 * and active loans inspection for students.
 */
public class BorrowManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(BorrowManagementPanel.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientBorrowController borrowController;
    private final boolean isStaff;

    private JTable recordTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JButton refreshBtn;
    private JButton newBorrowBtn;
    private JButton returnBtn;
    private JLabel statusLabel;

    // Detail card labels
    private JLabel detailIdVal;
    private JLabel detailStudentVal;
    private JLabel detailBookVal;
    private JLabel detailBorrowDateVal;
    private JLabel detailDueDateVal;
    private JLabel detailReturnDateVal;
    private JLabel detailStatusVal;
    private JLabel detailNotesVal;

    private List<BorrowRecordDTO> allRecords = new ArrayList<>();
    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public BorrowManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.borrowController = new ClientBorrowController(networkClient);
        this.isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        initUI();
        loadRecords();
        initAutoRefresh();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // Top Toolbar
        JPanel topToolbar = new JPanel(new BorderLayout(10, 0));
        topToolbar.setOpaque(false);
        topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftControls.setOpaque(false);

        leftControls.add(new JLabel("Tìm kiếm lượt mượn:"));
        searchField = new JTextField(13);
        searchField.setToolTipText("Tìm kiếm theo Mã phiếu, Mã SV, Tên SV, Tên sách hoặc ISBN");
        searchField.addActionListener(e -> applyFilter(false));
        SearchAutoCompleteHelper.attach(searchField, "BORROW", new ClientSearchSuggestionController(networkClient), () -> applyFilter(false));
        leftControls.add(searchField);

        statusFilterCombo = new JComboBox<>(new String[]{"Tất cả trạng thái", "Đang mượn (Chưa trả)", "Đã trả", "Quá hạn"});
        statusFilterCombo.addActionListener(e -> applyFilter(false));
        leftControls.add(statusFilterCombo);

        JButton filterBtn = new JButton("Tìm");
        filterBtn.addActionListener(e -> applyFilter(false));
        leftControls.add(filterBtn);

        JButton resetBtn = new JButton("Xóa bộ lọc");
        resetBtn.addActionListener(e -> {
            searchField.setText("");
            statusFilterCombo.setSelectedIndex(0);
            applyFilter(false);
        });
        leftControls.add(resetBtn);

        refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> loadRecords());
        leftControls.add(refreshBtn);

        topToolbar.add(leftControls, BorderLayout.WEST);

        // Action controls (Right)
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);

        if (isStaff) {
            newBorrowBtn = new JButton("Lập Phiếu Mượn");
            newBorrowBtn.setBackground(new Color(230, 245, 230));
            newBorrowBtn.addActionListener(e -> openBorrowDialog());
            rightControls.add(newBorrowBtn);

            returnBtn = new JButton("Nhận Trả Sách");
            returnBtn.setBackground(new Color(230, 240, 255));
            returnBtn.setEnabled(false);
            returnBtn.addActionListener(e -> performReturn());
            rightControls.add(returnBtn);
        }

        topToolbar.add(rightControls, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // Table
        String[] columns = isStaff
                ? new String[]{"Mã Phiếu", "Mã SV", "Tên Sinh Viên", "Tên Sách", "Ngày Mượn", "Hạn Trả", "Ngày Trả", "Trạng Thái"}
                : new String[]{"Mã Phiếu", "Tên Sách", "Mã ISBN", "Ngày Mượn", "Hạn Trả", "Ngày Trả", "Trạng Thái"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        recordTable = new JTable(tableModel);
        recordTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recordTable.setRowHeight(24);
        recordTable.getTableHeader().setFont(recordTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        recordTable.setAutoCreateRowSorter(true);

        recordTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                BorrowRecordDTO r = getSelectedRecord();
                if (returnBtn != null) {
                    returnBtn.setEnabled(r != null && (r.getStatus() == BorrowStatus.ACTIVE || r.getStatus() == BorrowStatus.OVERDUE));
                }
                updateDetails(r);
            }
        });

        JScrollPane tableScroll = new JScrollPane(recordTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));

        JPanel detailCard = createDetailPanel();

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
                loadRecordsSilently();
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

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JLabel header = new JLabel("Thông Tin Chi Tiết Lượt Mượn Được Chọn");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13.0f));
        panel.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        detailIdVal = new JLabel("Chưa chọn");
        detailStudentVal = new JLabel("Chưa chọn");
        detailBookVal = new JLabel("Chưa chọn");
        detailBorrowDateVal = new JLabel("Chưa chọn");
        detailDueDateVal = new JLabel("Chưa chọn");
        detailReturnDateVal = new JLabel("Chưa chọn");
        detailStatusVal = new JLabel("Chưa chọn");
        detailNotesVal = new JLabel("Chưa chọn");

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        grid.add(new JLabel("Mã phiếu mượn:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailIdVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Sinh viên:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailStudentVal, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        grid.add(new JLabel("Sách mượn:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailBookVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailStatusVal, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Ngày mượn:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailBorrowDateVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Hạn trả:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailDueDateVal, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        grid.add(new JLabel("Ngày trả thực tế:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailReturnDateVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailNotesVal, gbc);

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    public void loadRecords() {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshBtn.setEnabled(false);
        statusLabel.setText("Đang tải dữ liệu mượn trả từ máy chủ...");

        AsyncWorker.run(
                () -> isStaff ? borrowController.listAllBorrowRecords() : borrowController.getMyActiveLoans(),
                (List<BorrowRecordDTO> records) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    allRecords = records != null ? records : new ArrayList<>();
                    applyFilter(false);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    handleError(ex);
                }
        );
    }

    private void loadRecordsSilently() {
        if (isRefreshing) return;
        isRefreshing = true;

        AsyncWorker.run(
                () -> isStaff ? borrowController.listAllBorrowRecords() : borrowController.getMyActiveLoans(),
                (List<BorrowRecordDTO> records) -> {
                    isRefreshing = false;
                    allRecords = records != null ? records : new ArrayList<>();
                    applyFilter(true);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                }
        );
    }

    private void applyFilter(boolean silent) {
        Long selectedId = null;
        BorrowRecordDTO prev = getSelectedRecord();
        if (prev != null) selectedId = prev.getId();

        String kw = searchField.getText().trim().toLowerCase();
        int statusIdx = statusFilterCombo.getSelectedIndex();

        tableModel.setRowCount(0);
        int count = 0;
        int restoreRow = -1;

        for (BorrowRecordDTO r : allRecords) {
            // Status filter
            if (statusIdx == 1 && (r.getStatus() != BorrowStatus.ACTIVE && r.getStatus() != BorrowStatus.OVERDUE)) continue;
            if (statusIdx == 2 && r.getStatus() != BorrowStatus.RETURNED) continue;
            if (statusIdx == 3 && r.getStatus() != BorrowStatus.OVERDUE) continue;

            // Search filter (ID, StudentCode, StudentName, BookTitle, ISBN)
            if (!kw.isEmpty()) {
                String idStr = r.getId() != null ? r.getId().toString() : "";
                String sc = r.getStudentCode() != null ? r.getStudentCode().toLowerCase() : "";
                String sn = r.getStudentName() != null ? r.getStudentName().toLowerCase() : "";
                String bt = r.getBookTitle() != null ? r.getBookTitle().toLowerCase() : "";
                String isbn = r.getBookIsbn() != null ? r.getBookIsbn().toLowerCase() : "";

                if (!idStr.contains(kw) && !sc.contains(kw) && !sn.contains(kw) && !bt.contains(kw) && !isbn.contains(kw)) {
                    continue;
                }
            }

            if (selectedId != null && selectedId.equals(r.getId())) {
                restoreRow = count;
            }

            if (isStaff) {
                tableModel.addRow(new Object[]{
                        r.getId(),
                        r.getStudentCode(),
                        r.getStudentName(),
                        r.getBookTitle(),
                        r.getBorrowDate() != null ? DATE_FMT.format(r.getBorrowDate()) : "",
                        r.getDueDate() != null ? DATE_FMT.format(r.getDueDate()) : "",
                        r.getReturnDate() != null ? DATE_FMT.format(r.getReturnDate()) : "Chưa trả",
                        mapBorrowStatus(r.getStatus())
                });
            } else {
                tableModel.addRow(new Object[]{
                        r.getId(),
                        r.getBookTitle(),
                        r.getBookIsbn(),
                        r.getBorrowDate() != null ? DATE_FMT.format(r.getBorrowDate()) : "",
                        r.getDueDate() != null ? DATE_FMT.format(r.getDueDate()) : "",
                        r.getReturnDate() != null ? DATE_FMT.format(r.getReturnDate()) : "Chưa trả",
                        mapBorrowStatus(r.getStatus())
                });
            }
            count++;
        }

        if (restoreRow != -1) {
            int viewRow = recordTable.convertRowIndexToView(restoreRow);
            recordTable.setRowSelectionInterval(viewRow, viewRow);
            updateDetails(getSelectedRecord());
        } else if (!silent) {
            clearDetails();
        }

        if (count == 0) {
            statusLabel.setText("Không tìm thấy lượt mượn nào phù hợp.");
        } else {
            statusLabel.setText(String.format("Đã tải %d lượt mượn lúc %s", count, TIME_FMT.format(new Date())));
        }
    }

    private String mapBorrowStatus(BorrowStatus status) {
        if (status == null) return "Chưa rõ";
        switch (status) {
            case ACTIVE: return "Đang mượn";
            case RETURNED: return "Đã trả";
            case OVERDUE: return "Quá hạn";
            case LOST: return "Mất sách";
            default: return status.name();
        }
    }

    private BorrowRecordDTO getSelectedRecord() {
        int row = recordTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = recordTable.convertRowIndexToModel(row);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        if (idObj instanceof Long) {
            long targetId = (Long) idObj;
            for (BorrowRecordDTO r : allRecords) {
                if (r.getId().equals(targetId)) {
                    return r;
                }
            }
        }
        return null;
    }

    private void updateDetails(BorrowRecordDTO r) {
        if (r == null) {
            clearDetails();
            return;
        }

        detailIdVal.setText(r.getId().toString());
        detailStudentVal.setText(String.format("%s (Mã SV: %s, ID: %d)", r.getStudentName(), r.getStudentCode(), r.getStudentId()));
        detailBookVal.setText(String.format("%s (Mã sách: %d, ISBN: %s)", r.getBookTitle(), r.getBookId(), r.getBookIsbn()));
        detailBorrowDateVal.setText(r.getBorrowDate() != null ? DATE_FMT.format(r.getBorrowDate()) : "N/A");
        detailDueDateVal.setText(r.getDueDate() != null ? DATE_FMT.format(r.getDueDate()) : "N/A");
        detailReturnDateVal.setText(r.getReturnDate() != null ? DATE_FMT.format(r.getReturnDate()) : "Chưa trả");
        detailStatusVal.setText(mapBorrowStatus(r.getStatus()));
        detailNotesVal.setText(r.getNotes() != null && !r.getNotes().isEmpty() ? r.getNotes() : "Không có");
    }

    private void clearDetails() {
        detailIdVal.setText("Chưa chọn");
        detailStudentVal.setText("Chưa chọn");
        detailBookVal.setText("Chưa chọn");
        detailBorrowDateVal.setText("Chưa chọn");
        detailDueDateVal.setText("Chưa chọn");
        detailReturnDateVal.setText("Chưa chọn");
        detailStatusVal.setText("Chưa chọn");
        detailNotesVal.setText("Chưa chọn");
    }

    private void openBorrowDialog() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        BorrowDialog dialog = new BorrowDialog(owner, networkClient, null, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadRecords();
        }
    }

    private void performReturn() {
        BorrowRecordDTO record = getSelectedRecord();
        if (record == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một lượt mượn cần trả sách.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận nhận trả sách:\n'%s'\nSinh viên: %s (%s)\n\nTiến hành trả sách ngay bây giờ?",
                        record.getBookTitle(), record.getStudentName(), record.getStudentCode()),
                "Xác Nhận Trả Sách",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            returnBtn.setEnabled(false);
            statusLabel.setText("Đang xử lý trả sách trên máy chủ...");

            AsyncWorker.run(
                    () -> borrowController.returnBook(record.getStudentId(), record.getBookId()),
                    (ReturnResultDTO res) -> {
                        refreshBtn.setEnabled(true);
                        String alertMsg = res.getMessage();
                        if (res.isOverdue()) {
                            alertMsg += String.format("\n\nLƯU Ý: Sách đã bị quá hạn %d ngày!\nHệ thống đã tự động tạo khoản phạt %s VNĐ.",
                                    res.getOverdueDays(), res.getFineAmount());
                            JOptionPane.showMessageDialog(this, alertMsg, "Trả Sách Quá Hạn - Phát Sinh Phạt", JOptionPane.WARNING_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this, "Nhận trả sách thành công!", "Hoàn Tất Trả Sách", JOptionPane.INFORMATION_MESSAGE);
                        }
                        loadRecords();
                    },
                    this::handleError
            );
        }
    }

    private void handleError(Exception ex) {
        refreshBtn.setEnabled(true);
        if (newBorrowBtn != null) newBorrowBtn.setEnabled(true);
        statusLabel.setText("Lỗi: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Borrow/Return error", ex);

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
            JOptionPane.showMessageDialog(this, "Lỗi kết nối máy chủ: " + ex.getMessage(), "Lỗi Mạng", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi Mượn Trả", JOptionPane.ERROR_MESSAGE);
        }
    }
}
