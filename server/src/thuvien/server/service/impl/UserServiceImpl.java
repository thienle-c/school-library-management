package thuvien.server.service.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
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
            throw new LibraryException("Username is required.");
        }
        if (user.getRole() == null) {
            user.setRole(UserRole.STUDENT);
        }
        if (plainPassword == null || plainPassword.isEmpty()) {
            plainPassword = "password123";
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
}
