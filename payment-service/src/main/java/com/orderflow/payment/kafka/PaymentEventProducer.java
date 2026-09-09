package com.orderflow.payment.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.PaymentAuthorizedEvent;
import com.orderflow.events.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAuthorized(PaymentAuthorizedEvent event) {
        kafkaTemplate.send(EventTopics.PAYMENT_AUTHORIZED, event.orderId(), event);
    }

    public void publishFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(EventTopics.PAYMENT_FAILED, event.orderId(), event);
    }
}
