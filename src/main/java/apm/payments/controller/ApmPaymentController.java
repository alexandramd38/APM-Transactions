package apm.payments.controller;

import apm.payments.model.ApmPaymentRequest;
import apm.payments.model.ApmPaymentResponse;
import apm.payments.model.PaymentStatus;
import java.util.UUID;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/apm/payments")
public class ApmPaymentController {

  private static final Logger logger = LoggerFactory.getLogger(ApmPaymentController.class);

  @PostMapping
  public ResponseEntity<ApmPaymentResponse> receivePayment(
      @Valid @RequestBody ApmPaymentRequest request) {
    logger.info("Received APM payment request: " + request);
    ApmPaymentResponse response =
        new ApmPaymentResponse(
            UUID.randomUUID().toString(), request.getOrderReference(), PaymentStatus.RECEIVED);
    logger.info(
        "APM payment request for orderReference = "
            + request.getOrderReference()
            + " was received with transactionId = "
            + response.getTransactionId());
    return ResponseEntity.ok(response);
  }
}
