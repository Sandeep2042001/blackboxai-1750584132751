package com.henuka.imitations.service;

import com.henuka.imitations.exception.PaymentException;
import com.henuka.imitations.model.Order;
import com.henuka.imitations.model.PaymentOrder;
import com.henuka.imitations.repository.PaymentOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final com.razorpay.RazorpayClient razorpayClient;
    private final PaymentOrderRepository paymentOrderRepository;
    private final OrderService orderService;
    private final EmailService emailService;
    
    @Value("${app.payment.currency:INR}")
    private String currency;
    
    @Value("${app.payment.success-url}")
    private String successUrl;
    
    @Value("${app.payment.failure-url}")
    private String failureUrl;
    
    @Value("${app.payment.cancel-url}")
    private String cancelUrl;

    @Value("${app.payment.razorpay.key-secret}")
    private String razorpayKeySecret;
    
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public PaymentOrder createPaymentOrder(String orderId, BigDecimal amount) {
        try {
            // Create Razorpay order
            org.json.JSONObject orderRequest = new org.json.JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Convert to paise
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", orderId);
            orderRequest.put("payment_capture", 1);
            orderRequest.put("notes", new org.json.JSONObject()
                .put("order_id", orderId)
                .put("success_url", successUrl)
                .put("failure_url", failureUrl)
                .put("cancel_url", cancelUrl));

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            
            // Create and save payment order
            PaymentOrder paymentOrder = new PaymentOrder();
            paymentOrder.setOrderId(orderId);
            paymentOrder.setPaymentOrderId(razorpayOrder.get("id"));
            paymentOrder.setAmount(amount);
            paymentOrder.setCurrency(currency);
            paymentOrder.setStatus(PaymentOrder.PaymentStatus.PENDING);
            
            return paymentOrderRepository.save(paymentOrder);
        } catch (Exception e) {
            log.error("Error creating payment order", e);
            throw new PaymentException("Failed to create payment order", e);
        }
    }

    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        try {
            org.json.JSONObject attributes = new org.json.JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);

            return com.razorpay.Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
        } catch (Exception e) {
            log.error("Error verifying payment signature", e);
            return false;
        }
    }

    public void processPaymentSuccess(String orderId, String paymentId, String signature) {
        try {
            if (!verifyPaymentSignature(orderId, paymentId, signature)) {
                throw new PaymentException("Invalid payment signature");
            }

            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + orderId));
            
            paymentOrder.setStatus(PaymentOrder.PaymentStatus.SUCCESS);
            paymentOrder.setPaymentId(paymentId);
            paymentOrderRepository.save(paymentOrder);

            // Update order status
            Order order = orderService.confirmOrder(orderId);

            // Send confirmation email
            emailService.sendPaymentConfirmationEmail(order, paymentOrder);
        } catch (Exception e) {
            log.error("Error processing payment success", e);
            throw new PaymentException("Failed to process payment success", e);
        }
    }

    public void processPaymentFailure(String orderId, String errorCode, String errorDescription) {
        try {
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + orderId));
            
            paymentOrder.setStatus(PaymentOrder.PaymentStatus.FAILED);
            paymentOrder.setErrorCode(errorCode);
            paymentOrder.setErrorDescription(errorDescription);
            paymentOrderRepository.save(paymentOrder);

            // Update order status
            Order order = orderService.failOrder(orderId);

            // Send failure notification
            emailService.sendPaymentFailureEmail(order, paymentOrder);
        } catch (Exception e) {
            log.error("Error processing payment failure", e);
            throw new PaymentException("Failed to process payment failure", e);
        }
    }

    public void processRefund(String orderId, BigDecimal amount, String reason) {
        try {
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment order not found: " + orderId));
            
            if (paymentOrder.getStatus() != PaymentOrder.PaymentStatus.SUCCESS) {
                throw new PaymentException("Cannot refund non-successful payment");
            }

            org.json.JSONObject refundRequest = new org.json.JSONObject();
            refundRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue());
            refundRequest.put("speed", "normal");
            refundRequest.put("notes", new org.json.JSONObject().put("reason", reason));

            com.razorpay.Refund refund = razorpayClient.payments
                    .refund(paymentOrder.getPaymentId(), refundRequest);

            paymentOrder.setStatus(PaymentOrder.PaymentStatus.REFUNDED);
            paymentOrder.setRefundId(refund.get("id"));
            paymentOrderRepository.save(paymentOrder);

            // Update order status
            Order order = orderService.refundOrder(orderId);

            // Send refund notification
            emailService.sendRefundConfirmationEmail(order, paymentOrder);
        } catch (Exception e) {
            log.error("Error processing refund", e);
            throw new PaymentException("Failed to process refund", e);
        }
    }
}
