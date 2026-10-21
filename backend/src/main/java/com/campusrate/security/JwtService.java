package com.campusrate.security;

import com.campusrate.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Creates and checks JWT login tokens.
 *
 * A JWT is a signed string that says "this is user 42, a student, valid until tomorrow".
 * The server signs it with a secret key, so nobody can change the user id or role
 * without the signature check failing. That means the server doesn't need to
 * store sessions: every request carries its own proof of who is calling.
 */
@Service
public class JwtService {

    /** What we learn from a valid token. */
    public record TokenUser(Long id, String role) {
    }

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Returns who the token belongs to, or empty if the token is fake, changed or expired. */
    public Optional<TokenUser> read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            return Optional.of(new TokenUser(Long.parseLong(claims.getSubject()), role == null ? User.ROLE_STUDENT : role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Shortcut used by tests. */
    public Optional<Long> readUserId(String token) {
        return read(token).map(TokenUser::id);
    }
}
