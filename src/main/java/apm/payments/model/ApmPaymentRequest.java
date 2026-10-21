package apm.payments.model;

import apm.payments.validation.SupportedByPaymentMethod;
import apm.payments.validation.ValidCountryCode;
import apm.payments.validation.ValidCurrency;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@SupportedByPaymentMethod
public class ApmPaymentRequest {

  private static final String IDENTIFIER_REGEXP = "^[A-Za-z0-9_-]*$";
  private static final String IDENTIFIER_MESSAGE = "must contain only letters, digits, '-' and '_'";

  @NotBlank
  @Size(max = 50)
  @Pattern(regexp = IDENTIFIER_REGEXP, message = IDENTIFIER_MESSAGE)
  private String merchantId;

  @NotBlank
  @Size(max = 50)
  @Pattern(regexp = IDENTIFIER_REGEXP, message = IDENTIFIER_MESSAGE)
  private String orderReference;

  @NotNull
  @DecimalMin(value = "0.01", message = "must be at least 0.01")
  @DecimalMax(value = "100000.00", message = "must not exceed 100000.00")
  @Digits(integer = 6, fraction = 2, message = "must have at most 2 decimal places")
  private BigDecimal amount;

  @NotBlank @ValidCurrency private String currency;

  @NotNull private PaymentMethod paymentMethod;

  @NotBlank @ValidCountryCode private String countryCode;

  @NotBlank
  @Size(max = 2048)
  @Pattern(regexp = "^https?://[^\\s/$.?#][^\\s]*$", message = "must be a valid http(s) URL")
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
