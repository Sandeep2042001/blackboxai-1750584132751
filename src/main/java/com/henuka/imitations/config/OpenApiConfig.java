package com.henuka.imitations.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Configure OpenAPI documentation
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Henuka Imitations API")
                        .version("1.0.0")
                        .description("REST API documentation for Henuka Imitations e-commerce platform")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@henukaimitations.com")
                                .url("https://henukaimitations.com/support"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addServersItem(new Server()
                        .url("https://api.henukaimitations.com")
                        .description("Production server"))
                .addServersItem(new Server()
                        .url("https://staging-api.henukaimitations.com")
                        .description("Staging server"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Local development server"))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token authentication"))
                        .addSecuritySchemes("api-key", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API key authentication")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .addSecurityItem(new SecurityRequirement().addList("api-key"));
    }
}

/**
 * Custom OpenAPI operation customizer
 */
@org.springframework.stereotype.Component
class OpenApiOperationCustomizer implements org.springdoc.core.customizers.OperationCustomizer {
    
    @Override
    public io.swagger.v3.oas.models.Operation customize(
            io.swagger.v3.oas.models.Operation operation,
            org.springframework.web.method.HandlerMethod handlerMethod) {
        
        // Add rate limiting headers
        operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                .in("header")
                .name("X-Rate-Limit-Limit")
                .description("The number of allowed requests in the current period")
                .schema(new io.swagger.v3.oas.models.media.IntegerSchema()));
        
        operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                .in("header")
                .name("X-Rate-Limit-Remaining")
                .description("The number of remaining requests in the current period")
                .schema(new io.swagger.v3.oas.models.media.IntegerSchema()));
        
        // Add common response headers
        operation.getResponses().values().forEach(response -> {
            response.addHeaderObject("X-Request-ID", 
                new io.swagger.v3.oas.models.headers.Header()
                    .description("Unique request identifier")
                    .schema(new io.swagger.v3.oas.models.media.StringSchema()));
            
            response.addHeaderObject("X-Response-Time", 
                new io.swagger.v3.oas.models.headers.Header()
                    .description("Response time in milliseconds")
                    .schema(new io.swagger.v3.oas.models.media.IntegerSchema()));
        });
        
        return operation;
    }
}

/**
 * Custom OpenAPI schema customizer
 */
@org.springframework.stereotype.Component
class OpenApiSchemaCustomizer implements org.springdoc.core.customizers.GlobalOpenApiCustomizer {
    
    @Override
    public void customise(OpenAPI openApi) {
        // Add common schemas
        openApi.getComponents().addSchemas("ErrorResponse", new io.swagger.v3.oas.models.media.Schema<>()
                .type("object")
                .addProperties("code", new io.swagger.v3.oas.models.media.StringSchema())
                .addProperties("message", new io.swagger.v3.oas.models.media.StringSchema())
                .addProperties("details", new io.swagger.v3.oas.models.media.ArraySchema()
                        .items(new io.swagger.v3.oas.models.media.StringSchema())));
        
        openApi.getComponents().addSchemas("PaginationResponse", new io.swagger.v3.oas.models.media.Schema<>()
                .type("object")
                .addProperties("page", new io.swagger.v3.oas.models.media.IntegerSchema())
                .addProperties("size", new io.swagger.v3.oas.models.media.IntegerSchema())
                .addProperties("totalElements", new io.swagger.v3.oas.models.media.IntegerSchema())
                .addProperties("totalPages", new io.swagger.v3.oas.models.media.IntegerSchema()));
    }
}

/**
 * Custom OpenAPI group configuration
 */
@org.springframework.stereotype.Component
class OpenApiGroupConfig implements org.springdoc.core.models.GroupedOpenApi.Builder {
    
    @Bean
    public org.springdoc.core.models.GroupedOpenApi publicApi() {
        return org.springdoc.core.models.GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/public/**")
                .addOpenApiCustomiser(openApi -> openApi.info(new Info()
                        .title("Public API")
                        .version("1.0.0")
                        .description("Public API endpoints")))
                .build();
    }

    @Bean
    public org.springdoc.core.models.GroupedOpenApi adminApi() {
        return org.springdoc.core.models.GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/v1/admin/**")
                .addOpenApiCustomiser(openApi -> openApi.info(new Info()
                        .title("Admin API")
                        .version("1.0.0")
                        .description("Administrative API endpoints")))
                .build();
    }
}
