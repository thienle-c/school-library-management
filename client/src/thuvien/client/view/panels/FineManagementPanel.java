package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.math.BigDecimal;
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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientFineController;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.common.dto.FineDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Giao diện quản lý tiền phạt quá hạn và thanh toán tiền phạt.
 */
public class FineManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(FineManagementPanel.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final NetworkClient networkClient;
    private final ClientFineController fineController;
    private final boolean isStaff;

    private JTable fineTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JButton searchBtn;
    private JButton clearFilterBtn;
    private JButton refreshBtn;
    private JButton payFineBtn;
    private JSpinner calcDaysSpinner;
    private JButton calcBtn;
    private JLabel statusLabel;
    private JLabel summaryLabel;

    private List<FineDTO> allFines = new ArrayList<>();
    private List<FineDTO> currentFiltered = new ArrayList<>();
    private Timer pollTimer;
    private boolean isPolling = false;

    public FineManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.fineController = new ClientFineController(networkClient);
        this.isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        initUI();
        loadFines();
        startAutoRefresh();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // North container
        JPanel northPanel = new JPanel(new BorderLayout(5, 8));
        northPanel.setOpaque(false);

        // Header toolbar
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel(isStaff ? "Quản lý Tiền phạt & Phí quá hạn" : "Tiền phạt quá hạn của tôi");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16.0f));
        title.setForeground(new Color(25, 45, 80));
        headerPanel.add(title, BorderLayout.WEST);

        JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightHeaderPanel.setOpaque(false);

        if (isStaff) {
            rightHeaderPanel.add(new JLabel("Số ngày:"));
            calcDaysSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 365, 1));
            rightHeaderPanel.add(calcDaysSpinner);

            calcBtn = new JButton("Tính phí phạt");
            calcBtn.setToolTipText("Tính mức tiền phạt theo số ngày quá hạn");
            calcBtn.addActionListener(e -> calculateFinePreview());
            rightHeaderPanel.add(calcBtn);

            payFineBtn = new JButton("Thu tiền / Đã nộp");
            payFineBtn.setBackground(new Color(225, 245, 230));
            payFineBtn.setEnabled(false);
            payFineBtn.setToolTipText("Đánh dấu khoản phạt đã được thanh toán");
            payFineBtn.addActionListener(e -> performPayFine());
            rightHeaderPanel.add(payFineBtn);
        }

        refreshBtn = new JButton("Làm mới");
        refreshBtn.setToolTipText("Tải lại danh sách từ máy chủ");
        refreshBtn.addActionListener(e -> loadFines());
        rightHeaderPanel.add(refreshBtn);

        headerPanel.add(rightHeaderPanel, BorderLayout.EAST);
        northPanel.add(headerPanel, BorderLayout.NORTH);

        // Search & Filter Toolbar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 235, 240)));

        filterBar.add(new JLabel("Tìm kiếm:"));
        searchField = new JTextField(20);
        searchField.setToolTipText("Tìm theo Mã phạt, Mã SV, Tên sinh viên, Mã mượn...");
        searchField.addActionListener(e -> applyFilter());
        SearchAutoCompleteHelper.attach(searchField, "FINE", new ClientSearchSuggestionController(networkClient), this::applyFilter);
        filterBar.add(searchField);

        filterBar.add(new JLabel("Trạng thái:"));
        statusFilterCombo = new JComboBox<>(new String[]{
                "Tất cả",
                "Chưa thanh toán",
                "Đã thanh toán"
        });
        statusFilterCombo.addActionListener(e -> applyFilter());
        filterBar.add(statusFilterCombo);

        searchBtn = new JButton("Tìm");
        searchBtn.addActionListener(e -> applyFilter());
        filterBar.add(searchBtn);

        clearFilterBtn = new JButton("Xóa bộ lọc");
        clearFilterBtn.addActionListener(e -> resetFilter());
        filterBar.add(clearFilterBtn);

        northPanel.add(filterBar, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // Table
        String[] columns = isStaff
                ? new String[]{"Mã phạt", "Mã mượn", "Mã SV", "Tên sinh viên", "Tên sách", "Số ngày quá hạn", "Số tiền (VNĐ)", "Trạng thái", "Ngày tạo"}
                : new String[]{"Mã phạt", "Mã mượn", "Tên sách", "Số ngày quá hạn", "Số tiền (VNĐ)", "Trạng thái", "Ngày tạo"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        fineTable = new JTable(tableModel);
        fineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fineTable.setRowHeight(25);
        fineTable.getTableHeader().setFont(fineTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        fineTable.setAutoCreateRowSorter(true);

        fineTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && isStaff && payFineBtn != null) {
                FineDTO selected = getSelectedFine();
                payFineBtn.setEnabled(selected != null && !selected.isPaid());
            }
        });

        JScrollPane tableScroll = new JScrollPane(fineTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));
        add(tableScroll, BorderLayout.CENTER);

        // Bottom Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        bottomBar.add(statusLabel, BorderLayout.WEST);

        summaryLabel = new JLabel("");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        summaryLabel.setForeground(new Color(180, 50, 40));
        bottomBar.add(summaryLabel, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void startAutoRefresh() {
        pollTimer = new Timer(6000, e -> {
            if (isShowing() && !isPolling) {
                loadSilently();
            }
        });
        pollTimer.start();
    }

    public void loadFines() {
        refreshBtn.setEnabled(false);
        if (payFineBtn != null) payFineBtn.setEnabled(false);
        statusLabel.setText("Đang tải danh sách tiền phạt từ máy chủ...");

        if (isStaff) {
            AsyncWorker.run(
                    fineController::listAllFines,
                    this::onFinesLoaded,
                    this::handleError
            );
        } else {
            AsyncWorker.run(
                    () -> fineController.listStudentFines(null),
                    this::onFinesLoaded,
                    this::handleError
            );
        }
    }

    private void loadSilently() {
        isPolling = true;
        if (isStaff) {
            AsyncWorker.run(
                    fineController::listAllFines,
                    fines -> {
                        isPolling = false;
                        allFines = (fines != null) ? fines : new ArrayList<>();
                        applyFilterSilently();
                    },
                    ex -> isPolling = false
            );
        } else {
            AsyncWorker.run(
                    () -> fineController.listStudentFines(null),
                    fines -> {
                        isPolling = false;
                        allFines = (fines != null) ? fines : new ArrayList<>();
                        applyFilterSilently();
                    },
                    ex -> isPolling = false
            );
        }
    }

    private void onFinesLoaded(List<FineDTO> fines) {
        refreshBtn.setEnabled(true);
        allFines = (fines != null) ? fines : new ArrayList<>();
        applyFilter();
    }

    private void applyFilter() {
        applyFilterInternal(false);
    }

    private void applyFilterSilently() {
        applyFilterInternal(true);
    }

    private void applyFilterInternal(boolean silent) {
        String kw = searchField.getText().trim().toLowerCase();
        int statusIdx = statusFilterCombo.getSelectedIndex(); // 0: Tất cả, 1: Chưa thanh toán, 2: Đã thanh toán

        currentFiltered = new ArrayList<>();
        BigDecimal unpaidTotal = BigDecimal.ZERO;

        for (FineDTO f : allFines) {
            if (!f.isPaid() && f.getFineAmount() != null) {
                unpaidTotal = unpaidTotal.add(f.getFineAmount());
            }

            // Filter status
            if (statusIdx == 1 && f.isPaid()) continue;
            if (statusIdx == 2 && !f.isPaid()) continue;

            // Keyword match: ID, borrowRecordId, studentId, studentName, bookTitle
            if (!kw.isEmpty()) {
                boolean match = false;
                if (f.getId() != null && String.valueOf(f.getId()).contains(kw)) match = true;
                if (!match && f.getBorrowRecordId() != null && String.valueOf(f.getBorrowRecordId()).contains(kw)) match = true;
                if (!match && f.getStudentId() != null && String.valueOf(f.getStudentId()).contains(kw)) match = true;
                if (!match && f.getStudentName() != null && f.getStudentName().toLowerCase().contains(kw)) match = true;
                if (!match && f.getBookTitle() != null && f.getBookTitle().toLowerCase().contains(kw)) match = true;
                if (!match) continue;
            }

            currentFiltered.add(f);
        }

        Long prevSelectedId = null;
        if (silent) {
            FineDTO prev = getSelectedFine();
            if (prev != null) prevSelectedId = prev.getId();
        }

        tableModel.setRowCount(0);
        int restoreRow = -1;

        for (int i = 0; i < currentFiltered.size(); i++) {
            FineDTO f = currentFiltered.get(i);
            String createdDt = f.getCreatedAt() != null ? DATE_FMT.format(f.getCreatedAt()) : "";
            String st = f.isPaid() ? "Đã thanh toán" : "Chưa thanh toán";
            String amtStr = f.getFineAmount() != null ? String.format("%,.0f", f.getFineAmount().doubleValue()) : "0";

            if (isStaff) {
                tableModel.addRow(new Object[]{
                        f.getId(),
                        f.getBorrowRecordId(),
                        f.getStudentId(),
                        f.getStudentName() != null ? f.getStudentName() : ("ID: " + f.getStudentId()),
                        f.getBookTitle(),
                        f.getOverdueDays(),
                        amtStr,
                        st,
                        createdDt
                });
            } else {
                tableModel.addRow(new Object[]{
                        f.getId(),
                        f.getBorrowRecordId(),
                        f.getBookTitle(),
                        f.getOverdueDays(),
                        amtStr,
                        st,
                        createdDt
                });
            }

            if (prevSelectedId != null && prevSelectedId.equals(f.getId())) {
                restoreRow = i;
            }
        }

        if (restoreRow >= 0 && restoreRow < fineTable.getRowCount()) {
            fineTable.setRowSelectionInterval(restoreRow, restoreRow);
        }

        if (unpaidTotal.compareTo(BigDecimal.ZERO) > 0) {
            summaryLabel.setText(String.format("Tổng dư nợ chưa thanh toán: %,.0f VNĐ", unpaidTotal.doubleValue()));
        } else {
            summaryLabel.setText("Dư nợ: 0 VNĐ");
        }

        if (!silent) {
            if (currentFiltered.isEmpty()) {
                statusLabel.setText("Không tìm thấy bản ghi tiền phạt nào phù hợp.");
            } else {
                statusLabel.setText(String.format("Đã tải %d bản ghi tiền phạt.", currentFiltered.size()));
            }
        }
    }

    private void resetFilter() {
        searchField.setText("");
        statusFilterCombo.setSelectedIndex(0);
        applyFilter();
    }

    private FineDTO getSelectedFine() {
        int row = fineTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = fineTable.convertRowIndexToModel(row);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        if (idObj instanceof Long) {
            long targetId = (Long) idObj;
            for (FineDTO f : currentFiltered) {
                if (f.getId().equals(targetId)) {
                    return f;
                }
            }
        }
        return null;
    }

    private void calculateFinePreview() {
        int days = (Integer) calcDaysSpinner.getValue();
        calcBtn.setEnabled(false);
        statusLabel.setText("Đang tính tiền phạt với máy chủ...");

        AsyncWorker.run(
                () -> fineController.calculateFine(days),
                (BigDecimal amt) -> {
                    calcBtn.setEnabled(true);
                    statusLabel.setText("Tính toán hoàn tất.");
                    String amtStr = amt != null ? String.format("%,.0f", amt.doubleValue()) : "0";
                    JOptionPane.showMessageDialog(this,
                            String.format("Mức tiền phạt tính toán cho %d ngày quá hạn:\nTổng tiền: %s VNĐ", days, amtStr),
                            "Tính toán mức phạt",
                            JOptionPane.INFORMATION_MESSAGE);
                },
                this::handleError
        );
    }

    private void performPayFine() {
        FineDTO fine = getSelectedFine();
        if (fine == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một khoản phạt chưa nộp để thu tiền.", "Chưa chọn dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String amtStr = fine.getFineAmount() != null ? String.format("%,.0f", fine.getFineAmount().doubleValue()) : "0";
        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận thu tiền phạt #%d:\nSinh viên: %s\nSách: %s\nSố tiền: %s VNĐ\n\nĐánh dấu là ĐÃ THANH TOÁN?",
                        fine.getId(), fine.getStudentName(), fine.getBookTitle(), amtStr),
                "Xác nhận thu tiền phạt",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            if (payFineBtn != null) payFineBtn.setEnabled(false);
            statusLabel.setText("Đang ghi nhận thanh toán trên máy chủ...");

            AsyncWorker.run(
                    () -> fineController.payFine(fine.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã ghi nhận thanh toán tiền phạt thành công.", "Thu tiền thành công", JOptionPane.INFORMATION_MESSAGE);
                        loadFines();
                    },
                    this::handleError
            );
        }
    }

    private void handleError(Exception ex) {
        refreshBtn.setEnabled(true);
        if (calcBtn != null) calcBtn.setEnabled(true);
        statusLabel.setText("Lỗi: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Fine error", ex);

        if (ex instanceof AuthenticationException) {
            String msg = "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.";
            if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
            }
            JOptionPane.showMessageDialog(this, msg, "Hết phiên", JOptionPane.ERROR_MESSAGE);
            java.awt.Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) win.dispose();
            SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
        } else if (ex instanceof AuthorizationException) {
            JOptionPane.showMessageDialog(this, "Từ chối truy cập: " + ex.getMessage(), "Không có quyền", JOptionPane.ERROR_MESSAGE);
        } else if (ex instanceof NetworkException) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối máy chủ: " + ex.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Thao tác thất bại: " + ex.getMessage(), "Lỗi tiền phạt", JOptionPane.ERROR_MESSAGE);
        }
    }
}
