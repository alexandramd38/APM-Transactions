package apm.payments.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/** Checks that the request currency and country are supported by the chosen payment method. */
@Documented
@Constraint(validatedBy = SupportedByPaymentMethodValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedByPaymentMethod {

  String message() default "is not supported by the payment method";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
