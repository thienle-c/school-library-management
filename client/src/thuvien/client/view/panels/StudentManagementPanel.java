package thuvien.client.view.panels;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.controller.ClientStudentController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.client.view.dialogs.StudentFormDialog;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.StudentStatus;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Panel providing student account management for staff (Librarian/Admin)
 * and personal profile inspection for student accounts.
 */
public class StudentManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(StudentManagementPanel.class.getName());
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientStudentController studentController;
    private final boolean isStaff;

    private DefaultTableModel tableModel;
    private List<StudentDTO> allStudents = new ArrayList<>();
    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public StudentManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.studentController = new ClientStudentController(networkClient);
        this.isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        initComponents();
        initCustom();

        CardLayout cl = (CardLayout) getLayout();
        if (isStaff) {
            cl.show(this, "staffCard");
            loadStudents();
            initAutoRefresh();
        } else {
            cl.show(this, "studentProfileCard");
            loadStudentProfile();
        }
    }

    private void initCustom() {
        if (isStaff) {
            staffPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
            topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));
            detailCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)
            ));
            bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

            boolean isAdmin = ClientSession.getInstance().isAdmin();
            if (!isAdmin) {
                activationCodeBtn.setVisible(false);
                hardDeleteStudentBtn.setVisible(false);
            }

            SearchAutoCompleteHelper.attach(searchField, "STUDENT", new ClientSearchSuggestionController(networkClient), () -> applyFilter(false));

            String[] columns = {"Mã ID", "Mã Sinh Viên", "Họ Và Tên", "Lớp / Khóa", "Điện Thoại", "Địa Chỉ Email", "Đang Mượn / Tối Đa", "Trạng Thái"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
            };

            studentTable.setModel(tableModel);
            studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            studentTable.setRowHeight(24);
            studentTable.getTableHeader().setFont(studentTable.getTableHeader().getFont().deriveFont(Font.BOLD));
            studentTable.setAutoCreateRowSorter(true);

            studentTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    updateStaffDetails();
                }
            });
        } else {
            studentProfilePanel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));
            profileCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(225, 230, 238), 1),
                    BorderFactory.createEmptyBorder(20, 25, 20, 25)
            ));
        }
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

        staffPanel = new javax.swing.JPanel();
        topToolbar = new javax.swing.JPanel();
        leftPanel = new javax.swing.JPanel();
        lblSearch = new javax.swing.JLabel();
        searchField = new javax.swing.JTextField();
        statusFilterCombo = new javax.swing.JComboBox<String>();
        filterBtn = new javax.swing.JButton();
        resetBtn = new javax.swing.JButton();
        refreshBtn = new javax.swing.JButton();
        rightPanel = new javax.swing.JPanel();
        newStudentBtn = new javax.swing.JButton();
        editStudentBtn = new javax.swing.JButton();
        suspendStudentBtn = new javax.swing.JButton();
        activationCodeBtn = new javax.swing.JButton();
        hardDeleteStudentBtn = new javax.swing.JButton();
        splitPane = new javax.swing.JSplitPane();
        tableScroll = new javax.swing.JScrollPane();
        studentTable = new javax.swing.JTable();
        detailCard = new javax.swing.JPanel();
        detailHeader = new javax.swing.JLabel();
        gridDetails = new javax.swing.JPanel();
        lblId = new javax.swing.JLabel();
        detailIdVal = new javax.swing.JLabel();
        lblCode = new javax.swing.JLabel();
        detailCodeVal = new javax.swing.JLabel();
        lblName = new javax.swing.JLabel();
        detailNameVal = new javax.swing.JLabel();
        lblClass = new javax.swing.JLabel();
        detailClassVal = new javax.swing.JLabel();
        lblPhone = new javax.swing.JLabel();
        detailPhoneVal = new javax.swing.JLabel();
        lblEmail = new javax.swing.JLabel();
        detailEmailVal = new javax.swing.JLabel();
        lblBorrow = new javax.swing.JLabel();
        detailBorrowVal = new javax.swing.JLabel();
        lblStatus = new javax.swing.JLabel();
        detailStatusVal = new javax.swing.JLabel();
        bottomBar = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        studentProfilePanel = new javax.swing.JPanel();
        profileHeaderPanel = new javax.swing.JPanel();
        profileTitle = new javax.swing.JLabel();
        profileSubtitle = new javax.swing.JLabel();
        profileCard = new javax.swing.JPanel();
        lblProfCode = new javax.swing.JLabel();
        profCodeVal = new javax.swing.JLabel();
        lblProfName = new javax.swing.JLabel();
        profNameVal = new javax.swing.JLabel();
        lblProfClass = new javax.swing.JLabel();
        profClassVal = new javax.swing.JLabel();
        lblProfPhone = new javax.swing.JLabel();
        profPhoneVal = new javax.swing.JLabel();
        lblProfEmail = new javax.swing.JLabel();
        profEmailVal = new javax.swing.JLabel();
        lblProfBorrow = new javax.swing.JLabel();
        profBorrowVal = new javax.swing.JLabel();
        lblProfStatus = new javax.swing.JLabel();
        profStatusVal = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 255));
        setLayout(new java.awt.CardLayout());

        staffPanel.setBackground(new java.awt.Color(255, 255, 255));
        staffPanel.setLayout(new java.awt.BorderLayout(5, 5));

        topToolbar.setOpaque(false);
        topToolbar.setLayout(new java.awt.BorderLayout(10, 0));

        leftPanel.setOpaque(false);
        leftPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));

        lblSearch.setText("Tìm kiếm sinh viên:");
        leftPanel.add(lblSearch);

        searchField.setColumns(14);
        searchField.setToolTipText("Tìm kiếm theo Mã SV, Họ tên, Lớp, Email, SĐT hoặc Mã ID");
        searchField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchFieldActionPerformed(evt);
            }
        });
        leftPanel.add(searchField);

        statusFilterCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Tất cả trạng thái", "Đang hoạt động", "Tạm khóa", "Đã tốt nghiệp" }));
        statusFilterCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                statusFilterComboActionPerformed(evt);
            }
        });
        leftPanel.add(statusFilterCombo);

        filterBtn.setText("Tìm");
        filterBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterBtnActionPerformed(evt);
            }
        });
        leftPanel.add(filterBtn);

        resetBtn.setText("Xóa bộ lọc");
        resetBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetBtnActionPerformed(evt);
            }
        });
        leftPanel.add(resetBtn);

        refreshBtn.setText("Làm mới");
        refreshBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshBtnActionPerformed(evt);
            }
        });
        leftPanel.add(refreshBtn);

        topToolbar.add(leftPanel, java.awt.BorderLayout.WEST);

        rightPanel.setOpaque(false);
        rightPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));

        newStudentBtn.setBackground(new java.awt.Color(230, 245, 230));
        newStudentBtn.setText("Thêm Sinh Viên");
        newStudentBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newStudentBtnActionPerformed(evt);
            }
        });
        rightPanel.add(newStudentBtn);

        editStudentBtn.setText("Chỉnh Sửa");
        editStudentBtn.setEnabled(false);
        editStudentBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editStudentBtnActionPerformed(evt);
            }
        });
        rightPanel.add(editStudentBtn);

        suspendStudentBtn.setBackground(new java.awt.Color(255, 245, 230));
        suspendStudentBtn.setText("Tạm Khóa");
        suspendStudentBtn.setToolTipText("Chuyển trạng thái sinh viên sang tạm khóa/ngưng hoạt động");
        suspendStudentBtn.setEnabled(false);
        suspendStudentBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                suspendStudentBtnActionPerformed(evt);
            }
        });
        rightPanel.add(suspendStudentBtn);

        activationCodeBtn.setBackground(new java.awt.Color(230, 240, 255));
        activationCodeBtn.setText("Cấp Mã Kích Hoạt");
        activationCodeBtn.setToolTipText("Tạo mã kích hoạt an toàn để sinh viên tự đăng ký tài khoản");
        activationCodeBtn.setEnabled(false);
        activationCodeBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                activationCodeBtnActionPerformed(evt);
            }
        });
        rightPanel.add(activationCodeBtn);

        hardDeleteStudentBtn.setBackground(new java.awt.Color(255, 230, 230));
        hardDeleteStudentBtn.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        hardDeleteStudentBtn.setForeground(new java.awt.Color(180, 0, 0));
        hardDeleteStudentBtn.setText("Xóa Vĩnh Viễn");
        hardDeleteStudentBtn.setToolTipText("Xóa vĩnh viễn sinh viên khỏi cơ sở dữ liệu (Chỉ Quản trị viên)");
        hardDeleteStudentBtn.setEnabled(false);
        hardDeleteStudentBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hardDeleteStudentBtnActionPerformed(evt);
            }
        });
        rightPanel.add(hardDeleteStudentBtn);

        topToolbar.add(rightPanel, java.awt.BorderLayout.EAST);

        staffPanel.add(topToolbar, java.awt.BorderLayout.NORTH);

        splitPane.setDividerSize(6);
        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.65);

        studentTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Mã ID", "Mã Sinh Viên", "Họ Và Tên", "Lớp / Khóa", "Điện Thoại", "Địa Chỉ Email", "Đang Mượn / Tối Đa", "Trạng Thái"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        studentTable.setAutoCreateRowSorter(true);
        studentTable.setRowHeight(24);
        studentTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tableScroll.setViewportView(studentTable);

        splitPane.setTopComponent(tableScroll);

        detailCard.setBackground(new java.awt.Color(248, 250, 252));
        detailCard.setLayout(new java.awt.BorderLayout(10, 10));

        detailHeader.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        detailHeader.setText("Thông Tin Hồ Sơ Sinh Viên Được Chọn");
        detailCard.add(detailHeader, java.awt.BorderLayout.NORTH);

        gridDetails.setOpaque(false);
        gridDetails.setLayout(new java.awt.GridBagLayout());

        lblId.setText("Mã ID sinh viên:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblId, gridBagConstraints);

        detailIdVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailIdVal, gridBagConstraints);

        lblCode.setText("Mã số sinh viên:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblCode, gridBagConstraints);

        detailCodeVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailCodeVal, gridBagConstraints);

        lblName.setText("Họ và tên:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblName, gridBagConstraints);

        detailNameVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailNameVal, gridBagConstraints);

        lblClass.setText("Lớp / Khóa:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblClass, gridBagConstraints);

        detailClassVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailClassVal, gridBagConstraints);

        lblPhone.setText("Số điện thoại:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblPhone, gridBagConstraints);

        detailPhoneVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailPhoneVal, gridBagConstraints);

        lblEmail.setText("Địa chỉ Email:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblEmail, gridBagConstraints);

        detailEmailVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailEmailVal, gridBagConstraints);

        lblBorrow.setText("Sách đang mượn / Hạn mức:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblBorrow, gridBagConstraints);

        detailBorrowVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailBorrowVal, gridBagConstraints);

        lblStatus.setText("Trạng thái:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblStatus, gridBagConstraints);

        detailStatusVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailStatusVal, gridBagConstraints);

        detailCard.add(gridDetails, java.awt.BorderLayout.CENTER);

        splitPane.setBottomComponent(detailCard);

        staffPanel.add(splitPane, java.awt.BorderLayout.CENTER);

        bottomBar.setOpaque(false);
        bottomBar.setLayout(new java.awt.BorderLayout());

        statusLabel.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        statusLabel.setText("Sẵn sàng");
        bottomBar.add(statusLabel, java.awt.BorderLayout.WEST);

        staffPanel.add(bottomBar, java.awt.BorderLayout.SOUTH);

        add(staffPanel, "staffCard");

        studentProfilePanel.setBackground(new java.awt.Color(255, 255, 255));
        studentProfilePanel.setLayout(new java.awt.BorderLayout());

        profileHeaderPanel.setOpaque(false);
        profileHeaderPanel.setLayout(new java.awt.GridLayout(2, 1, 0, 5));

        profileTitle.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        profileTitle.setText("Hồ Sơ Cá Nhân Sinh Viên");
        profileHeaderPanel.add(profileTitle);

        profileSubtitle.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        profileSubtitle.setForeground(new java.awt.Color(128, 128, 128));
        profileSubtitle.setText("Thông tin tài khoản thư viện và hạn mức mượn sách");
        profileHeaderPanel.add(profileSubtitle);

        studentProfilePanel.add(profileHeaderPanel, java.awt.BorderLayout.NORTH);

        profileCard.setBackground(new java.awt.Color(250, 252, 255));
        profileCard.setLayout(new java.awt.GridBagLayout());

        lblProfCode.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfCode.setText("Mã số sinh viên:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfCode, gridBagConstraints);

        profCodeVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profCodeVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profCodeVal, gridBagConstraints);

        lblProfName.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfName.setText("Họ và tên:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfName, gridBagConstraints);

        profNameVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profNameVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profNameVal, gridBagConstraints);

        lblProfClass.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfClass.setText("Lớp / Khóa học:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfClass, gridBagConstraints);

        profClassVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profClassVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profClassVal, gridBagConstraints);

        lblProfPhone.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfPhone.setText("Số điện thoại:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfPhone, gridBagConstraints);

        profPhoneVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profPhoneVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profPhoneVal, gridBagConstraints);

        lblProfEmail.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfEmail.setText("Địa chỉ Email:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfEmail, gridBagConstraints);

        profEmailVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profEmailVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profEmailVal, gridBagConstraints);

        lblProfBorrow.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfBorrow.setText("Tình trạng mượn sách:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfBorrow, gridBagConstraints);

        profBorrowVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profBorrowVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profBorrowVal, gridBagConstraints);

        lblProfStatus.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        lblProfStatus.setText("Trạng thái tài khoản:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.3;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(lblProfStatus, gridBagConstraints);

        profStatusVal.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        profStatusVal.setText("Đang tải...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.7;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 8);
        profileCard.add(profStatusVal, gridBagConstraints);

        studentProfilePanel.add(profileCard, java.awt.BorderLayout.CENTER);

        add(studentProfilePanel, "studentProfileCard");
    }// </editor-fold>//GEN-END:initComponents

    private void searchFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchFieldActionPerformed
        applyFilter(false);
    }//GEN-LAST:event_searchFieldActionPerformed

    private void statusFilterComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_statusFilterComboActionPerformed
        applyFilter(false);
    }//GEN-LAST:event_statusFilterComboActionPerformed

    private void filterBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterBtnActionPerformed
        applyFilter(false);
    }//GEN-LAST:event_filterBtnActionPerformed

    private void resetBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBtnActionPerformed
        searchField.setText("");
        statusFilterCombo.setSelectedIndex(0);
        applyFilter(false);
    }//GEN-LAST:event_resetBtnActionPerformed

    private void refreshBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshBtnActionPerformed
        loadStudents();
    }//GEN-LAST:event_refreshBtnActionPerformed

    private void newStudentBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newStudentBtnActionPerformed
        openCreateDialog();
    }//GEN-LAST:event_newStudentBtnActionPerformed

    private void editStudentBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editStudentBtnActionPerformed
        openEditDialog();
    }//GEN-LAST:event_editStudentBtnActionPerformed

    private void suspendStudentBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_suspendStudentBtnActionPerformed
        suspendSelectedStudent();
    }//GEN-LAST:event_suspendStudentBtnActionPerformed

    private void activationCodeBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_activationCodeBtnActionPerformed
        generateActivationCodeForSelectedStudent();
    }//GEN-LAST:event_activationCodeBtnActionPerformed

    private void hardDeleteStudentBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hardDeleteStudentBtnActionPerformed
        hardDeleteSelectedStudent();
    }//GEN-LAST:event_hardDeleteStudentBtnActionPerformed

    private void initAutoRefresh() {
        pollTimer = new javax.swing.Timer(6000, e -> {
            if (isShowing() && isDisplayable() && !isRefreshing) {
                loadStudentsSilently();
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

    public void loadStudents() {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshBtn.setEnabled(false);
        statusLabel.setText("Đang tải danh sách sinh viên từ máy chủ...");

        AsyncWorker.run(
                studentController::listStudents,
                (List<StudentDTO> students) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    allStudents = students != null ? students : new ArrayList<>();
                    applyFilter(false);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    refreshBtn.setEnabled(true);
                    handleError(ex);
                }
        );
    }

    private void loadStudentsSilently() {
        if (isRefreshing) return;
        isRefreshing = true;

        AsyncWorker.run(
                studentController::listStudents,
                (List<StudentDTO> students) -> {
                    isRefreshing = false;
                    allStudents = students != null ? students : new ArrayList<>();
                    applyFilter(true);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                }
        );
    }

    private void applyFilter(boolean silent) {
        Long selectedId = null;
        StudentDTO prev = getSelectedStudent();
        if (prev != null) selectedId = prev.getId();

        String kw = searchField.getText().trim().toLowerCase();
        int statusIdx = statusFilterCombo.getSelectedIndex();

        tableModel.setRowCount(0);
        int count = 0;
        int restoreRow = -1;

        for (StudentDTO s : allStudents) {
            // Status filter
            if (statusIdx == 1 && s.getStatus() != StudentStatus.ACTIVE) continue;
            if (statusIdx == 2 && s.getStatus() != StudentStatus.SUSPENDED) continue;
            if (statusIdx == 3 && s.getStatus() != StudentStatus.GRADUATED) continue;

            // Search filter (StudentCode, FullName, ClassName, Email, Phone, ID)
            if (!kw.isEmpty()) {
                String idStr = s.getId() != null ? s.getId().toString() : "";
                String sc = s.getStudentCode() != null ? s.getStudentCode().toLowerCase() : "";
                String fn = s.getFullName() != null ? s.getFullName().toLowerCase() : "";
                String cn = s.getClassName() != null ? s.getClassName().toLowerCase() : "";
                String em = s.getEmail() != null ? s.getEmail().toLowerCase() : "";
                String ph = s.getPhone() != null ? s.getPhone().toLowerCase() : "";

                if (!idStr.contains(kw) && !sc.contains(kw) && !fn.contains(kw)
                        && !cn.contains(kw) && !em.contains(kw) && !ph.contains(kw)) {
                    continue;
                }
            }

            if (selectedId != null && selectedId.equals(s.getId())) {
                restoreRow = count;
            }

            tableModel.addRow(new Object[]{
                    s.getId(),
                    s.getStudentCode(),
                    s.getFullName(),
                    s.getClassName() != null ? s.getClassName() : "",
                    s.getPhone() != null ? s.getPhone() : "",
                    s.getEmail() != null ? s.getEmail() : "",
                    s.getCurrentBorrowCount() + " / " + s.getMaxBorrowLimit(),
                    mapStudentStatus(s.getStatus())
            });
            count++;
        }

        if (restoreRow != -1) {
            int viewRow = studentTable.convertRowIndexToView(restoreRow);
            studentTable.setRowSelectionInterval(viewRow, viewRow);
            updateStaffDetails();
        } else if (!silent) {
            clearStaffDetails();
        }

        if (count == 0) {
            statusLabel.setText("Không tìm thấy sinh viên nào phù hợp.");
        } else {
            statusLabel.setText(String.format("Đã tải %d sinh viên lúc %s", count, TIME_FMT.format(new Date())));
        }
    }

    private String mapStudentStatus(StudentStatus status) {
        if (status == null) return "Chưa rõ";
        switch (status) {
            case ACTIVE: return "Đang hoạt động";
            case SUSPENDED: return "Tạm khóa";
            case GRADUATED: return "Đã tốt nghiệp";
            default: return status.name();
        }
    }

    private StudentDTO getSelectedStudent() {
        int row = studentTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = studentTable.convertRowIndexToModel(row);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        if (idObj instanceof Long) {
            long targetId = (Long) idObj;
            for (StudentDTO s : allStudents) {
                if (s.getId().equals(targetId)) {
                    return s;
                }
            }
        }
        return null;
    }

    private void updateStaffDetails() {
        StudentDTO s = getSelectedStudent();
        boolean hasSel = (s != null);
        if (editStudentBtn != null) editStudentBtn.setEnabled(hasSel);
        if (suspendStudentBtn != null) suspendStudentBtn.setEnabled(hasSel);
        if (hardDeleteStudentBtn != null) hardDeleteStudentBtn.setEnabled(hasSel);
        if (activationCodeBtn != null) activationCodeBtn.setEnabled(hasSel && s.getUserId() == null);

        if (!hasSel) {
            clearStaffDetails();
            return;
        }

        detailIdVal.setText(s.getId().toString());
        detailCodeVal.setText(s.getStudentCode());
        detailNameVal.setText(s.getFullName());
        detailClassVal.setText(s.getClassName() != null ? s.getClassName() : "Không rõ");
        detailPhoneVal.setText(s.getPhone() != null && !s.getPhone().isEmpty() ? s.getPhone() : "Chưa có");
        detailEmailVal.setText(s.getEmail() != null && !s.getEmail().isEmpty() ? s.getEmail() : "Chưa có");
        detailBorrowVal.setText(String.format("%d cuốn (Hạn mức tối đa: %d)", s.getCurrentBorrowCount(), s.getMaxBorrowLimit()));
        detailStatusVal.setText(mapStudentStatus(s.getStatus()));
    }

    private void clearStaffDetails() {
        detailIdVal.setText("Chưa chọn");
        detailCodeVal.setText("Chưa chọn");
        detailNameVal.setText("Chưa chọn");
        detailClassVal.setText("Chưa chọn");
        detailPhoneVal.setText("Chưa chọn");
        detailEmailVal.setText("Chưa chọn");
        detailBorrowVal.setText("Chưa chọn");
        detailStatusVal.setText("Chưa chọn");
    }

    private void openCreateDialog() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dialog = new StudentFormDialog(owner, networkClient, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudents();
        }
    }

    private void openEditDialog() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một sinh viên để chỉnh sửa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dialog = new StudentFormDialog(owner, networkClient, s);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudents();
        }
    }

    private void suspendSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần tạm khóa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận tạm khóa sinh viên:\n%s (%s)?\n\nThao tác này sẽ chuyển trạng thái của sinh viên sang TẠM KHÓA (SUSPENDED).",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Tạm Khóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            if (suspendStudentBtn != null) suspendStudentBtn.setEnabled(false);
            statusLabel.setText("Đang tạm khóa sinh viên trên máy chủ...");

            AsyncWorker.run(
                    () -> studentController.deleteStudent(s.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã tạm khóa tài khoản sinh viên thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadStudents();
                    },
                    this::handleError
            );
        }
    }

    private void hardDeleteSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần xóa vĩnh viễn.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm1 = JOptionPane.showConfirmDialog(
                this,
                String.format("CẢNH BÁO NGUY HIỂM:\nBạn đang thực hiện XÓA VĨNH VIỄN sinh viên:\n%s (Mã SV: %s, ID: %d)\n\n"
                                + "Thao tác này KHÔNG THỂ HOÀN TÁC! Hồ sơ sinh viên sẽ bị xóa vĩnh viễn khỏi cơ sở dữ liệu.\n"
                                + "(Lưu ý: Nếu sinh viên có sách chưa trả hoặc tiền phạt chưa thanh toán, hệ thống sẽ từ chối xóa).\n\n"
                                + "Bạn có chắc chắn muốn tiếp tục?",
                        s.getFullName(), s.getStudentCode(), s.getId()),
                "Cảnh Báo: Xóa Vĩnh Viễn Sinh Viên",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm1 != JOptionPane.YES_OPTION) {
            return;
        }

        int confirm2 = JOptionPane.showConfirmDialog(
                this,
                String.format("Xác nhận lần 2: Bạn thực sự muốn XÓA VĨNH VIỄN sinh viên %s (%s)?",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Lần Cuối",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE
        );

        if (confirm2 == JOptionPane.YES_OPTION) {
            refreshBtn.setEnabled(false);
            if (hardDeleteStudentBtn != null) hardDeleteStudentBtn.setEnabled(false);
            statusLabel.setText("Đang xóa vĩnh viễn sinh viên ID " + s.getId() + " trên máy chủ...");

            AsyncWorker.run(
                    () -> studentController.hardDeleteStudent(s.getId()),
                    (Boolean ok) -> {
                        refreshBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã xóa vĩnh viễn sinh viên thành công khỏi hệ thống.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadStudents();
                    },
                    this::handleError
            );
        }
    }

    private void generateActivationCodeForSelectedStudent() {
        StudentDTO s = getSelectedStudent();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn sinh viên cần cấp mã kích hoạt.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (s.getUserId() != null) {
            JOptionPane.showMessageDialog(this, "Sinh viên này đã có tài khoản người dùng liên kết trong hệ thống.", "Đã Có Tài Khoản", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Tạo mã kích hoạt tài khoản mới cho sinh viên:\n%s (Mã SV: %s)?\n\nNếu đã có mã kích hoạt trước đó, mã cũ sẽ bị thay thế.",
                        s.getFullName(), s.getStudentCode()),
                "Xác Nhận Cấp Mã Kích Hoạt",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            statusLabel.setText("Đang tạo mã kích hoạt an toàn trên máy chủ...");
            AsyncWorker.run(
                    () -> studentController.generateActivationCode(s.getId()),
                    (String code) -> {
                        statusLabel.setText("Đã cấp mã kích hoạt cho " + s.getStudentCode());
                        JTextField copyField = new JTextField(code);
                        copyField.setEditable(false);
                        copyField.setFont(copyField.getFont().deriveFont(Font.BOLD, 15.0f));
                        copyField.setHorizontalAlignment(JTextField.CENTER);

                        Object[] message = {
                                "Mã kích hoạt tài khoản dành cho sinh viên " + s.getFullName() + ":",
                                copyField,
                                "Hãy cung cấp mã này cho sinh viên để tự đăng ký tài khoản trên ứng dụng."
                        };
                        JOptionPane.showMessageDialog(this, message, "Cấp Mã Kích Hoạt Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    },
                    this::handleError
            );
        }
    }

    public void loadStudentProfile() {
        profCodeVal.setText("Đang tải...");
        profNameVal.setText("Đang tải...");

        AsyncWorker.run(
                studentController::getMyProfile,
                (StudentDTO s) -> {
                    profCodeVal.setText(s.getStudentCode());
                    profNameVal.setText(s.getFullName());
                    profClassVal.setText(s.getClassName() != null ? s.getClassName() : "Không rõ");
                    profPhoneVal.setText(s.getPhone() != null && !s.getPhone().isEmpty() ? s.getPhone() : "Chưa có");
                    profEmailVal.setText(s.getEmail() != null && !s.getEmail().isEmpty() ? s.getEmail() : "Chưa có");
                    profBorrowVal.setText(String.format("%d cuốn sách đang mượn (Hạn mức tối đa: %d)",
                            s.getCurrentBorrowCount(), s.getMaxBorrowLimit()));
                    profStatusVal.setText(mapStudentStatus(s.getStatus()));
                },
                this::handleError
        );
    }

    private void handleError(Exception ex) {
        if (refreshBtn != null) refreshBtn.setEnabled(true);
        if (statusLabel != null) statusLabel.setText("Lỗi: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Student operation error", ex);

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
            JOptionPane.showMessageDialog(this, "Lỗi kết nối mạng: " + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thông Báo Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton activationCodeBtn;
    private javax.swing.JPanel bottomBar;
    private javax.swing.JLabel detailBorrowVal;
    private javax.swing.JPanel detailCard;
    private javax.swing.JLabel detailClassVal;
    private javax.swing.JLabel detailCodeVal;
    private javax.swing.JLabel detailEmailVal;
    private javax.swing.JLabel detailHeader;
    private javax.swing.JLabel detailIdVal;
    private javax.swing.JLabel detailNameVal;
    private javax.swing.JLabel detailPhoneVal;
    private javax.swing.JLabel detailStatusVal;
    private javax.swing.JButton editStudentBtn;
    private javax.swing.JButton filterBtn;
    private javax.swing.JPanel gridDetails;
    private javax.swing.JButton hardDeleteStudentBtn;
    private javax.swing.JLabel lblBorrow;
    private javax.swing.JLabel lblClass;
    private javax.swing.JLabel lblCode;
    private javax.swing.JLabel lblEmail;
    private javax.swing.JLabel lblId;
    private javax.swing.JLabel lblName;
    private javax.swing.JLabel lblPhone;
    private javax.swing.JLabel lblProfBorrow;
    private javax.swing.JLabel lblProfClass;
    private javax.swing.JLabel lblProfCode;
    private javax.swing.JLabel lblProfEmail;
    private javax.swing.JLabel lblProfName;
    private javax.swing.JLabel lblProfPhone;
    private javax.swing.JLabel lblProfStatus;
    private javax.swing.JLabel lblSearch;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JButton newStudentBtn;
    private javax.swing.JLabel profBorrowVal;
    private javax.swing.JPanel profileCard;
    private javax.swing.JPanel profileHeaderPanel;
    private javax.swing.JLabel profileSubtitle;
    private javax.swing.JLabel profileTitle;
    private javax.swing.JLabel profClassVal;
    private javax.swing.JLabel profCodeVal;
    private javax.swing.JLabel profEmailVal;
    private javax.swing.JLabel profNameVal;
    private javax.swing.JLabel profPhoneVal;
    private javax.swing.JLabel profStatusVal;
    private javax.swing.JButton refreshBtn;
    private javax.swing.JButton resetBtn;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JTextField searchField;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JPanel staffPanel;
    private javax.swing.JComboBox<String> statusFilterCombo;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JPanel studentProfilePanel;
    private javax.swing.JTable studentTable;
    private javax.swing.JButton suspendStudentBtn;
    private javax.swing.JScrollPane tableScroll;
    private javax.swing.JPanel topToolbar;
    // End of variables declaration//GEN-END:variables
}
