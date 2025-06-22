package com.henuka.imitations.service;

import com.henuka.imitations.model.Product;
import com.henuka.imitations.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public Product createProduct(Product product) {
        validateProduct(product);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = getProductById(id);
        
        // Update the product fields
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setImageUrl(productDetails.getImageUrl());
        product.setStockQuantity(productDetails.getStockQuantity());
        product.setCategory(productDetails.getCategory());
        product.setFeatured(productDetails.isFeatured());
        
        validateProduct(product);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts() {
        return productRepository.findByFeaturedTrue();
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            String search,
            Pageable pageable) {
        return productRepository.searchProducts(category, minPrice, maxPrice, inStock, search, pageable);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    public Product updateStock(Long id, int quantity) {
        Product product = getProductById(id);
        
        if (quantity < 0 && Math.abs(quantity) > product.getStockQuantity()) {
            throw new IllegalArgumentException("Not enough stock available");
        }
        
        product.setStockQuantity(product.getStockQuantity() + quantity);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getRelatedProducts(String category, Long productId, Pageable pageable) {
        return productRepository.findRelatedProducts(category, productId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsNeedingRestock(int threshold) {
        return productRepository.findProductsNeedingRestock(threshold);
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
        
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
        
        // Check if product name already exists (for new products)
        if (product.getId() == null && 
            productRepository.existsByNameIgnoreCase(product.getName())) {
            throw new IllegalArgumentException("Product with this name already exists");
        }
    }

    public boolean isInStock(Long productId, int requestedQuantity) {
        Product product = getProductById(productId);
        return product.getStockQuantity() >= requestedQuantity;
    }

    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        Product product = getProductById(productId);
        if (product.getStockQuantity() < quantity) {
            throw new IllegalStateException("Not enough stock available for product: " + product.getName());
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        Product product = getProductById(productId);
        product.setStockQuantity(product.getStockQuantity() + quantity);
        productRepository.save(product);
    }
}
