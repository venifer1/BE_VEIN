package com.vein.watchlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiException;
import com.vein.common.ApiResponse;
import com.vein.common.ErrorCode;
import com.vein.watchlist.WatchlistDto.InstrumentRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Default-watchlist endpoints scoped to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/watchlists/default")
@Tag(name = "Watchlist", description = "The current user's default watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "Get the default watchlist")
    public ApiResponse<WatchlistDto> getDefault() {
        return ApiResponse.of(watchlistService.getDefault(currentUserId()));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an instrument to the default watchlist")
    public ApiResponse<InstrumentRef> addItem(@Valid @RequestBody AddItemRequest request) {
        return ApiResponse.of(watchlistService.addItem(currentUserId(), request.instrumentId()));
    }

    @DeleteMapping("/items/{instrumentId}")
    @Operation(summary = "Remove an instrument from the default watchlist")
    public ResponseEntity<Void> removeItem(@PathVariable Long instrumentId) {
        watchlistService.removeItem(currentUserId(), instrumentId);
        return ResponseEntity.noContent().build();
    }

    /** Request body for POST /items; client sends {@code instrument_id} (snake_case). */
    public record AddItemRequest(@NotNull Long instrumentId) {
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
