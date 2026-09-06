package com.vein.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class WebPushDto {
    private WebPushDto() {
    }

    public record Config(boolean enabled, String publicKey) {
    }

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record SubscriptionRequest(@NotBlank String endpoint,
                                      @NotNull Keys keys,
                                      String userAgent) {
    }

    public record SubscriptionResponse(boolean active) {
    }
}
