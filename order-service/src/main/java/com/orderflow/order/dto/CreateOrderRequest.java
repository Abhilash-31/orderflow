package com.orderflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<LineItem> items
) {
    public record LineItem(
            @NotBlank String productId,
            @Min(1) int quantity,
            @NotNull BigDecimal unitPrice
    ) {
    }
}
