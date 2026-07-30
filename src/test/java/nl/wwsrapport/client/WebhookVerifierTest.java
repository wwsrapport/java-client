package nl.wwsrapport.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WebhookVerifierTest {
    @Test
    void verifiesValidSignature() {
        String payload = "{\"type\":\"webhook.test\"}";
        String secret = "whsec_test";
        String timestamp = "1710000000";
        String signature = "v1=50ebb068d786d8e4b90f5c8d2f4d49a988df87705e9fa45eb761216e8e78c6a7";

        assertTrue(WebhookVerifier.verify(payload, timestamp, signature, secret, 300, 1710000000));
    }

    @Test
    void rejectsExpiredSignature() {
        assertFalse(WebhookVerifier.verify("{}", "1710000000", "v1=bad", "whsec_test", 300, 1710001000));
    }
}
