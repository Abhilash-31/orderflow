package com.orderflow.events;

import java.math.BigDecimal;

/**
 * One line item within an order — a product and the quantity requested.
 */
public record OrderLineItem(String productId, int quantity, BigDecimal unitPrice) {
}
