package com.hackathon.order.service;

import com.hackathon.order.dto.ProductResponse;
import com.hackathon.order.dto.WebSocketEvent;
import com.hackathon.order.entity.Product;
import com.hackathon.order.exception.InsufficientInventoryException;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    /**
     * CRITICAL: Acquires a PESSIMISTIC_WRITE lock on the product row before checking
     * and decrementing inventory. This ensures no two threads can oversell concurrently.
     * The lock is held for the duration of the enclosing transaction.
     */
    @Transactional
    public void decrementInventory(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        log.debug("Checking inventory for product {} ({}): available={}, requested={}",
                productId, product.getName(), product.getQuantity(), quantity);

        if (product.getQuantity() < quantity) {
            throw new InsufficientInventoryException(
                    String.format("Insufficient inventory for product %s: available=%d, requested=%d",
                            product.getSku(), product.getQuantity(), quantity));
        }

        product.setQuantity(product.getQuantity() - quantity);
        productRepository.save(product);

        log.debug("Inventory decremented for product {}: new quantity={}", productId, product.getQuantity());

        // Broadcast inventory update
        messagingTemplate.convertAndSend("/topic/events",
                new WebSocketEvent("INVENTORY_UPDATED", ProductResponse.from(product)));
    }

    @Transactional(readOnly = true)
    public Product getProductWithLock(Long productId) {
        return productRepository.findByIdWithLock(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }
}
