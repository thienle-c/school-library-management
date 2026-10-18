package thuvien.client.view;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import thuvien.client.view.common.CyberCardPanel;
import thuvien.client.view.common.CyberHeaderPanel;
import thuvien.client.view.common.ModernUIHelper;
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
        if (this.networkClient != null && this.session != null) {
            ClientSession cs = new ClientSession();
            cs.setSession(this.session);
            this.networkClient.setSession(cs);
        }
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

    private float contentAlpha = 1.0f;
    private javax.swing.Timer fadeTimer;

    private void initCustom() {
        setTitle(String.format("Quản Lý Thư Viện - %s (%s)", session.getFullName(), getRoleDisplayName(session.getRole())));
        setSize(1140, 740);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
        getContentPane().setBackground(ModernUIHelper.BG_APP);

        // Replace topBar with CyberHeaderPanel (animated neon cyan laser scanner beam)
        CyberHeaderPanel cyberHeader = new CyberHeaderPanel();
        cyberHeader.setLayout(new BorderLayout());
        cyberHeader.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        cyberHeader.add(titlePanel, BorderLayout.WEST);
        cyberHeader.add(userPanel, BorderLayout.EAST);
        getContentPane().remove(topBar);
        getContentPane().add(cyberHeader, BorderLayout.NORTH);

        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        appTitle.setForeground(ModernUIHelper.PRIMARY_BLUE);
        appTitle.setIcon(ModernUIHelper.getVectorIcon("BOOKS", 24, ModernUIHelper.PRIMARY_BLUE));
        appTitle.setIconTextGap(10);
        appTitle.setText("THƯ VIỆN HỌC THUẬT");

        subTitle.setFont(ModernUIHelper.FONT_SUBTITLE);
        subTitle.setForeground(ModernUIHelper.TEXT_MUTED);
        subTitle.setText("Hệ thống quản lý & tra cứu ấn phẩm số • Kiến trúc phân tán");

        sidebar.setBackground(ModernUIHelper.BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x1E, 0x29, 0x3B)),
                BorderFactory.createEmptyBorder(15, 12, 15, 12)
        ));

        String badgeBg = session.getRole() == UserRole.ADMIN ? "#EFF6FF" : (session.getRole() == UserRole.LIBRARIAN ? "#F0FDF4" : "#FEF3C7");
        String badgeFg = session.getRole() == UserRole.ADMIN ? "#1D4ED8" : (session.getRole() == UserRole.LIBRARIAN ? "#15803D" : "#B45309");
        String badgeBorder = session.getRole() == UserRole.ADMIN ? "#BFDBFE" : (session.getRole() == UserRole.LIBRARIAN ? "#BBF7D0" : "#FDE68A");
        String roleBadge = String.format("<html><span style='font-size:13px; font-weight:600; color:#0F172A;'>%s</span> &nbsp;<span style='background:%s; border:1px solid %s; color:%s; padding:3px 10px; border-radius:10px; font-size:11px;'><b>%s</b></span></html>",
                session.getFullName(), badgeBg, badgeBorder, badgeFg, getRoleDisplayName(session.getRole()));
        userLabel.setText(roleBadge);
        userLabel.setFont(ModernUIHelper.FONT_LABEL);

        ModernUIHelper.styleChangePasswordButton(changePwdBtn, "Đổi mật khẩu");
        changePwdBtn.setIcon(ModernUIHelper.getVectorIcon("KEY", 14, ModernUIHelper.TEXT_PRIMARY));
        changePwdBtn.setIconTextGap(6);
        changePwdBtn.setPreferredSize(new Dimension(135, 32));

        ModernUIHelper.styleDangerButton(logoutBtn, "Đăng xuất");
        logoutBtn.setIcon(ModernUIHelper.getVectorIcon("LOGOUT", 14, new Color(0xFC, 0xA5, 0xA5)));
        logoutBtn.setIconTextGap(6);
        logoutBtn.setPreferredSize(new Dimension(110, 32));

        UserRole role = session.getRole();
        if (role == UserRole.STUDENT) {
            ModernUIHelper.styleSidebarButton(homeBtn, "Trang chủ sinh viên", ModernUIHelper.getVectorIcon("HOME", 16));
            ModernUIHelper.styleSidebarButton(booksBtn, "Tra cứu sách", ModernUIHelper.getVectorIcon("BOOKS", 16));
            ModernUIHelper.styleSidebarButton(studentsBtn, "Hồ sơ cá nhân", ModernUIHelper.getVectorIcon("STUDENTS", 16));
            ModernUIHelper.styleSidebarButton(borrowBtn, "Sách đang mượn", ModernUIHelper.getVectorIcon("BORROW", 16));
            ModernUIHelper.styleSidebarButton(finesBtn, "Khoản phạt của tôi", ModernUIHelper.getVectorIcon("FINES", 16));
            ModernUIHelper.styleSidebarButton(reservationsBtn, "Sách đã đặt trước", ModernUIHelper.getVectorIcon("RESERVATIONS", 16));
            overviewBtn.setVisible(false);
            auditBtn.setVisible(false);
        } else {
            ModernUIHelper.styleSidebarButton(homeBtn, "Thông tin phiên RMI", ModernUIHelper.getVectorIcon("SESSION", 16));
            ModernUIHelper.styleSidebarButton(overviewBtn, "Tổng quan & Chỉ số", ModernUIHelper.getVectorIcon("OVERVIEW", 16));
            ModernUIHelper.styleSidebarButton(booksBtn, "Quản lý đầu sách", ModernUIHelper.getVectorIcon("BOOKS", 16));
            ModernUIHelper.styleSidebarButton(studentsBtn, "Hồ sơ sinh viên", ModernUIHelper.getVectorIcon("STUDENTS", 16));
            ModernUIHelper.styleSidebarButton(borrowBtn, "Mượn & trả sách", ModernUIHelper.getVectorIcon("BORROW", 16));
            ModernUIHelper.styleSidebarButton(finesBtn, "Quản lý tiền phạt", ModernUIHelper.getVectorIcon("FINES", 16));
            ModernUIHelper.styleSidebarButton(reservationsBtn, "Đặt trước sách", ModernUIHelper.getVectorIcon("RESERVATIONS", 16));
            ModernUIHelper.styleSidebarButton(auditBtn, "Nhật ký kiểm toán", ModernUIHelper.getVectorIcon("AUDIT", 16));
            overviewBtn.setVisible(true);
            auditBtn.setVisible(role == UserRole.ADMIN);
        }

        // Replace contentCards with alpha-fade capable container
        getContentPane().remove(contentCards);
        contentCards = new JPanel() {
            @Override
            public void paint(Graphics g) {
                if (contentAlpha < 0.98f) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, contentAlpha))));
                    super.paint(g2);
                    g2.dispose();
                } else {
                    super.paint(g);
                }
            }
        };
        contentCards.setBackground(ModernUIHelper.BG_APP);
        cardLayout = new CardLayout();
        contentCards.setLayout(cardLayout);
        getContentPane().add(contentCards, BorderLayout.CENTER);

        if (role == UserRole.STUDENT) {
            contentCards.add(new StudentDashboardPanel(networkClient), "HOME");
            contentCards.add(new BookManagementPanel(networkClient), "BOOKS");
            contentCards.add(new BorrowManagementPanel(networkClient), "CIRCULATION");
            contentCards.add(new ReservationManagementPanel(networkClient), "RESERVATIONS");
            contentCards.add(new FineManagementPanel(networkClient), "FINES");
            contentCards.add(new StudentManagementPanel(networkClient), "STUDENTS");
            showCardWithFade("HOME", homeBtn);
        } else {
            contentCards.add(new DashboardOverviewPanel(networkClient), "OVERVIEW");
            contentCards.add(createHomeSessionPanel(), "HOME");
            contentCards.add(new BookManagementPanel(networkClient), "BOOKS");
            contentCards.add(new StudentManagementPanel(networkClient), "STUDENTS");
            contentCards.add(new BorrowManagementPanel(networkClient), "CIRCULATION");
            contentCards.add(new FineManagementPanel(networkClient), "FINES");
            contentCards.add(new ReservationManagementPanel(networkClient), "RESERVATIONS");
            if (role == UserRole.ADMIN) {
                contentCards.add(new AuditLogManagementPanel(networkClient), "AUDIT_LOGS");
            }
            showCardWithFade("OVERVIEW", overviewBtn);
        }
        ModernUIHelper.styleComponentTree(getContentPane());
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

    private javax.swing.JButton currentActiveBtn;

    private void showCardWithFade(String cardName, javax.swing.JButton activeBtn) {
        if (activeBtn != null) {
            setActiveSidebarButton(activeBtn);
        }
        if (cardLayout != null && contentCards != null) {
            cardLayout.show(contentCards, cardName);
        }
        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }
        contentAlpha = 0.20f;
        contentCards.repaint();
        fadeTimer = new javax.swing.Timer(30, e -> {
            contentAlpha += 0.16f;
            if (contentAlpha >= 1.0f) {
                contentAlpha = 1.0f;
                fadeTimer.stop();
            }
            contentCards.repaint();
        });
        fadeTimer.start();
    }

    private void setActiveSidebarButton(javax.swing.JButton activeBtn) {
        this.currentActiveBtn = activeBtn;
        javax.swing.JButton[] allButtons = {homeBtn, overviewBtn, booksBtn, studentsBtn, borrowBtn, finesBtn, reservationsBtn, auditBtn};
        for (javax.swing.JButton b : allButtons) {
            if (b == null) continue;
            ModernUIHelper.setSidebarButtonActive(b, b == activeBtn);
        }
    }

    private void homeBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_homeBtnActionPerformed
        showCardWithFade("HOME", homeBtn);
    }//GEN-LAST:event_homeBtnActionPerformed

    private void overviewBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overviewBtnActionPerformed
        showCardWithFade("OVERVIEW", overviewBtn);
    }//GEN-LAST:event_overviewBtnActionPerformed

    private void booksBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_booksBtnActionPerformed
        showCardWithFade("BOOKS", booksBtn);
    }//GEN-LAST:event_booksBtnActionPerformed

    private void studentsBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_studentsBtnActionPerformed
        showCardWithFade("STUDENTS", studentsBtn);
    }//GEN-LAST:event_studentsBtnActionPerformed

    private void borrowBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_borrowBtnActionPerformed
        showCardWithFade("CIRCULATION", borrowBtn);
    }//GEN-LAST:event_borrowBtnActionPerformed

    private void finesBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_finesBtnActionPerformed
        showCardWithFade("FINES", finesBtn);
    }//GEN-LAST:event_finesBtnActionPerformed

    private void reservationsBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reservationsBtnActionPerformed
        showCardWithFade("RESERVATIONS", reservationsBtn);
    }//GEN-LAST:event_reservationsBtnActionPerformed

    private void auditBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auditBtnActionPerformed
        showCardWithFade("AUDIT_LOGS", auditBtn);
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

    private void styleSidebarButton(final javax.swing.JButton btn) {
        // Handled via ModernUIHelper.styleSidebarButton in initCustom
    }

    private JPanel createHomeSessionPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(ModernUIHelper.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        // Welcome Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        headerPanel.setOpaque(false);
        JLabel welcomeTitle = new JLabel("Xin chào, " + session.getFullName() + "!");
        welcomeTitle.setFont(ModernUIHelper.FONT_TITLE);
        welcomeTitle.setForeground(ModernUIHelper.TEXT_ACCENT);

        JLabel welcomeSub = new JLabel(
        "Hệ thống Quản lý Thư Viện Trường Học (Giao thức mạng Java RMI)"
        );
        welcomeSub.setFont(ModernUIHelper.FONT_SUBTITLE);
        welcomeSub.setForeground(ModernUIHelper.TEXT_MUTED);
        headerPanel.add(welcomeTitle);
        headerPanel.add(welcomeSub);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Session Information Card
        CyberCardPanel cardPanel = new CyberCardPanel();
        cardPanel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 14, 10, 14);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addInfoRow(cardPanel, gbc, row++, "Tài khoản đăng nhập:", session.getUsername() + " (Mã: " + session.getUserId() + ")");
        addInfoRow(cardPanel, gbc, row++, "Vai trò phân quyền:", getRoleDisplayName(session.getRole()));
        addInfoRow(cardPanel, gbc, row++, "Họ và tên người dùng:", session.getFullName());

        String maskedToken = (session.getToken() != null && session.getToken().length() > 8)
                ? session.getToken().substring(0, 8) + "..."
                : (session.getToken() != null ? session.getToken() : "N/A");
        addInfoRow(cardPanel, gbc, row++, "Mã phiên làm việc (Token):", maskedToken);
        addInfoRow(cardPanel, gbc, row++, "Giao thức truyền tải mạng:", "Java RMI over TCP (Registry Port 1099, Service 1100)");
        addInfoRow(cardPanel, gbc, row++, "Trạng thái ứng dụng:", "Đang kết nối & Sẵn sàng hoạt động");

        panel.add(cardPanel, BorderLayout.CENTER);

        // Bottom status notice
        JLabel notice = new JLabel("Vui lòng chọn chức năng ở thanh menu bên trái để quản lý sách, sinh viên, mượn trả, đặt trước và tiền phạt.");
        notice.setFont(ModernUIHelper.FONT_SMALL);
        notice.setForeground(ModernUIHelper.TEXT_MUTED);
        panel.add(notice, BorderLayout.SOUTH);

        return panel;
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setFont(ModernUIHelper.FONT_LABEL);
        lbl.setForeground(ModernUIHelper.LABEL_DETAIL);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JLabel val = new JLabel(value);
        val.setFont(ModernUIHelper.FONT_INPUT);
        val.setForeground(ModernUIHelper.TEXT_PRIMARY);
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
