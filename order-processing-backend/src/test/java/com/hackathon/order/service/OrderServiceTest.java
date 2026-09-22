package com.hackathon.order.service;

import com.hackathon.order.dto.CreateOrderRequest;
import com.hackathon.order.dto.OrderItemRequest;
import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.entity.Product;
import com.hackathon.order.exception.ResourceNotFoundException;
import com.hackathon.order.repository.OrderRepository;
import com.hackathon.order.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderProcessingService orderProcessingService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ThreadPoolTaskExecutor orderExecutor;

    @InjectMocks private OrderService orderService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L).name("Test Product").sku("TEST-001").quantity(10).version(0L)
                .build();
    }

    @Test
    void createOrder_success() {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(1L, 2)));

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        Order saved = Order.builder()
                .id(1L).orderNumber("ORD-TEST").status(OrderStatus.PENDING)
                .retryCount(0).items(new ArrayList<>()).build();
        when(orderRepository.save(any(Order.class))).thenReturn(saved);
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/events"), any());
        verify(orderExecutor).submit(any(Runnable.class));
    }

    @Test
    void createOrder_productNotFound_throwsException() {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(999L, 1)));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getOrder_notFound_throwsException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getOrder(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retryOrder_onlyAllowsOutOfStockOrDeadLetter() {
        Order order = Order.builder().id(1L).orderNumber("ORD-TEST")
                .status(OrderStatus.COMPLETED).retryCount(0).items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.retryOrder(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retry");
    }

    @Test
    void retryOrder_outOfStock_reEnqueues() {
        Order order = Order.builder().id(1L).orderNumber("ORD-TEST")
                .status(OrderStatus.OUT_OF_STOCK).retryCount(1).items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        OrderResponse response = orderService.retryOrder(1L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.retryCount()).isEqualTo(0);
        verify(orderExecutor).submit(any(Runnable.class));
    }

    @Test
    void retryOrder_deadLetter_reEnqueues() {
        Order order = Order.builder().id(2L).orderNumber("ORD-DL")
                .status(OrderStatus.DEAD_LETTER).retryCount(3).items(new ArrayList<>()).build();
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);
        when(orderExecutor.submit(any(Runnable.class))).thenReturn(null);

        OrderResponse response = orderService.retryOrder(2L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderExecutor).submit(any(Runnable.class));
    }

    @Test
    void getAllOrders_returnsList() {
        Order o1 = Order.builder().id(1L).orderNumber("ORD-1")
                .status(OrderStatus.COMPLETED).retryCount(0).items(new ArrayList<>()).build();
        Order o2 = Order.builder().id(2L).orderNumber("ORD-2")
                .status(OrderStatus.PENDING).retryCount(0).items(new ArrayList<>()).build();
        when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

        List<OrderResponse> orders = orderService.getAllOrders();
        assertThat(orders).hasSize(2);
    }

    @Test
    void getOrder_existingId_returnsOrder() {
        Order order = Order.builder().id(5L).orderNumber("ORD-5")
                .status(OrderStatus.PROCESSING).retryCount(0).items(new ArrayList<>()).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(5L);
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.orderNumber()).isEqualTo("ORD-5");
    }
}
