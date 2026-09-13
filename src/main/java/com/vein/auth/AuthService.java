package com.vein.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Authentication, refresh-token rotation and reuse detection (부록 H-4),
 * plus public self-service signup (MONETIZATION 단계2 ①).
 */
@Service
public class AuthService {

    /** Role assigned to public signups. Admin roles are provisioned out-of-band. */
    private static final String DEFAULT_ROLE = "TESTER";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    /** When true, public signups are APPROVED immediately; false restores 승인제(PENDING). */
    private final boolean autoApprove;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       @Value("${vein.signup.auto-approve:true}") boolean autoApprove) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.autoApprove = autoApprove;
    }

    public record AuthResult(String accessToken, String refreshToken, long expiresIn, User user) {
    }

    /** Signup outcome: {@code auth} is null when the account lands in PENDING (승인제). */
    public record SignupResult(AuthResult auth, User user) {
        public boolean approved() {
            return auth != null;
        }
    }

    /**
     * Register a new account from the public form. Email is unique (case-insensitive);
     * password is BCrypt-hashed. With {@link #autoApprove} the account is APPROVED and a
     * token pair is issued (auto-login); otherwise it lands PENDING with no tokens.
     */
    @Transactional
    public SignupResult signup(String email, String rawPw, String source, String referrer, Instant now) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(u -> {
            throw new ApiException(ErrorCode.ALREADY_EXISTS, "이미 가입된 이메일입니다.");
        });

        UserStatus status = autoApprove ? UserStatus.APPROVED : UserStatus.PENDING;
        User user;
        try {
            user = userRepository.saveAndFlush(User.create(email, passwordEncoder.encode(rawPw),
                    DEFAULT_ROLE, status, trimTo(source, 64), trimTo(referrer, 255)));
        } catch (DataIntegrityViolationException e) {
            // Lost the race on the unique(lower(email)) index between check and insert.
            throw new ApiException(ErrorCode.ALREADY_EXISTS, "이미 가입된 이메일입니다.");
        }

        auditService.record(user.getId(), "SIGNUP", "user", String.valueOf(user.getId()),
                Map.of("status", status.name(), "source", source == null ? "" : source));

        if (!user.isApproved()) {
            return new SignupResult(null, user);
        }
        String access = jwtService.issueAccess(user, now);
        String refresh = issueRefresh(user.getId(), now);
        return new SignupResult(new AuthResult(access, refresh, jwtService.accessTtlSeconds(), user), user);
    }

    static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
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
