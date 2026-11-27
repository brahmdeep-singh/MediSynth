package com.personal.medisynth.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.personal.medisynth.DTO.ApiError;

/**
 * One error shape for everything: {timestamp,status,error,code,message,path,fieldErrors?}.
 * Never includes stack traces, SQL, constraint names or secrets.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String detail = String.valueOf(rootMessage(ex));
        if (detail.contains("uq_appt_doctor_slot")) {
            return build(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "That time slot is no longer available", null, request);
        }
        if (detail.contains("uq_appt_patient_slot")) {
            return build(HttpStatus.CONFLICT, "PATIENT_SLOT_CONFLICT", "You already have an appointment at that time", null, request);
        }
        if (detail.contains("uq_users_email") || detail.contains("uq_reg_one_pending_per_user")) {
            return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "This email is already registered", null, request);
        }
        if (detail.contains("uq_doctor_license")) {
            return build(HttpStatus.CONFLICT, "LICENSE_ALREADY_REGISTERED", "This license number is already registered", null, request);
        }
        if (detail.contains("uq_lab_employee_id")) {
            return build(HttpStatus.CONFLICT, "EMPLOYEE_ID_ALREADY_REGISTERED", "This employee ID is already registered", null, request);
        }
        if (detail.contains("uq_research_active_per_dataset")) {
            return build(HttpStatus.CONFLICT, "DUPLICATE_DATASET_REQUEST",
                    "You already have a pending or approved request for this dataset", null, request);
        }
        log.warn("Data integrity violation: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex); // server log only; the client gets a generic message
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid", fields, request);
    }

    /** Covers malformed JSON, bad parameter types, 404/405/415 etc. with safe, generic messages. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(statusCode.value());
        } catch (IllegalArgumentException unknownStatus) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = switch (status) {
            case BAD_REQUEST -> "The request is malformed or has invalid parameters";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "HTTP method not supported for this endpoint";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type";
            case NOT_ACCEPTABLE -> "Not acceptable";
            default -> "The request could not be processed";
        };
        return build(status, status.name(), message, null, request);
    }

    private ResponseEntity<Object> build(HttpStatus status, String code, String message,
            Map<String, String> fieldErrors, WebRequest request) {
        String path = request.getDescription(false).replaceFirst("^uri=", "");
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
                fieldErrors);
        return ResponseEntity.status(status).body(error);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
