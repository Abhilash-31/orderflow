package com.orderflow.payment.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * InventoryReserved can, in principle, be delivered to this service before
 * it has finished processing the OrderCreated event for the same order —
 * the two arrive on different topics with no cross-topic ordering guarantee.
 * Rather than dropping the event, retry a handful of times with a short
 * backoff; by then OrderCreated will almost always have been processed. If
 * it still hasn't after ~4 attempts, log and move on rather than blocking
 * the whole partition indefinitely.
 */
@Configuration
public class PaymentKafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(300L, 2.0);
        backOff.setMaxElapsedTime(5_000L);
        return new DefaultErrorHandler(backOff);
    }
}
