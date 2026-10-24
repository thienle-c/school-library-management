package thuvien.client;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.client.network.NetworkClient;
import thuvien.client.network.RMIClient;
import thuvien.client.session.ClientSession;
import thuvien.client.session.PermissionHelper;
import thuvien.common.dto.UserSessionDTO;
import thuvien.common.enums.UserRole;

/**
 * Unit test suite verifying centralized Role-Based Access Control logic (PermissionHelper).
 * Tests visibility rules, staff vs student boundaries, and admin privileges across all roles.
 */
public class PermissionHelperTest {

    private NetworkClient networkClient;

    @Before
    public void setUp() {
        ClientSession.getInstance().clear();
        networkClient = new RMIClient();
    }

    @After
    public void tearDown() {
        ClientSession.getInstance().clear();
    }

    @Test
    public void testStudentPermissions() {
        UserSessionDTO studentSession = new UserSessionDTO("token-student", 101L, "student1", "Nguyen Van Sinh Vien", UserRole.STUDENT);
        ClientSession cs = new ClientSession();
        cs.setSession(studentSession);
        networkClient.setSession(cs);
        ClientSession.getInstance().setSession(studentSession);

        // Role identification
        Assert.assertTrue("Should be student", PermissionHelper.isStudent(networkClient));
        Assert.assertFalse("Should NOT be librarian", PermissionHelper.isLibrarian(networkClient));
        Assert.assertFalse("Should NOT be admin", PermissionHelper.isAdmin(networkClient));
        Assert.assertFalse("Should NOT be staff", PermissionHelper.isStaff(networkClient));
        Assert.assertEquals(UserRole.STUDENT, PermissionHelper.getCurrentRole(networkClient));

        // Forbidden UI capabilities for student
        Assert.assertFalse("Student cannot manage books", PermissionHelper.canManageBooks(networkClient));
        Assert.assertFalse("Student cannot manage student directory", PermissionHelper.canManageStudents(networkClient));
        Assert.assertFalse("Student cannot delete students", PermissionHelper.canDeleteStudent(networkClient));
        Assert.assertFalse("Student cannot generate activation codes", PermissionHelper.canGenerateActivationCode(networkClient));
        Assert.assertFalse("Student cannot issue/return books", PermissionHelper.canManageBorrowReturn(networkClient));
        Assert.assertFalse("Student cannot manage or calculate fines", PermissionHelper.canManageFines(networkClient));
        Assert.assertFalse("Student cannot view overview metrics", PermissionHelper.canViewOverviewMetrics(networkClient));
        Assert.assertFalse("Student cannot view audit logs", PermissionHelper.canViewAuditLogs(networkClient));

        // Allowed student capabilities
        Assert.assertTrue("Student can view student dashboard", PermissionHelper.canViewStudentDashboard(networkClient));
    }

    @Test
    public void testLibrarianPermissions() {
        UserSessionDTO librarianSession = new UserSessionDTO("token-lib", 202L, "librarian1", "Le Thu Thu", UserRole.LIBRARIAN);
        ClientSession cs = new ClientSession();
        cs.setSession(librarianSession);
        networkClient.setSession(cs);
        ClientSession.getInstance().setSession(librarianSession);

        // Role identification
        Assert.assertFalse("Should NOT be student", PermissionHelper.isStudent(networkClient));
        Assert.assertTrue("Should be librarian", PermissionHelper.isLibrarian(networkClient));
        Assert.assertFalse("Should NOT be admin", PermissionHelper.isAdmin(networkClient));
        Assert.assertTrue("Should be staff", PermissionHelper.isStaff(networkClient));
        Assert.assertEquals(UserRole.LIBRARIAN, PermissionHelper.getCurrentRole(networkClient));

        // Allowed librarian capabilities
        Assert.assertTrue("Librarian can manage books", PermissionHelper.canManageBooks(networkClient));
        Assert.assertTrue("Librarian can manage student directory", PermissionHelper.canManageStudents(networkClient));
        Assert.assertTrue("Librarian can issue/return books", PermissionHelper.canManageBorrowReturn(networkClient));
        Assert.assertTrue("Librarian can manage/calculate fines", PermissionHelper.canManageFines(networkClient));
        Assert.assertTrue("Librarian can view overview metrics", PermissionHelper.canViewOverviewMetrics(networkClient));

        // Forbidden admin-only capabilities for librarian
        Assert.assertFalse("Librarian cannot hard delete students", PermissionHelper.canDeleteStudent(networkClient));
        Assert.assertFalse("Librarian cannot generate activation codes", PermissionHelper.canGenerateActivationCode(networkClient));
        Assert.assertFalse("Librarian cannot view audit logs", PermissionHelper.canViewAuditLogs(networkClient));
        Assert.assertFalse("Librarian does not see student personal dashboard", PermissionHelper.canViewStudentDashboard(networkClient));
    }

    @Test
    public void testAdminPermissions() {
        UserSessionDTO adminSession = new UserSessionDTO("token-admin", 1L, "admin", "Quan Tri Vien He Thong", UserRole.ADMIN);
        ClientSession cs = new ClientSession();
        cs.setSession(adminSession);
        networkClient.setSession(cs);
        ClientSession.getInstance().setSession(adminSession);

        // Role identification
        Assert.assertFalse("Should NOT be student", PermissionHelper.isStudent(networkClient));
        Assert.assertFalse("Should NOT be librarian", PermissionHelper.isLibrarian(networkClient));
        Assert.assertTrue("Should be admin", PermissionHelper.isAdmin(networkClient));
        Assert.assertTrue("Should be staff", PermissionHelper.isStaff(networkClient));
        Assert.assertEquals(UserRole.ADMIN, PermissionHelper.getCurrentRole(networkClient));

        // Full privileged capabilities for admin
        Assert.assertTrue("Admin can manage books", PermissionHelper.canManageBooks(networkClient));
        Assert.assertTrue("Admin can manage student directory", PermissionHelper.canManageStudents(networkClient));
        Assert.assertTrue("Admin can hard delete students", PermissionHelper.canDeleteStudent(networkClient));
        Assert.assertTrue("Admin can generate activation codes", PermissionHelper.canGenerateActivationCode(networkClient));
        Assert.assertTrue("Admin can issue/return books", PermissionHelper.canManageBorrowReturn(networkClient));
        Assert.assertTrue("Admin can manage fines", PermissionHelper.canManageFines(networkClient));
        Assert.assertTrue("Admin can view overview metrics", PermissionHelper.canViewOverviewMetrics(networkClient));
        Assert.assertTrue("Admin can view audit logs", PermissionHelper.canViewAuditLogs(networkClient));
        Assert.assertFalse("Admin does not see student personal dashboard", PermissionHelper.canViewStudentDashboard(networkClient));
    }

    @Test
    public void testNullSessionSafety() {
        networkClient.setSession(null);
        ClientSession.getInstance().clear();

        Assert.assertFalse("Null session cannot manage books", PermissionHelper.canManageBooks(networkClient));
        Assert.assertFalse("Null session cannot manage students", PermissionHelper.canManageStudents(networkClient));
        Assert.assertFalse("Null session cannot delete students", PermissionHelper.canDeleteStudent(networkClient));
        Assert.assertFalse("Null session cannot generate codes", PermissionHelper.canGenerateActivationCode(networkClient));
        Assert.assertFalse("Null session cannot borrow/return", PermissionHelper.canManageBorrowReturn(networkClient));
        Assert.assertFalse("Null session cannot manage fines", PermissionHelper.canManageFines(networkClient));
        Assert.assertFalse("Null session cannot view metrics", PermissionHelper.canViewOverviewMetrics(networkClient));
        Assert.assertFalse("Null session cannot view audit", PermissionHelper.canViewAuditLogs(networkClient));
        Assert.assertFalse("Null session cannot view student dashboard", PermissionHelper.canViewStudentDashboard(networkClient));
        Assert.assertFalse("Null session is not admin", PermissionHelper.isAdmin(networkClient));
        Assert.assertFalse("Null session is not staff", PermissionHelper.isStaff(networkClient));
        Assert.assertNull("Null session has no role", PermissionHelper.getCurrentRole(networkClient));
    }
}
