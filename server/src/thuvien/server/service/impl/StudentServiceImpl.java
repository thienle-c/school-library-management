package thuvien.server.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import thuvien.common.dto.StudentDTO;
import thuvien.common.enums.StudentStatus;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;
import thuvien.common.exception.ValidationException;
import thuvien.common.dto.RegisterStudentRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.UserRepository;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.repository.impl.UserRepositoryImpl;
import thuvien.server.security.PasswordHasher;
import thuvien.server.service.AuditLogService;
import thuvien.server.service.StudentService;
import java.security.SecureRandom;

public class StudentServiceImpl implements StudentService {
    private static final Logger LOGGER = Logger.getLogger(StudentServiceImpl.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public StudentServiceImpl() {
        this(new StudentRepositoryImpl(), new UserRepositoryImpl(), new AuditLogServiceImpl());
    }

    public StudentServiceImpl(StudentRepository studentRepository) {
        this(studentRepository, new UserRepositoryImpl(), null);
    }

    public StudentServiceImpl(StudentRepository studentRepository, UserRepository userRepository, AuditLogService auditLogService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository != null ? userRepository : new UserRepositoryImpl();
        this.auditLogService = auditLogService;
    }

    @Override
    public StudentDTO getStudentById(Long id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Student ID cannot be null.");
        }
        try {
            StudentDTO student = studentRepository.findById(id);
            if (student == null) {
                throw new EntityNotFoundException("Student not found with ID: " + id);
            }
            return student;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving student by ID: " + id, e);
            throw new LibraryException("Database error retrieving student: " + e.getMessage(), e);
        }
    }

    @Override
    public StudentDTO getStudentByUserId(Long userId) throws EntityNotFoundException, LibraryException {
        if (userId == null) {
            throw new EntityNotFoundException("User ID cannot be null.");
        }
        try {
            StudentDTO student = studentRepository.findByUserId(userId);
            if (student == null) {
                throw new EntityNotFoundException("Student profile not found for user ID: " + userId);
            }
            return student;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving student by user ID: " + userId, e);
            throw new LibraryException("Database error retrieving student: " + e.getMessage(), e);
        }
    }

    @Override
    public StudentDTO getStudentByCode(String code) throws EntityNotFoundException, LibraryException {
        if (code == null || code.trim().isEmpty()) {
            throw new EntityNotFoundException("Student code cannot be null or empty.");
        }
        try {
            StudentDTO student = studentRepository.findByStudentCode(code.trim());
            if (student == null) {
                throw new EntityNotFoundException("Student not found with code: " + code);
            }
            return student;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving student by code: " + code, e);
            throw new LibraryException("Database error retrieving student: " + e.getMessage(), e);
        }
    }

    @Override
    public List<StudentDTO> getAllStudents() throws LibraryException {
        try {
            return studentRepository.findAll();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving all students", e);
            throw new LibraryException("Database error retrieving all students: " + e.getMessage(), e);
        }
    }

    @Override
    public Long createStudent(StudentDTO student) throws LibraryException {
        if (student == null) {
            throw new LibraryException("Student payload cannot be null.");
        }
        if (student.getStudentCode() == null || student.getStudentCode().trim().isEmpty()) {
            throw new LibraryException("Student code is required.");
        }
        if (student.getFullName() == null || student.getFullName().trim().isEmpty()) {
            throw new LibraryException("Student full name is required.");
        }
        if (student.getClassName() == null || student.getClassName().trim().isEmpty()) {
            throw new LibraryException("Student class name is required.");
        }

        if (student.getMaxBorrowLimit() <= 0) {
            student.setMaxBorrowLimit(5);
        }
        if (student.getStatus() == null) {
            student.setStatus(StudentStatus.ACTIVE);
        }

        try {
            return studentRepository.create(student, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating student: " + student.getStudentCode(), e);
            throw new LibraryException("Database error creating student: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStudent(StudentDTO student) throws LibraryException {
        if (student == null || student.getId() == null) {
            throw new LibraryException("Student and Student ID are required for update.");
        }
        // Verify existence
        getStudentById(student.getId());

        try {
            return studentRepository.update(student, null);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating student ID: " + student.getId(), e);
            throw new LibraryException("Database error updating student: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteStudent(Long id) throws EntityNotFoundException, LibraryException {
        if (id == null) {
            throw new EntityNotFoundException("Student ID cannot be null.");
        }
        StudentDTO student = getStudentById(id);
        student.setStatus(StudentStatus.SUSPENDED);
        return updateStudent(student);
    }

    @Override
    public boolean hardDeleteStudent(Long id) throws EntityNotFoundException, ValidationException, LibraryException {
        if (id == null) {
            throw new ValidationException("Mã ID sinh viên không được để trống.");
        }
        StudentDTO student = getStudentById(id);

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Check active borrows
                String checkActiveBorrows = "SELECT COUNT(*) FROM borrow_records WHERE student_id = ? AND status IN ('ACTIVE', 'OVERDUE')";
                try (PreparedStatement stmt = conn.prepareStatement(checkActiveBorrows)) {
                    stmt.setLong(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new ValidationException("Không thể xóa vĩnh viễn: Sinh viên đang có " + rs.getInt(1) + " cuốn sách mượn chưa trả.");
                        }
                    }
                }

                // 2. Check unpaid fines
                String checkUnpaidFines = "SELECT COUNT(*) FROM fines WHERE student_id = ? AND is_paid = FALSE";
                try (PreparedStatement stmt = conn.prepareStatement(checkUnpaidFines)) {
                    stmt.setLong(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new ValidationException("Không thể xóa vĩnh viễn: Sinh viên còn khoản tiền phạt chưa thanh toán.");
                        }
                    }
                }

                // 3. Check active reservations
                String checkPendingRes = "SELECT COUNT(*) FROM reservations WHERE student_id = ? AND status = 'PENDING'";
                try (PreparedStatement stmt = conn.prepareStatement(checkPendingRes)) {
                    stmt.setLong(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new ValidationException("Không thể xóa vĩnh viễn: Sinh viên đang có yêu cầu đặt trước sách đang chờ.");
                        }
                    }
                }

                // 4. Check historical borrow_records or fines (FK RESTRICT in MySQL schema)
                String checkHistory = "SELECT (SELECT COUNT(*) FROM borrow_records WHERE student_id = ?) + (SELECT COUNT(*) FROM fines WHERE student_id = ?)";
                try (PreparedStatement stmt = conn.prepareStatement(checkHistory)) {
                    stmt.setLong(1, id);
                    stmt.setLong(2, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new ValidationException("Không thể xóa vĩnh viễn sinh viên này vì có dữ liệu lịch sử mượn trả/tiền phạt trong hệ thống. Hãy sử dụng chức năng 'Tạm khóa'.");
                        }
                    }
                }

                // 5. Clean up any non-active reservations (CANCELLED or EXPIRED) if they exist
                String cleanRes = "DELETE FROM reservations WHERE student_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(cleanRes)) {
                    stmt.setLong(1, id);
                    stmt.executeUpdate();
                }

                // 6. Delete student record
                boolean deleted = studentRepository.delete(id, conn);
                if (!deleted) {
                    throw new LibraryException("Không thể xóa bản ghi sinh viên (ID: " + id + ").");
                }

                // 7. If student has an associated user account with role STUDENT, remove user record
                if (student.getUserId() != null) {
                    String deleteUserSql = "DELETE FROM users WHERE id = ? AND role = 'STUDENT'";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteUserSql)) {
                        stmt.setLong(1, student.getUserId());
                        stmt.executeUpdate();
                    }
                }

                conn.commit();
                LOGGER.info("Successfully hard-deleted student ID: " + id);
                return true;
            } catch (ValidationException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Database error hard deleting student ID: " + id, e);
                if (e.getErrorCode() == 1451) {
                    throw new ValidationException("Không thể xóa vĩnh viễn sinh viên do có ràng buộc dữ liệu liên quan. Hãy sử dụng chức năng 'Tạm khóa'.");
                }
                throw new LibraryException("Lỗi cơ sở dữ liệu khi xóa sinh viên: " + e.getMessage(), e);
            } catch (LibraryException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Connection error during student hard delete", e);
            throw new LibraryException("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateActivationCode(Long studentId) throws EntityNotFoundException, LibraryException {
        if (studentId == null) {
            throw new EntityNotFoundException("Mã ID sinh viên không được để trống.");
        }
        StudentDTO student = getStudentById(studentId);
        if (student.getUserId() != null) {
            throw new ValidationException("Sinh viên này đã có tài khoản người dùng, không thể tạo mã kích hoạt mới.");
        }

        StringBuilder sb = new StringBuilder("ACT-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHANUM.charAt(SECURE_RANDOM.nextInt(ALPHANUM.length())));
        }
        String plainCode = sb.toString();

        try {
            String codeHash = PasswordHasher.hash(plainCode);
            studentRepository.saveActivationCode(studentId, codeHash, null);
            LOGGER.info("Generated new activation code for student ID: " + studentId);
            return plainCode;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error saving activation code for student ID: " + studentId, e);
            throw new LibraryException("Lỗi cơ sở dữ liệu khi lưu mã kích hoạt: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean registerStudentAccount(RegisterStudentRequestDTO request)
            throws ValidationException, LibraryException {
        if (request == null) {
            throw new ValidationException("Dữ liệu đăng ký không được để trống.");
        }

        String studentCode = request.getStudentCode() != null ? request.getStudentCode().trim() : "";
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";
        String confirmPassword = request.getConfirmPassword() != null ? request.getConfirmPassword() : "";
        String activationCode = request.getActivationCode() != null ? request.getActivationCode().trim() : "";

        if (studentCode.isEmpty()) {
            throw new ValidationException("Vui lòng nhập Mã sinh viên.");
        }
        if (username.isEmpty()) {
            throw new ValidationException("Vui lòng nhập Tên đăng nhập mong muốn.");
        }
        if (username.length() < 3) {
            throw new ValidationException("Tên đăng nhập phải có ít nhất 3 ký tự.");
        }
        if (password.length() < 6) {
            throw new ValidationException("Mật khẩu phải có ít nhất 6 ký tự.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Mật khẩu xác nhận không trùng khớp.");
        }
        if (activationCode.isEmpty()) {
            throw new ValidationException("Vui lòng nhập Mã kích hoạt đã được cấp.");
        }

        try {
            // 1. Verify student exists in school database
            StudentDTO student = studentRepository.findByStudentCode(studentCode);
            if (student == null) {
                throw new ValidationException("Mã sinh viên '" + studentCode + "' không tồn tại trong hồ sơ thư viện trường.");
            }
            if (student.getUserId() != null) {
                throw new ValidationException("Hồ sơ sinh viên này đã được liên kết với một tài khoản người dùng.");
            }

            // 2. Verify activation code
            String storedHash = studentRepository.getActivationCodeHash(student.getId(), null);
            if (storedHash == null) {
                throw new ValidationException("Sinh viên chưa được cấp mã kích hoạt. Vui lòng liên hệ Thư viện để được cấp mã.");
            }
            if (studentRepository.isActivationCodeUsed(student.getId(), null)) {
                throw new ValidationException("Mã kích hoạt này đã được sử dụng trước đó.");
            }
            if (!PasswordHasher.verify(activationCode, storedHash)) {
                throw new ValidationException("Mã kích hoạt không chính xác. Vui lòng kiểm tra lại.");
            }

            // 3. Verify username is available
            UserDTO existingUser = userRepository.findByUsername(username);
            if (existingUser != null) {
                throw new ValidationException("Tên đăng nhập '" + username + "' đã được sử dụng. Vui lòng chọn tên khác.");
            }

            // 4. Atomic Transaction: create user, link student, mark activation used
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    UserDTO newUser = new UserDTO();
                    newUser.setUsername(username);
                    newUser.setRole(UserRole.STUDENT);
                    newUser.setFullName(student.getFullName());
                    newUser.setEmail(student.getEmail() != null && !student.getEmail().trim().isEmpty()
                            ? student.getEmail() : request.getEmail());
                    newUser.setPhone(student.getPhone());
                    newUser.setActive(true);

                    String passwordHash = PasswordHasher.hash(password);
                    Long createdUserId = userRepository.create(newUser, passwordHash, conn);

                    student.setUserId(createdUserId);
                    studentRepository.update(student, conn);

                    studentRepository.markActivationCodeUsed(student.getId(), conn);

                    conn.commit();

                    if (auditLogService != null) {
                        auditLogService.log(createdUserId, "ACCOUNT_REGISTERED", "Student", student.getId(),
                                "Sinh viên kích hoạt tài khoản thành công: " + username + " (Mã SV: " + studentCode + ")");
                    }
                    LOGGER.info(String.format("Student account registered successfully: %s -> User ID: %d", username, createdUserId));
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    LOGGER.log(Level.SEVERE, "Transaction rollback during student registration: " + username, e);
                    throw new LibraryException("Lỗi xử lý giao dịch đăng ký tài khoản: " + e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during student registration: " + username, e);
            throw new LibraryException("Lỗi cơ sở dữ liệu trong quá trình đăng ký: " + e.getMessage(), e);
        }
    }
}

