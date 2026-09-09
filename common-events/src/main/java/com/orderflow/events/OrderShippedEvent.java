package com.orderflow.events;

import java.time.Instant;

public record OrderShippedEvent(
        String orderId,
        String shipmentId,
        String trackingNumber,
        Instant shippedAt
) {
}
