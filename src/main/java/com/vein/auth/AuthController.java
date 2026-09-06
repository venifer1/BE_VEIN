package com.vein.auth;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.auth.AuthService.AuthResult;
import com.vein.auth.AuthService.SignupResult;
import com.vein.common.ApiResponse;
import com.vein.user.UserDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Authentication endpoints: login, refresh-token rotation and logout (부록 H).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication and token lifecycle")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record SignupRequest(@Email @NotBlank String email,
                                @NotBlank @Size(min = 8, max = 100) String password,
                                String signupSource, String signupReferrer) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LoginResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        static LoginResponse from(AuthResult r) {
            return new LoginResponse(r.accessToken(), r.refreshToken(), r.expiresIn(),
                    UserDto.from(r.user()));
        }
    }

    /**
     * Signup body. On auto-approve, {@code access_token}/{@code refresh_token} are present
     * (auto-login) and {@code status=APPROVED}; under 승인제 they are null and {@code status=PENDING}.
     */
    public record SignupResponse(String status, UserDto user,
                                 String accessToken, String refreshToken, Long expiresIn) {
        static SignupResponse from(SignupResult r) {
            AuthResult a = r.auth();
            return new SignupResponse(r.user().getStatus().name(), UserDto.from(r.user()),
                    a == null ? null : a.accessToken(),
                    a == null ? null : a.refreshToken(),
                    a == null ? null : a.expiresIn());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.email(), request.password(), Instant.now());
        return ApiResponse.of(LoginResponse.from(result));
    }

    @PostMapping("/signup")
    @Operation(summary = "공개 회원가입",
            description = "이메일+비밀번호(8자 이상)로 가입. 이메일 중복 시 409 ALREADY_EXISTS. "
                    + "`vein.signup.auto-approve`(기본 true)면 즉시 APPROVED + 토큰 발급(자동 로그인), "
                    + "false면 PENDING(승인 대기, 토큰 없음). signup_source/referrer는 유입 추적용(선택). "
                    + "IP당 시간당 10건 레이트리밋.")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResult result = authService.signup(request.email(), request.password(),
                request.signupSource(), request.signupReferrer(), Instant.now());
        return ApiResponse.of(SignupResponse.from(result));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new access/refresh pair")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResult result = authService.refresh(request.refreshToken(), Instant.now());
        return ApiResponse.of(LoginResponse.from(result));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
