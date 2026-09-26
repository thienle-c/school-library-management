package thuvien.common.protocol;

import java.io.Serializable;

/**
 * Standard server-to-client response envelope.
 */
public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private int statusCode;
    private String message;
    private Object data;

    public Response() {}

    public Response(String requestId, int statusCode, String message, Object data) {
        this.requestId = requestId;
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }

    public static Response ok(String requestId, Object data) {
        return new Response(requestId, StatusCode.OK, "Success", data);
    }

    public static Response ok(String requestId, String message, Object data) {
        return new Response(requestId, StatusCode.OK, message, data);
    }

    public static Response error(String requestId, int statusCode, String message) {
        return new Response(requestId, statusCode, message, null);
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Response{" +
                "requestId='" + requestId + '\'' +
                ", statusCode=" + statusCode +
                ", message='" + message + '\'' +
                '}';
    }
}
