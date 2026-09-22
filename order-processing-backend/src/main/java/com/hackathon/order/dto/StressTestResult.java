package com.hackathon.order.dto;

public record StressTestResult(
    int totalOrders,
    int completedOrders,
    int outOfStockOrders,
    int remainingInventory,
    boolean inventoryNeverNegative,
    long durationMs
) {}
