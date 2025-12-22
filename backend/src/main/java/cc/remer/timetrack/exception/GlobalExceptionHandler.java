package cc.remer.timetrack.exception;

import cc.remer.timetrack.api.model.ErrorResponse;
import cc.remer.timetrack.usecase.recurringoffday.CreateRecurringOffDayExemption.ExemptionAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("USER_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmailException(DuplicateEmailException ex) {
        log.warn("Duplicate email: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("DUPLICATE_EMAIL", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("FORBIDDEN", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(RecurringOffDayNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecurringOffDayNotFoundException(RecurringOffDayNotFoundException ex) {
        log.warn("Recurring off-day not found: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("RECURRING_OFF_DAY_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(TimeOffNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTimeOffNotFoundException(TimeOffNotFoundException ex) {
        log.warn("Time-off entry not found: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("TIME_OFF_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("INVALID_CREDENTIALS", "Ungültige Anmeldedaten");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        String details = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining(", "));

        ErrorResponse error = createErrorResponse("VALIDATION_ERROR", "Validierungsfehler", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("INVALID_ARGUMENT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ExemptionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleExemptionAlreadyExistsException(ExemptionAlreadyExistsException ex) {
        log.warn("Exemption already exists: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("EXEMPTION_ALREADY_EXISTS", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse error = createErrorResponse(
                "INTERNAL_ERROR",
                "Ein interner Fehler ist aufgetreten",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Create an error response with code and message.
     *
     * @param code the error code
     * @param message the error message
     * @return the error response
     */
    private ErrorResponse createErrorResponse(String code, String message) {
        return createErrorResponse(code, message, null);
    }

    /**
     * Create an error response with code, message, and details.
     *
     * @param code the error code
     * @param message the error message
     * @param details additional error details
     * @return the error response
     */
    private ErrorResponse createErrorResponse(String code, String message, String details) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        error.setMessage(message);
        error.setDetails(details);
        error.setTimestamp(OffsetDateTime.now());
        return error;
    }
}
