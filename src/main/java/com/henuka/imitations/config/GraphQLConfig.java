package com.henuka.imitations.config;

import com.henuka.imitations.model.Product;
import com.henuka.imitations.model.Order;
import com.henuka.imitations.model.CartItem;
import com.henuka.imitations.service.ProductService;
import com.henuka.imitations.service.OrderService;
import com.henuka.imitations.service.CartService;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class GraphQLConfig {

    private final ProductService productService;
    private final OrderService orderService;
    private final CartService cartService;
    private final GraphQLMetrics graphQLMetrics;

    public GraphQLConfig(ProductService productService,
                        OrderService orderService,
                        CartService cartService,
                        GraphQLMetrics graphQLMetrics) {
        this.productService = productService;
        this.orderService = orderService;
        this.cartService = cartService;
        this.graphQLMetrics = graphQLMetrics;
    }

    /**
     * Configure GraphQL instance
     */
    @Bean
    public GraphQL graphQL() throws IOException {
        String sdl = loadSchemaDefinition();
        GraphQLSchema schema = buildSchema(sdl);
        return GraphQL.newGraphQL(schema).build();
    }

    /**
     * Load schema definition
     */
    private String loadSchemaDefinition() throws IOException {
        ClassPathResource resource = new ClassPathResource("graphql/schema.graphqls");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Build schema
     */
    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    /**
     * Build runtime wiring
     */
    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type("Query", typeWiring -> typeWiring
                        .dataFetcher("products", environment -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                java.util.List<Product> products = productService.getAllProducts();
                                graphQLMetrics.recordQuery("products", System.currentTimeMillis() - startTime);
                                return products;
                            } catch (Exception e) {
                                graphQLMetrics.recordError("products");
                                throw e;
                            }
                        })
                        .dataFetcher("product", environment -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                String id = environment.getArgument("id");
                                Product product = productService.getProductById(id);
                                graphQLMetrics.recordQuery("product", System.currentTimeMillis() - startTime);
                                return product;
                            } catch (Exception e) {
                                graphQLMetrics.recordError("product");
                                throw e;
                            }
                        })
                        .dataFetcher("orders", environment -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                String userId = environment.getArgument("userId");
                                java.util.List<Order> orders = orderService.getOrdersByUserId(userId);
                                graphQLMetrics.recordQuery("orders", System.currentTimeMillis() - startTime);
                                return orders;
                            } catch (Exception e) {
                                graphQLMetrics.recordError("orders");
                                throw e;
                            }
                        }))
                .type("Mutation", typeWiring -> typeWiring
                        .dataFetcher("createOrder", environment -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                String userId = environment.getArgument("userId");
                                Order order = orderService.createOrder(userId);
                                graphQLMetrics.recordMutation("createOrder", System.currentTimeMillis() - startTime);
                                return order;
                            } catch (Exception e) {
                                graphQLMetrics.recordError("createOrder");
                                throw e;
                            }
                        })
                        .dataFetcher("addToCart", environment -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                String userId = environment.getArgument("userId");
                                String productId = environment.getArgument("productId");
                                int quantity = environment.getArgument("quantity");
                                CartItem cartItem = cartService.addToCart(userId, productId, quantity);
                                graphQLMetrics.recordMutation("addToCart", System.currentTimeMillis() - startTime);
                                return cartItem;
                            } catch (Exception e) {
                                graphQLMetrics.recordError("addToCart");
                                throw e;
                            }
                        }))
                .build();
    }
}

/**
 * GraphQL exception handler
 */
@org.springframework.stereotype.Component
class GraphQLExceptionHandler implements graphql.execution.DataFetcherExceptionHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GraphQLExceptionHandler.class);

    @Override
    public void accept(graphql.execution.DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        log.error("GraphQL error", exception);
        
        graphql.execution.DataFetcherExceptionHandlerResult.Builder result = 
            graphql.execution.DataFetcherExceptionHandlerResult.newResult();
        
        if (exception instanceof GraphQLException) {
            result.error(graphql.GraphQLError.newError()
                    .message(exception.getMessage())
                    .build());
        } else {
            result.error(graphql.GraphQLError.newError()
                    .message("Internal server error")
                    .build());
        }
        
        handlerParameters.getSourceLocation().ifPresent(result::sourceLocation);
        handlerParameters.getPath().ifPresent(result::path);
        
        handlerParameters.getExecutionStepInfo().ifPresent(result::executionStepInfo);
    }
}

/**
 * GraphQL exception
 */
class GraphQLException extends RuntimeException {
    
    public GraphQLException(String message) {
        super(message);
    }

    public GraphQLException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * GraphQL metrics
 */
@org.springframework.stereotype.Component
class GraphQLMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public GraphQLMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordQuery(String operation, long duration) {
        registry.timer("graphql.query.duration",
            "operation", operation).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("graphql.query.count",
            "operation", operation).increment();
    }

    public void recordMutation(String operation, long duration) {
        registry.timer("graphql.mutation.duration",
            "operation", operation).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("graphql.mutation.count",
            "operation", operation).increment();
    }

    public void recordError(String operation) {
        registry.counter("graphql.error",
            "operation", operation).increment();
    }
}
