package com.hackathon.order.controller;

import com.hackathon.order.dto.ProductResponse;
import com.hackathon.order.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Product inventory management")
@CrossOrigin(origins = "*")
public class ProductController {

    private final InventoryService inventoryService;

    @GetMapping("/api/inventory")
    @Operation(summary = "Get all products with current inventory")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }

    @GetMapping("/api/inventory/{id}")
    @Operation(summary = "Get a product by ID")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getProduct(id));
    }
}
