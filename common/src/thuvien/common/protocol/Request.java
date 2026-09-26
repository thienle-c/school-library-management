package thuvien.common.protocol;

import java.io.Serializable;
import java.util.UUID;

/**
 * Standard client-to-server request envelope.
 */
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Action action;
    private String token;
    private Object payload;

    public Request() {
        this.requestId = UUID.randomUUID().toString();
    }

    public Request(Action action, Object payload) {
        this();
        this.action = action;
        this.payload = payload;
    }

    public Request(Action action, String token, Object payload) {
        this(action, payload);
        this.token = token;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Request{" +
                "requestId='" + requestId + '\'' +
                ", action=" + action +
                ", hasToken=" + (token != null && !token.isEmpty()) +
                '}';
    }
}
