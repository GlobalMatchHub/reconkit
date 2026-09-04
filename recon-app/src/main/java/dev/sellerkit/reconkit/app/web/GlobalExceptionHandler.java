package dev.sellerkit.reconkit.app.web;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * A lost optimistic lock is a conflict, not a server error. The client is told to
     * reload, because the row it was editing has been changed by somebody else and the
     * only safe next step is to look at what they wrote.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> conflict(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(
                "this record was changed by someone else, reload and try again"));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(body(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> unprocessable(IllegalStateException ex) {
        return ResponseEntity.unprocessableEntity().body(body(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        log.error("unhandled failure", ex);
        return ResponseEntity.internalServerError().body(body("unexpected failure: " + ex.getMessage()));
    }

    private Map<String, Object> body(String message) {
        return Map.of("message", message, "at", Instant.now().toString());
    }
}
