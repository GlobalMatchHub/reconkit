package dev.sellerkit.reconkit.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and reads the two tokens.
 *
 * <p>The access token is short lived and carries the tenant and role, so that an
 * authenticated request needs no database lookup to be scoped. The refresh token carries
 * nothing but an identity and its own id, because it lives long enough to be worth
 * stealing and should not be a portable copy of the user's permissions. A role revoked at
 * ten past the hour therefore takes effect on the next refresh rather than never.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${reconkit.jwt.secret}") String secret,
                      @Value("${reconkit.jwt.access-ttl-minutes:30}") long accessTtlMinutes,
                      @Value("${reconkit.jwt.refresh-ttl-days:14}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public String issueAccessToken(String email, String tenantId, String role, String displayName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("tenant", tenantId)
                .claim("role", role)
                .claim("name", displayName)
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .claim("typ", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Claims parseRefresh(String token) {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("not a refresh token");
        }
        return claims;
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
