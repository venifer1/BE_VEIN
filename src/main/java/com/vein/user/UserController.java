package com.vein.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints scoped to the currently authenticated user.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Current-user profile")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user's profile")
    public ApiResponse<UserDto> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
        Long userId = parseUserId(auth.getPrincipal().toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return ApiResponse.of(UserDto.from(user));
    }

    private Long parseUserId(String principal) {
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
    }
}
