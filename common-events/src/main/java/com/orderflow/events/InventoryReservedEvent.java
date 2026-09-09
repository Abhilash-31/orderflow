package com.orderflow.events;

import java.time.Instant;

/**
 * Published by Inventory Service once stock has been successfully reserved
 * for every line item in an order (oversell-safe conditional update).
 */
public record InventoryReservedEvent(
        String orderId,
        String reservationId,
        Instant reservedAt
) {
}
