package apm.payments.model;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ApmPaymentRequest {

  @NotBlank
  @Size(max = 50)
  private String merchantId;

  @NotBlank
  @Size(max = 50)
  private String orderReference;

  @NotNull
  @DecimalMin(value = "0.01")
  @Digits(integer = 15, fraction = 2)
  private BigDecimal amount;

  @NotBlank
  @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 currency code")
  private String currency;

  @NotNull private PaymentMethod paymentMethod;

  @NotBlank
  @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 country code")
  private String countryCode;

  @NotBlank
  @Pattern(regexp = "^https?://.+", message = "must be a valid http(s) URL")
  private String returnUrl;

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getOrderReference() {
    return orderReference;
  }

  public void setOrderReference(String orderReference) {
    this.orderReference = orderReference;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getReturnUrl() {
    return returnUrl;
  }

  public void setReturnUrl(String returnUrl) {
    this.returnUrl = returnUrl;
  }

  @Override
  public String toString() {
    return "ApmPaymentRequest{"
        + "merchantId='"
        + merchantId
        + '\''
        + ", orderReference='"
        + orderReference
        + '\''
        + ", amount="
        + amount
        + ", currency='"
        + currency
        + '\''
        + ", paymentMethod="
        + paymentMethod
        + ", countryCode='"
        + countryCode
        + '\''
        + ", returnUrl='"
        + returnUrl
        + '\''
        + '}';
  }
}
