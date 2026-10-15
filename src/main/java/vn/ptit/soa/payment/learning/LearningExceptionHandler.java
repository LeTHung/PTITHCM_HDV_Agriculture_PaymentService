package vn.ptit.soa.payment.learning;

import java.time.Instant;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile({"learning", "database-learning"})
@RestControllerAdvice(basePackageClasses = LearningController.class)
public class LearningExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> invalidInput(Exception exception, HttpServletRequest request) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Body must be valid JSON with the expected fields.";
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "code", "VALIDATION_ERROR",
                "message", message,
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(409).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 409,
                "code", "CONFLICT",
                "message", exception.getMessage(),
                "path", request.getRequestURI()));
    }
}
