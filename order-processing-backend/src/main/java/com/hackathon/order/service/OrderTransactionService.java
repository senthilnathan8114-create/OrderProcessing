package com.hackathon.order.service;

import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.dto.ProductResponse;
import com.hackathon.order.dto.WebSocketEvent;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderItem;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.entity.Product;
import com.hackathon.order.exception.InsufficientInventoryException;
import com.hackathon.order.repository.OrderRepository;
import com.hackathon.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOrderTransaction(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found: " + orderId));

        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);

        messagingTemplate.convertAndSend(
                "/topic/events",
                new WebSocketEvent(
                        "ORDER_PROCESSING",
                        OrderResponse.from(order)
                )
        );

        log.debug(
                "Processing order {} with {} items",
                order.getOrderNumber(),
                order.getItems().size()
        );

        for (OrderItem item : order.getItems()) {

            Long productId = item.getProduct().getId();
            int requestedQty = item.getQuantity();

            Product product = productRepository
                    .findByIdWithLock(productId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Product not found: " + productId
                            )
                    );

            log.debug(
                    "Locked product {} ({}): stock={}, need={}",
                    product.getSku(),
                    product.getName(),
                    product.getQuantity(),
                    requestedQty
            );

            if (product.getQuantity() < requestedQty) {
                throw new InsufficientInventoryException(
                        String.format(
                                "Product '%s' (%s): only %d available, requested %d",
                                product.getName(),
                                product.getSku(),
                                product.getQuantity(),
                                requestedQty
                        )
                );
            }

            product.setQuantity(
                    product.getQuantity() - requestedQty
            );

            productRepository.save(product);

            messagingTemplate.convertAndSend(
                    "/topic/events",
                    new WebSocketEvent(
                            "INVENTORY_UPDATED",
                            new ProductResponse(
                                    product.getId(),
                                    product.getName(),
                                    product.getSku(),
                                    product.getQuantity(),
                                    product.getVersion(),
                                    product.getCreatedAt(),
                                    product.getUpdatedAt()
                            )
                    )
            );
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setErrorMessage(null);
        orderRepository.save(order);

        log.info(
                "Order {} COMPLETED successfully",
                order.getOrderNumber()
        );

        messagingTemplate.convertAndSend(
                "/topic/events",
                new WebSocketEvent(
                        "ORDER_COMPLETED",
                        OrderResponse.from(order)
                )
        );
    }
}