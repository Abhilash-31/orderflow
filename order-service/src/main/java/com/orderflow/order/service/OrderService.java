package com.orderflow.order.service;

import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.OrderLineItem;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.kafka.OrderEventProducer;
import com.orderflow.order.model.OrderEntity;
import com.orderflow.order.model.OrderLineItemEmbeddable;
import com.orderflow.order.model.OrderStatus;
import com.orderflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    /**
     * Accepts a new order, persists it as PENDING, and publishes
     * OrderCreated. This is step 1 of the Saga — everything after this
     * happens asynchronously as Inventory/Payment/Shipping react to events.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setCustomerId(request.customerId());
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setItems(request.items().stream()
                .map(i -> new OrderLineItemEmbeddable(i.productId(), i.quantity(), i.unitPrice()))
                .toList());

        orderRepository.save(order);

        List<OrderLineItem> eventItems = request.items().stream()
                .map(i -> new OrderLineItem(i.productId(), i.quantity(), i.unitPrice()))
                .toList();

        eventProducer.publishOrderCreated(new OrderCreatedEvent(orderId, request.customerId(), eventItems, total, now));

        return OrderResponse.from(order);
    }

    public OrderResponse getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No order found with id " + orderId));
    }

    /**
     * Advances an order to a new status as Saga events arrive. Guards
     * against processing an event twice or out of order for an already
     * terminal order (defensive — Kafka delivery is at-least-once).
     */
    @Transactional
    public void transitionStatus(String orderId, OrderStatus newStatus) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
                log.info("Ignoring transition to {} for already-terminal order {}", newStatus, orderId);
                return;
            }
            log.info("Order {} transitioning {} -> {}", orderId, order.getStatus(), newStatus);
            order.setStatus(newStatus);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
        }, () -> log.warn("Received status transition for unknown orderId={}", orderId));
    }

    /**
     * Marks the order CANCELLED and returns whether inventory had already
     * been reserved for it — the compensation trigger needs to know that so
     * Inventory Service knows whether there's anything to release.
     */
    @Transactional
    public boolean cancelOrder(String orderId, String reason) {
        return orderRepository.findById(orderId).map(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return false;
            }
            boolean hadInventoryReserved = order.getStatus() == OrderStatus.INVENTORY_RESERVED
                    || order.getStatus() == OrderStatus.PAYMENT_AUTHORIZED;
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason(reason);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            return hadInventoryReserved;
        }).orElse(false);
    }
}
