package com.hackathon.order.service;

import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.dto.WebSocketEvent;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.exception.InsufficientInventoryException;
import com.hackathon.order.repository.DeadLetterRepository;
import com.hackathon.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 1000;

    private final OrderRepository orderRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderTransactionService orderTransactionService;

    public void processOrder(Long orderId) {

        log.info("Starting processing for order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found: " + orderId));

        try {

            orderTransactionService.processOrderTransaction(orderId);

        } catch (InsufficientInventoryException e) {

            handleOutOfStock(order, e.getMessage());

        } catch (Exception e) {

            log.error(
                    "Unexpected error processing order {}: {}",
                    orderId,
                    e.getMessage(),
                    e
            );

            handleFailure(order, e.getMessage());
        }
    }

    private void handleOutOfStock(Order order, String reason) {

        int currentRetry =
                order.getRetryCount() == null
                        ? 0
                        : order.getRetryCount();

        if (currentRetry < MAX_RETRIES) {

            scheduleRetry(
                    order,
                    reason,
                    currentRetry
            );

        } else {

            moveToDeadLetter(
                    order,
                    reason
            );
        }
    }

    private void handleFailure(Order order, String reason) {

        int currentRetry =
                order.getRetryCount() == null
                        ? 0
                        : order.getRetryCount();

        if (currentRetry < MAX_RETRIES) {

            scheduleRetry(
                    order,
                    reason,
                    currentRetry
            );

        } else {

            moveToDeadLetter(
                    order,
                    reason
            );
        }
    }

    private void scheduleRetry(
            Order order,
            String reason,
            int currentRetry) {

        order.setStatus(OrderStatus.RETRYING);
        order.setRetryCount(currentRetry + 1);
        order.setErrorMessage(reason);

        orderRepository.save(order);

        messagingTemplate.convertAndSend(
                "/topic/events",
                new WebSocketEvent(
                        "ORDER_RETRYING",
                        OrderResponse.from(order)
                )
        );

        long backoff =
                BASE_BACKOFF_MS *
                (long) Math.pow(2, currentRetry);

        log.info(
                "Order {} retrying (attempt {}/{}) after {}ms: {}",
                order.getOrderNumber(),
                currentRetry + 1,
                MAX_RETRIES,
                backoff,
                reason
        );

        try {

            Thread.sleep(backoff);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.error(
                    "Retry interrupted for order {}",
                    order.getOrderNumber()
            );

            return;
        }

        try {

            orderTransactionService.processOrderTransaction(
                    order.getId()
            );

        } catch (InsufficientInventoryException e) {

            Order refreshed =
                    orderRepository.findById(order.getId())
                            .orElse(order);

            handleOutOfStock(
                    refreshed,
                    e.getMessage()
            );

        } catch (Exception e) {

            Order refreshed =
                    orderRepository.findById(order.getId())
                            .orElse(order);

            handleFailure(
                    refreshed,
                    e.getMessage()
            );
        }
    }

    private void moveToDeadLetter(
            Order order,
            String reason) {

        order.setStatus(OrderStatus.DEAD_LETTER);
        order.setErrorMessage(reason);

        orderRepository.save(order);

        if (!deadLetterRepository.existsByOrderId(order.getId())) {

            var deadLetterOrder =
                    com.hackathon.order.entity.DeadLetterOrder.builder()
                            .order(order)
                            .reason(reason)
                            .retryCount(order.getRetryCount())
                            .build();

            deadLetterRepository.save(deadLetterOrder);
        }

        log.warn(
                "Order {} moved to DEAD_LETTER after {} retries: {}",
                order.getOrderNumber(),
                order.getRetryCount(),
                reason
        );

        messagingTemplate.convertAndSend(
                "/topic/events",
                new WebSocketEvent(
                        "ORDER_DEAD_LETTER",
                        OrderResponse.from(order)
                )
        );
    }
}