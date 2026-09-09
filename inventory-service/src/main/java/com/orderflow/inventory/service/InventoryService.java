package com.orderflow.inventory.service;

import com.orderflow.events.OrderLineItem;
import com.orderflow.inventory.model.InventoryEntity;
import com.orderflow.inventory.model.ReservationEntity;
import com.orderflow.inventory.repository.InventoryRepository;
import com.orderflow.inventory.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Attempts to reserve every line item for an order in one DB
     * transaction. If any single item can't be reserved, the whole method
     * throws, which rolls back every conditional UPDATE already applied in
     * this transaction — so a multi-item order can never end up
     * "half reserved". Idempotent: replaying the same orderId (e.g. after a
     * Kafka redelivery) is a no-op if a reservation already exists for it.
     */
    @Transactional
    public String reserveForOrder(String orderId, List<OrderLineItem> items) {
        if (!reservationRepository.findByOrderId(orderId).isEmpty()) {
            log.info("Order {} already has a reservation — treating as duplicate delivery, skipping", orderId);
            return "duplicate";
        }

        for (OrderLineItem item : items) {
            int updated = inventoryRepository.tryReserve(item.productId(), item.quantity());
            if (updated == 0) {
                throw new InsufficientStockException(
                        "Insufficient stock for productId=" + item.productId() + " quantity=" + item.quantity());
            }
        }

        for (OrderLineItem item : items) {
            reservationRepository.save(new ReservationEntity(
                    null, orderId, item.productId(), item.quantity(), Instant.now()));
        }

        log.info("Reserved inventory for order {} ({} line items)", orderId, items.size());
        return orderId;
    }

    /**
     * Compensating action: releases whatever was reserved for this order, if
     * anything. Safe to call even if nothing was ever reserved (e.g. the
     * order was cancelled because payment failed on an order whose
     * reservation had, in some edge case, already been separately released).
     */
    @Transactional
    public void releaseForOrder(String orderId) {
        List<ReservationEntity> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            log.info("No inventory reservation found for order {} — nothing to release", orderId);
            return;
        }
        for (ReservationEntity reservation : reservations) {
            inventoryRepository.release(reservation.getProductId(), reservation.getQuantity());
        }
        reservationRepository.deleteAll(reservations);
        log.info("Released inventory reservation for order {}", orderId);
    }

    @Transactional
    public InventoryEntity seed(String productId, int availableQuantity) {
        InventoryEntity entity = inventoryRepository.findById(productId)
                .orElse(new InventoryEntity(productId, 0));
        entity.setAvailableQuantity(availableQuantity);
        return inventoryRepository.save(entity);
    }
}
