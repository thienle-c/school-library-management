package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientReservationController;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.client.view.dialogs.ReservationDialog;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.enums.ReservationStatus;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Giao diện quản lý và theo dõi đặt trước sách (Reservations / Holds).
 */
public class ReservationManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(ReservationManagementPanel.class.getName());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final NetworkClient networkClient;
    private final ClientReservationController reservationController;
    private final boolean isStaff;

    private JTable reservationTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JButton searchBtn;
    private JButton clearFilterBtn;
    private JButton refreshBtn;
    private JButton newResBtn;
    private JButton cancelResBtn;
    private JLabel statusLabel;

    private List<ReservationDTO> allReservations = new ArrayList<>();
    private List<ReservationDTO> currentFiltered = new ArrayList<>();
    private Timer pollTimer;
    private boolean isPolling = false;

    public ReservationManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.reservationController = new ClientReservationController(networkClient);
        this.isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        initUI();
        loadReservations();
        startAutoRefresh();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // Header and Actions toolbar
        JPanel northPanel = new JPanel(new BorderLayout(5, 8));
        northPanel.setOpaque(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel(isStaff ? "Quản lý Đặt trước sách" : "Sách tôi đã đặt trước");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16.0f));
        title.setForeground(new Color(25, 45, 80));
        headerPanel.add(title, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBtns.setOpaque(false);

        newResBtn = new JButton("+ Đặt trước sách");
        newResBtn.setBackground(new Color(225, 245, 230));
        newResBtn.setToolTipText("Tạo yêu cầu đặt trước sách mới");
        newResBtn.addActionListener(e -> openReservationDialog());
        rightBtns.add(newResBtn);

        cancelResBtn = new JButton("Hủy đặt trước");
        cancelResBtn.setBackground(new Color(255, 235, 235));
        cancelResBtn.setEnabled(false);
        cancelResBtn.setToolTipText("Hủy yêu cầu đặt trước đã chọn");
        cancelResBtn.addActionListener(e -> cancelSelectedReservation());
        rightBtns.add(cancelResBtn);

        refreshBtn = new JButton("Làm mới");
        refreshBtn.setToolTipText("Tải lại danh sách từ máy chủ");
        refreshBtn.addActionListener(e -> loadReservations());
        rightBtns.add(refreshBtn);

        headerPanel.add(rightBtns, BorderLayout.EAST);
        northPanel.add(headerPanel, BorderLayout.NORTH);

        // Filter / Search Toolbar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 235, 240)));

        filterBar.add(new JLabel("Tìm kiếm:"));
        searchField = new JTextField(20);
        searchField.setToolTipText("Tìm theo Mã ĐT, Mã SV, Tên sinh viên, Tên sách...");
        searchField.addActionListener(e -> applyFilter());
        SearchAutoCompleteHelper.attach(searchField, "RESERVATION", new ClientSearchSuggestionController(networkClient), this::applyFilter);
        filterBar.add(searchField);

        filterBar.add(new JLabel("Trạng thái:"));
        statusFilterCombo = new JComboBox<>(new String[]{
                "Tất cả trạng thái",
                "Đang chờ (PENDING)",
                "Đã hoàn thành (FULFILLED)",
                "Đã hủy (CANCELLED)"
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
                ? new String[]{"Mã ĐT", "Mã SV", "Tên sinh viên", "Tên sách", "Ngày đặt", "Hạn giữ", "Trạng thái"}
                : new String[]{"Mã ĐT", "Tên sách", "Ngày đặt", "Hạn giữ", "Trạng thái"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        reservationTable = new JTable(tableModel);
        reservationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationTable.setRowHeight(25);
        reservationTable.getTableHeader().setFont(reservationTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        reservationTable.setAutoCreateRowSorter(true);

        reservationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ReservationDTO selected = getSelectedReservation();
                cancelResBtn.setEnabled(selected != null && selected.getStatus() == ReservationStatus.PENDING);
            }
        });

        JScrollPane tableScroll = new JScrollPane(reservationTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));
        add(tableScroll, BorderLayout.CENTER);

        // Bottom Status
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        bottomBar.add(statusLabel, BorderLayout.WEST);

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

    public void loadReservations() {
        refreshBtn.setEnabled(false);
        newResBtn.setEnabled(false);
        cancelResBtn.setEnabled(false);
        statusLabel.setText("Đang tải danh sách đặt trước từ máy chủ...");

        if (isStaff) {
            AsyncWorker.run(
                    reservationController::listAllReservations,
                    this::onLoaded,
                    this::handleError
            );
        } else {
            AsyncWorker.run(
                    () -> reservationController.listStudentReservations(null),
                    this::onLoaded,
                    this::handleError
            );
        }
    }

    private void loadSilently() {
        isPolling = true;
        if (isStaff) {
            AsyncWorker.run(
                    reservationController::listAllReservations,
                    list -> {
                        isPolling = false;
                        allReservations = (list != null) ? list : new ArrayList<>();
                        applyFilterSilently();
                    },
                    ex -> isPolling = false
            );
        } else {
            AsyncWorker.run(
                    () -> reservationController.listStudentReservations(null),
                    list -> {
                        isPolling = false;
                        allReservations = (list != null) ? list : new ArrayList<>();
                        applyFilterSilently();
                    },
                    ex -> isPolling = false
            );
        }
    }

    private void onLoaded(List<ReservationDTO> list) {
        refreshBtn.setEnabled(true);
        newResBtn.setEnabled(true);
        allReservations = (list != null) ? list : new ArrayList<>();
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
        int statusIdx = statusFilterCombo.getSelectedIndex();

        currentFiltered = new ArrayList<>();
        for (ReservationDTO r : allReservations) {
            // Status check
            if (statusIdx == 1 && r.getStatus() != ReservationStatus.PENDING) continue;
            if (statusIdx == 2 && r.getStatus() != ReservationStatus.FULFILLED) continue;
            if (statusIdx == 3 && r.getStatus() != ReservationStatus.CANCELLED) continue;

            // Keyword check
            if (!kw.isEmpty()) {
                boolean match = false;
                if (r.getId() != null && String.valueOf(r.getId()).contains(kw)) match = true;
                if (!match && r.getStudentId() != null && String.valueOf(r.getStudentId()).contains(kw)) match = true;
                if (!match && r.getStudentName() != null && r.getStudentName().toLowerCase().contains(kw)) match = true;
                if (!match && r.getBookTitle() != null && r.getBookTitle().toLowerCase().contains(kw)) match = true;
                if (!match) continue;
            }
            currentFiltered.add(r);
        }

        Long prevSelectedId = null;
        if (silent) {
            ReservationDTO prev = getSelectedReservation();
            if (prev != null) prevSelectedId = prev.getId();
        }

        tableModel.setRowCount(0);
        int restoreRow = -1;

        for (int i = 0; i < currentFiltered.size(); i++) {
            ReservationDTO r = currentFiltered.get(i);
            String resDt = r.getReservationDate() != null ? DATE_FMT.format(r.getReservationDate()) : "";
            String expDt = r.getExpiryDate() != null ? DATE_FMT.format(r.getExpiryDate()) : "";
            String st = formatStatus(r.getStatus());

            if (isStaff) {
                tableModel.addRow(new Object[]{
                        r.getId(),
                        r.getStudentId(),
                        r.getStudentName() != null ? r.getStudentName() : ("ID: " + r.getStudentId()),
                        r.getBookTitle(),
                        resDt,
                        expDt,
                        st
                });
            } else {
                tableModel.addRow(new Object[]{
                        r.getId(),
                        r.getBookTitle(),
                        resDt,
                        expDt,
                        st
                });
            }

            if (prevSelectedId != null && prevSelectedId.equals(r.getId())) {
                restoreRow = i;
            }
        }

        if (restoreRow >= 0 && restoreRow < reservationTable.getRowCount()) {
            reservationTable.setRowSelectionInterval(restoreRow, restoreRow);
        }

        if (!silent) {
            if (currentFiltered.isEmpty()) {
                statusLabel.setText("Không tìm thấy lượt đặt trước nào phù hợp.");
            } else {
                statusLabel.setText(String.format("Đã tải %d lượt đặt trước.", currentFiltered.size()));
            }
        }
    }

    private void resetFilter() {
        searchField.setText("");
        statusFilterCombo.setSelectedIndex(0);
        applyFilter();
    }

    private String formatStatus(ReservationStatus s) {
        if (s == null) return "";
        switch (s) {
            case PENDING:
                return "Đang chờ";
            case FULFILLED:
                return "Đã hoàn thành";
            case CANCELLED:
                return "Đã hủy";
            default:
                return s.name();
        }
    }

    private ReservationDTO getSelectedReservation() {
        int row = reservationTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = reservationTable.convertRowIndexToModel(row);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        if (idObj instanceof Long) {
            long targetId = (Long) idObj;
            for (ReservationDTO r : currentFiltered) {
                if (r.getId().equals(targetId)) {
                    return r;
                }
            }
        }
        return null;
    }

    private void openReservationDialog() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        ReservationDialog dialog = new ReservationDialog(owner, networkClient, null, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadReservations();
        }
    }

    private void cancelSelectedReservation() {
        ReservationDTO r = getSelectedReservation();
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một lượt đặt trước để hủy.", "Chưa chọn dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Bạn có chắc chắn muốn hủy lượt đặt trước #%d cho sách:\n'%s' không?", r.getId(), r.getBookTitle()),
                "Xác nhận hủy đặt trước",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            cancelResBtn.setEnabled(false);
            statusLabel.setText("Đang xử lý hủy đặt trước trên máy chủ...");

            AsyncWorker.run(
                    () -> reservationController.cancelReservation(r.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Hủy đặt trước thành công.", "Đã hủy", JOptionPane.INFORMATION_MESSAGE);
                        loadReservations();
                    },
                    this::handleError
            );
        }
    }

    private void handleError(Exception ex) {
        refreshBtn.setEnabled(true);
        newResBtn.setEnabled(true);
        statusLabel.setText("Lỗi: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Reservation error", ex);

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
            JOptionPane.showMessageDialog(this, "Thao tác thất bại: " + ex.getMessage(), "Lỗi đặt trước", JOptionPane.ERROR_MESSAGE);
        }
    }
}
