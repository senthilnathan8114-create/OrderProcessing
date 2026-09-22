package com.hackathon.order.entity;

public enum OrderStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    OUT_OF_STOCK,
    RETRYING,
    DEAD_LETTER
}
