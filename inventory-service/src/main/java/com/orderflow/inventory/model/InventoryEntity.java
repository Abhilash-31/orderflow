package com.orderflow.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stock for one product. "Sellable" quantity is always
 * {@code availableQuantity - reservedQuantity} — never stored directly, to
 * avoid it drifting out of sync with the two numbers it's derived from.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class InventoryEntity {

    @Id
    private String productId;

    private int availableQuantity;

    private int reservedQuantity;

    public InventoryEntity(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
    }

    public int getSellableQuantity() {
        return availableQuantity - reservedQuantity;
    }
}
