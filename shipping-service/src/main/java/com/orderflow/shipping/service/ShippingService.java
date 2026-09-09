package com.orderflow.shipping.service;

import com.orderflow.shipping.model.ShipmentEntity;
import com.orderflow.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;

    @Transactional
    public ShipmentEntity createShipment(String orderId) {
        return shipmentRepository.findByOrderId(orderId).orElseGet(() -> {
            ShipmentEntity shipment = new ShipmentEntity(
                    orderId, "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), Instant.now());
            log.info("Created shipment {} for order {}", shipment.getTrackingNumber(), orderId);
            return shipmentRepository.save(shipment);
        });
    }
}
