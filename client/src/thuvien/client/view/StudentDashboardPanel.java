package thuvien.client.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientDashboardController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.BorrowRecordDTO;
import thuvien.common.dto.ReservationDTO;
import thuvien.common.dto.StudentDashboardDTO;
import thuvien.common.exception.AuthenticationException;

/**
 * Personalized Dashboard Panel for authenticated Student users.
 */
public class StudentDashboardPanel extends JPanel {
    private final NetworkClient networkClient;
    private final ClientDashboardController dashboardController;

    private JLabel welcomeLabel;
    private JLabel activeBorrowsVal;
    private JLabel overdueBorrowsVal;
    private JLabel unpaidFinesVal;
    private JLabel pendingReservationsVal;

    private DefaultTableModel borrowTableModel;
    private DefaultTableModel reservationTableModel;
    private JButton refreshButton;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    public StudentDashboardPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.dashboardController = new ClientDashboardController(networkClient);
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        welcomeLabel = new JLabel("Đang tải thông tin sinh viên...");
        welcomeLabel.setFont(welcomeLabel.getFont().deriveFont(Font.BOLD, 18.0f));

        refreshButton = new JButton("Làm mới dữ liệu");
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.PLAIN, 12.0f));
        refreshButton.addActionListener(e -> loadData());

        headerPanel.add(welcomeLabel, BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center Content
        JPanel centerPanel = new JPanel(new BorderLayout(10, 15));

        // Metrics Grid (4 cards)
        JPanel cardsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        cardsPanel.setPreferredSize(new Dimension(0, 100));

        activeBorrowsVal = new JLabel("0", JLabel.CENTER);
        overdueBorrowsVal = new JLabel("0", JLabel.CENTER);
        unpaidFinesVal = new JLabel("0 đ", JLabel.CENTER);
        pendingReservationsVal = new JLabel("0", JLabel.CENTER);

        cardsPanel.add(createMetricCard("SÁCH ĐANG MƯỢN", activeBorrowsVal, new Color(41, 128, 185)));
        cardsPanel.add(createMetricCard("SÁCH QUÁ HẠN", overdueBorrowsVal, new Color(192, 57, 43)));
        cardsPanel.add(createMetricCard("TIỀN PHẠT CHƯA ĐÓNG", unpaidFinesVal, new Color(211, 84, 0)));
        cardsPanel.add(createMetricCard("YÊU CẦU ĐẶT TRƯỚC", pendingReservationsVal, new Color(142, 68, 173)));

        centerPanel.add(cardsPanel, BorderLayout.NORTH);

        // Split Pane with 2 Tables: Recent Borrows & Recent Reservations
        JPanel borrowSection = new JPanel(new BorderLayout(0, 6));
        JLabel borrowLbl = new JLabel("Danh Sách Mượn Gần Đây");
        borrowLbl.setFont(borrowLbl.getFont().deriveFont(Font.BOLD, 14.0f));
        borrowSection.add(borrowLbl, BorderLayout.NORTH);

        borrowTableModel = new DefaultTableModel(new String[]{"Mã mượn", "Tên sách", "Ngày mượn", "Hạn trả", "Ngày trả", "Trạng thái"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable borrowTable = new JTable(borrowTableModel);
        borrowTable.setRowHeight(24);
        borrowSection.add(new JScrollPane(borrowTable), BorderLayout.CENTER);

        JPanel resSection = new JPanel(new BorderLayout(0, 6));
        JLabel resLbl = new JLabel("Yêu Cầu Đặt Trước Sách");
        resLbl.setFont(resLbl.getFont().deriveFont(Font.BOLD, 14.0f));
        resSection.add(resLbl, BorderLayout.NORTH);

        reservationTableModel = new DefaultTableModel(new String[]{"Mã ĐT", "Tên sách", "Ngày đặt", "Hạn nhận", "Trạng thái"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable reservationTable = new JTable(reservationTableModel);
        reservationTable.setRowHeight(24);
        resSection.add(new JScrollPane(reservationTable), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, borrowSection, resSection);
        splitPane.setResizeWeight(0.55);
        splitPane.setDividerSize(6);

        centerPanel.add(splitPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(12, 10, 10, 10)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(title, JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11.0f));
        titleLabel.setForeground(new Color(110, 110, 110));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22.0f));
        valueLabel.setForeground(accentColor);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public void loadData() {
        refreshButton.setEnabled(false);
        AsyncWorker.run(
                dashboardController::getStudentDashboard,
                (StudentDashboardDTO data) -> {
                    refreshButton.setEnabled(true);
                    if (data == null) return;

                    welcomeLabel.setText("Xin chào: " + data.getFullName() +
                            "  |  Mã SV: " + data.getStudentCode() +
                            "  |  Lớp: " + data.getClassName());

                    activeBorrowsVal.setText(String.valueOf(data.getActiveBorrowCount()));
                    overdueBorrowsVal.setText(String.valueOf(data.getOverdueBorrowCount()));

                    String fineStr = (data.getUnpaidFineAmount() != null)
                            ? CURRENCY_FORMAT.format(data.getUnpaidFineAmount()) + " đ"
                            : "0 đ";
                    unpaidFinesVal.setText(fineStr);
                    pendingReservationsVal.setText(String.valueOf(data.getPendingReservationCount()));

                    // Populate borrows table
                    borrowTableModel.setRowCount(0);
                    if (data.getRecentBorrows() != null) {
                        for (BorrowRecordDTO b : data.getRecentBorrows()) {
                            String borrowDate = b.getBorrowDate() != null ? DATE_FORMAT.format(b.getBorrowDate()) : "-";
                            String dueDate = b.getDueDate() != null ? DATE_FORMAT.format(b.getDueDate()) : "-";
                            String returnDate = b.getReturnDate() != null ? DATE_FORMAT.format(b.getReturnDate()) : "-";
                            String statusStr = formatBorrowStatus(b.getStatus() != null ? b.getStatus().name() : "");
                            borrowTableModel.addRow(new Object[]{
                                    b.getId(),
                                    b.getBookTitle() != null ? b.getBookTitle() : ("Sách #" + b.getBookId()),
                                    borrowDate,
                                    dueDate,
                                    returnDate,
                                    statusStr
                            });
                        }
                    }

                    // Populate reservations table
                    reservationTableModel.setRowCount(0);
                    if (data.getRecentReservations() != null) {
                        for (ReservationDTO r : data.getRecentReservations()) {
                            String resDate = r.getReservationDate() != null ? DATE_FORMAT.format(r.getReservationDate()) : "-";
                            String expDate = r.getExpiryDate() != null ? DATE_FORMAT.format(r.getExpiryDate()) : "-";
                            String statusStr = formatReservationStatus(r.getStatus() != null ? r.getStatus().name() : "");
                            reservationTableModel.addRow(new Object[]{
                                    r.getId(),
                                    r.getBookTitle() != null ? r.getBookTitle() : ("Sách #" + r.getBookId()),
                                    resDate,
                                    expDate,
                                    statusStr
                            });
                        }
                    }
                },
                (Exception ex) -> {
                    refreshButton.setEnabled(true);
                    welcomeLabel.setText("Không thể tải thông tin trang tổng quan.");
                    if (ex instanceof AuthenticationException) {
                        String msg = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                        if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                            msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
                        }
                        JOptionPane.showMessageDialog(StudentDashboardPanel.this, msg, "Hết Hạn Phiên", JOptionPane.ERROR_MESSAGE);
                        java.awt.Window win = SwingUtilities.getWindowAncestor(StudentDashboardPanel.this);
                        if (win != null) win.dispose();
                        SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
                    }
                }
        );
    }

    private String formatBorrowStatus(String status) {
        switch (status) {
            case "ACTIVE": return "Đang mượn";
            case "RETURNED": return "Đã trả";
            case "OVERDUE": return "Quá hạn";
            case "LOST": return "Mất sách";
            default: return status;
        }
    }

    private String formatReservationStatus(String status) {
        switch (status) {
            case "PENDING": return "Chờ nhận";
            case "FULFILLED": return "Đã nhận";
            case "CANCELLED": return "Đã hủy";
            case "EXPIRED": return "Hết hạn";
            default: return status;
        }
    }
}
