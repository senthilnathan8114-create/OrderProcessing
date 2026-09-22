package com.hackathon.order.dto;

import com.hackathon.order.entity.Product;
import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    String name,
    String sku,
    Integer quantity,
    Long version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getSku(),
            product.getQuantity(),
            product.getVersion(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
