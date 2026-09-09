package com.orderflow.events;

/**
 * Central registry of Kafka topic names used across OrderFlow services.
 * Keeping these in one shared place avoids typo'd topic names causing silent
 * message loss between services owned by different teams.
 */
public final class EventTopics {

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_COMPLETED = "order.completed";

    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation-failed";
    public static final String INVENTORY_RELEASED = "inventory.released";

    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";

    public static final String ORDER_SHIPPED = "order.shipped";

    private EventTopics() {
    }
}
