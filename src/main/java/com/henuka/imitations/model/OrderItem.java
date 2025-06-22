package com.henuka.imitations.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
    
    @NotNull
    @Column(name = "unit_price")
    private BigDecimal unitPrice;
    
    @NotNull
    @Column(name = "subtotal")
    private BigDecimal subtotal;
    
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
    
    public OrderItem() {
    }
    
    public OrderItem(Product product, Integer quantity) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.calculateSubtotal();
    }
    
    private void calculateSubtotal() {
        this.subtotal = this.unitPrice.multiply(new BigDecimal(this.quantity));
    }
    
    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculateSubtotal();
    }
    
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        this.quantity = newQuantity;
        calculateSubtotal();
    }
    
    public boolean hasEnoughStock() {
        return product.getStockQuantity() >= this.quantity;
    }
    
    public void validateStock() {
        if (!hasEnoughStock()) {
            throw new IllegalStateException(
                String.format("Not enough stock for product %s. Available: %d, Requested: %d",
                    product.getName(),
                    product.getStockQuantity(),
                    this.quantity)
            );
        }
    }
}
