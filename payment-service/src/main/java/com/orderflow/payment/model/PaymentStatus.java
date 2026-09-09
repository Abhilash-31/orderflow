package com.orderflow.payment.model;

public enum PaymentStatus {
    /** OrderCreated seen, waiting on InventoryReserved before attempting to charge. */
    PENDING_INVENTORY,
    AUTHORIZED,
    FAILED,
    REFUNDED
}
