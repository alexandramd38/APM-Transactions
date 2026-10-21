package apm.payments.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

  @Test
  void receivePayment_validRequest_returnsOk() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
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
  void receivePayment_missingFields_returnsBadRequest() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("4000"))
        .andExpect(jsonPath("$.message").value(containsString("merchantId must not be blank")))
        .andExpect(jsonPath("$.message").value(containsString("paymentMethod must not be null")));
  }

  @Test
  void receivePayment_invalidValues_returnsBadRequest() throws Exception {
    String request =
        VALID_REQUEST
            .replace("49.99", "0")
            .replace("\"EUR\"", "\"euro\"")
            .replace("https://merchant.example.com/return", "not-a-url");
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("amount")))
        .andExpect(jsonPath("$.message").value(containsString("currency")))
        .andExpect(jsonPath("$.message").value(containsString("returnUrl")));
  }

  @Test
  void receivePayment_unknownPaymentMethod_returnsBadRequest() throws Exception {
    String request = VALID_REQUEST.replace("IDEAL", "UNKNOWN");
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed request body"));
  }
}
