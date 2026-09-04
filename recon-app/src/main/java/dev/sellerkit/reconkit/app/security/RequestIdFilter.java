package dev.sellerkit.reconkit.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps one identifier on everything a single call produces: log lines, audit rows, and
 * the response header. When an operator says a settlement changed and cannot say how, the
 * request id is what turns that into a query rather than an investigation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static String current() {
        String value = CURRENT.get();
        return value == null ? "none" : value;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 18);
        }
        CURRENT.set(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            CURRENT.remove();
            MDC.remove("requestId");
        }
    }
}
