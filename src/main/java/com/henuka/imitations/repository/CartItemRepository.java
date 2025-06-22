package com.henuka.imitations.repository;

import com.henuka.imitations.model.CartItem;
import com.henuka.imitations.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // Find all items in a shopping cart by session ID
    List<CartItem> findBySessionId(String sessionId);
    
    // Find a specific product in a cart
    Optional<CartItem> findBySessionIdAndProduct(String sessionId, Product product);
    
    // Delete all items in a cart
    @Transactional
    void deleteBySessionId(String sessionId);
    
    // Delete expired cart items (for cleanup)
    @Transactional
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.updatedAt < :expiryTime")
    void deleteExpiredCarts(@Param("expiryTime") LocalDateTime expiryTime);
    
    // Count items in a cart
    long countBySessionId(String sessionId);
    
    // Check if a product exists in a cart
    boolean existsBySessionIdAndProduct(String sessionId, Product product);
    
    // Find abandoned carts (not updated for a while)
    @Query("SELECT DISTINCT c.sessionId FROM CartItem c WHERE c.updatedAt < :cutoffTime")
    List<String> findAbandonedCartSessionIds(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Update quantity for a specific cart item
    @Transactional
    @Modifying
    @Query("UPDATE CartItem c SET c.quantity = :quantity, c.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE c.sessionId = :sessionId AND c.product.id = :productId")
    void updateQuantity(
        @Param("sessionId") String sessionId,
        @Param("productId") Long productId,
        @Param("quantity") Integer quantity
    );
    
    // Delete a specific product from cart
    @Transactional
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.sessionId = :sessionId AND c.product.id = :productId")
    void removeProductFromCart(
        @Param("sessionId") String sessionId,
        @Param("productId") Long productId
    );
    
    // Get total number of items in cart
    @Query("SELECT SUM(c.quantity) FROM CartItem c WHERE c.sessionId = :sessionId")
    Integer getTotalItemsInCart(@Param("sessionId") String sessionId);
    
    // Find carts with specific product
    @Query("SELECT c.sessionId FROM CartItem c WHERE c.product.id = :productId")
    List<String> findCartSessionsWithProduct(@Param("productId") Long productId);
}
