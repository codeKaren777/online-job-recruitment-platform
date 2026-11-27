package com.groupcc2.recruitment.common.exception;

import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.auth.service.EmailAlreadyRegisteredException;
import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import com.groupcc2.recruitment.auth.service.InvalidEmailVerificationTokenException;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
import com.groupcc2.recruitment.users.service.AuthorizationDeniedException;
import com.groupcc2.recruitment.users.service.DuplicateEmailException;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.InvalidRoleChangeException;
import com.groupcc2.recruitment.users.service.RoleRegistrationNotAllowedException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), safeValidationMessage(error)))
                .sorted(Comparator.comparing(ApiErrorResponse.FieldError::field)
                        .thenComparing(ApiErrorResponse.FieldError::message)).toList();
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more fields are invalid.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fields = exception.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldError(
                        v.getPropertyPath().toString().replaceAll(".*\\.", ""),
                        validationMessage(v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())))
                .sorted(Comparator.comparing(ApiErrorResponse.FieldError::field)).toList();
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more fields are invalid.", fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> methodValidation(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more fields are invalid.", null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class, RoleRegistrationNotAllowedException.class,
            InvalidPasswordResetTokenException.class, InvalidEmailVerificationTokenException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ApiErrorResponse> badRequest(Exception exception, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is invalid.", null);
    }

    @ExceptionHandler(InvalidRoleChangeException.class)
    ResponseEntity<ApiErrorResponse> invalidRoleChange(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_ROLE_CHANGE", "The requested role change is invalid.", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> uploadTooLarge(HttpServletRequest request) {
        return response(request, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The uploaded file is too large.", null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials(HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The supplied credentials are invalid.", null);
    }

    @ExceptionHandler({UserNotFoundException.class})
    ResponseEntity<ApiErrorResponse> notFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found.", null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiErrorResponse> noRoute(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "The requested resource was not found.", null);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, InactiveAccountException.class, AccessDeniedException.class})
    ResponseEntity<ApiErrorResponse> forbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", null);
    }

    @ExceptionHandler({EmailAlreadyRegisteredException.class, DuplicateEmailException.class})
    ResponseEntity<ApiErrorResponse> duplicateEmail(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "An account with that email already exists.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> conflict(Exception exception, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.", null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> rateLimited(HttpServletRequest request) {
        return response(request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.", null);
    }

    @ExceptionHandler(ApplicationFailure.class)
    ResponseEntity<ApiErrorResponse> applicationFailure(ApplicationFailure failure, HttpServletRequest request) {
        HttpStatus status = switch (failure.code()) {
            case PROFILE_INCOMPLETE -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_APPLICATION, JOB_NOT_ACCEPTING_APPLICATIONS, INVALID_STATUS_TRANSITION, APPLICATION_WITHDRAWN -> HttpStatus.CONFLICT;
            case INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return response(request, status, failure.code().name(), messageFor(status), null);
    }

    @ExceptionHandler(ProfileFailure.class)
    ResponseEntity<ApiErrorResponse> profileFailure(ProfileFailure failure, HttpServletRequest request) {
        HttpStatus status = switch (failure.code()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_FILE -> HttpStatus.BAD_REQUEST;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case STORAGE_UNAVAILABLE, INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return response(request, status, failure.code().name(), messageFor(status), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            log.error("Request failed with status {} (correlationId={})", exception.getStatusCode().value(), correlationId(request), exception);
            return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.", null);
        }
        String code = status == HttpStatus.NOT_FOUND ? "RESOURCE_NOT_FOUND"
                : status == HttpStatus.CONFLICT ? "CONFLICT"
                : status == HttpStatus.FORBIDDEN ? "FORBIDDEN"
                : status == HttpStatus.UNAUTHORIZED ? "AUTHENTICATION_REQUIRED"
                : status == HttpStatus.TOO_MANY_REQUESTS ? "RATE_LIMIT_EXCEEDED" : "BAD_REQUEST";
        return response(request, status, code, messageFor(status), null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure (correlationId={})", correlationId(request), exception);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.", null);
    }

    private static String safeValidationMessage(FieldError error) {
        return validationMessage(error.getCode());
    }

    private static String validationMessage(String constraint) {
        if (constraint == null) return "is invalid";
        return switch (constraint) {
            case "NotNull" -> "must not be null";
            case "NotBlank" -> "must not be blank";
            case "NotEmpty" -> "must not be empty";
            case "Email" -> "must be a well-formed email address";
            case "Size" -> "has an invalid length";
            case "Min", "DecimalMin" -> "is below the allowed minimum";
            case "Max", "DecimalMax" -> "exceeds the allowed maximum";
            case "Positive", "PositiveOrZero" -> "must be positive";
            case "Negative", "NegativeOrZero" -> "must be negative";
            case "Pattern" -> "does not match the required format";
            case "Past", "PastOrPresent" -> "must be a date in the past";
            case "Future", "FutureOrPresent" -> "must be a date in the future";
            default -> "is invalid";
        };
    }

    private static ResponseEntity<ApiErrorResponse> response(HttpServletRequest request, HttpStatus status,
                                                              String code, String message,
                                                              List<ApiErrorResponse.FieldError> fields) {
        return ResponseEntity.status(status).body(ApiErrorFactory.create(request, status.value(), code, message, fields));
    }

    private static String correlationId(HttpServletRequest request) {
        return Objects.toString(request.getAttribute(ApiErrorFactory.CORRELATION_ID_ATTRIBUTE), "unknown");
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request is invalid.";
            case UNAUTHORIZED -> "Authentication is required.";
            case FORBIDDEN -> "You do not have permission to perform this action.";
            case NOT_FOUND -> "The requested resource was not found.";
            case CONFLICT -> "The request conflicts with existing data.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please try again later.";
            case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable.";
            case PAYLOAD_TOO_LARGE -> "The uploaded file is too large.";
            default -> "The request could not be completed.";
        };
    }
}
