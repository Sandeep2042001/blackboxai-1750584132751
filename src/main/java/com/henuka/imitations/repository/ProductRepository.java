package com.henuka.imitations.repository;

import com.henuka.imitations.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Find featured products
    List<Product> findByFeaturedTrue();
    
    // Find products by category
    List<Product> findByCategory(String category);
    
    // Search products by name (case-insensitive)
    List<Product> findByNameContainingIgnoreCase(String name);
    
    // Find products by price range
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    // Find products that are in stock
    List<Product> findByStockQuantityGreaterThan(Integer minStock);
    
    // Find products with pagination
    Page<Product> findAll(Pageable pageable);
    
    // Search products with multiple criteria
    @Query("SELECT p FROM Product p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:inStock IS NULL OR (p.stockQuantity > 0) = :inStock) AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> searchProducts(
        @Param("category") String category,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("inStock") Boolean inStock,
        @Param("search") String search,
        Pageable pageable
    );
    
    // Find related products (same category, excluding the current product)
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.id != :productId")
    List<Product> findRelatedProducts(@Param("category") String category, @Param("productId") Long productId, Pageable pageable);
    
    // Count products by category
    Long countByCategory(String category);
    
    // Check if a product name already exists
    boolean existsByNameIgnoreCase(String name);
    
    // Find products that need restocking (low stock)
    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= :threshold")
    List<Product> findProductsNeedingRestock(@Param("threshold") Integer threshold);
}
