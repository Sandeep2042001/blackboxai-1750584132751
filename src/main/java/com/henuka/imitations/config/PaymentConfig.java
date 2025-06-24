package com.henuka.imitations.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PaymentConfig {

    @Value("${app.payment.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.payment.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Bean
    public com.razorpay.RazorpayClient razorpayClient() throws com.razorpay.RazorpayException {
        return new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }
}
