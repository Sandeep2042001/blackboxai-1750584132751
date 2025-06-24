package com.henuka.imitations.repository;

import com.henuka.imitations.model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);
    Optional<PaymentOrder> findByPaymentOrderId(String paymentOrderId);
    Optional<PaymentOrder> findByPaymentId(String paymentId);
}
