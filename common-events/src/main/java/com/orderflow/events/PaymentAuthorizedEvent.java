package com.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentAuthorizedEvent(
        String orderId,
        String paymentId,
        BigDecimal amount,
        Instant authorizedAt
) {
}
