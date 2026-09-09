package com.orderflow.inventory.controller;

import com.orderflow.inventory.model.InventoryEntity;
import com.orderflow.inventory.repository.InventoryRepository;
import com.orderflow.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Admin/demo endpoints — not part of the Saga itself. Useful for seeding
 * stock and eyeballing sellable quantity while exercising the order flow
 * locally (e.g. to demonstrate the oversell-prevention path by seeding a
 * product with quantity 1 and firing two concurrent orders at it).
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;

    @PostMapping("/{productId}/seed")
    public ResponseEntity<InventoryEntity> seed(@PathVariable String productId, @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.seed(productId, quantity));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryEntity> get(@PathVariable String productId) {
        return inventoryRepository.findById(productId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("No inventory record for " + productId));
    }
}
