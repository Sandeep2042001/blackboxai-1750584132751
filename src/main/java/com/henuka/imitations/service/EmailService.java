package com.henuka.imitations.service;

import com.henuka.imitations.model.Order;
import com.henuka.imitations.model.PaymentOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    
    @Async
    public void sendPaymentConfirmationEmail(Order order, PaymentOrder paymentOrder) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        
        sendEmail(
            order.getEmail(),
            "Payment Confirmation - Order #" + order.getOrderNumber(),
            "emails/payment-confirmation",
            variables
        );
    }
    
    @Async
    public void sendPaymentFailureEmail(Order order, PaymentOrder paymentOrder) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        
        sendEmail(
            order.getEmail(),
            "Payment Failed - Order #" + order.getOrderNumber(),
            "emails/payment-failed",
            variables
        );
    }
    
    @Async
    public void sendRefundConfirmationEmail(Order order, PaymentOrder paymentOrder) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        
        sendEmail(
            order.getEmail(),
            "Refund Processed - Order #" + order.getOrderNumber(),
            "emails/refund-confirmation",
            variables
        );
    }
    
    private void sendEmail(String to, String subject, String template, Map<String, Object> variables) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            Context context = new Context();
            variables.forEach(context::setVariable);
            
            String htmlContent = templateEngine.process(template, context);
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
