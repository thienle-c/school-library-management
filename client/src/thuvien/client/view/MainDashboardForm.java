package thuvien.client.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.network.RMIClient;
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
    private CardLayout cardLayout;

    public MainDashboardForm() {
        this(new RMIClient(), createDesignSession());
    }

    public MainDashboardForm(NetworkClient networkClient, UserSessionDTO session) {
        this.networkClient = networkClient;
        this.session = session != null ? session : createDesignSession();
        initComponents();
        initCustom();
    }

    private static UserSessionDTO createDesignSession() {
        UserSessionDTO dummy = new UserSessionDTO();
        dummy.setUserId(1L);
        dummy.setUsername("admin");
        dummy.setFullName("Quản Trị Viên");
        dummy.setRole(UserRole.ADMIN);
        dummy.setToken("DEMO_TOKEN");
        return dummy;
    }

    private void initCustom() {
        setTitle(String.format("Quản Lý Thư Viện - %s (%s)", session.getFullName(), getRoleDisplayName(session.getRole())));
        setSize(1020, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(215, 220, 228)),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(215, 220, 228)),
                BorderFactory.createEmptyBorder(15, 10, 15, 10)
        ));

        String roleBadge = String.format("<html><b>%s</b> &nbsp;|&nbsp; Vai trò: <span style='color:#0055aa;'>%s</span></html>",
                session.getFullName(), getRoleDisplayName(session.getRole()));
        userLabel.setText(roleBadge);

        UserRole role = session.getRole();
        if (role == UserRole.STUDENT) {
            homeBtn.setText("Trang chủ sinh viên");
            overviewBtn.setVisible(false);
            auditBtn.setVisible(false);
            booksBtn.setText("Tra cứu sách");
            studentsBtn.setText("Hồ sơ cá nhân");
            borrowBtn.setText("Sách đang mượn");
            finesBtn.setText("Khoản phạt của tôi");
            reservationsBtn.setText("Sách đã đặt trước");
        } else {
            homeBtn.setText("Trang chủ / Phiên");
            overviewBtn.setVisible(true);
            booksBtn.setText("Quản lý sách");
            studentsBtn.setText("Hồ sơ sinh viên");
            borrowBtn.setText("Mượn & Trả sách");
            finesBtn.setText("Quản lý tiền phạt");
            reservationsBtn.setText("Đặt trước sách");
            auditBtn.setVisible(role == UserRole.ADMIN);
        }

        cardLayout = (CardLayout) contentCards.getLayout();

        if (role == UserRole.STUDENT) {
            contentCards.add(new StudentDashboardPanel(networkClient), "HOME");
            contentCards.add(new BookManagementPanel(networkClient), "BOOKS");
            contentCards.add(new BorrowManagementPanel(networkClient), "CIRCULATION");
            contentCards.add(new ReservationManagementPanel(networkClient), "RESERVATIONS");
            contentCards.add(new FineManagementPanel(networkClient), "FINES");
            contentCards.add(new StudentManagementPanel(networkClient), "STUDENTS");
        } else {
            contentCards.add(createHomeSessionPanel(), "HOME");
            contentCards.add(new DashboardOverviewPanel(networkClient), "OVERVIEW");
            contentCards.add(new BookManagementPanel(networkClient), "BOOKS");
            contentCards.add(new StudentManagementPanel(networkClient), "STUDENTS");
            contentCards.add(new BorrowManagementPanel(networkClient), "CIRCULATION");
            contentCards.add(new FineManagementPanel(networkClient), "FINES");
            contentCards.add(new ReservationManagementPanel(networkClient), "RESERVATIONS");
            if (role == UserRole.ADMIN) {
                contentCards.add(new AuditLogManagementPanel(networkClient), "AUDIT_LOGS");
            }
        }

        cardLayout.show(contentCards, "HOME");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        topBar = new javax.swing.JPanel();
        titlePanel = new javax.swing.JPanel();
        appTitle = new javax.swing.JLabel();
        subTitle = new javax.swing.JLabel();
        userPanel = new javax.swing.JPanel();
        userLabel = new javax.swing.JLabel();
        changePwdBtn = new javax.swing.JButton();
        logoutBtn = new javax.swing.JButton();
        sidebar = new javax.swing.JPanel();
        homeBtn = new javax.swing.JButton();
        overviewBtn = new javax.swing.JButton();
        booksBtn = new javax.swing.JButton();
        studentsBtn = new javax.swing.JButton();
        borrowBtn = new javax.swing.JButton();
        finesBtn = new javax.swing.JButton();
        reservationsBtn = new javax.swing.JButton();
        auditBtn = new javax.swing.JButton();
        contentCards = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Hệ Thống Quản Lý Thư Viện");
        setMinimumSize(new java.awt.Dimension(880, 560));

        topBar.setBackground(new java.awt.Color(245, 247, 250));
        topBar.setLayout(new java.awt.BorderLayout());

        titlePanel.setOpaque(false);
        titlePanel.setLayout(new java.awt.GridLayout(2, 1, 0, 2));

        appTitle.setFont(new java.awt.Font("Segoe UI", 1, 17)); // NOI18N
        appTitle.setText("Hệ Thống Quản Lý Thư Viện Trường Học");
        titlePanel.add(appTitle);

        subTitle.setFont(new java.awt.Font("Segoe UI", 0, 11)); // NOI18N
        subTitle.setForeground(new java.awt.Color(110, 115, 125));
        subTitle.setText("Ứng Dụng Khách Kết Nối Java RMI — Kiến Trúc Phân Tán 3 Lớp");
        titlePanel.add(subTitle);

        topBar.add(titlePanel, java.awt.BorderLayout.WEST);

        userPanel.setOpaque(false);
        userPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 15, 0));

        userLabel.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        userLabel.setText("User Role");
        userPanel.add(userLabel);

        changePwdBtn.setText("Đổi mật khẩu");
        changePwdBtn.setFocusPainted(false);
        changePwdBtn.setPreferredSize(new java.awt.Dimension(115, 28));
        changePwdBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changePwdBtnActionPerformed(evt);
            }
        });
        userPanel.add(changePwdBtn);

        logoutBtn.setText("Đăng xuất");
        logoutBtn.setFocusPainted(false);
        logoutBtn.setPreferredSize(new java.awt.Dimension(95, 28));
        logoutBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logoutBtnActionPerformed(evt);
            }
        });
        userPanel.add(logoutBtn);

        topBar.add(userPanel, java.awt.BorderLayout.EAST);

        getContentPane().add(topBar, java.awt.BorderLayout.NORTH);

        sidebar.setBackground(new java.awt.Color(238, 241, 246));
        sidebar.setPreferredSize(new java.awt.Dimension(225, 0));
        sidebar.setLayout(new java.awt.GridLayout(9, 1, 0, 6));

        homeBtn.setBackground(new java.awt.Color(255, 255, 255));
        homeBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        homeBtn.setText("Trang chủ");
        homeBtn.setFocusPainted(false);
        homeBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        homeBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                homeBtnActionPerformed(evt);
            }
        });
        sidebar.add(homeBtn);

        overviewBtn.setBackground(new java.awt.Color(255, 255, 255));
        overviewBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        overviewBtn.setText("Tổng quan hệ thống");
        overviewBtn.setFocusPainted(false);
        overviewBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        overviewBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overviewBtnActionPerformed(evt);
            }
        });
        sidebar.add(overviewBtn);

        booksBtn.setBackground(new java.awt.Color(255, 255, 255));
        booksBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        booksBtn.setText("Quản lý sách");
        booksBtn.setFocusPainted(false);
        booksBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        booksBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                booksBtnActionPerformed(evt);
            }
        });
        sidebar.add(booksBtn);

        studentsBtn.setBackground(new java.awt.Color(255, 255, 255));
        studentsBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        studentsBtn.setText("Hồ sơ sinh viên");
        studentsBtn.setFocusPainted(false);
        studentsBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        studentsBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                studentsBtnActionPerformed(evt);
            }
        });
        sidebar.add(studentsBtn);

        borrowBtn.setBackground(new java.awt.Color(255, 255, 255));
        borrowBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        borrowBtn.setText("Mượn & Trả sách");
        borrowBtn.setFocusPainted(false);
        borrowBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        borrowBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                borrowBtnActionPerformed(evt);
            }
        });
        sidebar.add(borrowBtn);

        finesBtn.setBackground(new java.awt.Color(255, 255, 255));
        finesBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        finesBtn.setText("Quản lý tiền phạt");
        finesBtn.setFocusPainted(false);
        finesBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        finesBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                finesBtnActionPerformed(evt);
            }
        });
        sidebar.add(finesBtn);

        reservationsBtn.setBackground(new java.awt.Color(255, 255, 255));
        reservationsBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        reservationsBtn.setText("Đặt trước sách");
        reservationsBtn.setFocusPainted(false);
        reservationsBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        reservationsBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reservationsBtnActionPerformed(evt);
            }
        });
        sidebar.add(reservationsBtn);

        auditBtn.setBackground(new java.awt.Color(255, 255, 255));
        auditBtn.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        auditBtn.setText("Nhật ký & Báo cáo");
        auditBtn.setFocusPainted(false);
        auditBtn.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        auditBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auditBtnActionPerformed(evt);
            }
        });
        sidebar.add(auditBtn);

        getContentPane().add(sidebar, java.awt.BorderLayout.WEST);

        contentCards.setBackground(new java.awt.Color(255, 255, 255));
        contentCards.setLayout(new java.awt.CardLayout());
        getContentPane().add(contentCards, java.awt.BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void changePwdBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changePwdBtnActionPerformed
        ChangePasswordDialog dialog = new ChangePasswordDialog(this, networkClient);
        dialog.setVisible(true);
    }//GEN-LAST:event_changePwdBtnActionPerformed

    private void logoutBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logoutBtnActionPerformed
        logout();
    }//GEN-LAST:event_logoutBtnActionPerformed

    private void homeBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_homeBtnActionPerformed
        cardLayout.show(contentCards, "HOME");
    }//GEN-LAST:event_homeBtnActionPerformed

    private void overviewBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overviewBtnActionPerformed
        cardLayout.show(contentCards, "OVERVIEW");
    }//GEN-LAST:event_overviewBtnActionPerformed

    private void booksBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_booksBtnActionPerformed
        cardLayout.show(contentCards, "BOOKS");
    }//GEN-LAST:event_booksBtnActionPerformed

    private void studentsBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_studentsBtnActionPerformed
        cardLayout.show(contentCards, "STUDENTS");
    }//GEN-LAST:event_studentsBtnActionPerformed

    private void borrowBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_borrowBtnActionPerformed
        cardLayout.show(contentCards, "CIRCULATION");
    }//GEN-LAST:event_borrowBtnActionPerformed

    private void finesBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_finesBtnActionPerformed
        cardLayout.show(contentCards, "FINES");
    }//GEN-LAST:event_finesBtnActionPerformed

    private void reservationsBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reservationsBtnActionPerformed
        cardLayout.show(contentCards, "RESERVATIONS");
    }//GEN-LAST:event_reservationsBtnActionPerformed

    private void auditBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auditBtnActionPerformed
        cardLayout.show(contentCards, "AUDIT_LOGS");
    }//GEN-LAST:event_auditBtnActionPerformed

    private String getRoleDisplayName(UserRole role) {
        if (role == null) return "Chưa xác định";
        switch (role) {
            case ADMIN: return "Quản trị viên (ADMIN)";
            case LIBRARIAN: return "Thủ thư (LIBRARIAN)";
            case STUDENT: return "Sinh viên (STUDENT)";
            default: return role.name();
        }
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
        JLabel welcomeSub = new JLabel("Ứng Dụng Quản Lý Thư Viện Trường Học (Giao thức mạng TCP Socket / Java RMI)");
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
        addInfoRow(cardPanel, gbc, row++, "Giao thức truyền tải mạng:", "Java RMI over TCP (Registry 1099, Service 1100)");
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
                        ClientSession.getInstance().clear();
                        networkClient.disconnect();
                        dispose();
                        SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
                    }
            );
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel appTitle;
    private javax.swing.JButton auditBtn;
    private javax.swing.JButton booksBtn;
    private javax.swing.JButton borrowBtn;
    private javax.swing.JButton changePwdBtn;
    private javax.swing.JPanel contentCards;
    private javax.swing.JButton finesBtn;
    private javax.swing.JButton homeBtn;
    private javax.swing.JButton logoutBtn;
    private javax.swing.JButton overviewBtn;
    private javax.swing.JButton reservationsBtn;
    private javax.swing.JPanel sidebar;
    private javax.swing.JButton studentsBtn;
    private javax.swing.JLabel subTitle;
    private javax.swing.JPanel titlePanel;
    private javax.swing.JPanel topBar;
    private javax.swing.JLabel userLabel;
    private javax.swing.JPanel userPanel;
    // End of variables declaration//GEN-END:variables
}
