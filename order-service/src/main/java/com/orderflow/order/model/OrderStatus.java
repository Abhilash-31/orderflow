package com.orderflow.order.model;

/**
 * The order Saga's state machine. Every transition here corresponds to a
 * Kafka event either published or consumed by Order Service — see
 * {@code com.orderflow.order.kafka.OrderSagaListener}.
 */
public enum OrderStatus {
    /** Order accepted, OrderCreated published, awaiting inventory reservation. */
    PENDING,
    /** Inventory Service confirmed stock is reserved. */
    INVENTORY_RESERVED,
    /** Payment Service authorized the charge. */
    PAYMENT_AUTHORIZED,
    /** Shipping Service created the shipment — terminal success state. */
    COMPLETED,
    /** Saga failed at some step and compensation has run — terminal failure state. */
    CANCELLED
}
