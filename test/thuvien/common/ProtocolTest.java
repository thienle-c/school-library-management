package thuvien.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Assert;
import org.junit.Test;
import thuvien.common.dto.LoginRequestDTO;
import thuvien.common.protocol.Action;
import thuvien.common.protocol.Request;
import thuvien.common.protocol.Response;
import thuvien.common.protocol.StatusCode;

public class ProtocolTest {

    @Test
    public void testRequestSerialization() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("admin", "secret123");
        Request request = new Request(Action.LOGIN, dto);
        request.setToken("test-token-uuid");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(request);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Request deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (Request) ois.readObject();
        }

        Assert.assertNotNull(deserialized);
        Assert.assertEquals(request.getRequestId(), deserialized.getRequestId());
        Assert.assertEquals(Action.LOGIN, deserialized.getAction());
        Assert.assertEquals("test-token-uuid", deserialized.getToken());
        Assert.assertTrue(deserialized.getPayload() instanceof LoginRequestDTO);
        LoginRequestDTO readDto = (LoginRequestDTO) deserialized.getPayload();
        Assert.assertEquals("admin", readDto.getUsername());
        Assert.assertEquals("secret123", readDto.getPassword());
    }

    @Test
    public void testResponseSerialization() throws Exception {
        Response response = Response.ok("req-123", "User record loaded", "SampleData");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(response);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Response deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (Response) ois.readObject();
        }

        Assert.assertNotNull(deserialized);
        Assert.assertEquals("req-123", deserialized.getRequestId());
        Assert.assertEquals(StatusCode.OK, deserialized.getStatusCode());
        Assert.assertEquals("User record loaded", deserialized.getMessage());
        Assert.assertEquals("SampleData", deserialized.getData());
        Assert.assertTrue(deserialized.isSuccess());
    }
}
