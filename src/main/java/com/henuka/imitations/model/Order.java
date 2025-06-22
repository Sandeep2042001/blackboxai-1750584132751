package com.henuka.imitations.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_number", unique = true)
    private String orderNumber;
    
    @NotBlank(message = "Customer name is required")
    @Column(name = "customer_name")
    private String customerName;
    
    @NotBlank(message = "Email is required")
    private String email;
    
    @NotBlank(message = "Phone number is required")
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @NotBlank(message = "Shipping address is required")
    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();
    
    @NotNull
    @Column(name = "subtotal")
    private BigDecimal subtotal = BigDecimal.ZERO;
    
    @NotNull
    @Column(name = "shipping_cost")
    private BigDecimal shippingCost = BigDecimal.ZERO;
    
    @NotNull
    @Column(name = "total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus status = OrderStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Column(name = "payment_id")
    private String paymentId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
    
    public enum PaymentStatus {
        PENDING, PAID, FAILED, REFUNDED
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderNumber == null) {
            orderNumber = generateOrderNumber();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
    
    public void addItem(Product product, int quantity) {
        OrderItem orderItem = new OrderItem(product, quantity);
        items.add(orderItem);
        recalculateAmounts();
    }
    
    public void removeItem(OrderItem item) {
        items.remove(item);
        recalculateAmounts();
    }
    
    private void recalculateAmounts() {
        this.subtotal = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate shipping cost (can be modified based on business logic)
        this.shippingCost = subtotal.compareTo(new BigDecimal("500")) > 0 
            ? BigDecimal.ZERO 
            : new BigDecimal("50");
        
        this.totalAmount = this.subtotal.add(this.shippingCost);
    }
    
    public boolean canBeCancelled() {
        return this.status == OrderStatus.PENDING || this.status == OrderStatus.CONFIRMED;
    }
}
