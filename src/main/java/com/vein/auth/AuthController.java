package com.vein.auth;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.auth.AuthService.AuthResult;
import com.vein.common.ApiResponse;
import com.vein.user.UserDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LoginResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        static LoginResponse from(AuthResult r) {
            return new LoginResponse(r.accessToken(), r.refreshToken(), r.expiresIn(),
                    UserDto.from(r.user()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.email(), request.password(), Instant.now());
        return ApiResponse.of(LoginResponse.from(result));
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
