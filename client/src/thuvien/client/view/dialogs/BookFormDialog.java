package thuvien.client.view.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import thuvien.client.controller.ClientBookController;
import thuvien.client.network.NetworkClient;
import thuvien.client.view.common.AsyncWorker;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.enums.BookStatus;

/**
 * Modal dialog for creating and editing Book entries.
 */
public class BookFormDialog extends JDialog {
    private final ClientBookController bookController;
    private final BookDTO bookToEdit;
    private boolean saved = false;

    private JTextField titleField;
    private JTextField isbnField;
    private JComboBox<CategoryDTO> categoryCombo;
    private JButton retryCategoryBtn;
    private JTextField publisherField;
    private JSpinner yearSpinner;
    private JTextField editionField;
    private JSpinner totalCopiesSpinner;
    private JSpinner availableCopiesSpinner;
    private JTextField shelfLocationField;
    private JComboBox<BookStatus> statusCombo;

    private JButton saveButton;
    private JButton cancelButton;
    private JLabel statusLabel;

    public BookFormDialog(Window owner, NetworkClient networkClient, BookDTO bookToEdit) {
        super(owner, bookToEdit == null ? "Thêm Sách Mới" : "Chỉnh Sửa Sách (ID: " + bookToEdit.getId() + ")", ModalityType.APPLICATION_MODAL);
        this.bookController = new ClientBookController(networkClient);
        this.bookToEdit = bookToEdit;

        initUI();
        loadCategories();
    }

    private void initUI() {
        setSize(560, 540);
        setLocationRelativeTo(getOwner());
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Title
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tên sách:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        titleField = new JTextField(bookToEdit != null ? bookToEdit.getTitle() : "", 20);
        formPanel.add(titleField, gbc);
        row++;

        // ISBN
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mã ISBN:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        isbnField = new JTextField(bookToEdit != null ? bookToEdit.getIsbn() : "", 20);
        formPanel.add(isbnField, gbc);
        row++;

        // Category with retry button
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Thể loại:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel catPanel = new JPanel(new BorderLayout(6, 0));
        catPanel.setOpaque(false);
        categoryCombo = new JComboBox<>();
        categoryCombo.setEnabled(false);
        retryCategoryBtn = new JButton("Thử lại");
        retryCategoryBtn.setToolTipText("Tải lại danh sách thể loại từ máy chủ");
        retryCategoryBtn.addActionListener(e -> loadCategories());
        catPanel.add(categoryCombo, BorderLayout.CENTER);
        catPanel.add(retryCategoryBtn, BorderLayout.EAST);
        formPanel.add(catPanel, gbc);
        row++;

        // Publisher
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Nhà xuất bản:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        publisherField = new JTextField(bookToEdit != null && bookToEdit.getPublisher() != null ? bookToEdit.getPublisher() : "", 20);
        formPanel.add(publisherField, gbc);
        row++;

        // Publish Year
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Năm xuất bản:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        int initialYear = bookToEdit != null && bookToEdit.getPublishYear() != null ? bookToEdit.getPublishYear() : 2024;
        yearSpinner = new JSpinner(new SpinnerNumberModel(initialYear, 1000, 2100, 1));
        formPanel.add(yearSpinner, gbc);
        row++;

        // Edition
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tái bản / Phiên bản:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        editionField = new JTextField(bookToEdit != null && bookToEdit.getEdition() != null ? bookToEdit.getEdition() : "", 20);
        formPanel.add(editionField, gbc);
        row++;

        // Total Copies
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tổng số bản:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        int totalCopies = bookToEdit != null ? bookToEdit.getTotalCopies() : 5;
        totalCopiesSpinner = new JSpinner(new SpinnerNumberModel(totalCopies, 1, 9999, 1));
        formPanel.add(totalCopiesSpinner, gbc);
        row++;

        // Available Copies
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Bản hiện có sẵn:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        int availCopies = bookToEdit != null ? bookToEdit.getAvailableCopies() : 5;
        availableCopiesSpinner = new JSpinner(new SpinnerNumberModel(availCopies, 0, 9999, 1));
        formPanel.add(availableCopiesSpinner, gbc);
        row++;

        // Shelf Location
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Vị trí kệ:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        shelfLocationField = new JTextField(bookToEdit != null && bookToEdit.getShelfLocation() != null ? bookToEdit.getShelfLocation() : "", 20);
        formPanel.add(shelfLocationField, gbc);
        row++;

        // Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Trạng thái:*"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        statusCombo = new JComboBox<>(BookStatus.values());
        if (bookToEdit != null && bookToEdit.getStatus() != null) {
            statusCombo.setSelectedItem(bookToEdit.getStatus());
        } else {
            statusCombo.setSelectedItem(BookStatus.AVAILABLE);
        }
        formPanel.add(statusCombo, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Bottom area (Status label + Action Buttons)
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel(" ", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11.5f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        cancelButton = new JButton("Hủy bỏ");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        saveButton = new JButton(bookToEdit == null ? "Tạo Sách Mới" : "Lưu Thay Đổi");
        saveButton.setPreferredSize(new Dimension(130, 30));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));
        saveButton.addActionListener(e -> saveBook());
        buttonPanel.add(saveButton);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(saveButton);
    }

    private void loadCategories() {
        statusLabel.setText("Đang tải danh sách thể loại từ máy chủ...");
        if (retryCategoryBtn != null) retryCategoryBtn.setEnabled(false);
        categoryCombo.setEnabled(false);

        AsyncWorker.run(
                bookController::listCategories,
                (List<CategoryDTO> categories) -> {
                    categoryCombo.removeAllItems();
                    CategoryDTO selected = null;
                    for (CategoryDTO cat : categories) {
                        categoryCombo.addItem(cat);
                        if (bookToEdit != null && cat.getId().equals(bookToEdit.getCategoryId())) {
                            selected = cat;
                        }
                    }
                    if (selected != null) {
                        categoryCombo.setSelectedItem(selected);
                    }
                    categoryCombo.setEnabled(true);
                    if (retryCategoryBtn != null) retryCategoryBtn.setEnabled(true);
                    statusLabel.setText("Sẵn sàng");
                },
                (Exception ex) -> {
                    categoryCombo.setEnabled(false);
                    if (retryCategoryBtn != null) retryCategoryBtn.setEnabled(true);
                    statusLabel.setText("Lỗi tải thể loại: " + ex.getMessage() + " (Vui lòng bấm 'Thử lại')");
                }
        );
    }

    private void saveBook() {
        String title = titleField.getText().trim();
        String isbn = isbnField.getText().trim();
        CategoryDTO selectedCategory = (CategoryDTO) categoryCombo.getSelectedItem();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên sách.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            titleField.requestFocusInWindow();
            return;
        }

        if (isbn.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mã ISBN.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            isbnField.requestFocusInWindow();
            return;
        }

        if (selectedCategory == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thể loại hợp lệ. (Nhấn 'Thử lại' nếu chưa tải được thể loại)", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int totalCopies = (Integer) totalCopiesSpinner.getValue();
        int availableCopies = (Integer) availableCopiesSpinner.getValue();

        if (totalCopies < 0) {
            JOptionPane.showMessageDialog(this, "Tổng số bản không được âm.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (availableCopies > totalCopies) {
            JOptionPane.showMessageDialog(this, "Số bản có sẵn không được lớn hơn tổng số bản.", "Lỗi Xác Thực", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BookDTO book = (bookToEdit != null) ? bookToEdit : new BookDTO();
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setCategoryId(selectedCategory.getId());
        book.setCategoryName(selectedCategory.getName());
        book.setPublisher(publisherField.getText().trim());
        book.setPublishYear((Integer) yearSpinner.getValue());
        book.setEdition(editionField.getText().trim());
        book.setShelfLocation(shelfLocationField.getText().trim());
        book.setTotalCopies(totalCopies);
        book.setAvailableCopies(availableCopies);
        book.setStatus((BookStatus) statusCombo.getSelectedItem());

        // Set busy state
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setText("Đang lưu sách lên máy chủ...");

        if (bookToEdit == null) {
            // Create Book
            AsyncWorker.run(
                    () -> bookController.createBook(book),
                    (Long newId) -> {
                        saved = true;
                        JOptionPane.showMessageDialog(this, "Thêm sách thành công! Mã sách (ID): " + newId, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    },
                    (Exception ex) -> {
                        saveButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        statusLabel.setText("Lưu thất bại.");
                        JOptionPane.showMessageDialog(this, "Thêm sách thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
            );
        } else {
            // Update Book
            AsyncWorker.run(
                    () -> bookController.updateBook(book),
                    (Boolean ok) -> {
                        saved = true;
                        JOptionPane.showMessageDialog(this, "Cập nhật sách thành công!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    },
                    (Exception ex) -> {
                        saveButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        statusLabel.setText("Cập nhật thất bại.");
                        JOptionPane.showMessageDialog(this, "Cập nhật sách thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
            );
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
