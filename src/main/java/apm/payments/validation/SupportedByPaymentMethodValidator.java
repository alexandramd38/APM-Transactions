package apm.payments.validation;

import apm.payments.model.ApmPaymentRequest;
import apm.payments.model.PaymentMethod;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SupportedByPaymentMethodValidator
    implements ConstraintValidator<SupportedByPaymentMethod, ApmPaymentRequest> {

  private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  @Override
  public boolean isValid(ApmPaymentRequest request, ConstraintValidatorContext context) {
    PaymentMethod method = request.getPaymentMethod();
    if (method == null) {
      return true;
    }
    boolean valid = true;
    context.disableDefaultConstraintViolation();

    String currency = request.getCurrency();
    if (isIsoCurrency(currency) && !method.supportsCurrency(currency)) {
      valid = false;
      addViolation(
          context,
          "currency",
          "is not supported by "
              + method
              + " (supported: "
              + new TreeSet<>(method.getSupportedCurrencies())
              + ")");
    }

    String country = request.getCountryCode();
    if (country != null && ISO_COUNTRIES.contains(country) && !method.supportsCountry(country)) {
      valid = false;
      addViolation(
          context,
          "countryCode",
          "is not supported by "
              + method
              + " (supported: "
              + new TreeSet<>(method.getSupportedCountries())
              + ")");
    }
    return valid;
  }

  private static boolean isIsoCurrency(String currency) {
    if (currency == null) {
      return false;
    }
    try {
      Currency.getInstance(currency);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static void addViolation(
      ConstraintValidatorContext context, String field, String message) {
    context
        .buildConstraintViolationWithTemplate(message)
        .addPropertyNode(field)
        .addConstraintViolation();
  }
}
