package com.orderflow.shipping.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String orderId;
    private String trackingNumber;
    private Instant createdAt;

    public ShipmentEntity(String orderId, String trackingNumber, Instant createdAt) {
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.createdAt = createdAt;
    }
}
