package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.AppUserRepository;
import dev.sellerkit.reconkit.app.security.JwtService;
import dev.sellerkit.reconkit.domain.model.AppUser;
import io.jsonwebtoken.Claims;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                String email, String displayName, String role, String tenantId) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        AppUser user = users.findByEmail(request.email()).orElse(null);
        // The same response for an unknown address and a wrong password. Telling the two
        // apart hands an attacker a list of valid operator accounts for free.
        if (user == null || !user.isEnabled()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "email or password is incorrect"));
        }
        return ResponseEntity.ok(tokensFor(user));
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * <p>The user is reloaded here rather than trusted from the token. That is the point
     * of keeping permissions out of the refresh token: a disabled account or a changed
     * role takes effect at the next refresh instead of surviving until the long lived
     * token expires.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            Claims claims = jwtService.parseRefresh(request.refreshToken());
            AppUser user = users.findByEmail(claims.getSubject()).orElse(null);
            if (user == null || !user.isEnabled()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.Map.of("message", "account is no longer active"));
            }
            return ResponseEntity.ok(tokensFor(user));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "refresh token is not valid"));
        }
    }

    private TokenResponse tokensFor(AppUser user) {
        return new TokenResponse(
                jwtService.issueAccessToken(user.getEmail(), user.getTenantId(),
                        user.getRole().name(), user.getDisplayName()),
                jwtService.issueRefreshToken(user.getEmail()),
                jwtService.accessTtlSeconds(),
                user.getEmail(), user.getDisplayName(), user.getRole().name(), user.getTenantId());
    }
}
