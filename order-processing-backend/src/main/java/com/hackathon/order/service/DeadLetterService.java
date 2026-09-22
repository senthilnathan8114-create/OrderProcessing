package com.hackathon.order.service;

import com.hackathon.order.dto.DeadLetterResponse;
import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.entity.DeadLetterOrder;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.DeadLetterRepository;
import com.hackathon.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final OrderRepository orderRepository;
    private final OrderProcessingService orderProcessingService;
    private final ThreadPoolTaskExecutor orderExecutor;

    @Transactional(readOnly = true)
    public List<DeadLetterResponse> getAllDeadLetterOrders() {
        return deadLetterRepository.findAll().stream()
                .map(DeadLetterResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse retryDeadLetterOrder(Long deadLetterId) {
        DeadLetterOrder dl = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new ResourceNotFoundException("Dead letter order not found: " + deadLetterId));

        var order = dl.getOrder();
        order.setStatus(OrderStatus.PENDING);
        order.setRetryCount(0);
        order.setErrorMessage(null);
        orderRepository.save(order);

        // Remove from dead letter queue so it can be re-added if it fails again
        deadLetterRepository.delete(dl);

        log.info("Dead letter order {} manually retried", order.getOrderNumber());

        final Long orderId = order.getId();
        orderExecutor.submit(() -> orderProcessingService.processOrder(orderId));

        return OrderResponse.from(order);
    }
}
