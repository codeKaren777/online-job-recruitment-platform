package com.groupcc2.recruitment.common.exception;

import com.groupcc2.recruitment.application.controller.ApplicationController;
import com.groupcc2.recruitment.application.controller.EmployerApplicationController;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.users.service.AuthorizationDeniedException;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates application failures to HTTP; bodies carry only a stable error code, never internals. */
@RestControllerAdvice(assignableTypes = {ApplicationController.class, EmployerApplicationController.class})
public class ApplicationExceptionHandler {
    public record ErrorResponse(String code) {
    }

    @ExceptionHandler(ApplicationFailure.class)
    ResponseEntity<ErrorResponse> application(ApplicationFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_APPLICATION, JOB_NOT_ACCEPTING_APPLICATIONS, PROFILE_REQUIRED,
                    INVALID_STATUS_TRANSITION -> HttpStatus.CONFLICT;
            case INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(failure.code().name()));
    }

    @ExceptionHandler({InactiveAccountException.class, AuthorizationDeniedException.class, UserNotFoundException.class})
    ResponseEntity<ErrorResponse> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("FORBIDDEN"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> invalid() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST"));
    }
}
