package thuvien.client.session;

import thuvien.client.network.NetworkClient;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;

/**
 * Cơ chế kiểm tra quyền tập trung (Centralized Role-Based Access Control Helper)
 * dành riêng cho client Swing. Giúp quản lý nhất quán việc ẩn/hiện chức năng
 * và kiểm tra quyền, tránh phân tán các lệnh so sánh chuỗi role tùy tiện.
 */
public final class PermissionHelper {

    private PermissionHelper() {}

    /**
     * Lấy ClientSession đang hoạt động (ưu tiên từ networkClient, fallback sang singleton).
     */
    public static ClientSession getActiveSession(NetworkClient networkClient) {
        if (networkClient != null && networkClient.getSession() != null) {
            return networkClient.getSession();
        }
        return ClientSession.getInstance();
    }

    /**
     * Lấy vai trò UserRole hiện tại.
     */
    public static UserRole getCurrentRole(NetworkClient networkClient) {
        ClientSession cs = getActiveSession(networkClient);
        return cs != null ? cs.getRole() : null;
    }

    public static boolean isAdmin(NetworkClient networkClient) {
        ClientSession cs = getActiveSession(networkClient);
        return cs != null && cs.isAdmin();
    }

    public static boolean isLibrarian(NetworkClient networkClient) {
        ClientSession cs = getActiveSession(networkClient);
        return cs != null && cs.isLibrarian();
    }

    public static boolean isStudent(NetworkClient networkClient) {
        ClientSession cs = getActiveSession(networkClient);
        return cs != null && cs.isStudent();
    }

    /**
     * Nhân viên thư viện (Thủ thư hoặc Quản trị viên).
     */
    public static boolean isStaff(NetworkClient networkClient) {
        ClientSession cs = getActiveSession(networkClient);
        return cs != null && (cs.isAdmin() || cs.isLibrarian());
    }

    // =========================================================================
    // Quyền nghiệp vụ cụ thể
    // =========================================================================

    /**
     * Quyền quản lý sách (Thêm, sửa, xóa sách). Chỉ dành cho Thủ thư và Admin.
     */
    public static boolean canManageBooks(NetworkClient networkClient) {
        return isStaff(networkClient);
    }

    /**
     * Quyền quản lý hồ sơ sinh viên chung (Xem danh sách, thêm, sửa hồ sơ, đình chỉ).
     * Dành cho Thủ thư và Admin.
     */
    public static boolean canManageStudents(NetworkClient networkClient) {
        return isStaff(networkClient);
    }

    /**
     * Quyền xóa vĩnh viễn sinh viên. ĐẶC QUYỀN của riêng Quản trị viên (ADMIN).
     */
    public static boolean canDeleteStudent(NetworkClient networkClient) {
        return isAdmin(networkClient);
    }

    /**
     * Quyền cấp mã kích hoạt tài khoản sinh viên. ĐẶC QUYỀN của riêng Quản trị viên (ADMIN).
     */
    public static boolean canGenerateActivationCode(NetworkClient networkClient) {
        return isAdmin(networkClient);
    }

    /**
     * Quyền thực hiện mượn sách và nhận trả sách. Chỉ dành cho Thủ thư và Admin.
     */
    public static boolean canManageBorrowReturn(NetworkClient networkClient) {
        return isStaff(networkClient);
    }

    /**
     * Quyền thu tiền phạt và tính phạt thủ công. Chỉ dành cho Thủ thư và Admin.
     */
    public static boolean canManageFines(NetworkClient networkClient) {
        return isStaff(networkClient);
    }

    /**
     * Quyền xem chỉ số tổng quan hệ thống (DashboardOverviewPanel). Chỉ dành cho Thủ thư và Admin.
     */
    public static boolean canViewOverviewMetrics(NetworkClient networkClient) {
        return isStaff(networkClient);
    }

    /**
     * Quyền xem nhật ký kiểm toán hệ thống (AuditLogManagementPanel). ĐẶC QUYỀN của Admin.
     */
    public static boolean canViewAuditLogs(NetworkClient networkClient) {
        return isAdmin(networkClient);
    }

    /**
     * Quyền xem trang chủ cá nhân sinh viên (StudentDashboardPanel). Chỉ dành cho Sinh viên.
     */
    public static boolean canViewStudentDashboard(NetworkClient networkClient) {
        return isStudent(networkClient);
    }

    /**
     * Quyền đặt lại mật khẩu người dùng khác. ĐẶC QUYỀN của riêng Quản trị viên (ADMIN).
     */
    public static boolean canResetUserPassword(NetworkClient networkClient) {
        return isAdmin(networkClient);
    }
}
