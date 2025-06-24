package com.henuka.imitations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.util.Properties;

@Configuration
public class EmailConfig {

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private int mailPort;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    /**
     * Configure mail sender
     */
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }

    /**
     * Configure template resolver
     */
    @Bean
    public ClassLoaderTemplateResolver emailTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        return resolver;
    }

    /**
     * Configure template engine
     */
    @Bean
    public SpringTemplateEngine emailTemplateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(emailTemplateResolver());
        return templateEngine;
    }
}

/**
 * Email service
 */
@org.springframework.stereotype.Service
class EmailService {
    
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromEmail;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    public EmailService(JavaMailSender mailSender, 
                       SpringTemplateEngine templateEngine,
                       @Value("${spring.mail.from}") String fromEmail) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
    }

    /**
     * Send template email
     */
    public void sendTemplateEmail(String to, String subject, String template, 
                                java.util.Map<String, Object> variables) {
        try {
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariables(variables);

            String htmlContent = templateEngine.process(template, context);

            org.springframework.mail.javamail.MimeMessageHelper helper = 
                new org.springframework.mail.javamail.MimeMessageHelper(
                    mailSender.createMimeMessage(), true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(helper.getMimeMessage());
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new EmailException("Failed to send email", e);
        }
    }

    /**
     * Send order confirmation
     */
    public void sendOrderConfirmation(String to, String orderNumber, 
                                    java.util.List<OrderItem> items, double total) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("orderNumber", orderNumber);
        variables.put("items", items);
        variables.put("total", total);
        variables.put("date", java.time.LocalDateTime.now());

        sendTemplateEmail(to, "Order Confirmation #" + orderNumber, 
                         "order-confirmation", variables);
    }

    /**
     * Send password reset
     */
    public void sendPasswordReset(String to, String resetToken) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("resetToken", resetToken);
        variables.put("expiryTime", java.time.LocalDateTime.now().plusHours(24));

        sendTemplateEmail(to, "Password Reset Request", 
                         "password-reset", variables);
    }

    /**
     * Send welcome email
     */
    public void sendWelcomeEmail(String to, String name) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("name", name);
        variables.put("date", java.time.LocalDateTime.now());

        sendTemplateEmail(to, "Welcome to Henuka Imitations", 
                         "welcome", variables);
    }

    /**
     * Send payment confirmation email
     */
    public void sendPaymentConfirmationEmail(Order order, PaymentOrder paymentOrder) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        variables.put("baseUrl", getBaseUrl());

        sendTemplateEmail(order.getEmail(), 
                         "Payment Confirmation - Order #" + order.getOrderNumber(),
                         "emails/payment-confirmation", variables);
    }

    /**
     * Send payment failure email
     */
    public void sendPaymentFailureEmail(Order order, PaymentOrder paymentOrder) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        variables.put("baseUrl", getBaseUrl());

        sendTemplateEmail(order.getEmail(),
                         "Payment Failed - Order #" + order.getOrderNumber(),
                         "emails/payment-failed", variables);
    }

    /**
     * Send refund confirmation email
     */
    public void sendRefundConfirmationEmail(Order order, PaymentOrder paymentOrder) {
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("order", order);
        variables.put("payment", paymentOrder);
        variables.put("baseUrl", getBaseUrl());

        sendTemplateEmail(order.getEmail(),
                         "Refund Confirmation - Order #" + order.getOrderNumber(),
                         "emails/refund-confirmation", variables);
    }

    /**
     * Get base URL for email templates
     */
    private String getBaseUrl() {
        // In production, this should be configured via properties
        return "http://localhost:8000";
    }
}

/**
 * Email exception
 */
class EmailException extends RuntimeException {
    
    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Email metrics
 */
@org.springframework.stereotype.Component
class EmailMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public EmailMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordEmailSent(String template) {
        registry.counter("email.sent", "template", template).increment();
    }

    public void recordEmailError(String template) {
        registry.counter("email.error", "template", template).increment();
    }
}
