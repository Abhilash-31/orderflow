package com.orderflow.events;

import java.time.Instant;

/**
 * Published when one or more line items could not be reserved — most
 * commonly because requested quantity exceeded available (non-reserved)
 * stock. This is the signal that starts Saga compensation.
 */
public record InventoryReservationFailedEvent(
        String orderId,
        String reason,
        Instant failedAt
) {
}
