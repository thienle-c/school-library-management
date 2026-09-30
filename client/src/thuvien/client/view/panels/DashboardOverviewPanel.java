package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientDashboardController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.exception.AuthenticationException;

/**
 * Panel displaying real-time library dashboard metrics and statistics from the server.
 */
public class DashboardOverviewPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(DashboardOverviewPanel.class.getName());
    private static final java.text.SimpleDateFormat TIME_FMT = new java.text.SimpleDateFormat("HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientDashboardController dashboardController;

    private JLabel totalBooksVal;
    private JLabel availableBooksVal;
    private JLabel borrowedBooksVal;
    private JLabel activeBorrowsVal;
    private JLabel overdueBorrowsVal;
    private JLabel unpaidFinesVal;
    private JButton refreshBtn;
    private JLabel statusLabel;

    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public DashboardOverviewPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.dashboardController = new ClientDashboardController(networkClient);
        initUI();
        initAutoRefresh();
        loadMetrics();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("Tổng Quan Hệ Thống & Chỉ Số Hoạt Động");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18.0f));
        JLabel subtitle = new JLabel("Thống kê vận hành thư viện theo thời gian thực từ máy chủ");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12.0f));
        subtitle.setForeground(new Color(110, 115, 125));
        titlePanel.add(title);
        titlePanel.add(subtitle);
        headerPanel.add(titlePanel, BorderLayout.WEST);

        refreshBtn = new JButton("Làm mới chỉ số");
        refreshBtn.addActionListener(e -> loadMetrics());
        headerPanel.add(refreshBtn, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Cards Grid (2 rows x 3 columns)
        JPanel gridPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        gridPanel.setOpaque(false);

        totalBooksVal = new JLabel("...", JLabel.CENTER);
        availableBooksVal = new JLabel("...", JLabel.CENTER);
        borrowedBooksVal = new JLabel("...", JLabel.CENTER);
        activeBorrowsVal = new JLabel("...", JLabel.CENTER);
        overdueBorrowsVal = new JLabel("...", JLabel.CENTER);
        unpaidFinesVal = new JLabel("...", JLabel.CENTER);

        gridPanel.add(createCard("Tổng số đầu sách", totalBooksVal, new Color(41, 128, 185)));
        gridPanel.add(createCard("Sách có sẵn", availableBooksVal, new Color(39, 174, 96)));
        gridPanel.add(createCard("Sách đang được mượn", borrowedBooksVal, new Color(230, 126, 34)));
        gridPanel.add(createCard("Lượt mượn đang hoạt động", activeBorrowsVal, new Color(142, 68, 173)));
        gridPanel.add(createCard("Lượt mượn quá hạn", overdueBorrowsVal, new Color(192, 57, 43)));
        gridPanel.add(createCard("Tổng tiền phạt chưa nộp", unpaidFinesVal, new Color(211, 84, 0)));

        add(gridPanel, BorderLayout.CENTER);

        // Status Label at bottom
        statusLabel = new JLabel("Sẵn sàng", JLabel.LEFT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        statusLabel.setForeground(new Color(120, 120, 120));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void initAutoRefresh() {
        // Polite auto-polling every 6 seconds while panel is visible
        pollTimer = new javax.swing.Timer(6000, e -> {
            if (isShowing() && isDisplayable() && !isRefreshing) {
                loadMetricsSilently();
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

    private JPanel createCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 8));
        card.setBackground(new Color(250, 252, 255));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(225, 230, 238), 1),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                )
        ));

        JLabel titleLbl = new JLabel(title, JLabel.CENTER);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13.0f));
        titleLbl.setForeground(new Color(70, 75, 85));
        card.add(titleLbl, BorderLayout.NORTH);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22.0f));
        valueLabel.setForeground(accentColor);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    public void loadMetrics() {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshBtn.setEnabled(false);
        statusLabel.setText("Đang tải dữ liệu chỉ số từ máy chủ...");

        AsyncWorker.run(
                dashboardController::getMetrics,
                (DashboardMetricsDTO m) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    updateValues(m);
                    statusLabel.setText("Đã cập nhật chỉ số lúc: " + TIME_FMT.format(new java.util.Date()));
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    statusLabel.setText("Lỗi tải chỉ số: " + ex.getMessage());
                    LOGGER.log(Level.WARNING, "Dashboard metrics load failed", ex);

                    if (ex instanceof AuthenticationException) {
                        String msg = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                        if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                            msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
                        }
                        JOptionPane.showMessageDialog(this,
                                msg,
                                "Hết Hạn Phiên",
                                JOptionPane.ERROR_MESSAGE);
                        java.awt.Window win = SwingUtilities.getWindowAncestor(this);
                        if (win != null) win.dispose();
                        SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
                    }
                }
        );
    }

    private void loadMetricsSilently() {
        if (isRefreshing) return;
        isRefreshing = true;

        AsyncWorker.run(
                dashboardController::getMetrics,
                (DashboardMetricsDTO m) -> {
                    isRefreshing = false;
                    updateValues(m);
                    statusLabel.setText("Tự động đồng bộ lúc: " + TIME_FMT.format(new java.util.Date()));
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    // Keep existing metrics on silent failure
                }
        );
    }

    private void updateValues(DashboardMetricsDTO m) {
        if (m == null) return;
        totalBooksVal.setText(String.valueOf(m.getTotalBooks()));
        availableBooksVal.setText(String.valueOf(m.getAvailableBooks()));
        borrowedBooksVal.setText(String.valueOf(m.getBorrowedBooks()));
        activeBorrowsVal.setText(String.valueOf(m.getActiveBorrowCount()));
        overdueBorrowsVal.setText(String.valueOf(m.getOverdueBorrowCount()));
        unpaidFinesVal.setText(String.format("%,.0f VNĐ", m.getTotalUnpaidFines()));
    }
}
