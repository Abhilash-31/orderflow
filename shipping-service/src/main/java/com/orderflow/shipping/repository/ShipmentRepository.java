package com.orderflow.shipping.repository;

import com.orderflow.shipping.model.ShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<ShipmentEntity, String> {
    Optional<ShipmentEntity> findByOrderId(String orderId);
}
