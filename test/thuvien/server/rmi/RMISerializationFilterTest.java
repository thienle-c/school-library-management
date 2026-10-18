package thuvien.server.rmi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import thuvien.common.dto.BookDTO;
import thuvien.common.dto.CreateUserRequestDTO;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.dto.UserDTO;
import thuvien.common.enums.UserRole;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

/**
 * Unit test suite verifying JEP 290 Serialization Filter behavior:
 * 1. Whitelisted application DTOs, protocols, and standard Java types deserialize successfully.
 * 2. Non-whitelisted classes outside the policy are blocked with InvalidClassException.
 * 3. Filter configuration is active and process-wide.
 */
public class RMISerializationFilterTest {

    @BeforeClass
    public static void setUpFilter() {
        LibraryRMIServer.configureSerializationFilter();
    }

    private byte[] serialize(Object obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    @Test
    public void testFilterConfigurationIsActive() {
        String sysProp = System.getProperty("jdk.serialFilter");
        String secProp = java.security.Security.getProperty("jdk.serialFilter");
        Assert.assertNotNull("System property jdk.serialFilter must be set", sysProp);
        Assert.assertTrue("Filter must contain thuvien.common.**", sysProp.contains("thuvien.common.**"));
        Assert.assertTrue("Filter must reject everything else with !*", sysProp.endsWith("!*"));
    }

    @Test
    public void testValidApplicationRequestPayloadAllowed() throws Exception {
        UserDTO user = new UserDTO();
        user.setUsername("test_admin");
        user.setRole(UserRole.ADMIN);
        user.setFullName("Test Admin User");

        CreateUserRequestDTO createUserPayload = new CreateUserRequestDTO(user, "SecureInitialPass123!");
        Request request = new Request(Action.CREATE_USER, createUserPayload);
        request.setToken("session-token-test-12345");

        byte[] serialized = serialize(request);
        Object deserialized = deserialize(serialized);

        Assert.assertNotNull(deserialized);
        Assert.assertTrue(deserialized instanceof Request);
        Request restoredReq = (Request) deserialized;
        Assert.assertEquals(Action.CREATE_USER, restoredReq.getAction());
        Assert.assertEquals("session-token-test-12345", restoredReq.getToken());
        Assert.assertTrue(restoredReq.getPayload() instanceof CreateUserRequestDTO);
        CreateUserRequestDTO restoredPayload = (CreateUserRequestDTO) restoredReq.getPayload();
        Assert.assertEquals("test_admin", restoredPayload.getUser().getUsername());
        Assert.assertEquals("SecureInitialPass123!", restoredPayload.getInitialPassword());
    }

    @Test
    public void testValidResponseWithCollectionsAllowed() throws Exception {
        List<BookDTO> books = new ArrayList<>();
        BookDTO b1 = new BookDTO();
        b1.setId(101L);
        b1.setTitle("Design Patterns in Java");
        b1.setIsbn("978-0201633610");
        b1.setTotalCopies(5);
        b1.setAvailableCopies(3);
        books.add(b1);

        Response response = Response.ok("req-999", books);
        response.setMessage("Found 1 book");

        byte[] serialized = serialize(response);
        Object deserialized = deserialize(serialized);

        Assert.assertNotNull(deserialized);
        Assert.assertTrue(deserialized instanceof Response);
        Response restoredResp = (Response) deserialized;
        Assert.assertEquals(StatusCode.OK, restoredResp.getStatusCode());
        Assert.assertTrue(restoredResp.getData() instanceof List);
        List<?> list = (List<?>) restoredResp.getData();
        Assert.assertEquals(1, list.size());
        Assert.assertTrue(list.get(0) instanceof BookDTO);
        Assert.assertEquals("Design Patterns in Java", ((BookDTO) list.get(0)).getTitle());
    }

    @Test
    public void testStandardJavaTypesAllowed() throws Exception {
        Object[] types = new Object[]{
                "Sample string",
                123456789L,
                42,
                true,
                new BigDecimal("99000.50"),
                new Date()
        };

        for (Object item : types) {
            byte[] bytes = serialize(item);
            Object result = deserialize(bytes);
            Assert.assertEquals(item, result);
        }
    }

    /**
     * Dummy class simulating an arbitrary class not in the whitelist.
     */
    public static class UnauthorizedGadgetClass implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String payload = "exploit";
    }

    @Test
    public void testNonWhitelistedClassBlockedByFilter() {
        try {
            UnauthorizedGadgetClass unauthorized = new UnauthorizedGadgetClass();
            byte[] bytes = serialize(unauthorized);
            deserialize(bytes);
            Assert.fail("Deserialization of non-whitelisted class must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            // Expected security rejection
            Assert.assertTrue("Exception message should indicate filter rejection",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        } catch (Exception e) {
            Assert.fail("Expected InvalidClassException but received: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    public void testResourceLimitGraphDepthExceeded() throws Exception {
        // Build a nested list graph with depth = 20 (> maxdepth=15)
        List<Object> root = new ArrayList<>();
        List<Object> current = root;
        for (int i = 0; i < 20; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }

        byte[] bytes = serialize(root);
        try {
            deserialize(bytes);
            Assert.fail("Graph depth exceeding maxdepth=15 must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            Assert.assertTrue("Exception message should indicate filter rejection for depth limit",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        }
    }

    @Test
    public void testResourceLimitArraySizeExceeded() throws Exception {
        // Allocate array with length = 100,005 (> maxarray=100000)
        int[] largeArray = new int[100005];
        byte[] bytes = serialize(largeArray);
        try {
            deserialize(bytes);
            Assert.fail("Array size exceeding maxarray=100000 must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            Assert.assertTrue("Exception message should indicate filter rejection for array size limit",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        }
    }

    public interface UnauthorizedProxyInterface extends Serializable {
        void execute();
    }

    public static class SerializableProxyHandler implements java.lang.reflect.InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return null;
        }
    }

    @Test
    public void testDynamicProxyWithUnauthorizedInterfaceBlocked() throws Exception {
        // Dynamic proxy whose interface is outside the whitelist
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{UnauthorizedProxyInterface.class},
                new SerializableProxyHandler()
        );

        byte[] bytes = serialize(proxy);
        try {
            deserialize(bytes);
            Assert.fail("Dynamic proxy with non-whitelisted interface must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            Assert.assertTrue("Exception message should indicate filter rejection for unauthorized proxy interface",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        }
    }

    @Test
    public void testResourceLimitBytesExceeded() throws Exception {
        // Build a list of byte arrays exceeding maxbytes=10485760 (10 MB)
        // With each chunk <= maxarray=100000
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < 110; i++) {
            chunks.add(new byte[100000]);
        }
        byte[] bytes = serialize(chunks);
        try {
            deserialize(bytes);
            Assert.fail("Stream size exceeding maxbytes=10485760 must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            Assert.assertTrue("Exception message should indicate filter rejection for byte limit",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        }
    }

    @Test
    public void testResourceLimitRefsExceeded() throws Exception {
        // Create an array with 50,010 Date objects exceeding maxrefs=50000
        Date[] dates = new Date[50010];
        for (int i = 0; i < dates.length; i++) {
            dates[i] = new Date(i * 1000L);
        }
        byte[] bytes = serialize(dates);
        try {
            deserialize(bytes);
            Assert.fail("Object references exceeding maxrefs=50000 must be rejected by JEP 290 filter");
        } catch (InvalidClassException e) {
            Assert.assertTrue("Exception message should indicate filter rejection for refs limit",
                    e.getMessage().contains("filter status: REJECTED") || e.getMessage().contains("REJECTED"));
        }
    }
}
