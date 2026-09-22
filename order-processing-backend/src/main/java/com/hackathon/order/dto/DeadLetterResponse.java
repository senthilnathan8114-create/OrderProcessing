package com.hackathon.order.dto;

import com.hackathon.order.entity.DeadLetterOrder;
import java.time.LocalDateTime;

public record DeadLetterResponse(
    Long id,
    OrderResponse order,
    String reason,
    Integer retryCount,
    LocalDateTime failedAt
) {
    public static DeadLetterResponse from(DeadLetterOrder dl) {
        return new DeadLetterResponse(
            dl.getId(),
            OrderResponse.from(dl.getOrder()),
            dl.getReason(),
            dl.getRetryCount(),
            dl.getFailedAt()
        );
    }
}
