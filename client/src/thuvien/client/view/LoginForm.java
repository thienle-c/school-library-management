package thuvien.client.view;

import java.awt.Color;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import thuvien.client.controller.ClientAuthController;
import thuvien.client.network.NetworkClient;
import thuvien.client.network.RMIClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.ModernUIHelper;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.NetworkException;

/**
 * Authentication dialog / window for the Remote School Library client.
 * Connects to the server over Java RMI, handles login, and displays user-friendly status and errors.
 */
public class LoginForm extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(LoginForm.class.getName());

    private final NetworkClient networkClient;
    private final ClientAuthController authController;

    public LoginForm() {
        this(new RMIClient());
    }

    public LoginForm(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.authController = new ClientAuthController(networkClient);
        initComponents();
        initCustom();
    }

    private void initCustom() {
        // Window sizing & positioning
        setSize(920, 580);
        setResizable(false);
        setLocationRelativeTo(null);
        setTitle("Hệ Thống Quản Lý Thư Viện - Đăng Nhập");

        if (networkClient != null) {
            serverHostField.setText(networkClient.getHost());
            serverPortField.setText(String.valueOf(networkClient.getPort()));
        }

        getRootPane().setDefaultButton(loginButton);

        // Clear default generated content pane layout to build modern split layout
        getContentPane().removeAll();
        getContentPane().setLayout(new java.awt.BorderLayout());

        // =================================================================
        // 1. LEFT HERO PANEL (AI Grid background with moving nodes & scanline)
        // =================================================================
        thuvien.client.view.common.AIGridPanel heroPanel = new thuvien.client.view.common.AIGridPanel();
        heroPanel.setPreferredSize(new Dimension(380, 580));
        heroPanel.setLayout(new java.awt.BorderLayout(0, 16));
        heroPanel.setBorder(BorderFactory.createEmptyBorder(28, 28, 24, 28));

        // Hero Top Branding
        javax.swing.JPanel heroTopPanel = new javax.swing.JPanel();
        heroTopPanel.setOpaque(false);
        heroTopPanel.setLayout(new javax.swing.BoxLayout(heroTopPanel, javax.swing.BoxLayout.Y_AXIS));

        javax.swing.JLabel brandBadge = new javax.swing.JLabel("NHÀ SÁCH & THƯ VIỆN HỌC THUẬT");
        brandBadge.setIcon(ModernUIHelper.getVectorIcon("BOOKS", 12, ModernUIHelper.PRIMARY_BLUE));
        brandBadge.setIconTextGap(6);
        brandBadge.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
        brandBadge.setForeground(ModernUIHelper.PRIMARY_BLUE);
        brandBadge.setOpaque(true);
        brandBadge.setBackground(new Color(0xEF, 0xF6, 0xFF));
        brandBadge.setBorder(ModernUIHelper.createRoundedBorder(new Color(0xBF, 0xDB, 0xFE), 10, 3, 8, 3, 8));

        javax.swing.JLabel brandTitle = new javax.swing.JLabel("Thư Viện Tri Thức");
        brandTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
        brandTitle.setForeground(ModernUIHelper.TEXT_TITLE);

        javax.swing.JLabel brandDesc = new javax.swing.JLabel("Không gian học thuật & tra cứu ấn phẩm số thông minh");
        brandDesc.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        brandDesc.setForeground(ModernUIHelper.TEXT_MUTED);

        heroTopPanel.add(brandBadge);
        heroTopPanel.add(javax.swing.Box.createVerticalStrut(6));
        heroTopPanel.add(brandTitle);
        heroTopPanel.add(javax.swing.Box.createVerticalStrut(2));
        heroTopPanel.add(brandDesc);

        heroPanel.add(heroTopPanel, java.awt.BorderLayout.NORTH);

        // Hero Center Image
        javax.swing.JPanel imageContainer = new javax.swing.JPanel(new java.awt.BorderLayout());
        imageContainer.setOpaque(false);
        javax.swing.ImageIcon heroIcon = ModernUIHelper.loadResourceIcon("/images/library_hero.jpg", 324, 248);
        if (heroIcon != null) {
            javax.swing.JLabel imageLabel = new javax.swing.JLabel(heroIcon);
            imageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            imageLabel.setBorder(BorderFactory.createCompoundBorder(
                    ModernUIHelper.createRoundedBorder(ModernUIHelper.BORDER_INPUT, 10, 0, 0, 0, 0),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));
            imageContainer.add(imageLabel, java.awt.BorderLayout.CENTER);
        }
        heroPanel.add(imageContainer, java.awt.BorderLayout.CENTER);

        // Hero Bottom Features
        javax.swing.JPanel heroBottomPanel = new javax.swing.JPanel();
        heroBottomPanel.setOpaque(false);
        heroBottomPanel.setLayout(new javax.swing.BoxLayout(heroBottomPanel, javax.swing.BoxLayout.Y_AXIS));

        String[][] features = {
            {"BOOKS", "Tra cứu hơn 10.000+ đầu sách & tài liệu học thuật"},
            {"CIRCULATION", "Mượn trả & gia hạn tức thì qua mạng phân tán"},
            {"AUDIT", "Xác thực bảo mật đa tầng & phân quyền độc giả"}
        };
        for (String[] feat : features) {
            javax.swing.JLabel featLabel = new javax.swing.JLabel(feat[1]);
            featLabel.setIcon(ModernUIHelper.getVectorIcon(feat[0], 14, ModernUIHelper.PRIMARY_BLUE));
            featLabel.setIconTextGap(8);
            featLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
            featLabel.setForeground(ModernUIHelper.TEXT_BODY);
            heroBottomPanel.add(featLabel);
            heroBottomPanel.add(javax.swing.Box.createVerticalStrut(6));
        }

        javax.swing.JLabel verLabel = new javax.swing.JLabel("Phiên bản Học thuật v2.0 | Coursework Network Programming");
        verLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 10));
        verLabel.setForeground(ModernUIHelper.TEXT_MUTED);
        heroBottomPanel.add(javax.swing.Box.createVerticalStrut(4));
        heroBottomPanel.add(verLabel);

        heroPanel.add(heroBottomPanel, java.awt.BorderLayout.SOUTH);

        // =================================================================
        // 2. RIGHT AUTH PANEL (Cyber Card with glowing controls)
        // =================================================================
        javax.swing.JPanel authPanel = new javax.swing.JPanel();
        authPanel.setBackground(ModernUIHelper.BG_CARD);
        authPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, ModernUIHelper.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(28, 36, 24, 36)
        ));
        authPanel.setLayout(new javax.swing.BoxLayout(authPanel, javax.swing.BoxLayout.Y_AXIS));

        // Welcome Header
        javax.swing.JLabel welcomeTitle = new javax.swing.JLabel("Đăng Nhập");
        welcomeTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 22));
        welcomeTitle.setForeground(ModernUIHelper.TEXT_TITLE);
        welcomeTitle.setAlignmentX(0.0f);

        javax.swing.JLabel welcomeSub = new javax.swing.JLabel("Vui lòng nhập tài khoản để truy cập hệ thống thư viện.");
        welcomeSub.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        welcomeSub.setForeground(ModernUIHelper.TEXT_MUTED);
        welcomeSub.setAlignmentX(0.0f);

        authPanel.add(welcomeTitle);
        authPanel.add(javax.swing.Box.createVerticalStrut(4));
        authPanel.add(welcomeSub);
        authPanel.add(javax.swing.Box.createVerticalStrut(14));

        // Server Connection Status Pill & Collapsible Config (Bright Soft Green Pill)
        javax.swing.JPanel serverPill = new javax.swing.JPanel(new java.awt.BorderLayout(8, 0));
        serverPill.setBackground(new Color(0xF0, 0xFD, 0xF4)); // Soft emerald tint
        serverPill.setBorder(BorderFactory.createCompoundBorder(
                ModernUIHelper.createRoundedBorder(new Color(0xBB, 0xF7, 0xD0), 8, 0, 0, 0, 0),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        serverPill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        serverPill.setAlignmentX(0.0f);

        final javax.swing.JLabel serverStatusText = new javax.swing.JLabel(
                "Máy chủ trực tuyến: " + serverHostField.getText().trim() + ":" + serverPortField.getText().trim()
        );
        serverStatusText.setIcon(ModernUIHelper.getVectorIcon("CHECK", 14, new Color(0x16, 0x65, 0x34)));
        serverStatusText.setIconTextGap(6);
        serverStatusText.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        serverStatusText.setForeground(new Color(0x16, 0x65, 0x34)); // Dark emerald text for contrast

        final javax.swing.JButton toggleConfigBtn = new javax.swing.JButton("Cấu hình");
        toggleConfigBtn.setIcon(ModernUIHelper.getVectorIcon("GEAR", 12, ModernUIHelper.PRIMARY_BLUE));
        toggleConfigBtn.setIconTextGap(4);
        toggleConfigBtn.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        toggleConfigBtn.setForeground(ModernUIHelper.PRIMARY_BLUE);
        toggleConfigBtn.setBorderPainted(false);
        toggleConfigBtn.setContentAreaFilled(false);
        toggleConfigBtn.setFocusPainted(false);
        toggleConfigBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        serverPill.add(serverStatusText, java.awt.BorderLayout.CENTER);
        serverPill.add(toggleConfigBtn, java.awt.BorderLayout.EAST);

        authPanel.add(serverPill);
        authPanel.add(javax.swing.Box.createVerticalStrut(8));

        // Collapsible Server Config Box (Initially hidden)
        final javax.swing.JPanel serverConfigBox = new javax.swing.JPanel(new java.awt.GridBagLayout());
        serverConfigBox.setBackground(ModernUIHelper.BG_APP);
        serverConfigBox.setBorder(BorderFactory.createCompoundBorder(
                ModernUIHelper.createRoundedBorder(ModernUIHelper.BORDER_INPUT, 6, 0, 0, 0, 0),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        serverConfigBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        serverConfigBox.setAlignmentX(0.0f);
        serverConfigBox.setVisible(false);

        toggleConfigBtn.addActionListener(e -> {
            boolean isVisible = serverConfigBox.isVisible();
            serverConfigBox.setVisible(!isVisible);
            toggleConfigBtn.setText(isVisible ? "Cấu hình" : "Thu gọn");
            toggleConfigBtn.setIcon(ModernUIHelper.getVectorIcon(isVisible ? "GEAR" : "RESET", 12, ModernUIHelper.PRIMARY_BLUE));
            authPanel.revalidate();
            authPanel.repaint();
        });

        java.awt.GridBagConstraints sGbc = new java.awt.GridBagConstraints();
        sGbc.insets = new java.awt.Insets(0, 4, 0, 4);
        sGbc.fill = java.awt.GridBagConstraints.HORIZONTAL;

        sGbc.gridx = 0; sGbc.weightx = 0.0;
        ModernUIHelper.styleLabel(hostLabel, "IP:");
        serverConfigBox.add(hostLabel, sGbc);

        sGbc.gridx = 1; sGbc.weightx = 0.45;
        ModernUIHelper.styleInputField(serverHostField);
        serverConfigBox.add(serverHostField, sGbc);

        sGbc.gridx = 2; sGbc.weightx = 0.0;
        ModernUIHelper.styleLabel(portLabel, "Port:");
        serverConfigBox.add(portLabel, sGbc);

        sGbc.gridx = 3; sGbc.weightx = 0.25;
        ModernUIHelper.styleInputField(serverPortField);
        serverConfigBox.add(serverPortField, sGbc);

        sGbc.gridx = 4; sGbc.weightx = 0.3;
        ModernUIHelper.styleSecondaryButton(testConnectionButton, "Kiểm tra");
        testConnectionButton.setPreferredSize(new Dimension(85, 28));
        serverConfigBox.add(testConnectionButton, sGbc);

        authPanel.add(serverConfigBox);
        authPanel.add(javax.swing.Box.createVerticalStrut(12));

        // Form Fields (Username & Password)
        ModernUIHelper.styleLabel(userLabel, "Tên đăng nhập:");
        userLabel.setAlignmentX(0.0f);
        authPanel.add(userLabel);
        authPanel.add(javax.swing.Box.createVerticalStrut(4));

        ModernUIHelper.styleInputField(usernameField);
        usernameField.setPreferredSize(new Dimension(Integer.MAX_VALUE, 38));
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        usernameField.setAlignmentX(0.0f);
        authPanel.add(usernameField);
        authPanel.add(javax.swing.Box.createVerticalStrut(10));

        ModernUIHelper.styleLabel(passLabel, "Mật khẩu:");
        passLabel.setAlignmentX(0.0f);
        authPanel.add(passLabel);
        authPanel.add(javax.swing.Box.createVerticalStrut(4));

        // Password container with eye toggle
        javax.swing.JPanel passContainer = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
        passContainer.setOpaque(false);
        passContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        passContainer.setAlignmentX(0.0f);

        ModernUIHelper.styleInputField(passwordField);
        passwordField.setPreferredSize(new Dimension(Integer.MAX_VALUE, 38));

        final javax.swing.JButton togglePassBtn = new javax.swing.JButton(ModernUIHelper.getVectorIcon("EYE", 16, ModernUIHelper.LABEL_DETAIL));
        togglePassBtn.setToolTipText("Hiện/Ẩn mật khẩu");
        togglePassBtn.setFocusPainted(false);
        togglePassBtn.setBorderPainted(false);
        togglePassBtn.setContentAreaFilled(false);
        togglePassBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        final char defaultEcho = passwordField.getEchoChar();
        togglePassBtn.addActionListener(e -> {
            if (passwordField.getEchoChar() == defaultEcho) {
                passwordField.setEchoChar((char) 0);
                togglePassBtn.setIcon(ModernUIHelper.getVectorIcon("EYE_OFF", 16, ModernUIHelper.PRIMARY_BLUE));
            } else {
                passwordField.setEchoChar(defaultEcho);
                togglePassBtn.setIcon(ModernUIHelper.getVectorIcon("EYE", 16, ModernUIHelper.LABEL_DETAIL));
            }
        });

        passContainer.add(passwordField, java.awt.BorderLayout.CENTER);
        passContainer.add(togglePassBtn, java.awt.BorderLayout.EAST);
        authPanel.add(passContainer);
        authPanel.add(javax.swing.Box.createVerticalStrut(8));

        // Status Label / Alert Banner
        statusLabel.setFont(ModernUIHelper.FONT_SMALL);
        statusLabel.setForeground(ModernUIHelper.TEXT_MUTED);
        statusLabel.setText("Sẵn sàng kết nối tới máy chủ.");
        statusLabel.setAlignmentX(0.0f);
        authPanel.add(statusLabel);
        authPanel.add(javax.swing.Box.createVerticalStrut(12));

        // Login Button
        ModernUIHelper.stylePrimaryButton(loginButton, "ĐĂNG NHẬP");
        loginButton.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        loginButton.setPreferredSize(new Dimension(Integer.MAX_VALUE, 40));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loginButton.setAlignmentX(0.0f);
        authPanel.add(loginButton);
        authPanel.add(javax.swing.Box.createVerticalStrut(8));

        // Register Button Link
        registerButton.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        registerButton.setForeground(ModernUIHelper.PRIMARY_BLUE);
        registerButton.setText("Chưa có tài khoản sinh viên? Đăng ký tại đây");
        registerButton.setBorderPainted(false);
        registerButton.setContentAreaFilled(false);
        registerButton.setFocusPainted(false);
        registerButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        registerButton.setAlignmentX(0.0f);
        authPanel.add(registerButton);
        authPanel.add(javax.swing.Box.createVerticalStrut(14));

        // Demo Quick-Fill Bar
        javax.swing.JPanel demoPanel = new javax.swing.JPanel();
        demoPanel.setOpaque(false);
        demoPanel.setLayout(new javax.swing.BoxLayout(demoPanel, javax.swing.BoxLayout.Y_AXIS));
        demoPanel.setAlignmentX(0.0f);

        javax.swing.JLabel demoLabel = new javax.swing.JLabel("Tài khoản mẫu thử nghiệm:");
        demoLabel.setIcon(ModernUIHelper.getVectorIcon("SESSION", 12, ModernUIHelper.LABEL_DETAIL));
        demoLabel.setIconTextGap(6);
        demoLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        demoLabel.setForeground(ModernUIHelper.LABEL_DETAIL);
        demoPanel.add(demoLabel);
        demoPanel.add(javax.swing.Box.createVerticalStrut(6));

        javax.swing.JPanel chipsRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        chipsRow.setOpaque(false);

        Color chipBg = new Color(0xF1, 0xF5, 0xF9);
        javax.swing.JButton adminChip = new javax.swing.JButton();
        ModernUIHelper.styleChipButton(adminChip, "Admin", ModernUIHelper.getVectorIcon("AUDIT", 12, ModernUIHelper.PRIMARY_BLUE), chipBg, ModernUIHelper.TEXT_PRIMARY);
        adminChip.addActionListener(e -> {
            usernameField.setText("admin");
            passwordField.setText("admin123");
            statusLabel.setText("Đã điền tài khoản mẫu Admin.");
        });

        javax.swing.JButton libChip = new javax.swing.JButton();
        ModernUIHelper.styleChipButton(libChip, "Thủ thư", ModernUIHelper.getVectorIcon("BOOKS", 12, ModernUIHelper.PRIMARY_BLUE), chipBg, ModernUIHelper.TEXT_PRIMARY);
        libChip.addActionListener(e -> {
            usernameField.setText("librarian1");
            passwordField.setText("lib123");
            statusLabel.setText("Đã điền tài khoản mẫu Thủ thư.");
        });

        javax.swing.JButton studentChip = new javax.swing.JButton();
        ModernUIHelper.styleChipButton(studentChip, "Sinh viên", ModernUIHelper.getVectorIcon("STUDENTS", 12, ModernUIHelper.PRIMARY_BLUE), chipBg, ModernUIHelper.TEXT_PRIMARY);
        studentChip.addActionListener(e -> {
            usernameField.setText("student1");
            passwordField.setText("student123");
            statusLabel.setText("Đã điền tài khoản mẫu Sinh viên.");
        });

        chipsRow.add(adminChip);
        chipsRow.add(libChip);
        chipsRow.add(studentChip);
        demoPanel.add(chipsRow);

        authPanel.add(demoPanel);

        // Add both panels to content pane
        getContentPane().add(heroPanel, java.awt.BorderLayout.WEST);
        getContentPane().add(authPanel, java.awt.BorderLayout.CENTER);
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
        java.awt.GridBagConstraints gridBagConstraints;

        headerPanel = new javax.swing.JPanel();
        headerLabel = new javax.swing.JLabel();
        subHeaderLabel = new javax.swing.JLabel();
        formPanel = new javax.swing.JPanel();
        hostLabel = new javax.swing.JLabel();
        serverHostField = new javax.swing.JTextField();
        portLabel = new javax.swing.JLabel();
        serverPortField = new javax.swing.JTextField();
        testConnectionButton = new javax.swing.JButton();
        userLabel = new javax.swing.JLabel();
        usernameField = new javax.swing.JTextField();
        passLabel = new javax.swing.JLabel();
        passwordField = new javax.swing.JPasswordField();
        bottomPanel = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        buttonPanel = new javax.swing.JPanel();
        loginRow = new javax.swing.JPanel();
        loginButton = new javax.swing.JButton();
        regRow = new javax.swing.JPanel();
        registerButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Hệ Thống Quản Lý Thư Viện - Đăng Nhập");
        setResizable(false);
        getContentPane().setLayout(new java.awt.BorderLayout(10, 12));

        headerPanel.setOpaque(false);
        headerPanel.setLayout(new java.awt.BorderLayout(0, 4));

        headerLabel.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        headerLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        headerLabel.setText("Quản Lý Thư Viện Trường Học");
        headerPanel.add(headerLabel, java.awt.BorderLayout.NORTH);

        subHeaderLabel.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        subHeaderLabel.setForeground(new java.awt.Color(100, 100, 100));
        subHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        subHeaderLabel.setText("Ứng Dụng Khách Kết Nối Java RMI");
        headerPanel.add(subHeaderLabel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(headerPanel, java.awt.BorderLayout.NORTH);

        formPanel.setOpaque(false);
        formPanel.setLayout(new java.awt.GridBagLayout());

        hostLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        hostLabel.setText("Server IP:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(hostLabel, gridBagConstraints);

        serverHostField.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(serverHostField, gridBagConstraints);

        portLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        portLabel.setText("Port:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(portLabel, gridBagConstraints);

        serverPortField.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(serverPortField, gridBagConstraints);

        testConnectionButton.setText("Kiểm tra kết nối");
        testConnectionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testConnectionButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(testConnectionButton, gridBagConstraints);

        userLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        userLabel.setText("Tên đăng nhập:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(userLabel, gridBagConstraints);

        usernameField.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(usernameField, gridBagConstraints);

        passLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        passLabel.setText("Mật khẩu:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(passLabel, gridBagConstraints);

        passwordField.setColumns(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 5, 6);
        formPanel.add(passwordField, gridBagConstraints);

        getContentPane().add(formPanel, java.awt.BorderLayout.CENTER);

        bottomPanel.setOpaque(false);
        bottomPanel.setLayout(new java.awt.BorderLayout(5, 8));

        statusLabel.setForeground(new java.awt.Color(80, 80, 80));
        statusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        statusLabel.setText("Vui lòng nhập tài khoản và mật khẩu.");
        bottomPanel.add(statusLabel, java.awt.BorderLayout.NORTH);

        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new java.awt.BorderLayout(0, 8));

        loginRow.setOpaque(false);

        loginButton.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        loginButton.setText("Đăng nhập");
        loginButton.setPreferredSize(new java.awt.Dimension(140, 32));
        loginButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loginButtonActionPerformed(evt);
            }
        });
        loginRow.add(loginButton);

        buttonPanel.add(loginRow, java.awt.BorderLayout.NORTH);

        regRow.setOpaque(false);

        registerButton.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        registerButton.setForeground(new java.awt.Color(30, 80, 160));
        registerButton.setText("Chưa có tài khoản? Đăng ký tại đây");
        registerButton.setBorderPainted(false);
        registerButton.setContentAreaFilled(false);
        registerButton.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        registerButton.setFocusPainted(false);
        registerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                registerButtonActionPerformed(evt);
            }
        });
        regRow.add(registerButton);

        buttonPanel.add(regRow, java.awt.BorderLayout.SOUTH);

        bottomPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        getContentPane().add(bottomPanel, java.awt.BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void testConnectionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testConnectionButtonActionPerformed
        performTestConnection();
    }//GEN-LAST:event_testConnectionButtonActionPerformed

    private void loginButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loginButtonActionPerformed
        performLogin();
    }//GEN-LAST:event_loginButtonActionPerformed

    private void registerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_registerButtonActionPerformed
        if (!applyServerSettingsFromUI()) {
            return;
        }
        StudentRegistrationDialog dialog = new StudentRegistrationDialog(this, networkClient);
        dialog.setVisible(true);
    }//GEN-LAST:event_registerButtonActionPerformed

    private void setInputsEnabled(boolean enabled) {
        serverHostField.setEnabled(enabled);
        serverPortField.setEnabled(enabled);
        testConnectionButton.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    private boolean applyServerSettingsFromUI() {
        String host = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập địa chỉ Server IP.",
                    "Lỗi Cấu Hình",
                    JOptionPane.WARNING_MESSAGE);
            serverHostField.requestFocusInWindow();
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Out of range");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Port phải là số nguyên hợp lệ (1 - 65535).",
                    "Lỗi Cấu Hình",
                    JOptionPane.WARNING_MESSAGE);
            serverPortField.requestFocusInWindow();
            return false;
        }
        networkClient.setServerAddress(host, port);
        return true;
    }

    private void performTestConnection() {
        if (!applyServerSettingsFromUI()) {
            return;
        }
        final String host = networkClient.getHost();
        final int port = networkClient.getPort();

        setInputsEnabled(false);
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setText("Đang kiểm tra kết nối tới " + host + ":" + port + "...");

        AsyncWorker.run(
                () -> {
                    networkClient.connect(host, port);
                    return networkClient.ping();
                },
                (Boolean pongOk) -> {
                    setInputsEnabled(true);
                    if (Boolean.TRUE.equals(pongOk)) {
                        statusLabel.setForeground(new Color(20, 130, 50));
                        statusLabel.setText("Server connected");
                        JOptionPane.showMessageDialog(LoginForm.this,
                                "Server connected",
                                "Kết Nối Thành Công",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setForeground(new Color(180, 40, 40));
                        statusLabel.setText("Cannot connect to server");
                        JOptionPane.showMessageDialog(LoginForm.this,
                                "Cannot connect to server",
                                "Lỗi Kết Nối",
                                JOptionPane.ERROR_MESSAGE);
                    }
                },
                (Exception ex) -> {
                    setInputsEnabled(true);
                    statusLabel.setForeground(new Color(180, 40, 40));
                    String errorMsg = (ex.getMessage() != null && !ex.getMessage().trim().isEmpty())
                            ? ex.getMessage()
                            : "Cannot connect to server";
                    JOptionPane.showMessageDialog(LoginForm.this,
                            errorMsg,
                            "Lỗi Kết Nối RMI",
                            JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    private void performLogin() {
        if (!applyServerSettingsFromUI()) {
            return;
        }
        String username = usernameField.getText().trim();
        char[] passChars = passwordField.getPassword();
        String password = new String(passChars);

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.",
                    "Lỗi Nhập Liệu",
                    JOptionPane.WARNING_MESSAGE);
            if (username.isEmpty()) {
                usernameField.requestFocusInWindow();
            } else {
                passwordField.requestFocusInWindow();
            }
            return;
        }

        // Disable inputs and show loading state
        setInputsEnabled(false);
        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setText("Đang kết nối và xác thực với máy chủ...");

        // Non-blocking network call via AsyncWorker off the EDT
        AsyncWorker.run(
                () -> authController.login(username, password),
                (UserSessionDTO session) -> {
                    statusLabel.setText("Đăng nhập thành công.");
                    dispose();
                    SwingUtilities.invokeLater(() -> new MainDashboardForm(networkClient, session).setVisible(true));
                },
                (Exception ex) -> {
                    // Re-enable inputs on failure
                    setInputsEnabled(true);
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();

                    String errorTitle;
                    String errorMsg;

                    if (ex instanceof AuthenticationException) {
                        errorTitle = "Đăng Nhập Thất Bại";
                        errorMsg = (ex.getMessage() != null && !ex.getMessage().isEmpty())
                                ? ex.getMessage()
                                : "Tên đăng nhập hoặc mật khẩu không chính xác.";
                        statusLabel.setText(errorMsg);
                    } else if (ex instanceof NetworkException) {
                        errorTitle = "Lỗi Kết Nối Máy Chủ";
                        errorMsg = "Cannot connect to server (" + networkClient.getHost() + ":" + networkClient.getPort() + ").\n"
                                + "Vui lòng kiểm tra lại Server IP, Port và kết nối mạng.";
                        statusLabel.setText("Cannot connect to server");
                        LOGGER.log(Level.WARNING, "Login network failure", ex);
                    } else {
                        errorTitle = "Lỗi Hệ Thống";
                        errorMsg = "Đã xảy ra lỗi trong quá trình xác thực.";
                        statusLabel.setText("Lỗi hệ thống khi đăng nhập.");
                        LOGGER.log(Level.SEVERE, "Unexpected login error", ex);
                    }

                    JOptionPane.showMessageDialog(LoginForm.this, errorMsg, errorTitle, JOptionPane.ERROR_MESSAGE);
                }
        );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JPanel formPanel;
    private javax.swing.JLabel headerLabel;
    private javax.swing.JPanel headerPanel;
    private javax.swing.JLabel hostLabel;
    private javax.swing.JButton loginButton;
    private javax.swing.JPanel loginRow;
    private javax.swing.JLabel passLabel;
    private javax.swing.JPasswordField passwordField;
    private javax.swing.JLabel portLabel;
    private javax.swing.JPanel regRow;
    private javax.swing.JButton registerButton;
    private javax.swing.JTextField serverHostField;
    private javax.swing.JTextField serverPortField;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel subHeaderLabel;
    private javax.swing.JButton testConnectionButton;
    private javax.swing.JLabel userLabel;
    private javax.swing.JTextField usernameField;
    // End of variables declaration//GEN-END:variables
}
