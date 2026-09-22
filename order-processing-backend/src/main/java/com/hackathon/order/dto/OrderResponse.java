package com.hackathon.order.dto;

import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    String orderNumber,
    OrderStatus status,
    Integer retryCount,
    String errorMessage,
    List<OrderItemResponse> items,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus(),
            order.getRetryCount(),
            order.getErrorMessage(),
            order.getItems().stream().map(OrderItemResponse::from).toList(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
