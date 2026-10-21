package apm.payments.model;

import java.time.OffsetDateTime;
import java.util.List;

public class Fault {

  private final String code;
  private final String message;
  private final List<FieldViolation> errors;
  private final OffsetDateTime dateTime;

  public Fault(String code, String message, List<FieldViolation> errors, OffsetDateTime dateTime) {
    this.code = code;
    this.message = message;
    this.errors = errors;
    this.dateTime = dateTime;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public List<FieldViolation> getErrors() {
    return errors;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  @Override
  public String toString() {
    return "Fault{code='"
        + code
        + "', message='"
        + message
        + "', errors="
        + errors
        + ", dateTime="
        + dateTime
        + '}';
  }
}
