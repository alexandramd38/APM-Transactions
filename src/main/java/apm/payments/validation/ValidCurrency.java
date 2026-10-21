package apm.payments.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Documented
@Constraint(validatedBy = CurrencyValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {

  String message() default "must be a valid ISO 4217 currency code (e.g. EUR)";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
