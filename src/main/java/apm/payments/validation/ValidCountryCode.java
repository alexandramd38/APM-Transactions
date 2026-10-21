package apm.payments.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCountryCode {

  String message() default "must be a valid ISO 3166-1 alpha-2 country code (e.g. NL)";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
