package com.hackathon.order.service;

import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderProcessingService orderProcessingService;
    @Mock private ThreadPoolTaskExecutor orderExecutor;
    @InjectMocks private RetryService retryService;

    @Test
    void reEnqueueOrphanedRetryOrders_withOrphanedOrders_submitsToExecutor() {
        Order o1 = Order.builder().id(1L).orderNumber("ORD-1")
                .status(OrderStatus.RETRYING).retryCount(1).items(new ArrayList<>()).build();
        Order o2 = Order.builder().id(2L).orderNumber("ORD-2")
                .status(OrderStatus.RETRYING).retryCount(2).items(new ArrayList<>()).build();
        when(orderRepository.findByStatus(OrderStatus.RETRYING)).thenReturn(List.of(o1, o2));
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        retryService.reEnqueueOrphanedRetryOrders();

        verify(orderExecutor, times(2)).submit(any(Runnable.class));
    }

    @Test
    void reEnqueueOrphanedRetryOrders_noOrphans_doesNothing() {
        when(orderRepository.findByStatus(OrderStatus.RETRYING)).thenReturn(List.of());

        retryService.reEnqueueOrphanedRetryOrders();

        verify(orderExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void getRetryingOrders_returnsOnlyRetryingStatus() {
        Order o = Order.builder().id(1L).orderNumber("ORD-RETRY")
                .status(OrderStatus.RETRYING).retryCount(1).items(new ArrayList<>()).build();
        when(orderRepository.findByStatus(OrderStatus.RETRYING)).thenReturn(List.of(o));

        List<OrderResponse> result = retryService.getRetryingOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(OrderStatus.RETRYING);
        assertThat(result.get(0).retryCount()).isEqualTo(1);
    }

    @Test
    void reEnqueueOrphanedRetryOrders_singleOrder_submitsOnce() {
        Order o = Order.builder().id(3L).orderNumber("ORD-3")
                .status(OrderStatus.RETRYING).retryCount(0).items(new ArrayList<>()).build();
        when(orderRepository.findByStatus(OrderStatus.RETRYING)).thenReturn(List.of(o));
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        retryService.reEnqueueOrphanedRetryOrders();

        verify(orderExecutor, times(1)).submit(any(Runnable.class));
    }
}
