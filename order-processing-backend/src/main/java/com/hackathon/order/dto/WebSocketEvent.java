package com.hackathon.order.dto;

public record WebSocketEvent(
    String eventType,
    Object payload
) {}
