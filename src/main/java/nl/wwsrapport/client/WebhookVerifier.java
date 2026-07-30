package nl.wwsrapport.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookVerifier {
    private WebhookVerifier() {
    }

    public static boolean verify(String payload, String timestampHeader, String signatureHeader, String secret) {
        return verify(payload, timestampHeader, signatureHeader, secret, 300, Instant.now().getEpochSecond());
    }

    public static boolean verify(String payload, String timestampHeader, String signatureHeader, String secret, long toleranceSeconds, long nowEpochSeconds) {
        if (payload == null || timestampHeader == null || signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException exception) {
            return false;
        }

        if (Math.abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
            return false;
        }

        String signedPayload = timestampHeader + "." + payload;
        String expected = "v1=" + hmacSha256Hex(secret, signedPayload);

        for (String part : signatureHeader.split(",")) {
            if (constantTimeEquals(expected, part.trim())) {
                return true;
            }
        }

        return false;
    }

    private static String hmacSha256Hex(String secret, String value) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate WWSrapport webhook signature.", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}

