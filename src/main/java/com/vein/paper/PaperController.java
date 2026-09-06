package com.vein.paper;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;
import com.vein.paper.PaperDto.AccountResponse;
import com.vein.paper.PaperDto.CreateAccountRequest;
import com.vein.paper.PaperDto.CreateOrderRequest;
import com.vein.paper.PaperDto.OrderResponse;
import com.vein.paper.PaperDto.PerformanceResponse;
import com.vein.paper.PaperDto.PortfolioResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/paper")
@Tag(name = "Paper Trading", description = "Virtual account, order and portfolio simulation")
public class PaperController {

    private final PaperTradingService paperTradingService;

    public PaperController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or reset the active paper account")
    public ApiResponse<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.of(paperTradingService.createAccount(currentUserId(), request));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an immediately-filled virtual order")
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.of(paperTradingService.createOrder(currentUserId(), request));
    }

    @GetMapping("/portfolio")
    @Operation(summary = "Get paper positions, equity and recent virtual orders")
    public ApiResponse<PortfolioResponse> portfolio() {
        return ApiResponse.of(paperTradingService.portfolio(currentUserId()));
    }

    @GetMapping("/performance")
    @Operation(summary = "Get paper account performance summary")
    public ApiResponse<PerformanceResponse> performance() {
        return ApiResponse.of(paperTradingService.performance(currentUserId()));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.AUTH_INVALID);
        }
    }
}
