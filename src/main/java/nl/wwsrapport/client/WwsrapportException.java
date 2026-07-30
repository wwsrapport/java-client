package nl.wwsrapport.client;

import java.nio.charset.StandardCharsets;

public class WwsrapportException extends RuntimeException {
    private final int statusCode;
    private final String requestId;
    private final String responseBody;

    public WwsrapportException(String message, int statusCode, String requestId, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.responseBody = responseBody;
    }

    public WwsrapportException(String message, int statusCode, String requestId, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.responseBody = responseBody;
    }

    public static WwsrapportException fromResponse(int statusCode, String requestId, byte[] bodyBytes) {
        String body = bodyBytes == null ? "" : new String(bodyBytes, StandardCharsets.UTF_8);
        String message = "WWSrapport API request failed with HTTP " + statusCode;
        if (!body.isBlank()) {
            message += ": " + body;
        }
        return new WwsrapportException(message, statusCode, requestId, body);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

