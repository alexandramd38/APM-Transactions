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

Request body (all fields required):

| Field            | Description                                                                                                   |
| ---------------- | ------------------------------------------------------------------------------------------------------------- |
| `merchantId`     | Merchant identifier (max 50 chars)                                                                            |
| `orderReference` | Merchant order reference (max 50 chars)                                                                       |
| `amount`         | Amount, minimum `0.01`, max 2 decimals                                                                        |
| `currency`       | ISO 4217 currency code, e.g. `EUR`                                                                            |
| `paymentMethod`  | One of `PAYPAL`, `IDEAL`, `KLARNA`, `SOFORT`, `BANCONTACT`, `GIROPAY`, `BLIK`, `ALIPAY`, `WECHAT_PAY`         |
| `countryCode`    | ISO 3166-1 alpha-2 country code, e.g. `NL`                                                                    |
| `returnUrl`      | http(s) URL the shopper is redirected to after completing the payment                                         |

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
- `400 Bad Request` - missing/invalid fields or malformed body:
  ```
  {"code": "4000", "message": "[currency must be an ISO 4217 currency code]", "dateTime": "..."}
  ```
