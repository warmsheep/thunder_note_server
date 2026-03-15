package com.flashnote.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private static final String USER_ID_CLAIM = "userId";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.access-expiration:3600000}")
    private long accessExpiration;

    @Value("${security.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Value("${security.jwt.issuer:flashnote}")
    private String issuer;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username) {
        return generateToken(userId, username, "access", accessExpiration);
    }

    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, "refresh", refreshExpiration);
    }

    private String generateToken(Long userId, String username, String tokenType, long expirationMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .claim(USER_ID_CLAIM, userId)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            return expectedType == null || expectedType.equals(tokenType);
        } catch (Exception ex) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Number userId = parseClaims(token).get(USER_ID_CLAIM, Number.class);
        return userId == null ? null : userId.longValue();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String getTokenType(String token) {
        return parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    }

    public long getAccessExpirationSeconds() {
        return accessExpiration / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpiration / 1000;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
