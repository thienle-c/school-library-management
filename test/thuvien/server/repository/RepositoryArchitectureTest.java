package thuvien.server.repository;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Assert;
import org.junit.Test;
import thuvien.server.database.DatabaseManager;
import thuvien.server.repository.impl.AuditLogRepositoryImpl;
import thuvien.server.repository.impl.AuthorRepositoryImpl;
import thuvien.server.repository.impl.BookRepositoryImpl;
import thuvien.server.repository.impl.BorrowRepositoryImpl;
import thuvien.server.repository.impl.CategoryRepositoryImpl;
import thuvien.server.repository.impl.FineRepositoryImpl;
import thuvien.server.repository.impl.ReservationRepositoryImpl;
import thuvien.server.repository.impl.StudentRepositoryImpl;
import thuvien.server.repository.impl.UserRepositoryImpl;

public class RepositoryArchitectureTest {

    private final Class<?>[] repoClasses = new Class<?>[] {
        UserRepositoryImpl.class,
        StudentRepositoryImpl.class,
        CategoryRepositoryImpl.class,
        AuthorRepositoryImpl.class,
        BookRepositoryImpl.class,
        BorrowRepositoryImpl.class,
        FineRepositoryImpl.class,
        ReservationRepositoryImpl.class,
        AuditLogRepositoryImpl.class
    };

    @Test
    public void testRepositoryInterfaceImplementations() {
        Assert.assertTrue(UserRepository.class.isAssignableFrom(UserRepositoryImpl.class));
        Assert.assertTrue(StudentRepository.class.isAssignableFrom(StudentRepositoryImpl.class));
        Assert.assertTrue(CategoryRepository.class.isAssignableFrom(CategoryRepositoryImpl.class));
        Assert.assertTrue(AuthorRepository.class.isAssignableFrom(AuthorRepositoryImpl.class));
        Assert.assertTrue(BookRepository.class.isAssignableFrom(BookRepositoryImpl.class));
        Assert.assertTrue(BorrowRepository.class.isAssignableFrom(BorrowRepositoryImpl.class));
        Assert.assertTrue(FineRepository.class.isAssignableFrom(FineRepositoryImpl.class));
        Assert.assertTrue(ReservationRepository.class.isAssignableFrom(ReservationRepositoryImpl.class));
        Assert.assertTrue(AuditLogRepository.class.isAssignableFrom(AuditLogRepositoryImpl.class));
    }

    @Test
    public void testThreadSafetyNoMutableConnectionFields() {
        for (Class<?> clazz : repoClasses) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                Class<?> type = field.getType();
                Assert.assertFalse("Repository " + clazz.getSimpleName() + " must NOT have Connection field",
                        Connection.class.isAssignableFrom(type));
                Assert.assertFalse("Repository " + clazz.getSimpleName() + " must NOT have PreparedStatement field",
                        PreparedStatement.class.isAssignableFrom(type));
                Assert.assertFalse("Repository " + clazz.getSimpleName() + " must NOT have Statement field",
                        Statement.class.isAssignableFrom(type));
                Assert.assertFalse("Repository " + clazz.getSimpleName() + " must NOT have ResultSet field",
                        ResultSet.class.isAssignableFrom(type));
            }
        }
    }

    @Test
    public void testRepositoriesCanBeConstructedWithDatabaseManager() {
        DatabaseManager dummyManager = null;
        Assert.assertNotNull(new UserRepositoryImpl(dummyManager));
        Assert.assertNotNull(new StudentRepositoryImpl(dummyManager));
        Assert.assertNotNull(new CategoryRepositoryImpl(dummyManager));
        Assert.assertNotNull(new AuthorRepositoryImpl(dummyManager));
        Assert.assertNotNull(new BookRepositoryImpl(dummyManager));
        Assert.assertNotNull(new BorrowRepositoryImpl(dummyManager));
        Assert.assertNotNull(new FineRepositoryImpl(dummyManager));
        Assert.assertNotNull(new ReservationRepositoryImpl(dummyManager));
        Assert.assertNotNull(new AuditLogRepositoryImpl(dummyManager));
    }
}
