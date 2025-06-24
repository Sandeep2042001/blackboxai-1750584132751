package com.henuka.imitations.controller;

import com.henuka.imitations.model.Order;
import com.henuka.imitations.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentViewController {

    private final OrderService orderService;

    @Value("${app.payment.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.payment.success-url}")
    private String successUrl;

    @Value("${app.payment.failure-url}")
    private String failureUrl;

    @GetMapping("/checkout/{orderNumber}")
    public String showCheckout(@PathVariable String orderNumber, Model model) {
        Order order = orderService.getOrderByNumber(orderNumber);
        
        model.addAttribute("order", order);
        model.addAttribute("razorpayKeyId", razorpayKeyId);
        model.addAttribute("successUrl", successUrl);
        model.addAttribute("failureUrl", failureUrl);
        
        return "payments/checkout";
    }

    @GetMapping("/success")
    public String showSuccess(@RequestParam(required = false) String orderNumber, Model model) {
        if (orderNumber != null) {
            Order order = orderService.getOrderByNumber(orderNumber);
            model.addAttribute("order", order);
        }
        return "payments/success";
    }

    @GetMapping("/failure")
    public String showFailure(@RequestParam(required = false) String orderNumber, 
                            @RequestParam(required = false) String error,
                            Model model) {
        if (orderNumber != null) {
            model.addAttribute("orderNumber", orderNumber);
        }
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "payments/failure";
    }
}
