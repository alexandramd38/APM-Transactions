package apm.payments.model;

public class FieldViolation {

  private final String field;
  private final String message;
  private final Object rejectedValue;

  public FieldViolation(String field, String message, Object rejectedValue) {
    this.field = field;
    this.message = message;
    this.rejectedValue = rejectedValue;
  }

  public String getField() {
    return field;
  }

  public String getMessage() {
    return message;
  }

  public Object getRejectedValue() {
    return rejectedValue;
  }

  @Override
  public String toString() {
    return field + " " + message + " (rejected value: " + rejectedValue + ")";
  }
}
