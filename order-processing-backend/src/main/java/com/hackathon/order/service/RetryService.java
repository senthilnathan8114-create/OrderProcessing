package com.hackathon.order.service;

import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    private final OrderRepository orderRepository;
    private final OrderProcessingService orderProcessingService;
    private final ThreadPoolTaskExecutor orderExecutor;

    /**
     * Periodic sweep to re-enqueue any RETRYING orders that may have been
     * orphaned (e.g., app restart during backoff window).
     */
    @Scheduled(fixedDelay = 30000)
    public void reEnqueueOrphanedRetryOrders() {
        List<Order> retrying = orderRepository.findByStatus(OrderStatus.RETRYING);
        if (!retrying.isEmpty()) {
            log.info("RetryService: found {} orphaned RETRYING orders, re-enqueuing", retrying.size());
            retrying.forEach(order -> {
                log.info("Re-enqueuing orphaned order: {}", order.getOrderNumber());
                orderExecutor.submit(() -> orderProcessingService.processOrder(order.getId()));
            });
        }
    }

    public List<OrderResponse> getRetryingOrders() {
        return orderRepository.findByStatus(OrderStatus.RETRYING).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
