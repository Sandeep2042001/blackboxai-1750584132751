package com.henuka.imitations.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cart_items")
public class CartItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
    
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public CartItem() {
    }
    
    public CartItem(Product product, Integer quantity, String sessionId) {
        this.product = product;
        this.quantity = quantity;
        this.sessionId = sessionId;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getSubtotal() {
        return product.getPrice().multiply(new BigDecimal(quantity));
    }
    
    public void incrementQuantity(int amount) {
        if (this.quantity + amount <= 0) {
            throw new IllegalArgumentException("Quantity cannot be zero or negative");
        }
        this.quantity += amount;
    }
    
    public void decrementQuantity(int amount) {
        if (this.quantity - amount <= 0) {
            throw new IllegalArgumentException("Quantity cannot be zero or negative");
        }
        this.quantity -= amount;
    }
    
    public boolean hasEnoughStock() {
        return product.getStockQuantity() >= this.quantity;
    }
}
