package com.groupcc2.recruitment.common.exception;

import com.groupcc2.recruitment.admin.controller.AdminUserController;
import com.groupcc2.recruitment.users.controller.UserController;
import com.groupcc2.recruitment.users.service.AuthorizationDeniedException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {UserController.class, AdminUserController.class})
public class UserExceptionHandler {
    public record ErrorResponse(String code) {}

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("USER_NOT_FOUND"));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied(AuthorizationDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("FORBIDDEN"));
    }
}
