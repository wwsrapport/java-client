package nl.wwsrapport.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WwsrapportClient {
    private static final String DEFAULT_BASE_URL = "https://wwsrapport.nl/v1";
    private static final String CLIENT_HEADER = "wwsrapport-java-client/0.3.0";

    private final String apiKey;
    private final OAuthClientCredentials oauth;
    private final RequestContext requestContext;
    private final String baseUrl;
    private final HttpClient httpClient;
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public static final class OAuthClientCredentials {
        public final String clientId, clientSecret, tokenUrl, scope;
        public OAuthClientCredentials(String clientId, String clientSecret, String tokenUrl, String scope) {
            this.clientId = requireNotBlank(clientId, "clientId"); this.clientSecret = requireNotBlank(clientSecret, "clientSecret");
            this.tokenUrl = tokenUrl; this.scope = scope;
        }
    }

    public static final class RequestContext {
        public final String municipalityCode, purposeCode, caseReference, clientReference;
        public RequestContext(String municipalityCode, String purposeCode, String caseReference, String clientReference) {
            this.municipalityCode = municipalityCode; this.purposeCode = purposeCode; this.caseReference = caseReference; this.clientReference = clientReference;
        }
    }

    public WwsrapportClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, HttpClient.newHttpClient());
    }

    public WwsrapportClient(String apiKey, String baseUrl, HttpClient httpClient) {
        this.apiKey = requireNotBlank(apiKey, "apiKey");
        this.oauth = null;
        this.requestContext = null;
        this.baseUrl = trimTrailingSlash(requireNotBlank(baseUrl, "baseUrl"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public WwsrapportClient(OAuthClientCredentials oauth, RequestContext requestContext, String baseUrl, HttpClient httpClient) {
        this.apiKey = null;
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.requestContext = requestContext;
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

    public String reviewReport(String reportId, String reviewJson, String idempotencyKey) {
        return post("/reports/" + encodePath(reportId) + "/human-review", reviewJson, requireNotBlank(idempotencyKey, "idempotencyKey"));
    }

    public String createBatch(String batchJson, String idempotencyKey) { return post("/batches", batchJson, requireNotBlank(idempotencyKey, "idempotencyKey")); }
    public String getBatch(String id) { return getJson("/batches/" + encodePath(id), Map.of()); }
    public String retryBatch(String id, String idempotencyKey) { return post("/batches/" + encodePath(id) + "/retry", null, requireNotBlank(idempotencyKey, "idempotencyKey")); }
    public String requestTenantExport(String idempotencyKey) { return post("/exports", null, requireNotBlank(idempotencyKey, "idempotencyKey")); }
    public String getTenantExport(String id) { return getJson("/exports/" + encodePath(id), Map.of()); }
    public String createTenantExportDownloadUrl(String id) { return post("/exports/" + encodePath(id) + "/download-url", null, null); }
    public String requestOffboarding(String reference, String reason) {
        return post("/offboarding", "{\"confirmation\":\"REQUEST_OFFBOARDING\",\"requested_by_reference\":\"" + jsonEscape(reference) + "\",\"reason\":\"" + jsonEscape(reason) + "\"}", null);
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
        String bearerToken = bearerToken();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path, query))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", accept)
            .header("Authorization", "Bearer " + bearerToken)
            .header("X-WWSrapport-Client", CLIENT_HEADER);

        applyRequestContext(builder);

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

    private synchronized String bearerToken() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        if (accessToken != null && Instant.now().plusSeconds(30).isBefore(tokenExpiresAt)) return accessToken;
        String tokenUrl = oauth.tokenUrl;
        if (tokenUrl == null || tokenUrl.isBlank()) {
            URI base = URI.create(baseUrl);
            tokenUrl = base.getScheme() + "://" + base.getAuthority() + "/oauth/token";
        }
        String form = "grant_type=client_credentials" + (oauth.scope == null || oauth.scope.isBlank() ? "" : "&scope=" + encode(oauth.scope));
        String basic = Base64.getEncoder().encodeToString((oauth.clientId + ":" + oauth.clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + basic).POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw WwsrapportException.fromResponse(response.statusCode(), response.headers().firstValue("X-Request-Id").orElse(null), response.body().getBytes(StandardCharsets.UTF_8));
            Matcher token = Pattern.compile("\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(response.body());
            if (!token.find()) throw new WwsrapportException("OAuth response has no access_token.", 0, null, null, null);
            Matcher expires = Pattern.compile("\\\"expires_in\\\"\\s*:\\s*(\\d+)").matcher(response.body());
            long seconds = expires.find() ? Long.parseLong(expires.group(1)) : 300;
            accessToken = token.group(1); tokenExpiresAt = Instant.now().plusSeconds(seconds); return accessToken;
        } catch (IOException e) { throw new WwsrapportException("WWSrapport OAuth request failed: " + e.getMessage(), 0, null, null, e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new WwsrapportException("WWSrapport OAuth request interrupted.", 0, null, null, e); }
    }

    private void applyRequestContext(HttpRequest.Builder builder) {
        if (requestContext == null) return;
        header(builder, "X-WWS-Municipality-Code", requestContext.municipalityCode); header(builder, "X-WWS-Purpose-Code", requestContext.purposeCode);
        header(builder, "X-WWS-Case-Reference", requestContext.caseReference); header(builder, "X-WWS-Client-Reference", requestContext.clientReference);
    }

    private static void header(HttpRequest.Builder builder, String name, String value) { if (value != null && !value.isBlank()) builder.header(name, value); }
    private static String jsonEscape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }

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
