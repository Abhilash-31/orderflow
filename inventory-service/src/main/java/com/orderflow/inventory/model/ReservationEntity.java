package com.orderflow.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records exactly what was reserved for an order, so that if the order is
 * later cancelled, Inventory Service knows precisely what to release
 * without having to re-derive it from the original OrderCreated event
 * (which it may no longer have easy access to).
 */
@Entity
@Table(name = "inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String orderId;
    private String productId;
    private int quantity;
    private Instant createdAt;
}
