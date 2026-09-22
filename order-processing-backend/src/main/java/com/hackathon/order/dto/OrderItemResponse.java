package com.hackathon.order.dto;

import com.hackathon.order.entity.OrderItem;

public record OrderItemResponse(
    Long id,
    Long productId,
    String productName,
    String sku,
    Integer quantity
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
            item.getId(),
            item.getProduct().getId(),
            item.getProduct().getName(),
            item.getProduct().getSku(),
            item.getQuantity()
        );
    }
}
