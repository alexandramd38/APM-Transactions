package apm.payments.validation;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

  private static final Set<String> ISO_CURRENCIES =
      Currency.getAvailableCurrencies().stream()
          .map(Currency::getCurrencyCode)
          .collect(Collectors.toUnmodifiableSet());

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || value.isBlank() || ISO_CURRENCIES.contains(value);
  }
}
