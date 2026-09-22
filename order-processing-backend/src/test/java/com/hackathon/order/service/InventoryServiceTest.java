package com.hackathon.order.service;

import com.hackathon.order.dto.ProductResponse;
import com.hackathon.order.entity.Product;
import com.hackathon.order.exception.InsufficientInventoryException;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private InventoryService inventoryService;

    @Test
    void getAllProducts_returnsAll() {
        Product p1 = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(5).version(0L).build();
        Product p2 = Product.builder().id(2L).name("Mouse").sku("MOU-002").quantity(20).version(0L).build();
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        List<ProductResponse> result = inventoryService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sku()).isEqualTo("LAP-001");
        assertThat(result.get(1).sku()).isEqualTo("MOU-002");
    }

    @Test
    void getProduct_found_returnsProduct() {
        Product p = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(5).version(0L).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        ProductResponse result = inventoryService.getProduct(1L);
        assertThat(result.name()).isEqualTo("Laptop");
        assertThat(result.quantity()).isEqualTo(5);
    }

    @Test
    void getProduct_notFound_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.getProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void decrementInventory_success_updatesQuantity() {
        Product p = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(10).version(0L).build();
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenReturn(p);

        inventoryService.decrementInventory(1L, 3);

        assertThat(p.getQuantity()).isEqualTo(7);
        verify(productRepository).save(p);
        verify(messagingTemplate).convertAndSend(eq("/topic/events"), any());
    }

    @Test
    void decrementInventory_insufficient_throws() {
        Product p = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(2).version(0L).build();
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> inventoryService.decrementInventory(1L, 5))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("available=2")
                .hasMessageContaining("requested=5");
    }

    @Test
    void decrementInventory_exactAmount_reducesToZero() {
        Product p = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(5).version(0L).build();
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(p));
        when(productRepository.save(any())).thenReturn(p);

        inventoryService.decrementInventory(1L, 5);

        assertThat(p.getQuantity()).isEqualTo(0);
    }

    @Test
    void decrementInventory_zeroStock_throws() {
        Product p = Product.builder().id(1L).name("Laptop").sku("LAP-001").quantity(0).version(0L).build();
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> inventoryService.decrementInventory(1L, 1))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void decrementInventory_productNotFound_throws() {
        when(productRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.decrementInventory(99L, 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
