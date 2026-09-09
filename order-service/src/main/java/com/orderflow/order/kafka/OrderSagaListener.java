package com.orderflow.order.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderShippedEvent;
import com.orderflow.events.PaymentAuthorizedEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.order.model.OrderStatus;
import com.orderflow.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The heart of the Saga on the Order Service side. Order Service doesn't
 * orchestrate the other services (this is choreography, not orchestration)
 * — it just reacts to what they've announced, updates its own state, and on
 * failure publishes OrderCancelled so Inventory/Payment know to compensate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaListener {

    private final OrderService orderService;
    private final OrderEventProducer eventProducer;

    @KafkaListener(topics = EventTopics.INVENTORY_RESERVED, groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        orderService.transitionStatus(event.orderId(), OrderStatus.INVENTORY_RESERVED);
    }

    @KafkaListener(topics = EventTopics.INVENTORY_RESERVATION_FAILED, groupId = "order-service")
    public void onInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.warn("Inventory reservation failed for order {}: {}", event.orderId(), event.reason());
        boolean hadInventoryReserved = orderService.cancelOrder(event.orderId(), "Inventory: " + event.reason());
        eventProducer.publishOrderCancelled(
                new OrderCancelledEvent(event.orderId(), "inventory-reservation-failed", Instant.now()));
    }

    @KafkaListener(topics = EventTopics.PAYMENT_AUTHORIZED, groupId = "order-service")
    public void onPaymentAuthorized(PaymentAuthorizedEvent event) {
        orderService.transitionStatus(event.orderId(), OrderStatus.PAYMENT_AUTHORIZED);
    }

    @KafkaListener(topics = EventTopics.PAYMENT_FAILED, groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("Payment failed for order {}: {}", event.orderId(), event.reason());
        // Inventory was already reserved by this point in the happy path, so
        // cancelling here and re-publishing OrderCancelled is what tells
        // Inventory Service to release that reservation — the compensating
        // action for this failure branch.
        orderService.cancelOrder(event.orderId(), "Payment: " + event.reason());
        eventProducer.publishOrderCancelled(
                new OrderCancelledEvent(event.orderId(), "payment-failed", Instant.now()));
    }

    @KafkaListener(topics = EventTopics.ORDER_SHIPPED, groupId = "order-service")
    public void onOrderShipped(OrderShippedEvent event) {
        orderService.transitionStatus(event.orderId(), OrderStatus.COMPLETED);
    }
}
