package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.ValidationException;
import thuvien.server.repository.UserRepository;
import thuvien.server.repository.impl.UserRepositoryImpl;
import thuvien.server.security.PasswordHasher;
import thuvien.server.service.UserService;

public class UserServiceImpl implements UserService {
    private static final Logger LOGGER = Logger.getLogger(UserServiceImpl.class.getName());

    private final UserRepository userRepository;

    public UserServiceImpl() {
        this(new UserRepositoryImpl());
    }

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDTO getUserById(Long id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("User ID cannot be null.");
        }
        try {
            UserDTO user = userRepository.findById(id);
            if (user == null) {
                throw new EntityNotFoundException("User not found with ID: " + id);
            }
            return user;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving user by ID: " + id, e);
            throw new LibraryException("Database error retrieving user: " + e.getMessage(), e);
        }
    }

    @Override
    public UserDTO getUserByUsername(String username) throws EntityNotFoundException, LibraryException {
        if (username == null || username.trim().isEmpty()) {
            throw new EntityNotFoundException("Username cannot be null or empty.");
        }
        try {
            UserDTO user = userRepository.findByUsername(username.trim());
            if (user == null) {
                throw new EntityNotFoundException("User not found with username: " + username);
            }
            return user;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving user by username: " + username, e);
            throw new LibraryException("Database error retrieving user: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserDTO> getAllUsers() throws LibraryException {
        try {
            return userRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error listing users", e);
            throw new LibraryException("Database error listing users: " + e.getMessage(), e);
        }
    }

    @Override
    public Long createUser(UserDTO user, String plainPassword) throws LibraryException {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new ValidationException("Username is required.");
        }
        if (user.getRole() == null) {
            user.setRole(UserRole.STUDENT);
        }
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new ValidationException("Initial password cannot be empty.");
        }
        if (plainPassword.length() < 6) {
            throw new ValidationException("Initial password must be at least 6 characters long.");
        }
        String passwordHash = PasswordHasher.hash(plainPassword);

        try {
            return userRepository.create(user, passwordHash, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating user: " + user.getUsername(), e);
            throw new LibraryException("Database error creating user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateUser(UserDTO user) throws LibraryException {
        if (user == null || user.getId() == null) {
            throw new LibraryException("User and User ID are required for update.");
        }
        getUserById(user.getId());
        try {
            return userRepository.update(user, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating user ID: " + user.getId(), e);
            throw new LibraryException("Database error updating user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteUser(Long id) throws LibraryException {
        if (id == null) {
            throw new LibraryException("User ID is required for deletion.");
        }
        getUserById(id);
        try {
            return userRepository.delete(id, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error deleting user ID: " + id, e);
            throw new LibraryException("Database error deleting user: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean changePassword(Long userId, String currentPassword, String newPassword)
            throws thuvien.common.exception.ValidationException, EntityNotFoundException, LibraryException {
        if (userId == null) {
            throw new thuvien.common.exception.ValidationException("Mã ID người dùng không được để trống.");
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new thuvien.common.exception.ValidationException("Vui lòng nhập mật khẩu hiện tại.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new thuvien.common.exception.ValidationException("Mật khẩu mới phải có ít nhất 6 ký tự.");
        }

        UserDTO user = getUserById(userId);
        try {
            String currentHash = userRepository.getPasswordHash(user.getUsername());
            if (currentHash == null || !PasswordHasher.verify(currentPassword, currentHash)) {
                throw new thuvien.common.exception.ValidationException("Mật khẩu hiện tại không chính xác.");
            }

            String newHash = PasswordHasher.hash(newPassword);
            return userRepository.updatePassword(userId, newHash, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error changing password for user ID: " + userId, e);
            throw new LibraryException("Lỗi cơ sở dữ liệu khi đổi mật khẩu: " + e.getMessage(), e);
        }
    }

    @Override
    public thuvien.common.dto.AdminResetPasswordResponseDTO adminResetPassword(Long adminUserId, Long targetUserId)
            throws thuvien.common.exception.ValidationException, EntityNotFoundException, LibraryException {
        if (adminUserId == null) {
            throw new thuvien.common.exception.ValidationException("Mã ID của quản trị viên không hợp lệ.");
        }
        if (targetUserId == null) {
            throw new thuvien.common.exception.ValidationException("Mã ID người dùng mục tiêu không được để trống.");
        }
        if (adminUserId.equals(targetUserId)) {
            throw new thuvien.common.exception.ValidationException("Không thể tự đặt lại mật khẩu của chính mình bằng chức năng quản trị. Vui lòng sử dụng chức năng Đổi Mật Khẩu cá nhân.");
        }

        UserDTO targetUser = getUserById(targetUserId);
        if (targetUser == null) {
            throw new EntityNotFoundException("Không tìm thấy người dùng với ID: " + targetUserId);
        }
        if (!targetUser.isActive()) {
            throw new thuvien.common.exception.ValidationException("Không thể đặt lại mật khẩu cho tài khoản đã bị vô hiệu hóa.");
        }

        String tempPassword = thuvien.server.security.SecurePasswordGenerator.generateDefault();
        String newPasswordHash = PasswordHasher.hash(tempPassword);

        try (java.sql.Connection conn = thuvien.server.database.DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean updated = userRepository.resetPassword(targetUserId, newPasswordHash, true, conn);
                if (!updated) {
                    throw new LibraryException("Không thể cập nhật mật khẩu cho người dùng ID: " + targetUserId);
                }
                conn.commit();
                LOGGER.info("Admin ID " + adminUserId + " successfully reset temporary password for user ID " + targetUserId);
            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Transaction rollback during admin password reset for target ID: " + targetUserId, e);
                if (e instanceof LibraryException) throw (LibraryException) e;
                throw new LibraryException("Lỗi giao dịch khi cập nhật mật khẩu: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection error during admin password reset: " + e.getMessage(), e);
            throw new LibraryException("Lỗi kết nối cơ sở dữ liệu khi đặt lại mật khẩu: " + e.getMessage(), e);
        }

        return new thuvien.common.dto.AdminResetPasswordResponseDTO(targetUserId, targetUser.getUsername(), tempPassword, true);
    }
}
