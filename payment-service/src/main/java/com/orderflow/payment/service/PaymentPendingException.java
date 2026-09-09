package com.orderflow.payment.service;

/**
 * Thrown when InventoryReserved arrives before this service has finished
 * processing the corresponding OrderCreated event. Deliberately a checked
 * "retry me" signal for the Kafka error handler rather than a hard failure —
 * see {@code PaymentKafkaConfig} for the backoff/retry policy.
 */
public class PaymentPendingException extends RuntimeException {
    public PaymentPendingException(String message) {
        super(message);
    }
}
