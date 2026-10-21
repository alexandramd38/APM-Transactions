package apm.payments.model;

import java.time.OffsetDateTime;

public class Fault {

  private final String code;
  private final String message;
  private final OffsetDateTime dateTime;

  public Fault(String code, String message, OffsetDateTime dateTime) {
    this.code = code;
    this.message = message;
    this.dateTime = dateTime;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  @Override
  public String toString() {
    return "Fault{code='" + code + "', message='" + message + "', dateTime=" + dateTime + '}';
  }
}
