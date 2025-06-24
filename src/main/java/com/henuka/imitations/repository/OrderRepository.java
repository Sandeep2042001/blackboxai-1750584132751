package com.henuka.imitations.repository;

import com.henuka.imitations.model.Order;
import java.util.Map;
import com.henuka.imitations.model.Order.OrderStatus;
import com.henuka.imitations.model.Order.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Find order by order number
    Optional<Order> findByOrderNumber(String orderNumber);
    
    // Find orders by email
    List<Order> findByEmailOrderByCreatedAtDesc(String email);
    
    // Find orders by status
    List<Order> findByStatus(OrderStatus status);
    
    // Find orders by payment status
    List<Order> findByPaymentStatus(PaymentStatus paymentStatus);
    
    // Find orders between dates
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // Find orders with pagination and sorting
    Page<Order> findByEmail(String email, Pageable pageable);
    
    // Search orders with multiple criteria
    @Query("SELECT o FROM Order o WHERE " +
           "(:email IS NULL OR o.email = :email) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) AND " +
           "(:minAmount IS NULL OR o.totalAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR o.totalAmount <= :maxAmount) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate)")
    Page<Order> searchOrders(
        @Param("email") String email,
        @Param("status") OrderStatus status,
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    // Get total revenue between dates
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE " +
           "o.paymentStatus = 'PAID' AND " +
           "o.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalRevenue(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Count orders by status
    Long countByStatus(OrderStatus status);
    
    // Count orders by payment status
    Long countByPaymentStatus(PaymentStatus paymentStatus);
    
    // Find recent orders
    List<Order> findTop10ByOrderByCreatedAtDesc();
    
    // Find pending orders older than specified time
    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :cutoffTime")
    List<Order> findPendingOrdersOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Get order statistics
    @Query("SELECT new map(" +
           "COUNT(o) as totalOrders, " +
           "SUM(o.totalAmount) as totalRevenue, " +
           "AVG(o.totalAmount) as averageOrderValue) " +
           "FROM Order o WHERE o.paymentStatus = 'PAID' AND " +
           "o.createdAt BETWEEN :startDate AND :endDate")
    Map<String, Object> getOrderStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Find orders containing specific product
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i WHERE i.product.id = :productId")
    List<Order> findOrdersContainingProduct(@Param("productId") Long productId);
}
