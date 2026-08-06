package nl.wwsrapport.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class WwsrapportClient {
    private static final String DEFAULT_BASE_URL = "https://wwsrapport.nl/v1";
    private static final String CLIENT_HEADER = "wwsrapport-java-client/0.2.0";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public WwsrapportClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, HttpClient.newHttpClient());
    }

    public WwsrapportClient(String apiKey, String baseUrl, HttpClient httpClient) {
        this.apiKey = requireNotBlank(apiKey, "apiKey");
        this.baseUrl = trimTrailingSlash(requireNotBlank(baseUrl, "baseUrl"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public static WwsrapportClient fromApiKey(String apiKey) {
        return new WwsrapportClient(apiKey);
    }

    public String prefillProperty(String addressJson) {
        return post("/properties/prefill", "{\"address\":" + addressJson + "}", null);
    }

    public String validateReport(String reportInputJson) {
        return post("/reports/validate", reportInputJson, null);
    }

    public String createReport(String reportInputJson, String idempotencyKey) {
        return post("/reports", reportInputJson, requireNotBlank(idempotencyKey, "idempotencyKey"));
    }

    public String recalculateReport(String reportId, String recalculateInputJson, String idempotencyKey) {
        return post("/reports/" + encodePath(reportId) + "/recalculate", recalculateInputJson, requireNotBlank(idempotencyKey, "idempotencyKey"));
    }

    public String listReports(Map<String, ?> query) {
        return getJson("/reports", query);
    }

    public String getReport(String reportId) {
        return getJson("/reports/" + encodePath(reportId), Map.of());
    }

    public String getCalculation(String reportId) {
        return getJson("/reports/" + encodePath(reportId) + "/calculation", Map.of());
    }

    public String getImprovementAdvice(String reportId) {
        return getJson("/reports/" + encodePath(reportId) + "/improvement-advice", Map.of());
    }

    public String getReportVerification(String reportId) {
        return getJson("/reports/" + encodePath(reportId) + "/verification", Map.of());
    }

    public String deriveBagReference(String bagVboId) {
        validateBagVboId(bagVboId);
        return post("/registry/bag-reference", "{\"bagVboId\":\"" + bagVboId + "\"}", null);
    }

    public String searchRegistryByBag(String bagVboId) {
        validateBagVboId(bagVboId);
        return post("/registry/search-by-bag", "{\"bagVboId\":\"" + bagVboId + "\"}", null);
    }

    private static void validateBagVboId(String value) {
        if (value == null || !value.matches("[0-9]{16}")) {
            throw new IllegalArgumentException("BAG verblijfsobject ID must contain exactly sixteen digits.");
        }
    }

    public String listDocuments(String reportId) {
        return getJson("/reports/" + encodePath(reportId) + "/documents", Map.of());
    }

    public byte[] downloadWwsReport(String reportId) {
        return getBytes("/reports/" + encodePath(reportId) + "/documents/wws-report");
    }

    public byte[] downloadImprovementAdvice(String reportId) {
        return getBytes("/reports/" + encodePath(reportId) + "/documents/improvement-advice");
    }

    public String currentUsage() {
        return getJson("/usage/current", Map.of());
    }

    public String usageHistory(Map<String, ?> query) {
        return getJson("/usage/history", query);
    }

    public String listRulesets() {
        return getJson("/rulesets", Map.of());
    }

    public String listWebhooks() {
        return getJson("/webhooks", Map.of());
    }

    public String createWebhook(String webhookJson) {
        return post("/webhooks", webhookJson, null);
    }

    public String getWebhook(String webhookId) {
        return getJson("/webhooks/" + encodePath(webhookId), Map.of());
    }

    public String updateWebhook(String webhookId, String webhookJson) {
        return patch("/webhooks/" + encodePath(webhookId), webhookJson);
    }

    public String deleteWebhook(String webhookId) {
        return delete("/webhooks/" + encodePath(webhookId));
    }

    public String sendTestWebhook(String webhookId) {
        return post("/webhooks/" + encodePath(webhookId) + "/test", null, null);
    }

    public String listWebhookDeliveries(String webhookId, Map<String, ?> query) {
        return getJson("/webhooks/" + encodePath(webhookId) + "/deliveries", query);
    }

    public String retryWebhookDelivery(String webhookId, String deliveryId) {
        return post("/webhooks/" + encodePath(webhookId) + "/deliveries/" + encodePath(deliveryId) + "/retry", null, null);
    }

    private String getJson(String path, Map<String, ?> query) {
        return sendString("GET", path, query, null, null, "application/json");
    }

    private byte[] getBytes(String path) {
        return send("GET", path, Map.of(), null, null, "application/pdf, application/octet-stream").body();
    }

    private String post(String path, String body, String idempotencyKey) {
        return sendString("POST", path, Map.of(), body, idempotencyKey, "application/json");
    }

    private String patch(String path, String body) {
        return sendString("PATCH", path, Map.of(), body, null, "application/json");
    }

    private String delete(String path) {
        return sendString("DELETE", path, Map.of(), null, null, "application/json");
    }

    private String sendString(String method, String path, Map<String, ?> query, String body, String idempotencyKey, String accept) {
        return new String(send(method, path, query, body, idempotencyKey, accept).body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> send(String method, String path, Map<String, ?> query, String body, String idempotencyKey, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path, query))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", accept)
            .header("Authorization", "Bearer " + apiKey)
            .header("X-WWSrapport-Client", CLIENT_HEADER);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }

        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw WwsrapportException.fromResponse(response.statusCode(), response.headers().firstValue("X-Request-Id").orElse(null), response.body());
            }
            return response;
        } catch (IOException e) {
            throw new WwsrapportException("WWSrapport API request failed: " + e.getMessage(), 0, null, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WwsrapportException("WWSrapport API request interrupted.", 0, null, null, e);
        }
    }

    private URI uri(String path, Map<String, ?> query) {
        StringBuilder uri = new StringBuilder(baseUrl).append('/').append(path.replaceFirst("^/+", ""));
        String queryString = queryString(query);
        if (!queryString.isEmpty()) {
            uri.append('?').append(queryString);
        }
        return URI.create(uri.toString());
    }

    private static String queryString(Map<String, ?> query) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            joiner.add(encode(entry.getKey()) + "=" + encode(value.toString()));
        }
        return joiner.toString();
    }

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private static String encodePath(String value) {
        return encode(requireNotBlank(value, "path value")).replace("+", "%20");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
