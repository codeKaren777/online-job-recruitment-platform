package com.groupcc2.recruitment.common.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.groupcc2.recruitment.profile.controller.ProfileController;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
import com.groupcc2.recruitment.users.service.*;
@RestControllerAdvice(assignableTypes=ProfileController.class)
public class ProfileExceptionHandler {
 public record ErrorResponse(String code) {}
 @ExceptionHandler(ProfileFailure.class) ResponseEntity<ErrorResponse> profile(ProfileFailure e){
 HttpStatus status=switch(e.code()) {
 case FORBIDDEN -> HttpStatus.FORBIDDEN;
 case NOT_FOUND -> HttpStatus.NOT_FOUND;
 case INVALID_FILE -> HttpStatus.BAD_REQUEST;
 case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
 case STORAGE_UNAVAILABLE,INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
 };return ResponseEntity.status(status).body(new ErrorResponse(e.code().name()));}
 @ExceptionHandler({InactiveAccountException.class,AuthorizationDeniedException.class,UserNotFoundException.class})
 ResponseEntity<ErrorResponse> denied(RuntimeException e){return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN"));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,
 org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 ResponseEntity<ErrorResponse> invalid(Exception e){return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST"));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
 ResponseEntity<ErrorResponse> tooLarge(Exception e){return ResponseEntity.status(413).body(new ErrorResponse("FILE_TOO_LARGE"));}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> unavailable(Exception e){return ResponseEntity.status(503).body(new ErrorResponse("PROFILE_UNAVAILABLE"));}
}
