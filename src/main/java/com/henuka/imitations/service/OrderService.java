package com.henuka.imitations.service;

import com.henuka.imitations.model.CartItem;
import com.henuka.imitations.model.Order;
import com.henuka.imitations.model.OrderItem;
import com.henuka.imitations.model.Product;
import com.henuka.imitations.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("50");

    public Order createOrder(String sessionId, OrderRequest orderRequest) {
        // Validate cart before creating order
        cartService.validateCart(sessionId);
        
        List<CartItem> cartItems = cartService.getCartItems(sessionId);
        
        Order order = new Order();
        order.setCustomerName(orderRequest.customerName());
        order.setEmail(orderRequest.email());
        order.setPhoneNumber(orderRequest.phoneNumber());
        order.setShippingAddress(orderRequest.shippingAddress());
        
        // Convert cart items to order items
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            OrderItem orderItem = new OrderItem(product, cartItem.getQuantity());
            order.addItem(orderItem);
            
            // Decrease product stock
            productService.decreaseStock(product.getId(), cartItem.getQuantity());
        }
        
        // Calculate totals
        BigDecimal subtotal = order.getItems().stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        order.setSubtotal(subtotal);
        order.setShippingCost(calculateShippingCost(subtotal));
        order.setTotalAmount(subtotal.add(order.getShippingCost()));
        
        // Save the order
        Order savedOrder = orderRepository.save(order);
        
        // Clear the cart after successful order creation
        cartService.clearCart(sessionId);
        
        return savedOrder;
    }

    private BigDecimal calculateShippingCost(BigDecimal subtotal) {
        return subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0 ? BigDecimal.ZERO : SHIPPING_COST;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new EntityNotFoundException("Order not found with number: " + orderNumber));
    }

    public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = getOrderById(orderId);
        
        // Validate status transition
        validateStatusTransition(order.getStatus(), newStatus);
        
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Order updatePaymentStatus(Long orderId, Order.PaymentStatus newStatus) {
        Order order = getOrderById(orderId);
        order.setPaymentStatus(newStatus);
        
        if (newStatus == Order.PaymentStatus.PAID) {
            order.setStatus(Order.OrderStatus.CONFIRMED);
        }
        
        return orderRepository.save(order);
    }

    public Order cancelOrder(Long orderId) {
        Order order = getOrderById(orderId);
        
        if (!order.canBeCancelled()) {
            throw new IllegalStateException("Order cannot be cancelled in its current state");
        }
        
        // Restore product stock
        order.getItems().forEach(item -> 
            productService.increaseStock(item.getProduct().getId(), item.getQuantity())
        );
        
        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByEmail(String email) {
        return orderRepository.findByEmailOrderByCreatedAtDesc(email);
    }

    @Transactional(readOnly = true)
    public Page<Order> searchOrders(OrderSearchCriteria criteria, Pageable pageable) {
        return orderRepository.searchOrders(
            criteria.email(),
            criteria.status(),
            criteria.paymentStatus(),
            criteria.minAmount(),
            criteria.maxAmount(),
            criteria.startDate(),
            criteria.endDate(),
            pageable
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.getOrderStatistics(startDate, endDate);
    }

    private void validateStatusTransition(Order.OrderStatus currentStatus, Order.OrderStatus newStatus) {
        // Implement order status transition rules
        if (currentStatus == Order.OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of cancelled order");
        }
        
        if (currentStatus == Order.OrderStatus.DELIVERED && 
            newStatus != Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot change status of delivered order");
        }
        
        // Add more status transition validations as needed
    }

    // Data classes for request/response
    public record OrderRequest(
        String customerName,
        String email,
        String phoneNumber,
        String shippingAddress
    ) {}

    public record OrderSearchCriteria(
        String email,
        Order.OrderStatus status,
        Order.PaymentStatus paymentStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {}
}
