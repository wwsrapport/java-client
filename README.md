# WWSrapport Java client

Official Java SDK for the WWSrapport API.

`deriveBagReference`, `searchRegistryByBag` and `getReportVerification` expose the Solana attestation flow. `WebhookEvents.ALL` contains all 27 supported event types.

## Links

- API overview and Swagger: https://wwsrapport.nl/api/docs
- OpenAPI JSON: https://wwsrapport.nl/api/openapi.json
- Request API access: https://wwsrapport.nl/api/toegang-aanvragen
- GitHub organization: https://github.com/wwsrapport

## Install

Until the package is published to Maven Central, use this repository as a source dependency.

```xml
<dependency>
  <groupId>nl.wwsrapport</groupId>
  <artifactId>wwsrapport-client</artifactId>
  <version>0.2.1</version>
</dependency>
```

## Use

```java
import nl.wwsrapport.client.WwsrapportClient;

var client = WwsrapportClient.fromApiKey(System.getenv("WWSRAPPORT_API_KEY"));

String report = client.createReport("""
{
  "address": {
    "postcode": "3905RB",
    "house_number": "4",
    "country": "NL"
  },
  "customer_reference": "crm-demo-001",
  "input": {
    "living_area_m2": 53,
    "energy_label": "E"
  }
}
""", "crm-demo-001");

System.out.println(report);
```

## Supported resources

- Property prefill
- Report validation, creation, listing, retrieval and recalculation
- Calculation JSON and improvement advice JSON
- WWS report and improvement advice PDF downloads
- Usage and rulesets
- Webhook endpoint management, test deliveries and retries

Report creation and recalculation require an `Idempotency-Key`.

## Webhooks

```java
boolean valid = WebhookVerifier.verify(
    rawBody,
    timestampHeader,
    signatureHeader,
    System.getenv("WWSRAPPORT_WEBHOOK_SECRET")
);
```

The signature format is `v1=<hex-hmac-sha256>`, signed over:

```text
{timestamp}.{raw_body}
```

## Development

```bash
mvn test
```
