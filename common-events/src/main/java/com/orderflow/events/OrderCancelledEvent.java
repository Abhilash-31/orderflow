package com.orderflow.events;

import java.time.Instant;

/**
 * Published by Order Service when it decides to cancel an in-flight order —
 * e.g. because payment failed after inventory was already reserved. This is
 * the compensating-transaction trigger: Inventory Service releases any
 * reservation, Payment Service refunds any authorized charge.
 */
public record OrderCancelledEvent(
        String orderId,
        String reason,
        Instant cancelledAt
) {
}
