package com.hackathon.order.controller;

import com.hackathon.order.dto.*;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.repository.OrderRepository;
import com.hackathon.order.repository.ProductRepository;
import com.hackathon.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dev Tools", description = "Development and stress testing tools")
@CrossOrigin(origins = "*")
public class DevController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @PostMapping("/stress-test")
    @Operation(summary = "Run a concurrency stress test",
            description = "Fires N concurrent orders at one product and reports results")
    public ResponseEntity<StressTestResult> stressTest(@Valid @RequestBody StressTestRequest request) throws Exception {
        long startMs = System.currentTimeMillis();

        // Record initial inventory
        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        int initialInventory = product.getQuantity();

        int n = request.concurrentOrders();
        ExecutorService exec = Executors.newFixedThreadPool(Math.min(n, 50));
        CountDownLatch latch = new CountDownLatch(n);
        List<Future<String>> futures = new ArrayList<>();
        AtomicBoolean inventoryWentNegative = new AtomicBoolean(false);

        for (int i = 0; i < n; i++) {
            futures.add(exec.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // all threads start simultaneously
                    CreateOrderRequest req = new CreateOrderRequest(
                            List.of(new OrderItemRequest(request.productId(), 1)));
                    orderService.createOrder(req);
                    return "submitted";
                } catch (Exception e) {
                    return "error: " + e.getMessage();
                }
            }));
        }

        exec.shutdown();
        exec.awaitTermination(60, TimeUnit.SECONDS);

        // Wait for async processing to complete
        Thread.sleep(5000);

        // Tally results
        long completed = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long outOfStock = orderRepository.countByStatus(OrderStatus.OUT_OF_STOCK);

        var finalProduct = productRepository.findById(request.productId()).orElseThrow();
        int remaining = finalProduct.getQuantity();

        if (remaining < 0) inventoryWentNegative.set(true);

        long durationMs = System.currentTimeMillis() - startMs;

        log.info("Stress test: {} orders, {} completed, {} out-of-stock, inventory={}",
                n, completed, outOfStock, remaining);

        return ResponseEntity.ok(new StressTestResult(
                n, (int) completed, (int) outOfStock, remaining,
                !inventoryWentNegative.get(), durationMs));
    }
}
