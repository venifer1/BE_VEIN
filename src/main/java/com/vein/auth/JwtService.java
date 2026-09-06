package com.vein.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vein.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies HS256 access tokens using jjwt 0.12.6 (부록 H-2).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtl;
    private final long refreshTtl;

    public JwtService(@Value("${vein.security.jwt.secret}") String secret,
                      @Value("${vein.security.jwt.access-ttl}") long accessTtl,
                      @Value("${vein.security.jwt.refresh-ttl}") long refreshTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    /** Issue a signed access token whose subject is the user id. */
    public String issueAccess(User u, Instant now) {
        Instant exp = now.plusSeconds(accessTtl);
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("role", u.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /** Parse and verify a signed token; throws {@link io.jsonwebtoken.JwtException} on failure. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public long accessTtlSeconds() {
        return accessTtl;
    }

    public long refreshTtlSeconds() {
        return refreshTtl;
    }
}
