package com.henuka.imitations.controller;

import com.henuka.imitations.model.Order;
import com.henuka.imitations.service.CartService;
import com.henuka.imitations.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;

    @GetMapping("/checkout")
    public String showCheckout(Model model) {
        model.addAttribute("cart", cartService.getCurrentCart());
        return "orders/checkout";
    }

    @PostMapping("/place")
    public String placeOrder() {
        Order order = orderService.createOrderFromCart();
        cartService.clearCart();
        
        // Redirect to payment checkout
        return "redirect:/payments/checkout/" + order.getOrderNumber();
    }

    @GetMapping("/track/{orderNumber}")
    public String trackOrder(@PathVariable String orderNumber, Model model) {
        Order order = orderService.getOrderByNumber(orderNumber);
        model.addAttribute("order", order);
        return "orders/track";
    }

    @GetMapping("/confirmation/{orderNumber}")
    public String showConfirmation(@PathVariable String orderNumber, Model model) {
        Order order = orderService.getOrderByNumber(orderNumber);
        model.addAttribute("order", order);
        return "orders/confirmation";
    }
}
