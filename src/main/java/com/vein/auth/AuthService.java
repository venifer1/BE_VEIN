package com.vein.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.ops.AuditService;
import com.vein.user.User;
import com.vein.user.UserRepository;
import com.vein.user.UserStatus;

/**
 * Authentication, refresh-token rotation and reuse detection (부록 H-4).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public record AuthResult(String accessToken, String refreshToken, long expiresIn, User user) {
    }

    @Transactional
    public AuthResult login(String email, String rawPw, Instant now) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID));

        if (!passwordEncoder.matches(rawPw, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ApiException(ErrorCode.USER_LOCKED);
        }
        if (!user.isApproved()) {
            throw new ApiException(ErrorCode.USER_NOT_APPROVED);
        }

        String access = jwtService.issueAccess(user, now);
        String refresh = issueRefresh(user.getId(), now);
        return new AuthResult(access, refresh, jwtService.accessTtlSeconds(), user);
    }

    @Transactional
    public AuthResult refresh(String refreshToken, Instant now) {
        String hash = sha256(refreshToken);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);

        if (found.isEmpty()) {
            // Unknown token: treat as potential reuse of an already-rotated token.
            auditService.record(null, "TOKEN_REUSE", "refresh_token", null,
                    Map.of("reason", "unknown_token"));
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        RefreshToken token = found.get();

        if (token.isRevoked()) {
            // Reuse of a revoked (rotated) token: security event.
            auditService.record(token.getUserId(), "TOKEN_REUSED", "refresh_token", null,
                    Map.of("tokenId", token.getId()));
            throw new ApiException(ErrorCode.TOKEN_REUSED);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID));

        // Rotation: revoke the presented token and issue a fresh pair.
        token.revoke(now);
        String access = jwtService.issueAccess(user, now);
        String newRefresh = issueRefresh(user.getId(), now);
        return new AuthResult(access, newRefresh, jwtService.accessTtlSeconds(), user);
    }

    @Transactional
    public void logout(String refreshToken, Instant now) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(t -> t.revoke(now));
    }

    private String issueRefresh(Long userId, Instant now) {
        String raw = UUID.randomUUID().toString();
        Instant expires = now.plusSeconds(jwtService.refreshTtlSeconds());
        refreshTokenRepository.save(RefreshToken.issue(userId, sha256(raw), expires));
        return raw;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "SHA-256 unavailable");
        }
    }
}
