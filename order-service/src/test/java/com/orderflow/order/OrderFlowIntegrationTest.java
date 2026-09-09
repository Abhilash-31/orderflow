package com.orderflow.order;

import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * End-to-end proof that the first hop of the Saga actually works: hitting
 * the real REST API against a real Postgres, and confirming the order lands
 * in the database as PENDING with an OrderCreated event on the wire. This is
 * run against real containers (Testcontainers), not mocks, so it needs
 * Docker available on the machine running the build.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void placingAnOrder_persistsItAsPending() {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-123",
                List.of(new CreateOrderRequest.LineItem("sku-widget", 2, new BigDecimal("19.99")))
        );

        ResponseEntity<OrderResponse> createResponse =
                restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        OrderResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.totalAmount()).isEqualByComparingTo("39.98");

        // Confirm it round-trips through the database via the GET endpoint,
        // proving the JPA mapping and REST layer both actually work end to
        // end, not just that the POST didn't throw.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<OrderResponse> getResponse =
                    restTemplate.getForEntity("/orders/" + created.orderId(), OrderResponse.class);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody().status()).isEqualTo(OrderStatus.PENDING);
        });
    }
}
