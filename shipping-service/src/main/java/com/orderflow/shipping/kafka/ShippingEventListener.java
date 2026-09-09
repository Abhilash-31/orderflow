package com.orderflow.shipping.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.OrderShippedEvent;
import com.orderflow.events.PaymentAuthorizedEvent;
import com.orderflow.shipping.model.ShipmentEntity;
import com.orderflow.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ShippingEventListener {

    private final ShippingService shippingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = EventTopics.PAYMENT_AUTHORIZED, groupId = "shipping-service")
    public void onPaymentAuthorized(PaymentAuthorizedEvent event) {
        ShipmentEntity shipment = shippingService.createShipment(event.orderId());
        kafkaTemplate.send(EventTopics.ORDER_SHIPPED, event.orderId(),
                new OrderShippedEvent(event.orderId(), shipment.getId(), shipment.getTrackingNumber(), Instant.now()));
    }
}
