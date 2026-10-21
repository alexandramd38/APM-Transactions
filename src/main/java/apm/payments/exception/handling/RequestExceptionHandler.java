package apm.payments.exception.handling;

import static java.time.ZoneOffset.UTC;

import apm.payments.model.ApmPaymentRequest;
import apm.payments.model.Fault;
import apm.payments.model.FieldViolation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RequestExceptionHandler {

  static final String VALIDATION_ERROR = "4000";
  static final String UNREADABLE_BODY = "4001";
  static final String METHOD_NOT_ALLOWED = "4050";
  static final String UNSUPPORTED_MEDIA_TYPE = "4150";

  private static final Logger logger = LoggerFactory.getLogger(RequestExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Fault> handleInvalidFieldsException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<FieldViolation> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage(), e.getRejectedValue()))
            .sorted(
                Comparator.comparing(FieldViolation::getField)
                    .thenComparing(FieldViolation::getMessage))
            .collect(Collectors.toList());
    Object target = exception.getBindingResult().getTarget();
    String orderReference =
        target instanceof ApmPaymentRequest
            ? ((ApmPaymentRequest) target).getOrderReference()
            : null;
    errors.forEach(
        e ->
            logger.warn(
                "Validation failed for orderReference = {}: field '{}' {} (rejected value: '{}')",
                orderReference,
                e.getField(),
                e.getMessage(),
                e.getRejectedValue()));
    return fault(
        HttpStatus.BAD_REQUEST,
        VALIDATION_ERROR,
        "Request validation failed with " + errors.size() + " error(s)",
        errors,
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Fault> handleUnreadableMessageException(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    Throwable cause = exception.getCause();
    List<FieldViolation> errors = List.of();
    String message;
    if (cause instanceof UnrecognizedPropertyException) {
      String field = ((UnrecognizedPropertyException) cause).getPropertyName();
      message = "Request contains an unknown field";
      errors = List.of(new FieldViolation(field, "is not a recognized field", null));
    } else if (cause instanceof InvalidFormatException
        && ((InvalidFormatException) cause).getTargetType().isEnum()) {
      InvalidFormatException invalidFormat = (InvalidFormatException) cause;
      message = "Request contains an invalid value";
      errors =
          List.of(
              new FieldViolation(
                  fieldPath(invalidFormat),
                  "must be one of "
                      + Arrays.toString(invalidFormat.getTargetType().getEnumConstants()),
                  invalidFormat.getValue()));
    } else if (cause instanceof InvalidFormatException) {
      InvalidFormatException invalidFormat = (InvalidFormatException) cause;
      message = "Request contains an invalid value";
      errors =
          List.of(
              new FieldViolation(
                  fieldPath(invalidFormat),
                  "has an invalid format, expected "
                      + invalidFormat.getTargetType().getSimpleName(),
                  invalidFormat.getValue()));
    } else if (cause instanceof MismatchedInputException
        && !((MismatchedInputException) cause).getPath().isEmpty()) {
      MismatchedInputException mismatched = (MismatchedInputException) cause;
      message = "Request contains an invalid value";
      errors =
          List.of(
              new FieldViolation(
                  fieldPath(mismatched),
                  "has an invalid type, expected " + mismatched.getTargetType().getSimpleName(),
                  null));
    } else if (cause instanceof JsonParseException) {
      message = "Malformed JSON request body";
    } else {
      message = "Request body is missing or unreadable";
    }
    logger.warn("Unreadable request body: {} {}", message, errors);
    return fault(HttpStatus.BAD_REQUEST, UNREADABLE_BODY, message, errors, request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Fault> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    String message =
        "Content-Type '" + exception.getContentType() + "' is not supported, use application/json";
    logger.warn(message);
    return fault(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE, message, List.of(), request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Fault> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    String message =
        "HTTP method " + exception.getMethod() + " is not supported for " + request.getRequestURI();
    logger.warn(message);
    return fault(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED, message, List.of(), request);
  }

  private static String fieldPath(JsonMappingException exception) {
    return exception.getPath().stream()
        .map(
            reference ->
                reference.getFieldName() != null
                    ? reference.getFieldName()
                    : "[" + reference.getIndex() + "]")
        .collect(Collectors.joining("."));
  }

  private static ResponseEntity<Fault> fault(
      HttpStatus status,
      String code,
      String message,
      List<FieldViolation> errors,
      HttpServletRequest request) {
    Fault fault = new Fault(code, message, errors, OffsetDateTime.now(UTC));
    logger.warn(
        "Rejected {} {} with HTTP {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        fault);
    return ResponseEntity.status(status).body(fault);
  }
}
