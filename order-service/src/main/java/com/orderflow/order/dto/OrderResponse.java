package com.orderflow.order.dto;

import com.orderflow.order.model.OrderEntity;
import com.orderflow.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        OrderStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(OrderEntity entity) {
        return new OrderResponse(
                entity.getId(),
                entity.getCustomerId(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
