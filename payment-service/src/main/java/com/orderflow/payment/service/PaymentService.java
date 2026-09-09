package com.orderflow.payment.service;

import com.orderflow.payment.model.PaymentEntity;
import com.orderflow.payment.model.PaymentStatus;
import com.orderflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Deterministic mock "decline" so the failure/compensation path can be
     * demonstrated on demand rather than relying on random chance — order
     * anything over this amount to see PaymentFailed -> OrderCancelled ->
     * inventory released play out.
     */
    @Value("${payment.decline-threshold:1000}")
    private BigDecimal declineThreshold;

    @Transactional
    public void recordPendingPayment(String orderId, BigDecimal amount) {
        if (paymentRepository.existsById(orderId)) {
            return; // duplicate delivery of OrderCreated — idempotent no-op
        }
        paymentRepository.save(new PaymentEntity(orderId, amount, PaymentStatus.PENDING_INVENTORY, Instant.now()));
    }

    /**
     * @return true if authorized, false if declined. Throws
     * PaymentPendingException if OrderCreated hasn't been processed for this
     * order yet, so the Kafka listener can retry rather than silently
     * dropping the event.
     */
    @Transactional
    public boolean authorize(String orderId) {
        PaymentEntity payment = paymentRepository.findById(orderId)
                .orElseThrow(() -> new PaymentPendingException(
                        "No pending payment record yet for order " + orderId));

        if (payment.getStatus() != PaymentStatus.PENDING_INVENTORY) {
            log.info("Payment for order {} already processed (status={}) — skipping duplicate", orderId, payment.getStatus());
            return payment.getStatus() == PaymentStatus.AUTHORIZED;
        }

        boolean approved = payment.getAmount().compareTo(declineThreshold) <= 0;
        payment.setStatus(approved ? PaymentStatus.AUTHORIZED : PaymentStatus.FAILED);
        payment.setPaymentId(approved ? UUID.randomUUID().toString() : null);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
        return approved;
    }

    public PaymentEntity get(String orderId) {
        return paymentRepository.findById(orderId).orElse(null);
    }

    @Transactional
    public void refund(String orderId) {
        paymentRepository.findById(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setUpdatedAt(Instant.now());
                paymentRepository.save(payment);
                log.info("Refunded payment for order {}", orderId);
            }
        });
    }
}
