package com.hackathon.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StressTestRequest(
    @NotNull Long productId,
    @NotNull @Min(1) Integer concurrentOrders
) {}
