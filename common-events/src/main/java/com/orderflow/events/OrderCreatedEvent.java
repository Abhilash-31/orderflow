package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Published by Order Service the moment a new order is accepted, before any
 * inventory has been reserved or payment taken. This is the event that kicks
 * off the Saga.
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        List<OrderLineItem> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
}
