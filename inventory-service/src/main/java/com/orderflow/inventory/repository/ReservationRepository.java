package com.orderflow.inventory.repository;

import com.orderflow.inventory.model.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {
    List<ReservationEntity> findByOrderId(String orderId);
}
