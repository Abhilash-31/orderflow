package com.orderflow.order.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreated for orderId={}", event.orderId());
        // Keyed by orderId so every event for a given order lands on the same
        // partition and downstream consumers see them in order.
        kafkaTemplate.send(EventTopics.ORDER_CREATED, event.orderId(), event);
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.warn("Publishing OrderCancelled for orderId={} reason={}", event.orderId(), event.reason());
        kafkaTemplate.send(EventTopics.ORDER_CANCELLED, event.orderId(), event);
    }
}
