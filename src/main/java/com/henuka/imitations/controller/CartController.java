package com.henuka.imitations.controller;

import com.henuka.imitations.model.CartItem;
import com.henuka.imitations.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private static final String SESSION_CART_ID = "cartSessionId";

    private String getOrCreateCartSessionId(HttpSession session) {
        String cartSessionId = (String) session.getAttribute(SESSION_CART_ID);
        if (cartSessionId == null) {
            cartSessionId = UUID.randomUUID().toString();
            session.setAttribute(SESSION_CART_ID, cartSessionId);
        }
        return cartSessionId;
    }

    @GetMapping
    public String viewCart(Model model, HttpSession session) {
        String cartSessionId = getOrCreateCartSessionId(session);
        CartService.CartSummary cartSummary = cartService.getCartSummary(cartSessionId);
        
        model.addAttribute("cartItems", cartSummary.items());
        model.addAttribute("subtotal", cartSummary.subtotal());
        model.addAttribute("shippingCost", cartSummary.shippingCost());
        model.addAttribute("total", cartSummary.total());
        
        return "cart/view";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        try {
            String cartSessionId = getOrCreateCartSessionId(session);
            CartItem cartItem = cartService.addToCart(cartSessionId, productId, quantity);
            
            redirectAttributes.addFlashAttribute("message", 
                "Added " + quantity + " item(s) to cart");
            
            // If the request is AJAX, return the updated cart item count
            if (isAjaxRequest()) {
                return ResponseEntity.ok(cartService.getTotalItemsInCart(cartSessionId))
                    .toString();
            }
            
            return "redirect:/cart";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products/" + productId;
        }
    }

    @PostMapping("/update")
    public String updateQuantity(
            @RequestParam Long productId,
            @RequestParam int quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        try {
            String cartSessionId = getOrCreateCartSessionId(session);
            cartService.updateQuantity(cartSessionId, productId, quantity);
            
            if (isAjaxRequest()) {
                CartService.CartSummary summary = cartService.getCartSummary(cartSessionId);
                return ResponseEntity.ok(new CartUpdateResponse(
                    summary.subtotal(),
                    summary.shippingCost(),
                    summary.total(),
                    cartService.getTotalItemsInCart(cartSessionId)
                )).toString();
            }
            
            return "redirect:/cart";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }

    @PostMapping("/remove")
    public String removeFromCart(
            @RequestParam Long productId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        try {
            String cartSessionId = getOrCreateCartSessionId(session);
            cartService.removeFromCart(cartSessionId, productId);
            
            redirectAttributes.addFlashAttribute("message", "Item removed from cart");
            
            if (isAjaxRequest()) {
                CartService.CartSummary summary = cartService.getCartSummary(cartSessionId);
                return ResponseEntity.ok(new CartUpdateResponse(
                    summary.subtotal(),
                    summary.shippingCost(),
                    summary.total(),
                    cartService.getTotalItemsInCart(cartSessionId)
                )).toString();
            }
            
            return "redirect:/cart";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }

    @PostMapping("/clear")
    public String clearCart(
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        String cartSessionId = getOrCreateCartSessionId(session);
        cartService.clearCart(cartSessionId);
        
        redirectAttributes.addFlashAttribute("message", "Cart cleared");
        return "redirect:/cart";
    }

    // API endpoints for AJAX calls
    @GetMapping("/api/count")
    @ResponseBody
    public ResponseEntity<Integer> getCartItemCount(HttpSession session) {
        String cartSessionId = getOrCreateCartSessionId(session);
        return ResponseEntity.ok(cartService.getTotalItemsInCart(cartSessionId));
    }

    @GetMapping("/api/summary")
    @ResponseBody
    public ResponseEntity<CartService.CartSummary> getCartSummary(HttpSession session) {
        String cartSessionId = getOrCreateCartSessionId(session);
        return ResponseEntity.ok(cartService.getCartSummary(cartSessionId));
    }

    private boolean isAjaxRequest() {
        return true; // Simplified for example; implement actual AJAX detection logic
    }

    // Response record for cart updates
    private record CartUpdateResponse(
        java.math.BigDecimal subtotal,
        java.math.BigDecimal shippingCost,
        java.math.BigDecimal total,
        Integer itemCount
    ) {}
}
