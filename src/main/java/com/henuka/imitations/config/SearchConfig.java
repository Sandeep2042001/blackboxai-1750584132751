package com.henuka.imitations.config;

import com.henuka.imitations.model.Product;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import java.util.List;

@Configuration
public class SearchConfig {

    @Value("${app.search.index-base-path:indexes}")
    private String indexBasePath;

    @Bean
    public org.hibernate.search.backend.lucene.cfg.LuceneBackendSettings luceneSettings() {
        return new org.hibernate.search.backend.lucene.cfg.LuceneBackendSettings()
                .setRootDirectory(indexBasePath)
                .setAnalyzer("standard");
    }
}

/**
 * Service for handling search operations
 */
@Service
class SearchService {
    private final EntityManager entityManager;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchService.class);

    public SearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Search products by query
     */
    public List<Product> searchProducts(String query, int page, int size) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Product.class)
                .where(f -> f.match()
                        .fields("name", "description", "category.name")
                        .matching(query)
                        .fuzzy(2))
                .sort(f -> f.score())
                .fetchHits(page * size, size);
    }

    /**
     * Search products by category
     */
    public List<Product> searchProductsByCategory(String category, int page, int size) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Product.class)
                .where(f -> f.match()
                        .field("category.name")
                        .matching(category))
                .sort(f -> f.field("name_sort"))
                .fetchHits(page * size, size);
    }

    /**
     * Search products by price range
     */
    public List<Product> searchProductsByPriceRange(double minPrice, double maxPrice, int page, int size) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Product.class)
                .where(f -> f.range()
                        .field("price")
                        .between(minPrice, maxPrice))
                .sort(f -> f.field("price_sort"))
                .fetchHits(page * size, size);
    }

    /**
     * Advanced product search with multiple criteria
     */
    public List<Product> advancedProductSearch(SearchCriteria criteria) {
        SearchSession searchSession = Search.session(entityManager);

        var predicate = searchSession.search(Product.class)
                .where(f -> {
                    var bool = f.bool();

                    if (criteria.getQuery() != null && !criteria.getQuery().isEmpty()) {
                        bool.must(f.match()
                                .fields("name", "description")
                                .matching(criteria.getQuery())
                                .fuzzy(2));
                    }

                    if (criteria.getCategory() != null) {
                        bool.must(f.match()
                                .field("category.name")
                                .matching(criteria.getCategory()));
                    }

                    if (criteria.getMinPrice() != null && criteria.getMaxPrice() != null) {
                        bool.must(f.range()
                                .field("price")
                                .between(criteria.getMinPrice(), criteria.getMaxPrice()));
                    }

                    if (criteria.getInStock() != null && criteria.getInStock()) {
                        bool.must(f.range()
                                .field("stockQuantity")
                                .greaterThan(0));
                    }

                    return bool;
                });

        // Add sorting
        if (criteria.getSortBy() != null) {
            switch (criteria.getSortBy()) {
                case "price_asc":
                    predicate.sort(f -> f.field("price_sort").asc());
                    break;
                case "price_desc":
                    predicate.sort(f -> f.field("price_sort").desc());
                    break;
                case "name":
                    predicate.sort(f -> f.field("name_sort").asc());
                    break;
                default:
                    predicate.sort(f -> f.score());
            }
        }

        return predicate.fetchHits(criteria.getPage() * criteria.getSize(), criteria.getSize());
    }

    /**
     * Reindex all entities
     */
    public void reindexAll() {
        try {
            SearchSession searchSession = Search.session(entityManager);
            searchSession.massIndexer()
                    .threadsToLoadObjects(4)
                    .batchSizeToLoadObjects(50)
                    .startAndWait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Indexing interrupted", e);
        }
    }
}

/**
 * Search criteria class for advanced search
 */
class SearchCriteria {
    private String query;
    private String category;
    private Double minPrice;
    private Double maxPrice;
    private Boolean inStock;
    private String sortBy;
    private int page;
    private int size;

    // Getters and setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }
    
    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }
    
    public Boolean getInStock() { return inStock; }
    public void setInStock(Boolean inStock) { this.inStock = inStock; }
    
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}

/**
 * Builder for search criteria
 */
class SearchCriteriaBuilder {
    private final SearchCriteria criteria = new SearchCriteria();

    public SearchCriteriaBuilder withQuery(String query) {
        criteria.setQuery(query);
        return this;
    }

    public SearchCriteriaBuilder withCategory(String category) {
        criteria.setCategory(category);
        return this;
    }

    public SearchCriteriaBuilder withPriceRange(Double minPrice, Double maxPrice) {
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        return this;
    }

    public SearchCriteriaBuilder withInStock(Boolean inStock) {
        criteria.setInStock(inStock);
        return this;
    }

    public SearchCriteriaBuilder withSortBy(String sortBy) {
        criteria.setSortBy(sortBy);
        return this;
    }

    public SearchCriteriaBuilder withPagination(int page, int size) {
        criteria.setPage(page);
        criteria.setSize(size);
        return this;
    }

    public SearchCriteria build() {
        return criteria;
    }
}
