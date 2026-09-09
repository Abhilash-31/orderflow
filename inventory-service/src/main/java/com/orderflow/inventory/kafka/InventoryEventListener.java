package com.orderflow.inventory.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.inventory.service.InsufficientStockException;
import com.orderflow.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryService inventoryService;
    private final InventoryEventProducer eventProducer;

    @KafkaListener(topics = EventTopics.ORDER_CREATED, groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            String reservationId = inventoryService.reserveForOrder(event.orderId(), event.items());
            eventProducer.publishReserved(new InventoryReservedEvent(event.orderId(), reservationId, Instant.now()));
        } catch (InsufficientStockException ex) {
            log.warn("Could not reserve inventory for order {}: {}", event.orderId(), ex.getMessage());
            eventProducer.publishReservationFailed(
                    new InventoryReservationFailedEvent(event.orderId(), ex.getMessage(), Instant.now()));
        }
    }

    @KafkaListener(topics = EventTopics.ORDER_CANCELLED, groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        // Compensating transaction: whatever this order had reserved (if
        // anything) is released back to sellable stock.
        inventoryService.releaseForOrder(event.orderId());
    }
}
