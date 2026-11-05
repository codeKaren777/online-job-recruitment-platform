package com.groupcc2.recruitment.common.exception;

import com.groupcc2.recruitment.auth.service.EmailAlreadyRegisteredException;
import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
import com.groupcc2.recruitment.users.service.DuplicateEmailException;
import com.groupcc2.recruitment.users.service.AuthorizationDeniedException;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.InvalidRoleChangeException;
import com.groupcc2.recruitment.users.service.RoleRegistrationNotAllowedException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Translates API failures to one stable response shape without exposing implementation details. */
@RestControllerAdvice
public class ApplicationExceptionHandler {
    public record ErrorResponse(String code, String message, String path, List<FieldErrorResponse> fieldErrors) {
        public ErrorResponse {
            fieldErrors = List.copyOf(fieldErrors);
        }
    }

    public record FieldErrorResponse(String field, String message) {
    }

    @ExceptionHandler(ApplicationFailure.class)
    ResponseEntity<ErrorResponse> application(ApplicationFailure failure, HttpServletRequest request) {
        return switch (failure.code()) {
            case PROFILE_INCOMPLETE -> response(HttpStatus.BAD_REQUEST, "PROFILE_INCOMPLETE",
                    "Complete your profile before applying.", request);
            case FORBIDDEN -> response(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "You are not allowed to perform this operation.", request);
            case NOT_FOUND -> response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "The requested application resource was not found.", request);
            case DUPLICATE_APPLICATION -> response(HttpStatus.CONFLICT, "DUPLICATE_APPLICATION",
                    "You have already applied to this job.", request);
            case JOB_NOT_ACCEPTING_APPLICATIONS -> response(HttpStatus.CONFLICT, "JOB_NOT_ACCEPTING_APPLICATIONS",
                    "This job is not accepting applications.", request);
            case INVALID_STATUS_TRANSITION -> response(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "The requested application status change is not allowed.", request);
            case INTEGRATION_UNAVAILABLE -> response(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE",
                    "A required service is temporarily unavailable.", request);
        };
    }

    @ExceptionHandler(ProfileFailure.class)
    ResponseEntity<ErrorResponse> profile(ProfileFailure failure, HttpServletRequest request) {
        return switch (failure.code()) {
            case FORBIDDEN -> response(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "You are not allowed to access this profile resource.", request);
            case NOT_FOUND -> response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "The requested profile resource was not found.", request);
            case INVALID_FILE -> response(HttpStatus.BAD_REQUEST, "INVALID_FILE",
                    "The uploaded file must be a supported PDF.", request);
            case FILE_TOO_LARGE -> response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The uploaded file exceeds the 5 MiB limit.", request);
            case APPLICATION_CV_IN_USE -> response(HttpStatus.CONFLICT, "APPLICATION_CV_IN_USE",
                    "A CV attached to a submitted application cannot be deleted.", request);
            case STORAGE_UNAVAILABLE, INTEGRATION_UNAVAILABLE -> response(HttpStatus.SERVICE_UNAVAILABLE,
                    failure.code().name(), "Document storage is temporarily unavailable.", request);
        };
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> userNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The requested user was not found.", request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this operation.", request);
    }

    @ExceptionHandler(InactiveAccountException.class)
    ResponseEntity<ErrorResponse> inactiveAccount(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "This account is not active.", request);
    }

    @ExceptionHandler({EmailAlreadyRegisteredException.class, DuplicateEmailException.class})
    ResponseEntity<ErrorResponse> duplicateEmail(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email address already exists.", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ErrorResponse> badCredentials(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The supplied credentials are invalid.", request);
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    ResponseEntity<ErrorResponse> invalidResetToken(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "The password reset token is invalid or expired.", request);
    }

    @ExceptionHandler({InvalidRoleChangeException.class, RoleRegistrationNotAllowedException.class})
    ResponseEntity<ErrorResponse> invalidRoleChange(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ROLE_CHANGE",
                "The requested role change is not allowed.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
                return validationErrors(exception.getBindingResult().getFieldErrors(), request);
        }

        @ExceptionHandler(BindException.class)
        ResponseEntity<ErrorResponse> binding(BindException exception, HttpServletRequest request) {
                return validationErrors(exception.getBindingResult().getFieldErrors(), request);
        }

        private ResponseEntity<ErrorResponse> validationErrors(List<FieldError> errors, HttpServletRequest request) {
                List<FieldErrorResponse> fields = errors.stream()
                .map(this::fieldError)
                .sorted(Comparator.comparing(FieldErrorResponse::field).thenComparing(FieldErrorResponse::message))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException exception,
                                                       HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field).thenComparing(FieldErrorResponse::message))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> unreadableRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "The request body or one of its values is invalid.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception,
                                               HttpServletRequest request) {
        FieldErrorResponse field = new FieldErrorResponse(exception.getName(), "The supplied value has an invalid format.");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A request parameter has an invalid value.",
                request, List.of(field));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException exception,
                                                    HttpServletRequest request) {
        FieldErrorResponse field = new FieldErrorResponse(exception.getParameterName(), "This parameter is required.");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A required request parameter is missing.",
                request, List.of(field));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> missingPart(MissingServletRequestPartException exception,
                                               HttpServletRequest request) {
        FieldErrorResponse field = new FieldErrorResponse(exception.getRequestPartName(), "This file part is required.");
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A required request part is missing.",
                request, List.of(field));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The uploaded file exceeds the configured size limit.", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String code = knownStatus == null ? "HTTP_" + status.value() : knownStatus.name();
        String message = Objects.requireNonNullElse(exception.getReason(), "The request could not be completed.");
        return response(status, code, message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected server error occurred.", request);
    }

    private FieldErrorResponse fieldError(FieldError error) {
        return new FieldErrorResponse(error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value."));
    }

    private ResponseEntity<ErrorResponse> response(HttpStatusCode status, String code, String message,
                                                   HttpServletRequest request) {
        return response(status, code, message, request, List.of());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatusCode status, String code, String message,
                                                   HttpServletRequest request, List<FieldErrorResponse> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, request.getRequestURI(), fieldErrors));
    }
}
