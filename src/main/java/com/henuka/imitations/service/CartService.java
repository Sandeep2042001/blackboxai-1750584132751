package com.henuka.imitations.service;

import com.henuka.imitations.model.CartItem;
import com.henuka.imitations.model.Product;
import com.henuka.imitations.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("50");

    public CartItem addToCart(String sessionId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Product product = productService.getProductById(productId);
        
        // Check if product is in stock
        if (!productService.isInStock(productId, quantity)) {
            throw new IllegalStateException("Not enough stock available for product: " + product.getName());
        }

        // Check if product already exists in cart
        Optional<CartItem> existingItem = cartItemRepository.findBySessionIdAndProduct(sessionId, product);
        
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + quantity;
            
            // Validate new quantity against stock
            if (!productService.isInStock(productId, newQuantity)) {
                throw new IllegalStateException("Not enough stock available for requested quantity");
            }
            
            item.setQuantity(newQuantity);
            return cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem(product, quantity, sessionId);
            return cartItemRepository.save(newItem);
        }
    }

    public void updateQuantity(String sessionId, Long productId, int quantity) {
        if (quantity <= 0) {
            removeFromCart(sessionId, productId);
            return;
        }

        Product product = productService.getProductById(productId);
        
        // Check if new quantity is available in stock
        if (!productService.isInStock(productId, quantity)) {
            throw new IllegalStateException("Not enough stock available for product: " + product.getName());
        }

        cartItemRepository.updateQuantity(sessionId, productId, quantity);
    }

    public void removeFromCart(String sessionId, Long productId) {
        cartItemRepository.removeProductFromCart(sessionId, productId);
    }

    public void clearCart(String sessionId) {
        cartItemRepository.deleteBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(String sessionId) {
        return cartItemRepository.findBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public CartSummary getCartSummary(String sessionId) {
        List<CartItem> items = getCartItems(sessionId);
        
        BigDecimal subtotal = items.stream()
            .map(item -> item.getProduct().getPrice().multiply(new BigDecimal(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal shippingCost = calculateShippingCost(subtotal);
        BigDecimal total = subtotal.add(shippingCost);
        
        return new CartSummary(items, subtotal, shippingCost, total);
    }

    private BigDecimal calculateShippingCost(BigDecimal subtotal) {
        return subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0 ? BigDecimal.ZERO : SHIPPING_COST;
    }

    public void validateCart(String sessionId) {
        List<CartItem> items = getCartItems(sessionId);
        
        if (items.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        for (CartItem item : items) {
            if (!productService.isInStock(item.getProduct().getId(), item.getQuantity())) {
                throw new IllegalStateException(
                    "Not enough stock available for product: " + item.getProduct().getName()
                );
            }
        }
    }

    // Clean up expired cart items (can be scheduled to run periodically)
    @Transactional
    public void cleanupExpiredCarts(int expiryHours) {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(expiryHours);
        cartItemRepository.deleteExpiredCarts(expiryTime);
    }

    @Transactional(readOnly = true)
    public Integer getTotalItemsInCart(String sessionId) {
        return cartItemRepository.getTotalItemsInCart(sessionId);
    }

    // Data class for cart summary
    public record CartSummary(
        List<CartItem> items,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal total
    ) {}
}
