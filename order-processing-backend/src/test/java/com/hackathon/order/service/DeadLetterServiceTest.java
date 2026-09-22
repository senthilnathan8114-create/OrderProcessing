package com.hackathon.order.service;

import com.hackathon.order.dto.DeadLetterResponse;
import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.entity.DeadLetterOrder;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.DeadLetterRepository;
import com.hackathon.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterServiceTest {

    @Mock private DeadLetterRepository deadLetterRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderProcessingService orderProcessingService;
    @Mock private ThreadPoolTaskExecutor orderExecutor;
    @InjectMocks private DeadLetterService deadLetterService;

    @Test
    void getAllDeadLetterOrders_returnsList() {
        Order order = Order.builder().id(1L).orderNumber("ORD-1")
                .status(OrderStatus.DEAD_LETTER).retryCount(3).items(new ArrayList<>()).build();
        DeadLetterOrder dl = DeadLetterOrder.builder().id(1L).order(order)
                .reason("Insufficient inventory: available=0, requested=1").retryCount(3).build();
        when(deadLetterRepository.findAll()).thenReturn(List.of(dl));

        List<DeadLetterResponse> result = deadLetterService.getAllDeadLetterOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).contains("available=0");
        assertThat(result.get(0).retryCount()).isEqualTo(3);
        assertThat(result.get(0).order().orderNumber()).isEqualTo("ORD-1");
    }

    @Test
    void getAllDeadLetterOrders_empty_returnsEmptyList() {
        when(deadLetterRepository.findAll()).thenReturn(List.of());

        List<DeadLetterResponse> result = deadLetterService.getAllDeadLetterOrders();
        assertThat(result).isEmpty();
    }

    @Test
    void retryDeadLetterOrder_notFound_throws() {
        when(deadLetterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deadLetterService.retryDeadLetterOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void retryDeadLetterOrder_success_resetsStatusAndEnqueues() {
        Order order = Order.builder().id(1L).orderNumber("ORD-1")
                .status(OrderStatus.DEAD_LETTER).retryCount(3).items(new ArrayList<>()).build();
        DeadLetterOrder dl = DeadLetterOrder.builder().id(1L).order(order)
                .reason("No stock").retryCount(3).build();
        when(deadLetterRepository.findById(1L)).thenReturn(Optional.of(dl));
        when(orderRepository.save(any())).thenReturn(order);
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        OrderResponse response = deadLetterService.retryDeadLetterOrder(1L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.retryCount()).isEqualTo(0);
        assertThat(response.errorMessage()).isNull();
        verify(deadLetterRepository).delete(dl);
        verify(orderExecutor).submit(any(Runnable.class));
    }

    @Test
    void retryDeadLetterOrder_removesFromDeadLetterTable() {
        Order order = Order.builder().id(2L).orderNumber("ORD-2")
                .status(OrderStatus.DEAD_LETTER).retryCount(3).items(new ArrayList<>()).build();
        DeadLetterOrder dl = DeadLetterOrder.builder().id(5L).order(order)
                .reason("timeout").retryCount(3).build();
        when(deadLetterRepository.findById(5L)).thenReturn(Optional.of(dl));
        when(orderRepository.save(any())).thenReturn(order);
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        deadLetterService.retryDeadLetterOrder(5L);

        verify(deadLetterRepository).delete(dl);
    }
}
