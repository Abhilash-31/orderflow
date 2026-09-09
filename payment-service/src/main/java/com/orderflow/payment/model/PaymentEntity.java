package com.orderflow.payment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment Service's own local, read-optimized copy of just the order data
 * it needs (the total amount) — populated from OrderCreated events rather
 * than calling Order Service synchronously. This is deliberate: it keeps
 * services decoupled and available even if Order Service is temporarily
 * down, at the cost of eventual consistency.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEntity {

    @Id
    private String orderId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String paymentId;

    private Instant createdAt;
    private Instant updatedAt;

    public PaymentEntity(String orderId, BigDecimal amount, PaymentStatus status, Instant now) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
