package vn.ptit.soa.payment.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.ptit.soa.payment.contract.DepositNotFoundException;
import vn.ptit.soa.payment.dispute.DisputeReadNotFoundException;

@Profile("secure")
@RestControllerAdvice(basePackages = "vn.ptit.soa.payment.security")
public class SecurePaymentExceptionHandler {
    @ExceptionHandler({DepositNotFoundException.class, DisputeReadNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource does not exist or is not accessible.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidId(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ID must be a UUID.", request);
    }

    @ExceptionHandler(PaymentActionForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "This account cannot perform the action.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code,
            String message, HttpServletRequest request) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Map.of(
                "timestamp", Instant.now().toString(), "status", status.value(),
                "code", code, "message", message, "path", request.getRequestURI()));
    }
}
