package com.henuka.imitations.controller;

import com.henuka.imitations.exception.PaymentException;
import com.henuka.imitations.model.PaymentOrder;
import com.henuka.imitations.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<?> createPayment(@RequestBody Map<String, String> request) {
        try {
            String orderId = request.get("orderId");
            BigDecimal amount = new BigDecimal(request.get("amount"));
            
            PaymentOrder paymentOrder = paymentService.createPaymentOrder(orderId, amount);
            
            return ResponseEntity.ok(Map.of(
                "orderId", paymentOrder.getPaymentOrderId(),
                "amount", paymentOrder.getAmount(),
                "currency", paymentOrder.getCurrency()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> request) {
        try {
            String orderId = request.get("razorpay_order_id");
            String paymentId = request.get("razorpay_payment_id");
            String signature = request.get("razorpay_signature");

            paymentService.processPaymentSuccess(orderId, paymentId, signature);
            
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (PaymentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/failure")
    public ResponseEntity<?> handlePaymentFailure(@RequestBody Map<String, String> request) {
        try {
            String orderId = request.get("order_id");
            String errorCode = request.get("error_code");
            String errorDescription = request.get("error_description");

            paymentService.processPaymentFailure(orderId, errorCode, errorDescription);
            
            return ResponseEntity.ok(Map.of("status", "failure_recorded"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refund")
    public ResponseEntity<?> initiateRefund(@RequestBody Map<String, String> request) {
        try {
            String orderId = request.get("orderId");
            BigDecimal amount = new BigDecimal(request.get("amount"));
            String reason = request.get("reason");

            paymentService.processRefund(orderId, amount, reason);
            
            return ResponseEntity.ok(Map.of("status", "refund_initiated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
