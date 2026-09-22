package com.hackathon.order.dto;

public record DashboardStats(
    long totalOrders,
    long completedOrders,
    long failedOrders,
    long processingOrders,
    long retryingOrders,
    long deadLetterOrders,
    long pendingOrders,
    long ordersPerMinute
) {}
