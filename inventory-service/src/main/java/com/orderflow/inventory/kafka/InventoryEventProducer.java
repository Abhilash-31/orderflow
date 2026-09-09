package com.orderflow.inventory.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(EventTopics.INVENTORY_RESERVED, event.orderId(), event);
    }

    public void publishReservationFailed(InventoryReservationFailedEvent event) {
        kafkaTemplate.send(EventTopics.INVENTORY_RESERVATION_FAILED, event.orderId(), event);
    }
}
