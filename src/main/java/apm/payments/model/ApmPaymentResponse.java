package apm.payments.model;

public class ApmPaymentResponse {

  private final String transactionId;
  private final String orderReference;
  private final PaymentStatus status;

  public ApmPaymentResponse(String transactionId, String orderReference, PaymentStatus status) {
    this.transactionId = transactionId;
    this.orderReference = orderReference;
    this.status = status;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getOrderReference() {
    return orderReference;
  }

  public PaymentStatus getStatus() {
    return status;
  }
}
