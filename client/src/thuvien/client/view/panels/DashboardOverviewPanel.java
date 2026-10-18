package thuvien.client.view.panels;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientDashboardController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.ModernUIHelper;
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

    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public DashboardOverviewPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.dashboardController = new ClientDashboardController(networkClient);
        initComponents();
        initCustom();
        initAutoRefresh();
        loadMetrics();
    }

    private void initCustom() {
        setBackground(ModernUIHelper.BG_MAIN);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(22, 28, 20, 28));

        // Header Styling
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 22));
        title.setForeground(ModernUIHelper.TEXT_PRIMARY);
        title.setText("Tổng Quan Hệ Thống & Chỉ Số Hoạt Động");

        subtitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        subtitle.setForeground(ModernUIHelper.TEXT_MUTED);
        subtitle.setText("Dữ liệu thống kê thời gian thực từ máy chủ phân tán Java RMI");

        headerPanel.setOpaque(false);
        headerPanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, ModernUIHelper.BORDER_COLOR),
                javax.swing.BorderFactory.createEmptyBorder(0, 0, 16, 0)
        ));

        ModernUIHelper.stylePrimaryButton(refreshBtn, "Làm mới chỉ số");
        refreshBtn.setIcon(ModernUIHelper.getVectorIcon("REFRESH", 14, ModernUIHelper.CYAN_TEXT));
        refreshBtn.setIconTextGap(8);
        refreshBtn.setPreferredSize(new java.awt.Dimension(160, 36));

        // Grid Panel Layout
        gridPanel.setOpaque(false);
        gridPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 0, 18, 0));

        // Style 6 Metric Cards with 6 accurate WCAG compliant accents
        styleMetricCard(cardTotalBooks, lblCard1, totalBooksVal, "Tổng số đầu sách", "BOOKS", ModernUIHelper.KPI_BLUE);
        styleMetricCard(cardAvailable, lblCard2, availableBooksVal, "Sách có sẵn", "CHECK", ModernUIHelper.KPI_GREEN);
        styleMetricCard(cardBorrowed, lblCard3, borrowedBooksVal, "Sách đang được mượn", "RESERVATIONS", ModernUIHelper.KPI_AMBER);
        styleMetricCard(cardActiveBorrows, lblCard4, activeBorrowsVal, "Lượt mượn hiện hành", "CIRCULATION", ModernUIHelper.KPI_PURPLE);
        styleMetricCard(cardOverdue, lblCard5, overdueBorrowsVal, "Lượt mượn quá hạn", "AUDIT", ModernUIHelper.KPI_RED);
        styleMetricCard(cardUnpaidFines, lblCard6, unpaidFinesVal, "Tiền phạt chưa nộp", "FINES", ModernUIHelper.KPI_ORANGE);

        // Status Label Styling
        statusLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 11));
        statusLabel.setForeground(ModernUIHelper.TEXT_MUTED);
        statusLabel.setText("Đang khởi tạo kết nối chỉ số...");
        statusLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 0, 4, 0));
        ModernUIHelper.styleComponentTree(this);
    }

    private void styleMetricCard(final javax.swing.JPanel card, javax.swing.JLabel lblTitle, javax.swing.JLabel lblVal,
                                 String titleText, final String iconType, final java.awt.Color accentColor) {
        card.setOpaque(false);
        card.setLayout(new java.awt.BorderLayout(8, 10));

        // 60 FPS Micro-Animation for smooth card hover transition
        final float[] hoverProgress = {0f};
        final javax.swing.Timer[] animTimer = new javax.swing.Timer[1];

        // Custom background and border painting for 60fps lerp
        card.setBorder(new javax.swing.border.Border() {
            @Override
            public void paintBorder(java.awt.Component c, java.awt.Graphics g, int x, int y, int width, int height) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                // 1. Interpolated Background (#131F33 to #1A2942)
                java.awt.Color bg = ModernUIHelper.lerp(ModernUIHelper.BG_SURFACE, ModernUIHelper.BG_CARD_HOVER, hoverProgress[0]);
                g2.setColor(bg);
                g2.fillRoundRect(x, y, width - 1, height - 1, 10, 10);

                // 2. Interpolated Border (#1E293B to glowing accent color)
                java.awt.Color borderCol = ModernUIHelper.lerp(ModernUIHelper.BORDER_COLOR, accentColor, hoverProgress[0]);
                g2.setColor(borderCol);
                g2.setStroke(new java.awt.BasicStroke(1.0f));
                g2.drawRoundRect(x, y, width - 1, height - 1, 10, 10);

                // 3. 4px Left vertical accent stripe
                g2.setColor(accentColor);
                g2.fillRoundRect(x, y + 4, 4, height - 8, 3, 3);
                g2.dispose();
            }

            @Override
            public java.awt.Insets getBorderInsets(java.awt.Component c) {
                return new java.awt.Insets(14, 18, 14, 16);
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        });

        // Mouse hover listener with 60fps interpolation timer (15ms step)
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (animTimer[0] != null && animTimer[0].isRunning()) animTimer[0].stop();
                animTimer[0] = new javax.swing.Timer(15, evt -> {
                    hoverProgress[0] += 0.12f;
                    if (hoverProgress[0] >= 1.0f) {
                        hoverProgress[0] = 1.0f;
                        animTimer[0].stop();
                    }
                    card.repaint();
                });
                animTimer[0].start();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (animTimer[0] != null && animTimer[0].isRunning()) animTimer[0].stop();
                animTimer[0] = new javax.swing.Timer(15, evt -> {
                    hoverProgress[0] -= 0.12f;
                    if (hoverProgress[0] <= 0.0f) {
                        hoverProgress[0] = 0.0f;
                        animTimer[0].stop();
                    }
                    card.repaint();
                });
                animTimer[0].start();
            }
        });

        javax.swing.JPanel topRow = new javax.swing.JPanel(new java.awt.BorderLayout(6, 0));
        topRow.setOpaque(false);

        lblTitle.setText(titleText);
        lblTitle.setFont(ModernUIHelper.FONT_LABEL);
        lblTitle.setForeground(ModernUIHelper.LABEL_DETAIL);
        lblTitle.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        topRow.add(lblTitle, java.awt.BorderLayout.CENTER);

        // 36x36px Rounded square with 15% opacity accent background and crisp vector icon
        javax.swing.JComponent iconBadge = new javax.swing.JComponent() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(36, 36);
            }

            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                // 15% opacity accent background (alpha = 38)
                java.awt.Color bgAlpha = new java.awt.Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 38);
                g2.setColor(bgAlpha);
                g2.fillRoundRect(0, 0, 36, 36, 8, 8);

                // 1px subtle accent outline
                g2.setColor(new java.awt.Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80));
                g2.drawRoundRect(0, 0, 35, 35, 8, 8);

                // Center vector icon in exact accent color
                javax.swing.Icon vIcon = ModernUIHelper.getVectorIcon(iconType, 18, accentColor);
                if (vIcon != null) {
                    vIcon.paintIcon(this, g2, 9, 9);
                }
                g2.dispose();
            }
        };
        topRow.add(iconBadge, java.awt.BorderLayout.EAST);

        lblVal.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 26));
        lblVal.setForeground(accentColor);
        lblVal.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

        card.removeAll();
        card.add(topRow, java.awt.BorderLayout.NORTH);
        card.add(lblVal, java.awt.BorderLayout.CENTER);
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        headerPanel = new javax.swing.JPanel();
        titlePanel = new javax.swing.JPanel();
        title = new javax.swing.JLabel();
        subtitle = new javax.swing.JLabel();
        refreshBtn = new javax.swing.JButton();
        gridPanel = new javax.swing.JPanel();
        cardTotalBooks = new javax.swing.JPanel();
        lblCard1 = new javax.swing.JLabel();
        totalBooksVal = new javax.swing.JLabel();
        cardAvailable = new javax.swing.JPanel();
        lblCard2 = new javax.swing.JLabel();
        availableBooksVal = new javax.swing.JLabel();
        cardBorrowed = new javax.swing.JPanel();
        lblCard3 = new javax.swing.JLabel();
        borrowedBooksVal = new javax.swing.JLabel();
        cardActiveBorrows = new javax.swing.JPanel();
        lblCard4 = new javax.swing.JLabel();
        activeBorrowsVal = new javax.swing.JLabel();
        cardOverdue = new javax.swing.JPanel();
        lblCard5 = new javax.swing.JLabel();
        overdueBorrowsVal = new javax.swing.JLabel();
        cardUnpaidFines = new javax.swing.JPanel();
        lblCard6 = new javax.swing.JLabel();
        unpaidFinesVal = new javax.swing.JLabel();
        statusLabel = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 255));
        setLayout(new java.awt.BorderLayout(10, 10));

        headerPanel.setOpaque(false);
        headerPanel.setLayout(new java.awt.BorderLayout());

        titlePanel.setOpaque(false);
        titlePanel.setLayout(new java.awt.GridLayout(2, 1, 0, 2));

        title.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        title.setText("Tổng Quan Hệ Thống & Chỉ Số Hoạt Động");
        titlePanel.add(title);

        subtitle.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        subtitle.setForeground(new java.awt.Color(110, 115, 125));
        subtitle.setText("Thống kê vận hành thư viện theo thời gian thực từ máy chủ");
        titlePanel.add(subtitle);

        headerPanel.add(titlePanel, java.awt.BorderLayout.WEST);

        refreshBtn.setText("Làm mới chỉ số");
        refreshBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshBtnActionPerformed(evt);
            }
        });
        headerPanel.add(refreshBtn, java.awt.BorderLayout.EAST);

        add(headerPanel, java.awt.BorderLayout.NORTH);

        gridPanel.setOpaque(false);
        gridPanel.setLayout(new java.awt.GridLayout(2, 3, 15, 15));

        cardTotalBooks.setBackground(new java.awt.Color(250, 252, 255));
        cardTotalBooks.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(41, 128, 185)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardTotalBooks.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard1.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard1.setForeground(new java.awt.Color(70, 75, 85));
        lblCard1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard1.setText("Tổng số đầu sách");
        cardTotalBooks.add(lblCard1, java.awt.BorderLayout.NORTH);

        totalBooksVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        totalBooksVal.setForeground(new java.awt.Color(41, 128, 185));
        totalBooksVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        totalBooksVal.setText("...");
        cardTotalBooks.add(totalBooksVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardTotalBooks);

        cardAvailable.setBackground(new java.awt.Color(250, 252, 255));
        cardAvailable.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(39, 174, 96)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardAvailable.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard2.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard2.setForeground(new java.awt.Color(70, 75, 85));
        lblCard2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard2.setText("Sách có sẵn");
        cardAvailable.add(lblCard2, java.awt.BorderLayout.NORTH);

        availableBooksVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        availableBooksVal.setForeground(new java.awt.Color(39, 174, 96));
        availableBooksVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        availableBooksVal.setText("...");
        cardAvailable.add(availableBooksVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardAvailable);

        cardBorrowed.setBackground(new java.awt.Color(250, 252, 255));
        cardBorrowed.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(230, 126, 34)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardBorrowed.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard3.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard3.setForeground(new java.awt.Color(70, 75, 85));
        lblCard3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard3.setText("Sách đang được mượn");
        cardBorrowed.add(lblCard3, java.awt.BorderLayout.NORTH);

        borrowedBooksVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        borrowedBooksVal.setForeground(new java.awt.Color(230, 126, 34));
        borrowedBooksVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        borrowedBooksVal.setText("...");
        cardBorrowed.add(borrowedBooksVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardBorrowed);

        cardActiveBorrows.setBackground(new java.awt.Color(250, 252, 255));
        cardActiveBorrows.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(142, 68, 173)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardActiveBorrows.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard4.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard4.setForeground(new java.awt.Color(70, 75, 85));
        lblCard4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard4.setText("Lượt mượn đang hoạt động");
        cardActiveBorrows.add(lblCard4, java.awt.BorderLayout.NORTH);

        activeBorrowsVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        activeBorrowsVal.setForeground(new java.awt.Color(142, 68, 173));
        activeBorrowsVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        activeBorrowsVal.setText("...");
        cardActiveBorrows.add(activeBorrowsVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardActiveBorrows);

        cardOverdue.setBackground(new java.awt.Color(250, 252, 255));
        cardOverdue.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(192, 57, 43)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardOverdue.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard5.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard5.setForeground(new java.awt.Color(70, 75, 85));
        lblCard5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard5.setText("Lượt mượn quá hạn");
        cardOverdue.add(lblCard5, java.awt.BorderLayout.NORTH);

        overdueBorrowsVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        overdueBorrowsVal.setForeground(new java.awt.Color(192, 57, 43));
        overdueBorrowsVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        overdueBorrowsVal.setText("...");
        cardOverdue.add(overdueBorrowsVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardOverdue);

        cardUnpaidFines.setBackground(new java.awt.Color(250, 252, 255));
        cardUnpaidFines.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 4, 0, 0, new java.awt.Color(211, 84, 0)), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(225, 230, 238)), javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15))));
        cardUnpaidFines.setLayout(new java.awt.BorderLayout(5, 8));

        lblCard6.setFont(new java.awt.Font("Tahoma", 1, 13)); // NOI18N
        lblCard6.setForeground(new java.awt.Color(70, 75, 85));
        lblCard6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCard6.setText("Tổng tiền phạt chưa nộp");
        cardUnpaidFines.add(lblCard6, java.awt.BorderLayout.NORTH);

        unpaidFinesVal.setFont(new java.awt.Font("Tahoma", 1, 22)); // NOI18N
        unpaidFinesVal.setForeground(new java.awt.Color(211, 84, 0));
        unpaidFinesVal.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        unpaidFinesVal.setText("...");
        cardUnpaidFines.add(unpaidFinesVal, java.awt.BorderLayout.CENTER);

        gridPanel.add(cardUnpaidFines);

        add(gridPanel, java.awt.BorderLayout.CENTER);

        statusLabel.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        statusLabel.setForeground(new java.awt.Color(120, 120, 120));
        statusLabel.setText("Sẵn sàng");
        add(statusLabel, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void refreshBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshBtnActionPerformed
        loadMetrics();
    }//GEN-LAST:event_refreshBtnActionPerformed

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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel activeBorrowsVal;
    private javax.swing.JLabel availableBooksVal;
    private javax.swing.JLabel borrowedBooksVal;
    private javax.swing.JPanel cardActiveBorrows;
    private javax.swing.JPanel cardAvailable;
    private javax.swing.JPanel cardBorrowed;
    private javax.swing.JPanel cardOverdue;
    private javax.swing.JPanel cardTotalBooks;
    private javax.swing.JPanel cardUnpaidFines;
    private javax.swing.JPanel gridPanel;
    private javax.swing.JPanel headerPanel;
    private javax.swing.JLabel lblCard1;
    private javax.swing.JLabel lblCard2;
    private javax.swing.JLabel lblCard3;
    private javax.swing.JLabel lblCard4;
    private javax.swing.JLabel lblCard5;
    private javax.swing.JLabel lblCard6;
    private javax.swing.JLabel overdueBorrowsVal;
    private javax.swing.JButton refreshBtn;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel subtitle;
    private javax.swing.JLabel title;
    private javax.swing.JPanel titlePanel;
    private javax.swing.JLabel totalBooksVal;
    private javax.swing.JLabel unpaidFinesVal;
    // End of variables declaration//GEN-END:variables
}
