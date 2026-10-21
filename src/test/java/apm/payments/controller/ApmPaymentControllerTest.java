package apm.payments.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ApmPaymentController.class)
class ApmPaymentControllerTest {

  private static final String URL = "/v1/apm/payments";
  private static final String VALID_REQUEST =
      "{"
          + "\"merchantId\":\"MERCHANT-1\","
          + "\"orderReference\":\"ORDER-123\","
          + "\"amount\":49.99,"
          + "\"currency\":\"EUR\","
          + "\"paymentMethod\":\"IDEAL\","
          + "\"countryCode\":\"NL\","
          + "\"returnUrl\":\"https://merchant.example.com/return\""
          + "}";

  @Autowired private MockMvc mockMvc;

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String errorMessages(String field) {
    return "$.errors[?(@.field == '" + field + "')].message";
  }

  @Test
  void receivePayment_validRequest_returnsOk() throws Exception {
    postJson(VALID_REQUEST)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderReference").value("ORDER-123"))
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(
            jsonPath("$.transactionId")
                .value(
                    matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
  }

  @Test
  void receivePayment_missingFields_returnsAllErrors() throws Exception {
    postJson("{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4000"))
        .andExpect(jsonPath("$.errors", hasSize(7)))
        .andExpect(jsonPath(errorMessages("merchantId")).value(hasItem("must not be blank")))
        .andExpect(jsonPath(errorMessages("orderReference")).value(hasItem("must not be blank")))
        .andExpect(jsonPath(errorMessages("amount")).value(hasItem("must not be null")))
        .andExpect(jsonPath(errorMessages("currency")).value(hasItem("must not be blank")))
        .andExpect(jsonPath(errorMessages("paymentMethod")).value(hasItem("must not be null")))
        .andExpect(jsonPath(errorMessages("countryCode")).value(hasItem("must not be blank")))
        .andExpect(jsonPath(errorMessages("returnUrl")).value(hasItem("must not be blank")));
  }

  @Test
  void receivePayment_invalidFieldFormats_returnsErrorPerField() throws Exception {
    String request =
        VALID_REQUEST
            .replace("MERCHANT-1", "MERCHANT 1!")
            .replace("ORDER-123", "O".repeat(51))
            .replace("\"EUR\"", "\"XYZ\"")
            .replace("\"NL\"", "\"XX\"")
            .replace("https://merchant.example.com/return", "ftp://merchant.example.com");
    postJson(request)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(errorMessages("merchantId"))
                .value(hasItem("must contain only letters, digits, '-' and '_'")))
        .andExpect(
            jsonPath(errorMessages("orderReference"))
                .value(hasItem("size must be between 0 and 50")))
        .andExpect(
            jsonPath(errorMessages("currency"))
                .value(hasItem(containsString("valid ISO 4217 currency code"))))
        .andExpect(
            jsonPath(errorMessages("countryCode"))
                .value(hasItem(containsString("valid ISO 3166-1 alpha-2 country code"))))
        .andExpect(
            jsonPath(errorMessages("returnUrl")).value(hasItem("must be a valid http(s) URL")))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'currency')].rejectedValue").value(hasItem("XYZ")));
  }

  @Test
  void receivePayment_amountTooLow_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("49.99", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath(errorMessages("amount")).value(hasItem("must be at least 0.01")));
  }

  @Test
  void receivePayment_amountTooHigh_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("49.99", "100000.01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath(errorMessages("amount")).value(hasItem("must not exceed 100000.00")));
  }

  @Test
  void receivePayment_amountWithTooManyDecimals_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("49.99", "49.999"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(errorMessages("amount")).value(hasItem("must have at most 2 decimal places")));
  }

  @Test
  void receivePayment_currencyNotSupportedByPaymentMethod_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("\"EUR\"", "\"USD\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(
            jsonPath(errorMessages("currency"))
                .value(hasItem("is not supported by IDEAL (supported: [EUR])")));
  }

  @Test
  void receivePayment_countryNotSupportedByPaymentMethod_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("\"NL\"", "\"DE\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(
            jsonPath(errorMessages("countryCode"))
                .value(hasItem("is not supported by IDEAL (supported: [NL])")));
  }

  @Test
  void receivePayment_unknownPaymentMethod_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("IDEAL", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4001"))
        .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("must be one of [PAYPAL")))
        .andExpect(jsonPath("$.errors[0].rejectedValue").value("UNKNOWN"));
  }

  @Test
  void receivePayment_nonNumericAmount_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("49.99", "\"abc\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4001"))
        .andExpect(jsonPath("$.errors[0].field").value("amount"))
        .andExpect(jsonPath("$.errors[0].rejectedValue").value("abc"));
  }

  @Test
  void receivePayment_unknownField_returnsBadRequest() throws Exception {
    postJson(VALID_REQUEST.replace("{", "{\"cardNumber\":\"4111\","))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4001"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"))
        .andExpect(jsonPath("$.errors[0].message").value("is not a recognized field"));
  }

  @Test
  void receivePayment_malformedJson_returnsBadRequest() throws Exception {
    postJson("{\"merchantId\": ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4001"));
  }

  @Test
  void receivePayment_missingBody_returnsBadRequest() throws Exception {
    postJson("")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4001"))
        .andExpect(jsonPath("$.message").value("Request body is missing or unreadable"));
  }

  @Test
  void receivePayment_unsupportedContentType_returnsUnsupportedMediaType() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(VALID_REQUEST))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("4150"));
  }

  @Test
  void receivePayment_unsupportedMethod_returnsMethodNotAllowed() throws Exception {
    mockMvc
        .perform(get(URL))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("4050"));
  }
}
