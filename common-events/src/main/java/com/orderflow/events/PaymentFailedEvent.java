package com.orderflow.events;

import java.time.Instant;

public record PaymentFailedEvent(
        String orderId,
        String reason,
        Instant failedAt
) {
}
