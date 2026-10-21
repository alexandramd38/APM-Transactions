package apm.payments.exception.handling;

import static java.time.ZoneOffset.UTC;

import apm.payments.model.Fault;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RequestExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(RequestExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Fault> handleInvalidFieldsException(
      MethodArgumentNotValidException exception) {
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " " + e.getDefaultMessage())
            .sorted()
            .collect(Collectors.toList());
    return badRequest(errors.toString());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Fault> handleUnreadableMessageException(
      HttpMessageNotReadableException exception) {
    return badRequest("Malformed request body");
  }

  private ResponseEntity<Fault> badRequest(String message) {
    Fault fault = new Fault("4000", message, OffsetDateTime.now(UTC));
    logger.warn("Request processing failed due to validation errors: " + fault);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(fault);
  }
}
