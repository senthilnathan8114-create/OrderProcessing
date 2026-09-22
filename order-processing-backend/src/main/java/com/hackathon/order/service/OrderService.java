package com.hackathon.order.service;

import com.hackathon.order.dto.*;
import com.hackathon.order.entity.*;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.OrderRepository;
import com.hackathon.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderProcessingService orderProcessingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor orderExecutor;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Validate all products exist before creating order
        for (OrderItemRequest itemReq : request.items()) {
            productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.productId()));
        }

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(OrderStatus.PENDING)
                .retryCount(0)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Add items
        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId()).get();
            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(itemReq.quantity())
                    .build();
            savedOrder.getItems().add(item);
        }
        orderRepository.save(savedOrder);

        log.info("Order created: {} with {} items", savedOrder.getOrderNumber(), savedOrder.getItems().size());

        // Broadcast ORDER_CREATED event
        messagingTemplate.convertAndSend("/topic/events",
                new WebSocketEvent("ORDER_CREATED", OrderResponse.from(savedOrder)));

        // Submit to bounded thread pool for async processing
        final Long orderId = savedOrder.getId();
        orderExecutor.submit(() -> orderProcessingService.processOrder(orderId));

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public OrderResponse retryOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (order.getStatus() != OrderStatus.OUT_OF_STOCK && order.getStatus() != OrderStatus.DEAD_LETTER) {
            throw new IllegalStateException("Only OUT_OF_STOCK or DEAD_LETTER orders can be retried");
        }

        order.setStatus(OrderStatus.PENDING);
        order.setRetryCount(0);
        order.setErrorMessage(null);
        orderRepository.save(order);

        final Long orderId = order.getId();
        orderExecutor.submit(() -> orderProcessingService.processOrder(orderId));

        return OrderResponse.from(order);
    }
}
