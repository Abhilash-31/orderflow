package com.orderflow.shipping.controller;

import com.orderflow.shipping.model.ShipmentEntity;
import com.orderflow.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<ShipmentEntity> getByOrder(@PathVariable String orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("No shipment yet for order " + orderId));
    }
}
