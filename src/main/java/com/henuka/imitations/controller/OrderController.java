package com.henuka.imitations.controller;

import com.henuka.imitations.model.Order;
import com.henuka.imitations.service.CartService;
import com.henuka.imitations.service.OrderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private static final String SESSION_CART_ID = "cartSessionId";

    @GetMapping("/checkout")
    public String showCheckoutForm(Model model, HttpSession session) {
        String cartSessionId = (String) session.getAttribute(SESSION_CART_ID);
        if (cartSessionId == null) {
            return "redirect:/cart";
        }

        try {
            // Validate cart before showing checkout
            cartService.validateCart(cartSessionId);
            
            CartService.CartSummary cartSummary = cartService.getCartSummary(cartSessionId);
            model.addAttribute("cartSummary", cartSummary);
            model.addAttribute("orderRequest", new OrderService.OrderRequest(null, null, null, null));
            
            return "orders/checkout";
            
        } catch (Exception e) {
            return "redirect:/cart";
        }
    }

    @PostMapping("/checkout")
    public String processCheckout(
            @Valid @ModelAttribute OrderService.OrderRequest orderRequest,
            BindingResult result,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        if (result.hasErrors()) {
            return "orders/checkout";
        }

        String cartSessionId = (String) session.getAttribute(SESSION_CART_ID);
        if (cartSessionId == null) {
            return "redirect:/cart";
        }

        try {
            Order order = orderService.createOrder(cartSessionId, orderRequest);
            
            // Clear the cart session after successful order
            session.removeAttribute(SESSION_CART_ID);
            
            redirectAttributes.addFlashAttribute("message", 
                "Order placed successfully! Your order number is: " + order.getOrderNumber());
            
            return "redirect:/orders/" + order.getId() + "/confirmation";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/checkout";
        }
    }

    @GetMapping("/{id}/confirmation")
    public String showOrderConfirmation(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderById(id);
        model.addAttribute("order", order);
        return "orders/confirmation";
    }

    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderById(id);
        model.addAttribute("order", order);
        return "orders/view";
    }

    @GetMapping("/track/{orderNumber}")
    public String trackOrder(@PathVariable String orderNumber, Model model) {
        Order order = orderService.getOrderByNumber(orderNumber);
        model.addAttribute("order", order);
        return "orders/track";
    }

    // Admin endpoints
    @PostMapping("/admin/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status,
            RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.updateOrderStatus(id, status);
            redirectAttributes.addFlashAttribute("message", 
                "Order status updated to: " + status);
            return "redirect:/orders/admin/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/admin/" + id;
        }
    }

    @PostMapping("/admin/{id}/payment-status")
    public String updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam Order.PaymentStatus status,
            RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.updatePaymentStatus(id, status);
            redirectAttributes.addFlashAttribute("message", 
                "Payment status updated to: " + status);
            return "redirect:/orders/admin/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/admin/" + id;
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.cancelOrder(id);
            redirectAttributes.addFlashAttribute("message", "Order cancelled successfully");
            return "redirect:/orders/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/orders/" + id;
        }
    }

    // API endpoints for AJAX calls
    @GetMapping("/api/{orderNumber}/status")
    @ResponseBody
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String orderNumber) {
        Order order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(new OrderStatusResponse(
            order.getStatus(),
            order.getPaymentStatus()
        ));
    }

    private record OrderStatusResponse(
        Order.OrderStatus orderStatus,
        Order.PaymentStatus paymentStatus
    ) {}
}
