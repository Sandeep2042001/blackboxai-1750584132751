package com.henuka.imitations.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.henuka.imitations.repository.search")
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    @Value("${elasticsearch.username}")
    private String username;

    @Value("${elasticsearch.password}")
    private String password;

    /**
     * Configure Elasticsearch client
     */
    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        ClientConfiguration clientConfig = ClientConfiguration.builder()
                .connectedTo(host + ":" + port)
                .withBasicAuth(username, password)
                .withConnectTimeout(java.time.Duration.ofSeconds(5))
                .withSocketTimeout(java.time.Duration.ofSeconds(3))
                .build();
        return RestClients.create(clientConfig).rest();
    }

    /**
     * Configure Elasticsearch operations
     */
    @Bean
    public ElasticsearchOperations elasticsearchTemplate() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}

/**
 * Search service
 */
@org.springframework.stereotype.Service
class SearchService {
    
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchMetrics searchMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchService.class);

    public SearchService(ElasticsearchOperations elasticsearchOperations,
                        SearchMetrics searchMetrics) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchMetrics = searchMetrics;
    }

    /**
     * Search products
     */
    public java.util.List<ProductDocument> searchProducts(String query, 
                                                        java.util.List<String> categories,
                                                        double minPrice,
                                                        double maxPrice,
                                                        int page,
                                                        int size) {
        try {
            long startTime = System.currentTimeMillis();

            org.springframework.data.elasticsearch.core.query.NativeSearchQuery searchQuery = 
                new org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder()
                    .withQuery(buildSearchQuery(query, categories, minPrice, maxPrice))
                    .withPageable(org.springframework.data.domain.PageRequest.of(page, size))
                    .build();

            org.springframework.data.elasticsearch.core.SearchHits<ProductDocument> searchHits = 
                elasticsearchOperations.search(searchQuery, ProductDocument.class);

            long duration = System.currentTimeMillis() - startTime;
            searchMetrics.recordSearch(duration, searchHits.getTotalHits());

            return searchHits.getSearchHits().stream()
                    .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            searchMetrics.recordError("search");
            throw new SearchException("Search failed", e);
        }
    }

    private org.elasticsearch.index.query.QueryBuilder buildSearchQuery(String query,
                                                                      java.util.List<String> categories,
                                                                      double minPrice,
                                                                      double maxPrice) {
        org.elasticsearch.index.query.BoolQueryBuilder boolQuery = 
            org.elasticsearch.index.query.QueryBuilders.boolQuery();

        // Text search
        if (query != null && !query.isEmpty()) {
            boolQuery.must(org.elasticsearch.index.query.QueryBuilders.multiMatchQuery(query)
                    .field("name", 2.0f)
                    .field("description")
                    .type(org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.BEST_FIELDS));
        }

        // Category filter
        if (categories != null && !categories.isEmpty()) {
            boolQuery.filter(org.elasticsearch.index.query.QueryBuilders.termsQuery("category", categories));
        }

        // Price range filter
        boolQuery.filter(org.elasticsearch.index.query.QueryBuilders.rangeQuery("price")
                .gte(minPrice)
                .lte(maxPrice));

        return boolQuery;
    }

    /**
     * Index product
     */
    public void indexProduct(ProductDocument product) {
        try {
            long startTime = System.currentTimeMillis();
            
            elasticsearchOperations.save(product);
            
            long duration = System.currentTimeMillis() - startTime;
            searchMetrics.recordIndexing(duration);
            
            log.debug("Product indexed: {}", product.getId());
        } catch (Exception e) {
            log.error("Failed to index product: {}", product.getId(), e);
            searchMetrics.recordError("index");
            throw new SearchException("Failed to index product", e);
        }
    }

    /**
     * Bulk index products
     */
    public void bulkIndexProducts(java.util.List<ProductDocument> products) {
        try {
            long startTime = System.currentTimeMillis();
            
            elasticsearchOperations.save(products);
            
            long duration = System.currentTimeMillis() - startTime;
            searchMetrics.recordBulkIndexing(duration, products.size());
            
            log.debug("Bulk indexed {} products", products.size());
        } catch (Exception e) {
            log.error("Failed to bulk index products", e);
            searchMetrics.recordError("bulk_index");
            throw new SearchException("Failed to bulk index products", e);
        }
    }
}

/**
 * Product document
 */
@org.springframework.data.elasticsearch.annotations.Document(indexName = "products")
class ProductDocument {
    
    @org.springframework.data.annotation.Id
    private String id;

    @org.springframework.data.elasticsearch.annotations.Field(type = org.springframework.data.elasticsearch.annotations.FieldType.Text)
    private String name;

    @org.springframework.data.elasticsearch.annotations.Field(type = org.springframework.data.elasticsearch.annotations.FieldType.Text)
    private String description;

    @org.springframework.data.elasticsearch.annotations.Field(type = org.springframework.data.elasticsearch.annotations.FieldType.Keyword)
    private String category;

    @org.springframework.data.elasticsearch.annotations.Field(type = org.springframework.data.elasticsearch.annotations.FieldType.Double)
    private double price;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}

/**
 * Search exception
 */
class SearchException extends RuntimeException {
    
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Search metrics
 */
@org.springframework.stereotype.Component
class SearchMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public SearchMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSearch(long duration, long hits) {
        registry.timer("elasticsearch.search.duration").record(java.time.Duration.ofMillis(duration));
        registry.summary("elasticsearch.search.hits").record(hits);
    }

    public void recordIndexing(long duration) {
        registry.timer("elasticsearch.index.duration").record(java.time.Duration.ofMillis(duration));
    }

    public void recordBulkIndexing(long duration, int count) {
        registry.timer("elasticsearch.bulk.index.duration").record(java.time.Duration.ofMillis(duration));
        registry.counter("elasticsearch.bulk.index.count").increment(count);
    }

    public void recordError(String operation) {
        registry.counter("elasticsearch.error", "operation", operation).increment();
    }
}
