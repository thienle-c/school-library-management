package thuvien.client.view.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
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

    private JTextField searchField;
    private JComboBox<Object> categoryFilterCombo;
    private JComboBox<String> statusFilterCombo;
    private JButton searchBtn;
    private JButton resetBtn;
    private JButton refreshBtn;
    private JButton newBookBtn;
    private JButton editBookBtn;
    private JButton deleteBookBtn;

    private JTable bookTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    // Detail Panel Components
    private JLabel detailTitleVal;
    private JLabel detailIsbnVal;
    private JLabel detailCategoryVal;
    private JLabel detailAuthorsVal;
    private JLabel detailPublisherVal;
    private JLabel detailYearVal;
    private JLabel detailCopiesVal;
    private JLabel detailShelfVal;
    private JLabel detailStatusVal;

    private List<BookDTO> currentBookList = new ArrayList<>();
    private javax.swing.Timer pollTimer;
    private volatile boolean isRefreshing = false;

    public BookManagementPanel(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.bookController = new ClientBookController(networkClient);

        initUI();
        loadCategories();
        loadBooks();
        initAutoRefresh();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBackground(Color.WHITE);

        // =====================================================================
        // 1. Top Toolbar (Search Bar + Action Buttons)
        // =====================================================================
        JPanel topToolbar = new JPanel(new BorderLayout(10, 0));
        topToolbar.setOpaque(false);
        topToolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Search Section (Left)
        JPanel searchSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchSection.setOpaque(false);

        searchSection.add(new JLabel("Tìm kiếm:"));
        searchField = new JTextField(14);
        searchField.setToolTipText("Tìm kiếm theo tên sách, tác giả, thể loại, ISBN, NXB, vị trí kệ hoặc ID");
        searchField.addActionListener(e -> loadBooks());
        SearchAutoCompleteHelper.attach(searchField, "BOOK", new ClientSearchSuggestionController(networkClient), this::loadBooks);
        searchSection.add(searchField);

        categoryFilterCombo = new JComboBox<>(new Object[]{"Tất cả thể loại"});
        categoryFilterCombo.setToolTipText("Lọc theo thể loại");
        categoryFilterCombo.addActionListener(e -> loadBooks());
        searchSection.add(categoryFilterCombo);

        statusFilterCombo = new JComboBox<>(new String[]{"Tất cả trạng thái", "Còn sách (Có sẵn)", "Hết sách"});
        statusFilterCombo.setToolTipText("Lọc theo tình trạng sách");
        statusFilterCombo.addActionListener(e -> loadBooks());
        searchSection.add(statusFilterCombo);

        searchBtn = new JButton("Tìm kiếm");
        searchBtn.addActionListener(e -> loadBooks());
        searchSection.add(searchBtn);

        resetBtn = new JButton("Xóa bộ lọc");
        resetBtn.addActionListener(e -> {
            searchField.setText("");
            categoryFilterCombo.setSelectedIndex(0);
            statusFilterCombo.setSelectedIndex(0);
            loadBooks();
        });
        searchSection.add(resetBtn);

        refreshBtn = new JButton("Làm mới");
        refreshBtn.addActionListener(e -> loadBooks());
        searchSection.add(refreshBtn);

        topToolbar.add(searchSection, BorderLayout.WEST);

        // Administrative Buttons (Right) - Role Aware
        JPanel actionSection = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionSection.setOpaque(false);

        boolean isStaff = ClientSession.getInstance().isAdmin() || ClientSession.getInstance().isLibrarian();

        if (isStaff) {
            newBookBtn = new JButton("Thêm Sách Mới");
            newBookBtn.setBackground(new Color(230, 245, 230));
            newBookBtn.addActionListener(e -> openCreateDialog());
            actionSection.add(newBookBtn);

            editBookBtn = new JButton("Chỉnh Sửa");
            editBookBtn.setEnabled(false);
            editBookBtn.addActionListener(e -> openEditDialog());
            actionSection.add(editBookBtn);

            deleteBookBtn = new JButton("Xóa Sách");
            deleteBookBtn.setBackground(new Color(255, 235, 235));
            deleteBookBtn.setEnabled(false);
            deleteBookBtn.addActionListener(e -> confirmDelete());
            actionSection.add(deleteBookBtn);
        }

        topToolbar.add(actionSection, BorderLayout.EAST);
        add(topToolbar, BorderLayout.NORTH);

        // =====================================================================
        // 2. Center: Table & Details in JSplitPane
        // =====================================================================
        String[] columns = {"Mã ID", "Mã ISBN", "Tên Sách", "Thể Loại", "Nhà Xuất Bản", "Năm XB", "Tổng Bản", "Có Sẵn", "Trạng Thái"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        bookTable = new JTable(tableModel);
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookTable.setRowHeight(24);
        bookTable.getTableHeader().setFont(bookTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        bookTable.setAutoCreateRowSorter(true);

        // Table column widths
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

        // Double click on row
        bookTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && bookTable.getSelectedRow() != -1) {
                    if (isStaff) {
                        openEditDialog();
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(bookTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));

        // Detail Panel
        JPanel detailCard = createDetailPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailCard);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerSize(6);
        add(splitPane, BorderLayout.CENTER);

        // =====================================================================
        // 3. Bottom Status Bar
        // =====================================================================
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        bottomBar.add(statusLabel, BorderLayout.WEST);

        add(bottomBar, BorderLayout.SOUTH);
    }

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

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JLabel header = new JLabel("Thông Tin Chi Tiết Sách Được Chọn");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13.0f));
        panel.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        detailTitleVal = new JLabel("Chưa chọn");
        detailIsbnVal = new JLabel("Chưa chọn");
        detailCategoryVal = new JLabel("Chưa chọn");
        detailAuthorsVal = new JLabel("Chưa chọn");
        detailPublisherVal = new JLabel("Chưa chọn");
        detailYearVal = new JLabel("Chưa chọn");
        detailCopiesVal = new JLabel("Chưa chọn");
        detailShelfVal = new JLabel("Chưa chọn");
        detailStatusVal = new JLabel("Chưa chọn");

        // Row 0
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        grid.add(new JLabel("Tên sách:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailTitleVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Mã ISBN:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailIsbnVal, gbc);

        // Row 1
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        grid.add(new JLabel("Thể loại:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailCategoryVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Tác giả:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailAuthorsVal, gbc);

        // Row 2
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Nhà xuất bản:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailPublisherVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Năm XB & Tái bản:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailYearVal, gbc);

        // Row 3
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        grid.add(new JLabel("Số lượng bản:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailCopiesVal, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        grid.add(new JLabel("Vị trí kệ:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        grid.add(detailShelfVal, gbc);

        // Row 4
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        grid.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        grid.add(detailStatusVal, gbc);

        panel.add(grid, BorderLayout.CENTER);
        return panel;
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
}
