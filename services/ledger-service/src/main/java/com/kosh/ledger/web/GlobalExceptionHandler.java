package com.kosh.ledger.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.kosh.ledger.domain.InvalidStatusChangeException;
import com.kosh.ledger.service.AccountNotActiveException;
import com.kosh.ledger.service.AccountNotFoundException;
import com.kosh.ledger.service.EntryNotFoundException;
import com.kosh.ledger.service.InsufficientFundsException;
import com.kosh.ledger.service.InvalidAccountException;
import com.kosh.ledger.service.InvalidEntryException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404: the thing in the URL doesn't exist
    @ExceptionHandler({AccountNotFoundException.class, EntryNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 422: a well-formed request, but a business rule says no
    @ExceptionHandler({InsufficientFundsException.class, AccountNotActiveException.class,
            InvalidEntryException.class})
    public ResponseEntity<ApiError> businessRule(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request);
    }

    // 409: the request conflicts with the current state
    @ExceptionHandler(InvalidStatusChangeException.class)
    public ResponseEntity<ApiError> badStatusChange(InvalidStatusChangeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // 409: a database constraint said no (for now: mostly a reused Idempotency-Key)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT,
                "The request conflicts with existing data (for example, a reused Idempotency-Key)", request);
    }

    // 400: the request itself is broken
    @ExceptionHandler({InvalidAccountException.class, MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest request) {
        String message;
        if (ex instanceof HttpMessageNotReadableException) {
            message = "Request body is missing or is not valid JSON (check field types and enum values)";
        } else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Invalid value for '" + mismatch.getName() + "'";
        } else {
            message = ex.getMessage();
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // 400 from @Valid, with one message per bad field
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), "Validation failed",
                request.getRequestURI(), Instant.now(), fields);
        return ResponseEntity.status(status).body(body);
    }

    // Anything else
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        // Spring's own web errors (unknown URL, wrong HTTP method, ...) already know their status.
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return build(status, status.getReasonPhrase(), request);
        }
        // Everything else is a bug: log it all here, tell the client nothing internal.
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, request));
    }
}
