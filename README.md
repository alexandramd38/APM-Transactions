# APM-Payments

Spring Boot service that receives alternative payment method (APM) transactions.

## Run

```
mvn package
java -jar target/apm-payments-0.0.1.jar
```

The application starts on port _8090_ (`server.port` in `application.properties`).

## API

`POST /v1/apm/payments`

Request body (all fields required, unknown fields are rejected):

| Field            | Rules                                                                                                 |
| ---------------- | ----------------------------------------------------------------------------------------------------- |
| `merchantId`     | Max 50 chars; letters, digits, `-` and `_` only                                                        |
| `orderReference` | Max 50 chars; letters, digits, `-` and `_` only                                                        |
| `amount`         | Between `0.01` and `100000.00`, max 2 decimals                                                         |
| `currency`       | Existing ISO 4217 currency code, e.g. `EUR`, supported by the payment method                           |
| `paymentMethod`  | One of `PAYPAL`, `IDEAL`, `KLARNA`, `SOFORT`, `BANCONTACT`, `GIROPAY`, `BLIK`, `ALIPAY`, `WECHAT_PAY` |
| `countryCode`    | Existing ISO 3166-1 alpha-2 country code, e.g. `NL`, supported by the payment method                   |
| `returnUrl`      | http(s) URL, max 2048 chars                                                                            |

Currencies and countries accepted per payment method (defined in `PaymentMethod`):

| Payment method | Currencies                               | Countries                              |
| -------------- | ---------------------------------------- | -------------------------------------- |
| `PAYPAL`       | EUR, USD, GBP, PLN, SEK, NOK, DKK, CHF   | any                                    |
| `IDEAL`        | EUR                                      | NL                                     |
| `KLARNA`       | EUR, SEK, NOK, DKK, GBP, USD             | DE, AT, NL, BE, FI, SE, NO, DK, GB, US |
| `SOFORT`       | EUR                                      | DE, AT, BE, NL, IT, ES                 |
| `BANCONTACT`   | EUR                                      | BE                                     |
| `GIROPAY`      | EUR                                      | DE                                     |
| `BLIK`         | PLN                                      | PL                                     |
| `ALIPAY`       | CNY, USD, EUR, GBP                       | any                                    |
| `WECHAT_PAY`   | CNY, USD, EUR, GBP                       | any                                    |

Example:

```
curl -X POST http://localhost:8090/v1/apm/payments \
  -H 'Content-Type: application/json' \
  -d '{
        "merchantId": "MERCHANT-1",
        "orderReference": "ORDER-123",
        "amount": 49.99,
        "currency": "EUR",
        "paymentMethod": "IDEAL",
        "countryCode": "NL",
        "returnUrl": "https://merchant.example.com/return"
      }'
```

Responses:

- `200 OK` - request received:
  ```
  {"transactionId": "998c0560-6365-40d0-91fb-f77e07059674", "orderReference": "ORDER-123", "status": "RECEIVED"}
  ```
- `400 Bad Request` with code `4000` - one or more fields failed validation. Every failing field is listed:
  ```
  {"code": "4000", "message": "Request validation failed with 2 error(s)",
   "errors": [{"field": "countryCode", "message": "is not supported by IDEAL (supported: [NL])", "rejectedValue": "DE"},
              {"field": "currency", "message": "is not supported by IDEAL (supported: [EUR])", "rejectedValue": "USD"}],
   "dateTime": "..."}
  ```
- `400 Bad Request` with code `4001` - body is missing, is not valid JSON, has an unknown field, or a value has the wrong type (e.g. unknown `paymentMethod`, non-numeric `amount`).
- `405 Method Not Allowed` with code `4050` - any method other than `POST`.
- `415 Unsupported Media Type` with code `4150` - `Content-Type` is not `application/json`.

## Logs

Logs are written to the console and to `logs/apm-payments.log` (override with the `LOG_FILE` environment variable).
Every rejected request logs at `WARN`: one line per failing field, plus a summary line with the HTTP status and the full error response:

```
WARN ... RequestExceptionHandler : Validation failed for orderReference = ORDER-123: field 'currency' is not supported by IDEAL (supported: [EUR]) (rejected value: 'USD')
WARN ... RequestExceptionHandler : Rejected POST /v1/apm/payments with HTTP 400: Fault{code='4000', ...}
```

## Negative test scenarios

Each command below triggers a rejection and a `WARN` log entry. Start the service first.

```
URL=http://localhost:8090/v1/apm/payments
H='Content-Type: application/json'

# 1. All fields missing -> 400 / 4000, 7 errors
curl -i -X POST $URL -H "$H" -d '{}'

# 2. Invalid formats: merchantId characters, unknown currency/country, non-http URL -> 400 / 4000
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT 1!","orderReference":"ORDER-123","amount":49.99,"currency":"XYZ","paymentMethod":"IDEAL","countryCode":"XX","returnUrl":"ftp://merchant.example.com"}'

# 3. Amount too low -> 400 / 4000
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":0,"currency":"EUR","paymentMethod":"IDEAL","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 4. Amount too high -> 400 / 4000
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":100000.01,"currency":"EUR","paymentMethod":"IDEAL","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 5. Amount with 3 decimals -> 400 / 4000
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":49.999,"currency":"EUR","paymentMethod":"IDEAL","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 6. Currency and country not supported by the payment method (IDEAL is EUR/NL only) -> 400 / 4000
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":49.99,"currency":"USD","paymentMethod":"IDEAL","countryCode":"DE","returnUrl":"https://merchant.example.com/return"}'

# 7. Unknown payment method -> 400 / 4001
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":49.99,"currency":"EUR","paymentMethod":"BITCOIN","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 8. Non-numeric amount -> 400 / 4001
curl -i -X POST $URL -H "$H" -d '{"merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":"abc","currency":"EUR","paymentMethod":"IDEAL","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 9. Unknown field -> 400 / 4001
curl -i -X POST $URL -H "$H" -d '{"cardNumber":"4111111111111111","merchantId":"MERCHANT-1","orderReference":"ORDER-123","amount":49.99,"currency":"EUR","paymentMethod":"IDEAL","countryCode":"NL","returnUrl":"https://merchant.example.com/return"}'

# 10. Malformed JSON -> 400 / 4001
curl -i -X POST $URL -H "$H" -d '{"merchantId": '

# 11. Missing body -> 400 / 4001
curl -i -X POST $URL -H "$H"

# 12. Wrong content type -> 415 / 4150
curl -i -X POST $URL -H 'Content-Type: text/plain' -d 'hello'

# 13. Wrong HTTP method -> 405 / 4050
curl -i -X GET $URL
```

## Mock Splunk

`tools/mock-splunk/` contains a fake Splunk MCP connector that answers Splunk searches from these
logs, so Devin (including Devin Oncall) can investigate them without a real Splunk instance. See
[tools/mock-splunk/README.md](tools/mock-splunk/README.md).
