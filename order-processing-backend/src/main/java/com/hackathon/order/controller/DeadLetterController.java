package com.hackathon.order.controller;

import com.hackathon.order.dto.DeadLetterResponse;
import com.hackathon.order.dto.OrderResponse;
import com.hackathon.order.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dead-letter")
@RequiredArgsConstructor
@Tag(name = "Dead Letter Queue", description = "Dead letter order management")
@CrossOrigin(origins = "*")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @GetMapping
    @Operation(summary = "Get all dead letter orders")
    public ResponseEntity<List<DeadLetterResponse>> getAll() {
        return ResponseEntity.ok(deadLetterService.getAllDeadLetterOrders());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually retry a dead letter order")
    public ResponseEntity<OrderResponse> retry(@PathVariable Long id) {
        return ResponseEntity.ok(deadLetterService.retryDeadLetterOrder(id));
    }
}
