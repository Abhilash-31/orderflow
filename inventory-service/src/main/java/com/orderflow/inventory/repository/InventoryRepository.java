package com.orderflow.inventory.repository;

import com.orderflow.inventory.model.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {

    /**
     * The core oversell-prevention mechanism: a single atomic conditional
     * UPDATE. Postgres evaluates the WHERE clause and applies the increment
     * as one atomic operation per row, so two concurrent requests racing for
     * the last unit of stock cannot both succeed — the second one's WHERE
     * clause simply won't match once the first has committed. This makes the
     * reservation safe under concurrency without needing an explicit
     * SELECT ... FOR UPDATE lock.
     *
     * @return number of rows updated — 0 means insufficient sellable stock.
     */
    @Modifying
    @Query("""
            UPDATE InventoryEntity i
            SET i.reservedQuantity = i.reservedQuantity + :quantity
            WHERE i.productId = :productId
              AND (i.availableQuantity - i.reservedQuantity) >= :quantity
            """)
    int tryReserve(@Param("productId") String productId, @Param("quantity") int quantity);

    @Modifying
    @Query("""
            UPDATE InventoryEntity i
            SET i.reservedQuantity = i.reservedQuantity - :quantity
            WHERE i.productId = :productId
              AND i.reservedQuantity >= :quantity
            """)
    int release(@Param("productId") String productId, @Param("quantity") int quantity);
}
