package com.orderflow.inventory;

import com.orderflow.events.OrderLineItem;
import com.orderflow.inventory.repository.InventoryRepository;
import com.orderflow.inventory.service.InsufficientStockException;
import com.orderflow.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This is the test that actually proves the resume claim: "prevents
 * overselling under concurrent load". It seeds exactly one unit of stock and
 * fires ten concurrent reservation attempts at it — the conditional-update
 * approach in InventoryRepository.tryReserve should let exactly one succeed
 * and the other nine fail cleanly, with no lost updates and no double sale,
 * without any explicit application-level locking.
 */
@Testcontainers
@SpringBootTest
class OversellPreventionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventorydb")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No Kafka needed for this test — point at an address nothing will
        // ever connect to and rely on the fact that we never publish here.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
    }

    @Autowired
    InventoryService inventoryService;

    @Autowired
    InventoryRepository inventoryRepository;

    @Test
    void sequentialReservation_failsOnceStockIsExhausted() {
        inventoryService.seed("sku-limited", 3);

        inventoryService.reserveForOrder("order-1", List.of(new OrderLineItem("sku-limited", 3, BigDecimal.ONE)));

        assertThatThrownBy(() ->
                inventoryService.reserveForOrder("order-2", List.of(new OrderLineItem("sku-limited", 1, BigDecimal.ONE)))
        ).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void concurrentReservations_forTheLastUnit_exactlyOneSucceeds() throws InterruptedException {
        String productId = "sku-hot-drop";
        inventoryService.seed(productId, 1);

        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startingGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(attempts);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            String orderId = "race-order-" + UUID.randomUUID();
            pool.submit(() -> {
                try {
                    startingGun.await();
                    inventoryService.reserveForOrder(orderId, List.of(new OrderLineItem(productId, 1, BigDecimal.ONE)));
                    successes.incrementAndGet();
                } catch (InsufficientStockException expected) {
                    failures.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startingGun.countDown();
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(attempts - 1);
        assertThat(inventoryRepository.findById(productId).orElseThrow().getSellableQuantity()).isZero();
    }
}
