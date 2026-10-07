package com.example.loop_assessment.exception;

import com.example.loop_assessment.service.ServiceException;
import com.example.loop_assessment.soap_resources.UpstreamException;
import jakarta.validation.ConstraintViolationException;

import java.util.LinkedHashMap;

import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    ResponseEntity<ProblemDetail> service(ServiceException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<ProblemDetail> upstream(UpstreamException e) {
        log.atWarn().addKeyValue("retryable", e.retryable()).log("Country provider failed", e);
        return problem(
                e.retryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                "PROVIDER_FAILURE",
                "Country provider is unavailable or returned an invalid response; try again later");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        var response =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        var errors = new LinkedHashMap<String, String>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        response.getBody().setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> invalid(Exception e) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Provide valid JSON, field values, and request parameters");
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class
    })
    ResponseEntity<ProblemDetail> conflict(Exception e) {
        return problem(
                HttpStatus.CONFLICT,
                "COUNTRY_CONFLICT",
                "Country already exists or was modified; fetch the latest data and retry");
    }

    @ExceptionHandler({
            DataAccessException.class,
            org.springframework.transaction.CannotCreateTransactionException.class
    })
    ResponseEntity<ProblemDetail> database(RuntimeException e) {
        log.error("Database operation failed", e);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE",
                "Data storage is temporarily unavailable; try again later");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> missing(Exception e) {
        return problem(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Endpoint was not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(Exception e) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this endpoint");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> media(Exception e) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Send JSON using Content-Type: application/json");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> unacceptable(Exception e) {
        return problem(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "Request a JSON response using Accept: application/json");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unexpected request failure", e);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred; contact support with the request ID");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        detail.setProperty("requestId", MDC.get("requestId"));
        var builder = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) builder.header("Retry-After", "30");
        return builder.body(detail);
    }
}
