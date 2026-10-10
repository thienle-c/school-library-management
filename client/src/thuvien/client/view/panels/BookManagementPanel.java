package thuvien.client.view.panels;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import thuvien.client.controller.ClientBookController;
import thuvien.client.controller.ClientSearchSuggestionController;
import thuvien.client.network.NetworkClient;
import thuvien.client.session.ClientSession;
import thuvien.client.view.LoginForm;
import thuvien.client.view.common.AsyncWorker;
import thuvien.client.view.common.SearchAutoCompleteHelper;
import thuvien.client.view.dialogs.BookFormDialog;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.BookSearchCriteriaDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.PageResponseDTO;
import thuvien.common.enums.BookStatus;
import thuvien.common.exception.AuthenticationException;
import thuvien.common.exception.AuthorizationException;
import thuvien.common.exception.NetworkException;

/**
 * Panel providing book catalog browsing, searching, and administrative management.
 */
public class BookManagementPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(BookManagementPanel.class.getName());
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    private final NetworkClient networkClient;
    private final ClientBookController bookController;

    private DefaultTableModel tableModel;
    private List<BookDTO> currentBookList = new ArrayList<>();
    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public BookManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.bookController = new ClientBookController(networkClient);

        initComponents();
        initCustom();
        loadCategories();
        loadBooks();
        initAutoRefresh();
    }

    private void initCustom() {
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));
        detailCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        boolean isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();
        if (!isStaff) {
            newBookBtn.setVisible(false);
            editBookBtn.setVisible(false);
            deleteBookBtn.setVisible(false);
        }

        SearchAutoCompleteHelper.attach(searchField, "BOOK", new ClientSearchSuggestionController(networkClient), this::loadBooks);

        String[] columns = {"Mã ID", "Mã ISBN", "Tên Sách", "Thể Loại", "Nhà Xuất Bản", "Năm XB", "Tổng Bản", "Có Sẵn", "Trạng Thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        bookTable.setModel(tableModel);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookTable.setRowHeight(24);
        bookTable.getTableHeader().setFont(bookTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        bookTable.setAutoCreateRowSorter(true);

        if (bookTable.getColumnModel().getColumnCount() >= 9) {
            bookTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            bookTable.getColumnModel().getColumn(1).setPreferredWidth(120);
            bookTable.getColumnModel().getColumn(2).setPreferredWidth(230);
            bookTable.getColumnModel().getColumn(3).setPreferredWidth(110);
            bookTable.getColumnModel().getColumn(4).setPreferredWidth(120);
            bookTable.getColumnModel().getColumn(5).setPreferredWidth(60);
            bookTable.getColumnModel().getColumn(6).setPreferredWidth(65);
            bookTable.getColumnModel().getColumn(7).setPreferredWidth(65);
            bookTable.getColumnModel().getColumn(8).setPreferredWidth(95);
        }

        bookTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailPanel();
            }
        });

        bookTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && bookTable.getSelectedRow() != -1) {
                    if (ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian()) {
                        openEditDialog();
                    }
                }
            }
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
        java.awt.GridBagConstraints gridBagConstraints;

        topToolbar = new javax.swing.JPanel();
        searchSection = new javax.swing.JPanel();
        lblSearch = new javax.swing.JLabel();
        searchField = new javax.swing.JTextField();
        categoryFilterCombo = new javax.swing.JComboBox<Object>();
        statusFilterCombo = new javax.swing.JComboBox<String>();
        searchBtn = new javax.swing.JButton();
        resetBtn = new javax.swing.JButton();
        refreshBtn = new javax.swing.JButton();
        actionSection = new javax.swing.JPanel();
        newBookBtn = new javax.swing.JButton();
        editBookBtn = new javax.swing.JButton();
        deleteBookBtn = new javax.swing.JButton();
        splitPane = new javax.swing.JSplitPane();
        tableScroll = new javax.swing.JScrollPane();
        bookTable = new javax.swing.JTable();
        detailCard = new javax.swing.JPanel();
        detailHeader = new javax.swing.JLabel();
        gridDetails = new javax.swing.JPanel();
        lblTitle = new javax.swing.JLabel();
        detailTitleVal = new javax.swing.JLabel();
        lblIsbn = new javax.swing.JLabel();
        detailIsbnVal = new javax.swing.JLabel();
        lblCategory = new javax.swing.JLabel();
        detailCategoryVal = new javax.swing.JLabel();
        lblAuthors = new javax.swing.JLabel();
        detailAuthorsVal = new javax.swing.JLabel();
        lblPublisher = new javax.swing.JLabel();
        detailPublisherVal = new javax.swing.JLabel();
        lblYear = new javax.swing.JLabel();
        detailYearVal = new javax.swing.JLabel();
        lblCopies = new javax.swing.JLabel();
        detailCopiesVal = new javax.swing.JLabel();
        lblShelf = new javax.swing.JLabel();
        detailShelfVal = new javax.swing.JLabel();
        lblStatus = new javax.swing.JLabel();
        detailStatusVal = new javax.swing.JLabel();
        bottomBar = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 255));
        setLayout(new java.awt.BorderLayout(5, 5));

        topToolbar.setOpaque(false);
        topToolbar.setLayout(new java.awt.BorderLayout(10, 0));

        searchSection.setOpaque(false);
        searchSection.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));

        lblSearch.setText("Tìm kiếm:");
        searchSection.add(lblSearch);

        searchField.setColumns(14);
        searchField.setToolTipText("Tìm kiếm theo tên sách, tác giả, thể loại, ISBN, NXB, vị trí kệ hoặc ID");
        searchField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchFieldActionPerformed(evt);
            }
        });
        searchSection.add(searchField);

        categoryFilterCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Tất cả thể loại" }));
        categoryFilterCombo.setToolTipText("Lọc theo thể loại");
        categoryFilterCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                categoryFilterComboActionPerformed(evt);
            }
        });
        searchSection.add(categoryFilterCombo);

        statusFilterCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Tất cả trạng thái", "Còn sách (Có sẵn)", "Hết sách" }));
        statusFilterCombo.setToolTipText("Lọc theo tình trạng sách");
        statusFilterCombo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                statusFilterComboActionPerformed(evt);
            }
        });
        searchSection.add(statusFilterCombo);

        searchBtn.setText("Tìm kiếm");
        searchBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchBtnActionPerformed(evt);
            }
        });
        searchSection.add(searchBtn);

        resetBtn.setText("Xóa bộ lọc");
        resetBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetBtnActionPerformed(evt);
            }
        });
        searchSection.add(resetBtn);

        refreshBtn.setText("Làm mới");
        refreshBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshBtnActionPerformed(evt);
            }
        });
        searchSection.add(refreshBtn);

        topToolbar.add(searchSection, java.awt.BorderLayout.WEST);

        actionSection.setOpaque(false);
        actionSection.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));

        newBookBtn.setBackground(new java.awt.Color(230, 245, 230));
        newBookBtn.setText("Thêm Sách Mới");
        newBookBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newBookBtnActionPerformed(evt);
            }
        });
        actionSection.add(newBookBtn);

        editBookBtn.setText("Chỉnh Sửa");
        editBookBtn.setEnabled(false);
        editBookBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editBookBtnActionPerformed(evt);
            }
        });
        actionSection.add(editBookBtn);

        deleteBookBtn.setBackground(new java.awt.Color(255, 235, 235));
        deleteBookBtn.setText("Xóa Sách");
        deleteBookBtn.setEnabled(false);
        deleteBookBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteBookBtnActionPerformed(evt);
            }
        });
        actionSection.add(deleteBookBtn);

        topToolbar.add(actionSection, java.awt.BorderLayout.EAST);

        add(topToolbar, java.awt.BorderLayout.NORTH);

        splitPane.setDividerSize(6);
        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.65);

        bookTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Mã ID", "Mã ISBN", "Tên Sách", "Thể Loại", "Nhà Xuất Bản", "Năm XB", "Tổng Bản", "Có Sẵn", "Trạng Thái"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        bookTable.setAutoCreateRowSorter(true);
        bookTable.setRowHeight(24);
        bookTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tableScroll.setViewportView(bookTable);

        splitPane.setTopComponent(tableScroll);

        detailCard.setBackground(new java.awt.Color(248, 250, 252));
        detailCard.setLayout(new java.awt.BorderLayout(10, 10));

        detailHeader.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        detailHeader.setText("Thông Tin Chi Tiết Sách Được Chọn");
        detailCard.add(detailHeader, java.awt.BorderLayout.NORTH);

        gridDetails.setOpaque(false);
        gridDetails.setLayout(new java.awt.GridBagLayout());

        lblTitle.setText("Tên sách:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblTitle, gridBagConstraints);

        detailTitleVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailTitleVal, gridBagConstraints);

        lblIsbn.setText("Mã ISBN:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblIsbn, gridBagConstraints);

        detailIsbnVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailIsbnVal, gridBagConstraints);

        lblCategory.setText("Thể loại:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblCategory, gridBagConstraints);

        detailCategoryVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailCategoryVal, gridBagConstraints);

        lblAuthors.setText("Tác giả:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblAuthors, gridBagConstraints);

        detailAuthorsVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailAuthorsVal, gridBagConstraints);

        lblPublisher.setText("Nhà xuất bản:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblPublisher, gridBagConstraints);

        detailPublisherVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailPublisherVal, gridBagConstraints);

        lblYear.setText("Năm XB & Tái bản:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblYear, gridBagConstraints);

        detailYearVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailYearVal, gridBagConstraints);

        lblCopies.setText("Số lượng bản:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblCopies, gridBagConstraints);

        detailCopiesVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailCopiesVal, gridBagConstraints);

        lblShelf.setText("Vị trí kệ:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblShelf, gridBagConstraints);

        detailShelfVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailShelfVal, gridBagConstraints);

        lblStatus.setText("Trạng thái:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(lblStatus, gridBagConstraints);

        detailStatusVal.setText("Chưa chọn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 8);
        gridDetails.add(detailStatusVal, gridBagConstraints);

        detailCard.add(gridDetails, java.awt.BorderLayout.CENTER);

        splitPane.setBottomComponent(detailCard);

        add(splitPane, java.awt.BorderLayout.CENTER);

        bottomBar.setOpaque(false);
        bottomBar.setLayout(new java.awt.BorderLayout());

        statusLabel.setFont(new java.awt.Font("Segoe UI", 0, 12)); // NOI18N
        statusLabel.setText("Sẵn sàng");
        bottomBar.add(statusLabel, java.awt.BorderLayout.WEST);

        add(bottomBar, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void searchFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchFieldActionPerformed
        loadBooks();
    }//GEN-LAST:event_searchFieldActionPerformed

    private void categoryFilterComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_categoryFilterComboActionPerformed
        loadBooks();
    }//GEN-LAST:event_categoryFilterComboActionPerformed

    private void statusFilterComboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_statusFilterComboActionPerformed
        loadBooks();
    }//GEN-LAST:event_statusFilterComboActionPerformed

    private void searchBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchBtnActionPerformed
        loadBooks();
    }//GEN-LAST:event_searchBtnActionPerformed

    private void resetBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBtnActionPerformed
        searchField.setText("");
        categoryFilterCombo.setSelectedIndex(0);
        statusFilterCombo.setSelectedIndex(0);
        loadBooks();
    }//GEN-LAST:event_resetBtnActionPerformed

    private void refreshBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshBtnActionPerformed
        loadBooks();
    }//GEN-LAST:event_refreshBtnActionPerformed

    private void newBookBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newBookBtnActionPerformed
        openCreateDialog();
    }//GEN-LAST:event_newBookBtnActionPerformed

    private void editBookBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editBookBtnActionPerformed
        openEditDialog();
    }//GEN-LAST:event_editBookBtnActionPerformed

    private void deleteBookBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBookBtnActionPerformed
        confirmDelete();
    }//GEN-LAST:event_deleteBookBtnActionPerformed

    private void initAutoRefresh() {
        pollTimer = new javax.swing.Timer(6000, e -> {
            if (isShowing() && isDisplayable() && !isRefreshing) {
                loadBooksSilently();
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

    private void loadCategories() {
        AsyncWorker.run(
                bookController::listCategories,
                (List<CategoryDTO> categories) -> {
                    if (categoryFilterCombo != null && categories != null) {
                        Object prevSel = categoryFilterCombo.getSelectedItem();
                        categoryFilterCombo.removeAllItems();
                        categoryFilterCombo.addItem("Tất cả thể loại");
                        for (CategoryDTO c : categories) {
                            categoryFilterCombo.addItem(c);
                        }
                        if (prevSel != null) {
                            categoryFilterCombo.setSelectedItem(prevSel);
                        }
                    }
                },
                (Exception ex) -> LOGGER.log(Level.FINE, "Failed loading categories for filter", ex)
        );
    }

    private void setControlsEnabled(boolean enabled) {
        searchBtn.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        refreshBtn.setEnabled(enabled);
        if (newBookBtn != null) newBookBtn.setEnabled(enabled);
        if (editBookBtn != null) editBookBtn.setEnabled(enabled && bookTable.getSelectedRow() != -1);
        if (deleteBookBtn != null) deleteBookBtn.setEnabled(enabled && bookTable.getSelectedRow() != -1);
    }

    private BookSearchCriteriaDTO buildCriteria() {
        String keyword = searchField.getText().trim();
        BookSearchCriteriaDTO criteria = new BookSearchCriteriaDTO();
        if (!keyword.isEmpty()) {
            criteria.setKeyword(keyword);
        }

        Object catSel = categoryFilterCombo.getSelectedItem();
        if (catSel instanceof CategoryDTO) {
            criteria.setCategoryId(((CategoryDTO) catSel).getId());
        }

        int statusIdx = statusFilterCombo.getSelectedIndex();
        if (statusIdx == 1) {
            criteria.setStatus(BookStatus.AVAILABLE);
        }

        criteria.setPage(1);
        criteria.setPageSize(200);
        return criteria;
    }

    public void loadBooks() {
        if (isRefreshing) return;
        isRefreshing = true;
        setControlsEnabled(false);
        statusLabel.setText("Đang tải dữ liệu sách từ máy chủ...");

        BookSearchCriteriaDTO criteria = buildCriteria();

        AsyncWorker.run(
                () -> bookController.searchBooks(criteria),
                (PageResponseDTO<BookDTO> page) -> {
                    isRefreshing = false;
                    setControlsEnabled(true);
                    populateTable(page.getItems(), false);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                    setControlsEnabled(true);
                    handleError(ex);
                }
        );
    }

    private void loadBooksSilently() {
        if (isRefreshing) return;
        isRefreshing = true;

        BookSearchCriteriaDTO criteria = buildCriteria();

        AsyncWorker.run(
                () -> bookController.searchBooks(criteria),
                (PageResponseDTO<BookDTO> page) -> {
                    isRefreshing = false;
                    populateTable(page.getItems(), true);
                },
                (Exception ex) -> {
                    isRefreshing = false;
                }
        );
    }

    private void populateTable(List<BookDTO> books, boolean silent) {
        Long selectedId = null;
        BookDTO prevSel = getSelectedBook();
        if (prevSel != null) {
            selectedId = prevSel.getId();
        }

        currentBookList = books != null ? books : new ArrayList<>();
        tableModel.setRowCount(0);

        if (currentBookList.isEmpty()) {
            statusLabel.setText("Không tìm thấy cuốn sách nào phù hợp.");
            if (!silent) clearDetailPanel();
            return;
        }

        int restoreRow = -1;
        for (int i = 0; i < currentBookList.size(); i++) {
            BookDTO b = currentBookList.get(i);
            if (selectedId != null && selectedId.equals(b.getId())) {
                restoreRow = i;
            }
            tableModel.addRow(new Object[]{
                    b.getId(),
                    b.getIsbn(),
                    b.getTitle(),
                    b.getCategoryName() != null ? b.getCategoryName() : "Chưa phân loại",
                    b.getPublisher() != null ? b.getPublisher() : "",
                    b.getPublishYear() != null ? b.getPublishYear() : "",
                    b.getTotalCopies(),
                    b.getAvailableCopies(),
                    mapBookStatus(b.getStatus())
            });
        }

        if (restoreRow != -1) {
            int viewRow = bookTable.convertRowIndexToView(restoreRow);
            bookTable.setRowSelectionInterval(viewRow, viewRow);
            updateDetailPanel();
        } else if (!silent) {
            clearDetailPanel();
        }

        statusLabel.setText(String.format("Đã tải %d cuốn sách lúc %s", currentBookList.size(), TIME_FMT.format(new Date())));
    }

    private String mapBookStatus(BookStatus status) {
        if (status == null) return "Chưa rõ";
        switch (status) {
            case AVAILABLE: return "Có sẵn";
            case BORROWED: return "Đang mượn hết";
            case RESERVED: return "Đã đặt trước";
            case LOST: return "Bị thất lạc";
            default: return status.name();
        }
    }

    private BookDTO getSelectedBook() {
        int row = bookTable.getSelectedRow();
        if (row == -1) return null;
        int modelRow = bookTable.convertRowIndexToModel(row);
        if (modelRow >= 0 && modelRow < currentBookList.size()) {
            return currentBookList.get(modelRow);
        }
        return null;
    }

    private void updateDetailPanel() {
        BookDTO b = getSelectedBook();
        boolean hasSelection = (b != null);

        if (editBookBtn != null) editBookBtn.setEnabled(hasSelection);
        if (deleteBookBtn != null) deleteBookBtn.setEnabled(hasSelection);

        if (!hasSelection) {
            clearDetailPanel();
            return;
        }

        detailTitleVal.setText(b.getTitle());
        detailIsbnVal.setText(b.getIsbn());
        detailCategoryVal.setText(b.getCategoryName() != null ? b.getCategoryName() : "Chưa phân loại");

        if (b.getAuthors() != null && !b.getAuthors().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < b.getAuthors().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(b.getAuthors().get(i).getName());
            }
            detailAuthorsVal.setText(sb.toString());
        } else {
            detailAuthorsVal.setText("Không có thông tin");
        }

        detailPublisherVal.setText(b.getPublisher() != null && !b.getPublisher().isEmpty() ? b.getPublisher() : "Không rõ");
        String yr = (b.getPublishYear() != null ? b.getPublishYear().toString() : "Không rõ");
        String ed = (b.getEdition() != null && !b.getEdition().isEmpty() ? " (Tái bản: " + b.getEdition() + ")" : "");
        detailYearVal.setText(yr + ed);

        detailCopiesVal.setText(String.format("%d bản có sẵn / %d tổng số bản", b.getAvailableCopies(), b.getTotalCopies()));
        detailShelfVal.setText(b.getShelfLocation() != null && !b.getShelfLocation().isEmpty() ? b.getShelfLocation() : "Chưa xếp kệ");
        detailStatusVal.setText(mapBookStatus(b.getStatus()));
    }

    private void clearDetailPanel() {
        detailTitleVal.setText("Chưa chọn");
        detailIsbnVal.setText("Chưa chọn");
        detailCategoryVal.setText("Chưa chọn");
        detailAuthorsVal.setText("Chưa chọn");
        detailPublisherVal.setText("Chưa chọn");
        detailYearVal.setText("Chưa chọn");
        detailCopiesVal.setText("Chưa chọn");
        detailShelfVal.setText("Chưa chọn");
        detailStatusVal.setText("Chưa chọn");
    }

    private void openCreateDialog() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        BookFormDialog dialog = new BookFormDialog(owner, networkClient, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadBooks();
        }
    }

    private void openEditDialog() {
        BookDTO selected = getSelectedBook();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một cuốn sách cần sửa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        BookFormDialog dialog = new BookFormDialog(owner, networkClient, selected);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadBooks();
        }
    }

    private void confirmDelete() {
        BookDTO selected = getSelectedBook();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một cuốn sách cần xóa.", "Yêu Cầu Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("Bạn có chắc chắn muốn xóa cuốn sách:\n'%s' (ISBN: %s)?\n\nThao tác này không thể hoàn tác.",
                        selected.getTitle(), selected.getIsbn()),
                "Xác Nhận Xóa Sách",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            setControlsEnabled(false);
            statusLabel.setText("Đang xóa sách mã " + selected.getId() + " trên máy chủ...");

            AsyncWorker.run(
                    () -> bookController.deleteBook(selected.getId()),
                    (Boolean ok) -> {
                        setControlsEnabled(true);
                        JOptionPane.showMessageDialog(this, "Đã xóa sách thành công.", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        loadBooks();
                    },
                    this::handleError
            );
        }
    }

    private void handleError(Exception ex) {
        setControlsEnabled(true);
        statusLabel.setText("Lỗi thao tác: " + ex.getMessage());
        LOGGER.log(Level.WARNING, "Book operation error", ex);

        if (ex instanceof AuthenticationException) {
            String msg = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
            if (ex.getMessage() != null && ex.getMessage().contains("tạm khóa")) {
                msg = "Tài khoản của bạn đang bị tạm khóa do đăng nhập sai quá số lần cho phép. Vui lòng thử lại sau 15 phút.";
            }
            JOptionPane.showMessageDialog(this,
                    msg,
                    "Hết Hạn Phiên",
                    JOptionPane.ERROR_MESSAGE);
            java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
            if (owner != null) {
                owner.dispose();
            }
            SwingUtilities.invokeLater(() -> new LoginForm(networkClient).setVisible(true));
        } else if (ex instanceof AuthorizationException) {
            JOptionPane.showMessageDialog(this,
                    "Từ chối quyền truy cập: " + ex.getMessage(),
                    "Lỗi Phân Quyền",
                    JOptionPane.ERROR_MESSAGE);
        } else if (ex instanceof NetworkException) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi kết nối mạng đến máy chủ: " + ex.getMessage(),
                    "Lỗi Kết Nối",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Thao tác thất bại: " + ex.getMessage(),
                    "Lỗi Thư Viện",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionSection;
    private javax.swing.JTable bookTable;
    private javax.swing.JPanel bottomBar;
    private javax.swing.JComboBox<Object> categoryFilterCombo;
    private javax.swing.JButton deleteBookBtn;
    private javax.swing.JLabel detailAuthorsVal;
    private javax.swing.JPanel detailCard;
    private javax.swing.JLabel detailCategoryVal;
    private javax.swing.JLabel detailCopiesVal;
    private javax.swing.JLabel detailHeader;
    private javax.swing.JLabel detailIsbnVal;
    private javax.swing.JLabel detailPublisherVal;
    private javax.swing.JLabel detailShelfVal;
    private javax.swing.JLabel detailStatusVal;
    private javax.swing.JLabel detailTitleVal;
    private javax.swing.JLabel detailYearVal;
    private javax.swing.JButton editBookBtn;
    private javax.swing.JPanel gridDetails;
    private javax.swing.JLabel lblAuthors;
    private javax.swing.JLabel lblCategory;
    private javax.swing.JLabel lblCopies;
    private javax.swing.JLabel lblIsbn;
    private javax.swing.JLabel lblPublisher;
    private javax.swing.JLabel lblSearch;
    private javax.swing.JLabel lblShelf;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblYear;
    private javax.swing.JButton newBookBtn;
    private javax.swing.JButton refreshBtn;
    private javax.swing.JButton resetBtn;
    private javax.swing.JButton searchBtn;
    private javax.swing.JTextField searchField;
    private javax.swing.JPanel searchSection;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JComboBox<String> statusFilterCombo;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JScrollPane tableScroll;
    private javax.swing.JPanel topToolbar;
    // End of variables declaration//GEN-END:variables
}
