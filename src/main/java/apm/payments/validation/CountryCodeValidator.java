package apm.payments.validation;

import java.util.Locale;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CountryCodeValidator implements ConstraintValidator<ValidCountryCode, String> {

  private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || value.isBlank() || ISO_COUNTRIES.contains(value);
  }
}
