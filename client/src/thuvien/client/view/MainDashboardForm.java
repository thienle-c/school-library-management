package thuvien.client.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.panels.AuditLogManagementPanel;
import thuvien.client.view.panels.BookManagementPanel;
import thuvien.client.view.panels.BorrowManagementPanel;
import thuvien.client.view.panels.DashboardOverviewPanel;
import thuvien.client.view.panels.FineManagementPanel;
import thuvien.client.view.panels.ReservationManagementPanel;
import thuvien.client.view.panels.StudentManagementPanel;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;

/**
 * Main application dashboard shell for the Remote School Library desktop client.
 * Establishes the role-aware navigation foundation, session management, and content card container.
 */
public class MainDashboardForm extends JFrame {

    private final NetworkClient networkClient;
    private final UserSessionDTO session;

    private JPanel contentCards;
    private CardLayout cardLayout;

    public MainDashboardForm(NetworkClient networkClient, UserSessionDTO session) {
        this.networkClient = networkClient;
        this.session = session;
        initUI();
    }

    private void initUI() {
        setTitle(String.format("Quản Lý Thư Viện - %s (%s)", session.getFullName(), getRoleDisplayName(session.getRole())));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1020, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout());

        // =====================================================================
        // 1. Top Navigation Bar
        // =====================================================================
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(245, 247, 250));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 228)),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel appTitle = new JLabel("Hệ Thống Quản Lý Thư Viện Trường Học");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 17.0f));
        JLabel subTitle = new JLabel("Ứng Dụng Khách Kết Nối TCP Từ Xa — Kiến Trúc Phân Tán 3 Lớp");
        subTitle.setFont(subTitle.getFont().deriveFont(Font.PLAIN, 11.0f));
        subTitle.setForeground(new Color(110, 115, 125));
        titlePanel.add(appTitle);
        titlePanel.add(subTitle);
        topBar.add(titlePanel, BorderLayout.WEST);

        // User profile and Logout button
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        userPanel.setOpaque(false);

        String roleBadge = String.format("<html><b>%s</b> &nbsp;|&nbsp; Vai trò: <span style='color:#0055aa;'>%s</span></html>",
                session.getFullName(), getRoleDisplayName(session.getRole()));
        JLabel userLabel = new JLabel(roleBadge);
        userLabel.setFont(userLabel.getFont().deriveFont(13.0f));
        userPanel.add(userLabel);

        JButton changePwdBtn = new JButton("Đổi mật khẩu");
        changePwdBtn.setPreferredSize(new Dimension(115, 28));
        changePwdBtn.setFocusPainted(false);
        changePwdBtn.addActionListener(e -> {
            ChangePasswordDialog dialog = new ChangePasswordDialog(this, networkClient);
            dialog.setVisible(true);
        });
        userPanel.add(changePwdBtn);

        JButton logoutBtn = new JButton("Đăng xuất");
        logoutBtn.setPreferredSize(new Dimension(95, 28));
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> logout());
        userPanel.add(logoutBtn);

        topBar.add(userPanel, BorderLayout.EAST);
        rootPanel.add(topBar, BorderLayout.NORTH);

        // =====================================================================
        // 2. Sidebar Navigation (Role-Aware)
        // =====================================================================
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new GridLayout(9, 1, 0, 6));
        sidebar.setPreferredSize(new Dimension(225, 0));
        sidebar.setBackground(new Color(238, 241, 246));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(215, 220, 228)),
                BorderFactory.createEmptyBorder(15, 10, 15, 10)
        ));

        UserRole role = session.getRole();

        // Shared Home Navigation
        String homeLabel = (role == UserRole.STUDENT) ? "Trang chủ sinh viên" : "Trang chủ / Phiên làm việc";
        JButton homeBtn = createNavButton(homeLabel);
        homeBtn.addActionListener(e -> cardLayout.show(contentCards, "HOME"));
        sidebar.add(homeBtn);

        // Staff-specific navigation
        if (role == UserRole.ADMIN || role == UserRole.LIBRARIAN) {
            JButton overviewBtn = createNavButton("Tổng quan hệ thống");
            overviewBtn.addActionListener(e -> cardLayout.show(contentCards, "OVERVIEW"));
            sidebar.add(overviewBtn);

            JButton booksBtn = createNavButton("Quản lý sách");
            booksBtn.addActionListener(e -> cardLayout.show(contentCards, "BOOKS"));
            sidebar.add(booksBtn);

            JButton studentsBtn = createNavButton("Hồ sơ sinh viên");
            studentsBtn.addActionListener(e -> cardLayout.show(contentCards, "STUDENTS"));
            sidebar.add(studentsBtn);

            JButton borrowBtn = createNavButton("Mượn & Trả sách");
            borrowBtn.addActionListener(e -> cardLayout.show(contentCards, "CIRCULATION"));
            sidebar.add(borrowBtn);

            JButton finesBtn = createNavButton("Quản lý tiền phạt");
            finesBtn.addActionListener(e -> cardLayout.show(contentCards, "FINES"));
            sidebar.add(finesBtn);

            JButton reservationsBtn = createNavButton("Đặt trước sách");
            reservationsBtn.addActionListener(e -> cardLayout.show(contentCards, "RESERVATIONS"));
            sidebar.add(reservationsBtn);

            if (role == UserRole.ADMIN) {
                JButton auditBtn = createNavButton("Nhật ký & Báo cáo");
                auditBtn.addActionListener(e -> cardLayout.show(contentCards, "AUDIT_LOGS"));
                sidebar.add(auditBtn);
            }
        } else if (role == UserRole.STUDENT) {
            JButton searchBooksBtn = createNavButton("Tra cứu danh mục sách");
            searchBooksBtn.addActionListener(e -> cardLayout.show(contentCards, "BOOKS"));
            sidebar.add(searchBooksBtn);

            JButton myBorrowsBtn = createNavButton("Sách đang mượn");
            myBorrowsBtn.addActionListener(e -> cardLayout.show(contentCards, "MY_BORROWS"));
            sidebar.add(myBorrowsBtn);

            JButton myReservationsBtn = createNavButton("Sách đã đặt trước");
            myReservationsBtn.addActionListener(e -> cardLayout.show(contentCards, "MY_RESERVATIONS"));
            sidebar.add(myReservationsBtn);

            JButton myFinesBtn = createNavButton("Khoản phạt của tôi");
            myFinesBtn.addActionListener(e -> cardLayout.show(contentCards, "MY_FINES"));
            sidebar.add(myFinesBtn);

            JButton profileBtn = createNavButton("Hồ sơ cá nhân");
            profileBtn.addActionListener(e -> cardLayout.show(contentCards, "PROFILE"));
            sidebar.add(profileBtn);
        }

        rootPanel.add(sidebar, BorderLayout.WEST);

        // =====================================================================
        // 3. Content Area (CardLayout Foundation)
        // =====================================================================
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);
        contentCards.setBackground(Color.WHITE);

        // Default Card: Home & Session Status
        if (role == UserRole.STUDENT) {
            contentCards.add(new StudentDashboardPanel(networkClient), "HOME");
        } else {
            contentCards.add(createHomeSessionPanel(), "HOME");
        }

        // Live Book Management Panel (Shared)
        contentCards.add(new BookManagementPanel(networkClient), "BOOKS");

        if (role == UserRole.ADMIN || role == UserRole.LIBRARIAN) {
            contentCards.add(new DashboardOverviewPanel(networkClient), "OVERVIEW");
            contentCards.add(new StudentManagementPanel(networkClient), "STUDENTS");
            contentCards.add(new BorrowManagementPanel(networkClient), "CIRCULATION");
            contentCards.add(new FineManagementPanel(networkClient), "FINES");
            contentCards.add(new ReservationManagementPanel(networkClient), "RESERVATIONS");
            if (role == UserRole.ADMIN) {
                contentCards.add(new AuditLogManagementPanel(networkClient), "AUDIT_LOGS");
            }
        } else if (role == UserRole.STUDENT) {
            contentCards.add(new BorrowManagementPanel(networkClient), "MY_BORROWS");
            contentCards.add(new ReservationManagementPanel(networkClient), "MY_RESERVATIONS");
            contentCards.add(new FineManagementPanel(networkClient), "MY_FINES");
            contentCards.add(new StudentManagementPanel(networkClient), "PROFILE");
        }

        rootPanel.add(contentCards, BorderLayout.CENTER);
        setContentPane(rootPanel);

        // Show HOME by default
        cardLayout.show(contentCards, "HOME");
    }

    private String getRoleDisplayName(UserRole role) {
        if (role == null) return "Chưa xác định";
        switch (role) {
            case ADMIN: return "Quản trị viên (ADMIN)";
            case LIBRARIAN: return "Thủ thư (LIBRARIAN)";
            case STUDENT: return "Sinh viên (STUDENT)";
            default: return role.name();
        }
    }

    private JButton createNavButton(String text) {
        JButton btn = new JButton(text);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setFont(btn.getFont().deriveFont(12.5f));
        return btn;
    }

    private JPanel createHomeSessionPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        // Welcome Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        headerPanel.setOpaque(false);
        JLabel welcomeTitle = new JLabel("Xin chào, " + session.getFullName() + "!");
        welcomeTitle.setFont(welcomeTitle.getFont().deriveFont(Font.BOLD, 22.0f));
        JLabel welcomeSub = new JLabel("Ứng Dụng Quản Lý Thư Viện Trường Học (Giao thức mạng TCP Socket)");
        welcomeSub.setFont(welcomeSub.getFont().deriveFont(Font.PLAIN, 13.0f));
        welcomeSub.setForeground(Color.GRAY);
        headerPanel.add(welcomeTitle);
        headerPanel.add(welcomeSub);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Session Information Card
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBackground(new Color(248, 250, 252));
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 238), 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addInfoRow(cardPanel, gbc, row++, "Tài khoản đăng nhập:", session.getUsername() + " (Mã người dùng: " + session.getUserId() + ")");
        addInfoRow(cardPanel, gbc, row++, "Vai trò hệ thống:", getRoleDisplayName(session.getRole()));
        addInfoRow(cardPanel, gbc, row++, "Họ và tên:", session.getFullName());

        String maskedToken = (session.getToken() != null && session.getToken().length() > 8)
                ? session.getToken().substring(0, 8) + "..."
                : (session.getToken() != null ? session.getToken() : "N/A");
        addInfoRow(cardPanel, gbc, row++, "Mã phiên làm việc (Token):", maskedToken);
        addInfoRow(cardPanel, gbc, row++, "Giao thức truyền tải mạng:", "TCP Socket nhị phân nguyên bản (Port 8888)");
        addInfoRow(cardPanel, gbc, row++, "Trạng thái ứng dụng:", "Đang kết nối & Sẵn sàng hoạt động");

        panel.add(cardPanel, BorderLayout.CENTER);

        // Bottom status notice
        JLabel notice = new JLabel("Vui lòng chọn chức năng ở thanh menu bên trái để quản lý sách, sinh viên, mượn trả, đặt trước và tiền phạt.");
        notice.setFont(notice.getFont().deriveFont(Font.ITALIC, 12.0f));
        notice.setForeground(new Color(120, 120, 120));
        panel.add(notice, BorderLayout.SOUTH);

        return panel;
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13.0f));
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(Font.PLAIN, 13.0f));
        panel.add(val, gbc);
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?",
                "Xác Nhận Đăng Xuất",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            // Asynchronously notify server of logout, clear local session, and disconnect socket
            AsyncWorker.run(
                    () -> {
                        new ClientAuthController(networkClient).logout();
                        return null;
                    },
                    (res) -> {
                        dispose();
                        SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
                    },
                    (ex) -> {
                        // Even if server communication fails, session is cleared locally
                        ClientSession.getInstance().clear();
                        networkClient.disconnect();
                        dispose();
                        SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
                    }
            );
        }
    }
}
