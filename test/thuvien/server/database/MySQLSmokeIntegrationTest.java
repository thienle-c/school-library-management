package thuvien.server.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.common.dto.AuthorDTO;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.CategoryDTO;
import thuvien.common.dto.StudentDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.server.repository.AuthorRepository;
import thuvien.server.repository.BookRepository;
import thuvien.server.repository.CategoryRepository;
import thuvien.server.repository.StudentRepository;
import thuvien.server.repository.UserRepository;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.AuthorRepositoryImpl;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.CategoryRepositoryImpl;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.repository.impl.UserRepositoryImpl;

public class MySQLSmokeIntegrationTest {

    private DatabaseManager databaseManager;

    @Before
    public void setUp() {
        databaseManager = DatabaseManager.getInstance();
    }

    @Test
    public void testSelectOneConnectivity() throws Exception {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            Assert.assertTrue("SELECT 1 must return a result set", rs.next());
            Assert.assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    public void testSchemaAllTablesExist() throws Exception {
        Set<String> expectedTables = new HashSet<>();
        expectedTables.add("users");
        expectedTables.add("students");
        expectedTables.add("categories");
        expectedTables.add("authors");
        expectedTables.add("books");
        expectedTables.add("book_authors");
        expectedTables.add("borrow_records");
        expectedTables.add("fines");
        expectedTables.add("reservations");
        expectedTables.add("audit_logs");

        Set<String> actualTables = new HashSet<>();
        try (Connection conn = databaseManager.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables("school_library", null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        for (String expected : expectedTables) {
            Assert.assertTrue("Table " + expected + " must exist in database", actualTables.contains(expected));
        }
    }

    @Test
    public void testSeedDataLoaded() throws Exception {
        UserRepository userRepo = new UserRepositoryImpl(databaseManager);
        UserDTO admin = userRepo.findByUsername("admin");
        Assert.assertNotNull("Seed user 'admin' must exist", admin);
        Assert.assertEquals(UserRole.ADMIN, admin.getRole());

        StudentRepository studentRepo = new StudentRepositoryImpl(databaseManager);
        StudentDTO s1 = studentRepo.findByStudentCode("STU001");
        Assert.assertNotNull("Seed student 'STU001' must exist", s1);

        BookRepository bookRepo = new BookRepositoryImpl(databaseManager);
        BookDTO b1 = bookRepo.findByIsbn("978-0132126953");
        Assert.assertNotNull("Seed book with ISBN 978-0132126953 must exist", b1);
        Assert.assertEquals("Computer Networks", b1.getTitle());

        CategoryRepository catRepo = new CategoryRepositoryImpl(databaseManager);
        List<CategoryDTO> categories = catRepo.findAll();
        Assert.assertTrue("Categories seed must have >= 5 entries", categories.size() >= 5);

        AuthorRepository authorRepo = new AuthorRepositoryImpl(databaseManager);
        List<AuthorDTO> authors = authorRepo.findAll();
        Assert.assertTrue("Authors seed must have >= 5 entries", authors.size() >= 5);
    }

    @Test
    public void testRepositorySmokeQueries() throws Exception {
        // Test all 9 repositories run basic query without SQL error
        UserRepository userRepo = new UserRepositoryImpl(databaseManager);
        Assert.assertNotNull(userRepo.findAll());

        StudentRepository studentRepo = new StudentRepositoryImpl(databaseManager);
        Assert.assertNotNull(studentRepo.findAll());

        BookRepository bookRepo = new BookRepositoryImpl(databaseManager);
        Assert.assertNotNull(bookRepo.findAll());

        CategoryRepository catRepo = new CategoryRepositoryImpl(databaseManager);
        Assert.assertNotNull(catRepo.findAll());

        AuthorRepository authorRepo = new AuthorRepositoryImpl(databaseManager);
        Assert.assertNotNull(authorRepo.findAll());

        BorrowRepositoryImpl borrowRepo = new BorrowRepositoryImpl(databaseManager);
        Assert.assertNotNull(borrowRepo.findAll());

        FineRepositoryImpl fineRepo = new FineRepositoryImpl(databaseManager);
        Assert.assertNotNull(fineRepo.findAll());

        ReservationRepositoryImpl resRepo = new ReservationRepositoryImpl(databaseManager);
        Assert.assertNotNull(resRepo.findAll());

        AuditLogRepositoryImpl auditRepo = new AuditLogRepositoryImpl(databaseManager);
        Assert.assertNotNull(auditRepo.findRecent(10));
    }
}
