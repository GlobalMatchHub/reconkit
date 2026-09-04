package dev.sellerkit.reconkit.app.security;

import dev.sellerkit.reconkit.app.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the access token, binds the tenant, and clears it again.
 *
 * <p>The clearing is in a finally block and is not optional. Servlet threads are pooled,
 * and a thread that finishes a request still holding a tenant identifier will hand it to
 * whoever lands on it next, which in a multi tenant system means one customer's dashboard
 * rendering another customer's transactions.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header != null && header.startsWith("Bearer ")) {
                Claims claims = jwtService.parse(header.substring(7));
                String tenantId = claims.get("tenant", String.class);
                String role = claims.get("role", String.class);
                TenantContext.set(tenantId);
                MDC.put("tenant", tenantId);
                MDC.put("actor", claims.getSubject());
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid token");
        } finally {
            TenantContext.clear();
            MDC.remove("tenant");
            MDC.remove("actor");
            SecurityContextHolder.clearContext();
        }
    }
}
