package com.orderflow.payment.kafka;

import com.orderflow.events.EventTopics;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.OrderCancelledEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.PaymentAuthorizedEvent;
import com.orderflow.events.PaymentFailedEvent;
import com.orderflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final PaymentEventProducer eventProducer;

    @KafkaListener(topics = EventTopics.ORDER_CREATED, groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        // Payment Service keeps its own local copy of just the order total,
        // sourced from the event stream rather than a synchronous call back
        // to Order Service. See PaymentEntity for why.
        paymentService.recordPendingPayment(event.orderId(), event.totalAmount());
    }

    @KafkaListener(topics = EventTopics.INVENTORY_RESERVED, groupId = "payment-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        // If this throws PaymentPendingException, PaymentKafkaConfig's error
        // handler retries with backoff rather than dropping the event — see
        // the class-level note on why this race is possible.
        boolean approved = paymentService.authorize(event.orderId());
        if (approved) {
            var payment = paymentService.get(event.orderId());
            eventProducer.publishAuthorized(new PaymentAuthorizedEvent(
                    event.orderId(), payment.getPaymentId(), payment.getAmount(), Instant.now()));
        } else {
            log.warn("Payment declined for order {}", event.orderId());
            eventProducer.publishFailed(new PaymentFailedEvent(
                    event.orderId(), "amount exceeds mock decline threshold", Instant.now()));
        }
    }

    @KafkaListener(topics = EventTopics.ORDER_CANCELLED, groupId = "payment-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        // Compensating transaction: refund if (and only if) this order's
        // payment had actually been authorized.
        paymentService.refund(event.orderId());
    }
}
