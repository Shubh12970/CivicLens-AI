package com.civiclens.user;

import com.civiclens.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates HS256 JWTs. A token carries the user id and a token version; the version is compared with the
 * database on each request so logout (version bump) revokes all existing tokens.
 */
@Service
public class JwtService {

    public static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.security() == null ? null : props.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is required (at least "
                    + MIN_SECRET_LENGTH + " characters). See .env.example.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = props.security().jwtTtl() == null ? Duration.ofHours(12) : props.security().jwtTtl();
        this.issuer = props.security().jwtIssuer() == null ? "civiclens" : props.security().jwtIssuer();
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("tv", user.getTokenVersion())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expires);
    }

    /** Returns the claims if the signature, issuer and expiry are valid; empty otherwise. */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer version = claims.get("tv", Integer.class);
            if (version == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(UUID.fromString(claims.getSubject()), version));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(UUID userId, int tokenVersion) {
    }
}
