package vn.ptit.soa.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class PaymentAccessStateFilter extends OncePerRequestFilter {
    private final UserAccessStateClient access;
    private final ObjectMapper json;

    PaymentAccessStateFilter(UserAccessStateClient access, ObjectMapper json) {
        this.access = access;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean protectedRead = "GET".equals(request.getMethod())
                && (path.equals("/api/v1/payments/deposits")
                || path.matches("/api/v1/payments/(?:contracts|deposits)/[^/]+"));
        boolean protectedWrite = "POST".equals(request.getMethod())
                && path.matches("/api/v1/payments/(?:contracts/[^/]+/complete|deposits/[^/]+/release)");
        if ((protectedRead || protectedWrite)
                && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            try {
                access.requireActiveParticipant(jwt.getToken());
            } catch (UserAccessStateClient.AccountForbiddenException denied) {
                PaymentSecurityConfig.writeError(json, response, request.getRequestURI(),
                        HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active or not a participant.");
                return;
            } catch (UserAccessStateClient.AccessStateUnavailableException unavailable) {
                PaymentSecurityConfig.writeError(json, response, request.getRequestURI(),
                        HttpStatus.SERVICE_UNAVAILABLE, "ACCESS_STATE_UNAVAILABLE", "Cannot verify account state.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
