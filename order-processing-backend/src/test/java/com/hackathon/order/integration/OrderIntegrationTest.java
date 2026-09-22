package com.hackathon.order.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.order.dto.CreateOrderRequest;
import com.hackathon.order.dto.OrderItemRequest;
import com.hackathon.order.entity.Order;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.entity.Product;
import com.hackathon.order.repository.DeadLetterRepository;
import com.hackathon.order.repository.OrderRepository;
import com.hackathon.order.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OrderIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("orderdb")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.sql.init.mode", () -> "never"); // disable seed in tests
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DeadLetterRepository deadLetterRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        deadLetterRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        testProduct = productRepository.save(Product.builder()
                .name("Test Product")
                .sku("TEST-" + System.nanoTime())
                .quantity(10)
                .version(0L)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Basic REST tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createOrder_validPayload_returnsCreated() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void createOrder_emptyItems_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_invalidProductId_returns404() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(99999L, 1)));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllOrders_returnsArray() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void inventoryEndpoint_returnsProducts() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void inventoryById_returnsProduct() throws Exception {
        mockMvc.perform(get("/api/inventory/" + testProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testProduct.getId()))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void dashboardStats_returnsExpectedFields() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").exists())
                .andExpect(jsonPath("$.completedOrders").exists())
                .andExpect(jsonPath("$.failedOrders").exists())
                .andExpect(jsonPath("$.processingOrders").exists());
    }

    @Test
    void deadLetterEndpoint_returnsArray() throws Exception {
        mockMvc.perform(get("/api/dead-letter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Full flow tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(30)
    void fullOrderFlow_pendingToCompleted() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)));

        var result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        long orderId = responseJson.get("id").asLong();

        // Wait until COMPLETED (async processing)
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Order o = orderRepository.findById(orderId).orElseThrow();
                    return o.getStatus() == OrderStatus.COMPLETED;
                });

        // Verify inventory decremented
        Product updated = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(9);
    }

    @Test
    @Timeout(60)
    void insufficientInventory_exhaustsRetries_movesToDeadLetter() throws Exception {
        // Request 1000 units when only 10 available
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1000)));

        var result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        long orderId = responseJson.get("id").asLong();

        // Wait until DEAD_LETTER (3 retries with exponential backoff: 1s + 2s + 4s = 7s min)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    Order o = orderRepository.findById(orderId).orElseThrow();
                    return o.getStatus() == OrderStatus.DEAD_LETTER;
                });

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.DEAD_LETTER);
        assertThat(finalOrder.getRetryCount()).isGreaterThanOrEqualTo(3);

        // Verify dead letter entry created
        assertThat(deadLetterRepository.existsByOrderId(orderId)).isTrue();

        // Inventory unchanged
        Product unchanged = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(unchanged.getQuantity()).isEqualTo(10);
    }

    @Test
    @Timeout(30)
    void dbPersistenceCorrectness_orderAndInventoryConsistent() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 3)));

        var result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        long orderId = responseJson.get("id").asLong();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> orderRepository.findById(orderId).orElseThrow().getStatus() == OrderStatus.COMPLETED);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(order.getOrderNumber()).startsWith("ORD-");

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getQuantity()).isEqualTo(7); // 10 - 3
    }

    // ──────────────────────────────────────────────────────────────────────────
    // THE CONCURRENCY TEST — core requirement
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fires 100 concurrent orders at a single product with inventory = 10.
     *
     * Invariants that MUST hold:
     *   1. remainingInventory >= 0  (never negative — no oversell)
     *   2. completedOrders == 10    (exactly as many succeed as units available)
     *   3. remainingInventory == 0  (all units claimed, none wasted)
     */
    @Test
    @Timeout(120)
    void shouldNeverOversellInventory() throws Exception {
        Product limitedProduct = productRepository.save(Product.builder()
                .name("Limited Edition Item")
                .sku("LIMITED-" + System.nanoTime())
                .quantity(10)
                .version(0L)
                .build());

        int totalOrders = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalOrders);
        AtomicInteger submitted = new AtomicInteger(0);

        for (int i = 0; i < totalOrders; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    CreateOrderRequest req = new CreateOrderRequest(
                            List.of(new OrderItemRequest(limitedProduct.getId(), 1)));
                    mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)));
                    submitted.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Submit error: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        boolean allDone = doneLatch.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(allDone).as("All 100 HTTP requests should complete within timeout").isTrue();

        // Wait for async processing to fully settle
        // With exponential backoff retries this can take up to ~15s for 90 failed orders
        await().atMost(90, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    // No orders should be in PROCESSING or RETRYING or PENDING state
                    long inFlight = orderRepository.findAll().stream()
                            .filter(o -> o.getItems().stream()
                                    .anyMatch(item -> item.getProduct().getId().equals(limitedProduct.getId())))
                            .filter(o -> o.getStatus() == OrderStatus.PROCESSING
                                    || o.getStatus() == OrderStatus.RETRYING
                                    || o.getStatus() == OrderStatus.PENDING)
                            .count();
                    return inFlight == 0;
                });

        // Collect final state
        List<Order> allProductOrders = orderRepository.findAll().stream()
                .filter(o -> o.getItems().stream()
                        .anyMatch(item -> item.getProduct().getId().equals(limitedProduct.getId())))
                .collect(Collectors.toList());

        long completedOrders = allProductOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .count();

        long rejectedOrders = allProductOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.OUT_OF_STOCK
                        || o.getStatus() == OrderStatus.DEAD_LETTER)
                .count();

        Product finalProduct = productRepository.findById(limitedProduct.getId()).orElseThrow();
        int remainingInventory = finalProduct.getQuantity();

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     CONCURRENCY TEST RESULTS          ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║ Total orders submitted:  " + String.format("%-12d", totalOrders) + "║");
        System.out.println("║ Completed orders:        " + String.format("%-12d", completedOrders) + "║");
        System.out.println("║ Rejected orders:         " + String.format("%-12d", rejectedOrders) + "║");
        System.out.println("║ Remaining inventory:     " + String.format("%-12d", remainingInventory) + "║");
        System.out.println("╚══════════════════════════════════════╝");

        // ── Core assertions ──────────────────────────────────────────────────
        assertThat(remainingInventory)
                .as("Inventory must NEVER go negative — no oversell allowed")
                .isGreaterThanOrEqualTo(0);

        assertThat(completedOrders)
                .as("Exactly 10 orders should succeed (one per unit of inventory)")
                .isEqualTo(10);

        assertThat(remainingInventory)
                .as("All 10 inventory units must be consumed")
                .isEqualTo(0);
    }
}
